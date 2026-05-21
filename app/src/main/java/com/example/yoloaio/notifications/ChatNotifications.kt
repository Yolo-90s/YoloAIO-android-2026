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
 */
object ChatNotifications {

    private const val TAG = "ChatNotifications"
    const val EXTRA_OPEN_CHAT_PARTNER_UID = "openChatWithUid"

    /** Set by [com.example.yoloaio.features.chat.ChatConversationScreen] when
     *  visible. The observer suppresses notifications for this chat. */
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
                    @Suppress("UNCHECKED_CAST")
                    val participants = (doc.get("participants") as? List<String>).orEmpty()
                    val partnerUid = participants.firstOrNull { it != uid } ?: return@forEach
                    attachMessagesListener(appContext, doc.id, partnerUid)
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
        partnerUid: String
    ) {
        val uid = auth.currentUser?.uid ?: return
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
                        // user's history.
                        val tsMs = doc.getDate("timestamp")?.time ?: 0L
                        tsMs > attachedAtMs &&
                            tsMs > processStartMs &&
                            doc.getString("senderId") != uid
                    }
                    .forEach { doc ->
                        // Live-view suppression: if you're already reading this
                        // chat, the message arrives in-line via the chat screen's
                        // listener — no need to also pop a system notification.
                        if (activeChatPartnerUid == partnerUid) return@forEach
                        scope.launch { postFor(appContext, doc, partnerUid) }
                    }
            }
        messageListeners[chatId] = registration
    }

    private suspend fun postFor(
        appContext: Context,
        doc: DocumentSnapshot,
        partnerUid: String
    ) {
        val senderName = resolveDisplayName(partnerUid)
        val preview = previewFor(doc)
        post(appContext, doc.id, senderName, preview, partnerUid)
    }

    private suspend fun resolveDisplayName(uid: String): String {
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
        notificationKey: String,
        senderName: String,
        preview: String,
        chatPartnerUid: String
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
            data = Uri.parse("yoloaio://chat/$chatPartnerUid")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT_PARTNER_UID, chatPartnerUid)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            chatPartnerUid.hashCode(),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, NotificationChannels.CHAT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Use partner uid as the notification id so successive messages from
        // the same person replace (not stack) — matches typical messenger UX.
        NotificationManagerCompat.from(appContext)
            .notify(chatPartnerUid.hashCode(), notification)
    }
}
