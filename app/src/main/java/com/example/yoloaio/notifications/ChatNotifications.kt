package com.example.yoloaio.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.yoloaio.MainActivity
import com.example.yoloaio.R
import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.data.UserProfile
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * App-foreground chat notification engine. Wires up nested Firestore listeners
 * — outer one on the chats list, inner one per chat on its messages collection
 * — and pops a local notification when a message arrives from someone else and
 * we're not actively viewing that conversation.
 *
 * This is the Option-A path: works while the process is alive (foreground or
 * recently backgrounded). It does NOT survive a force-stop / OS-kill. For
 * that you need FCM + a Cloud Function backend, which can layer on top of
 * this code without changing it.
 *
 * Generalized for group chats: every identity/suppression/dedup concept that
 * used to be "the other participant's uid" is now a [chatId]-keyed
 * `notificationKey` (== the partner uid for a 1:1 chat, == the chatId for a
 * group) — a 1:1 chat's key happens to equal its partner uid, so behavior
 * there is unchanged bit-for-bit; only group chats exercise the new path.
 */
object ChatNotifications {

    private const val TAG = "ChatNotifications"
    const val EXTRA_OPEN_CHAT_PARTNER_UID = "openChatWithUid"
    const val EXTRA_OPEN_GROUP_CHAT_ID = "openGroupChatId"

    /** Set by [com.example.yoloaio.features.chat.ChatConversationScreen] /
     *  [com.example.yoloaio.features.chat.GroupChatScreen] when visible —
     *  1:1 sets this to the partner's uid, group sets it to the chatId
     *  (same value space as [attachMessagesListener]'s notificationKey).
     *  The observer suppresses notifications for whichever chat is active. */
    @Volatile
    var activeChatPartnerUid: String? = null

    private val firestore get() = FirebaseModule.firestore
    private val auth get() = FirebaseModule.auth

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var chatsListener: ListenerRegistration? = null
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()
    private val processStartMs = System.currentTimeMillis()
    private val displayNameCache = mutableMapOf<String, String>()

    /** Idempotent — calling [start] twice is a no-op. Tied to MainActivity's
     *  lifecycle so it stops automatically when the activity is destroyed. */
    @Synchronized
    fun start(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        if (chatsListener != null) return

        NotificationChannels.ensure(context)
        val appContext = context.applicationContext

        chatsListener = firestore.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    if (err != null) Log.w(TAG, "chats listener error: ${err.message}")
                    return@addSnapshotListener
                }
                val seenChatIds = mutableSetOf<String>()
                snap.documents.forEach { doc ->
                    seenChatIds += doc.id
                    if (messageListeners.containsKey(doc.id)) return@forEach
                    val isGroup = doc.getBoolean("isGroup") == true
                    if (isGroup) {
                        val groupName = doc.getString("groupName")?.takeIf { it.isNotBlank() } ?: "Group"
                        attachMessagesListener(appContext, doc.id, isGroup = true, partnerUid = null, groupName = groupName)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val participants = (doc.get("participants") as? List<String>).orEmpty()
                        val partnerUid = participants.firstOrNull { it != uid } ?: return@forEach
                        attachMessagesListener(appContext, doc.id, isGroup = false, partnerUid = partnerUid, groupName = null)
                    }
                }
                // Drop listeners for any chat we're no longer part of.
                val toRemove = messageListeners.keys - seenChatIds
                toRemove.forEach { id ->
                    messageListeners.remove(id)?.remove()
                }
            }
    }

    @Synchronized
    fun stop() {
        chatsListener?.remove()
        chatsListener = null
        messageListeners.values.forEach { it.remove() }
        messageListeners.clear()
        activeChatPartnerUid = null
    }

    private fun attachMessagesListener(
        appContext: Context,
        chatId: String,
        isGroup: Boolean,
        partnerUid: String?,
        groupName: String?
    ) {
        val uid = auth.currentUser?.uid ?: return
        val notificationKey = if (isGroup) chatId else partnerUid ?: return
        val attachedAtMs = System.currentTimeMillis()
        val registration = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    if (err != null) Log.w(TAG, "messages listener error ($chatId): ${err.message}")
                    return@addSnapshotListener
                }
                snap.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .mapNotNull { it.document }
                    .filter { doc ->
                        // Only notify for messages that arrived AFTER both the
                        // process and this listener came up. Without this guard
                        // every fresh launch would re-buzz every message in the
                        // user's history. System messages (group join/leave/
                        // rename announcements) never trigger a notification.
                        val tsMs = doc.getDate("timestamp")?.time ?: 0L
                        tsMs > attachedAtMs &&
                            tsMs > processStartMs &&
                            doc.getString("senderId") != uid &&
                            doc.getString("type") != "system"
                    }
                    .forEach { doc ->
                        // Live-view suppression: if you're already reading this
                        // chat, the message arrives in-line via the chat screen's
                        // listener — no need to also pop a system notification.
                        if (activeChatPartnerUid == notificationKey) return@forEach
                        scope.launch { postFor(appContext, doc, chatId, isGroup, notificationKey, partnerUid, groupName) }
                    }
            }
        messageListeners[chatId] = registration
    }

    private suspend fun postFor(
        appContext: Context,
        doc: DocumentSnapshot,
        chatId: String,
        isGroup: Boolean,
        notificationKey: String,
        partnerUid: String?,
        groupName: String?
    ) {
        val preview = previewFor(doc)
        if (isGroup) {
            val senderName = resolveDisplayName(doc.getString("senderId").orEmpty())
            post(
                appContext = appContext,
                title = groupName ?: "Group",
                body = "$senderName: $preview",
                notificationKey = notificationKey,
                isGroup = true,
                chatId = chatId
            )
        } else {
            val senderName = resolveDisplayName(partnerUid.orEmpty())
            post(
                appContext = appContext,
                title = senderName,
                body = preview,
                notificationKey = notificationKey,
                isGroup = false,
                chatId = chatId
            )
        }
    }

    private suspend fun resolveDisplayName(uid: String): String {
        if (uid.isBlank()) return "Someone"
        displayNameCache[uid]?.let { return it }
        val name = runCatching {
            val snap = firestore.collection("users").document(uid).get().await()
            snap.toObject(UserProfile::class.java)?.displayName?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "Someone"
        displayNameCache[uid] = name
        return name
    }

    private fun previewFor(doc: DocumentSnapshot): String {
        val type = doc.getString("type") ?: "text"
        return when (type) {
            "image" -> "📷 Photo"
            "gif" -> "🎞️ GIF"
            else -> doc.getString("text")?.takeIf { it.isNotBlank() }
                ?: doc.getString("mediaLabel")?.takeIf { it.isNotBlank() }
                ?: "New message"
        }
    }

    private fun post(
        appContext: Context,
        title: String,
        body: String,
        notificationKey: String,
        isGroup: Boolean,
        chatId: String
    ) {
        // Skip silently if the user hasn't granted POST_NOTIFICATIONS. We don't
        // pester them here — MainActivity handles the runtime request.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val deepLink = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (isGroup) {
                data = Uri.parse("yoloaio://group/$chatId")
                putExtra(EXTRA_OPEN_GROUP_CHAT_ID, chatId)
            } else {
                data = Uri.parse("yoloaio://chat/$notificationKey")
                putExtra(EXTRA_OPEN_CHAT_PARTNER_UID, notificationKey)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationKey.hashCode(),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, NotificationChannels.CHAT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Use the notification key (partner uid for 1:1, chatId for group) as
        // the notification id so successive messages from the same
        // conversation replace (not stack) — matches typical messenger UX.
        NotificationManagerCompat.from(appContext)
            .notify(notificationKey.hashCode(), notification)
    }
}
