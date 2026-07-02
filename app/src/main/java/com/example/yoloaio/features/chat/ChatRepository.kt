package com.example.yoloaio.features.chat

import android.net.Uri
import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.data.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository {
    private val firestore = FirebaseModule.firestore
    private val storage = FirebaseModule.storage
    private val auth = FirebaseModule.auth

    private val currentUid: String?
        get() = auth.currentUser?.uid

    private inline fun <reified T : Any> safeDocToObject(doc: com.google.firebase.firestore.DocumentSnapshot): T? =
        runCatching { doc.toObject(T::class.java) }.getOrNull()

    fun observeOtherUsers(): Flow<List<UserProfile>> = callbackFlow {
        val me = currentUid
        val registration = firestore.collection("users")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val users = snap?.documents
                    ?.mapNotNull { safeDocToObject<UserProfile>(it) }
                    ?.filter { it.uid.isNotBlank() && it.uid != me }
                    ?.sortedBy { it.displayName.lowercase() }
                    ?: emptyList()
                trySend(users)
            }
        awaitClose { registration.remove() }
    }

    suspend fun fetchUser(uid: String): UserProfile? = runCatching {
        val doc = firestore.collection("users").document(uid).get().await()
        safeDocToObject<UserProfile>(doc)
    }.getOrNull()

    private fun observeMyChats(): Flow<Map<String, ChatDoc>> = callbackFlow {
        val me = currentUid
        if (me == null) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("chats")
            .whereArrayContains("participants", me)
            .addSnapshotListener { snap, _ ->
                val map = snap?.documents
                    ?.mapNotNull { safeDocToObject<ChatDoc>(it) }
                    ?.associateBy { chat ->
                        chat.participants.firstOrNull { it != me } ?: ""
                    }
                    ?.filterKeys { it.isNotBlank() }
                    ?: emptyMap()
                trySend(map)
            }
        awaitClose { registration.remove() }
    }

    fun observeChatPreviews(): Flow<List<ChatPreview>> =
        observeOtherUsers().combine(observeMyChats()) { users, chatsByOther ->
            users.map { user ->
                val chat = chatsByOther[user.uid]
                ChatPreview(
                    user = user,
                    lastMessage = chat?.lastMessage.orEmpty(),
                    lastTimeMs = chat?.lastTime?.toDate()?.time ?: 0L
                )
            }.sortedWith(
                compareByDescending<ChatPreview> { it.lastTimeMs }
                    .thenBy { it.user.displayName.lowercase() }
            )
        }

    fun observeMessages(otherUid: String): Flow<List<ChatMessageDoc>> = callbackFlow {
        val me = currentUid
        if (me == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val chatId = ChatIds.chatIdFor(me, otherUid)
        val registration = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val msgs = snap?.documents?.mapNotNull { doc ->
                    safeDocToObject<ChatMessageDoc>(doc)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendText(otherUid: String, text: String): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Message is empty" }

        val chatId = ChatIds.chatIdFor(me, otherUid)
        val chatRef = firestore.collection("chats").document(chatId)

        chatRef.set(
            mapOf(
                "participants" to listOf(me, otherUid).sorted(),
                "lastMessage" to trimmed,
                "lastTime" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()

        chatRef.collection("messages").add(
            mapOf(
                "senderId" to me,
                "type" to ChatMessageDoc.TYPE_TEXT,
                "text" to trimmed,
                "timestamp" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    /**
     * Drops a "call invite" into the chat. The recipient sees a clickable
     * card in their message list that joins the same Jitsi room. We pass
     * the room name in the doc so both sides know what URL to load even if
     * we ever change the [CallRoom] hash scheme.
     */
    suspend fun sendCallInvite(
        otherUid: String,
        room: String,
        video: Boolean
    ): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        require(room.isNotBlank()) { "room required" }

        val chatId = ChatIds.chatIdFor(me, otherUid)
        val chatRef = firestore.collection("chats").document(chatId)
        val previewLabel = if (video) "📹 Video call" else "📞 Voice call"

        chatRef.set(
            mapOf(
                "participants" to listOf(me, otherUid).sorted(),
                "lastMessage" to previewLabel,
                "lastTime" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()

        chatRef.collection("messages").add(
            mapOf(
                "senderId" to me,
                "type" to ChatMessageDoc.TYPE_CALL,
                "callRoom" to room,
                "callVideo" to video,
                "text" to previewLabel,         // also a human-readable fallback
                "timestamp" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    /**
     * Drops a "shared location" message into the chat. The recipient sees
     * a clickable card with the coordinates + an Open-in-Maps button. The
     * sender can later refresh via [updateLocation] which overwrites the
     * same doc rather than appending a new message — keeps the chat clean
     * and gives both sides a single "live" pin to look at.
     */
    suspend fun sendLocation(
        otherUid: String,
        lat: Double,
        lon: Double
    ): Result<String> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val chatId = ChatIds.chatIdFor(me, otherUid)
        val chatRef = firestore.collection("chats").document(chatId)
        val now = System.currentTimeMillis()

        chatRef.set(
            mapOf(
                "participants" to listOf(me, otherUid).sorted(),
                "lastMessage" to "📍 Location shared",
                "lastTime" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()

        val ref = chatRef.collection("messages").add(
            mapOf(
                "senderId" to me,
                "type" to ChatMessageDoc.TYPE_LOCATION,
                "locLat" to lat,
                "locLon" to lon,
                "locUpdatedAt" to now,
                "text" to "📍 Location shared",
                "timestamp" to FieldValue.serverTimestamp()
            )
        ).await()
        ref.id
    }

    /**
     * Updates an already-sent location message with fresh coordinates.
     * Used by the "Refresh" affordance on the sender's own location card.
     */
    suspend fun updateLocation(
        otherUid: String,
        messageId: String,
        lat: Double,
        lon: Double
    ): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        require(messageId.isNotBlank()) { "messageId required" }
        val chatId = ChatIds.chatIdFor(me, otherUid)
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.collection("messages").document(messageId).update(
            mapOf(
                "locLat" to lat,
                "locLon" to lon,
                "locUpdatedAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    suspend fun sendMedia(
        otherUid: String,
        uri: Uri,
        type: String,
        label: String? = null
    ): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        require(type == ChatMessageDoc.TYPE_IMAGE || type == ChatMessageDoc.TYPE_GIF) {
            "Unsupported media type: $type"
        }

        val chatId = ChatIds.chatIdFor(me, otherUid)
        val ext = if (type == ChatMessageDoc.TYPE_GIF) "gif" else "jpg"
        val storageRef = storage.reference.child(
            "chats/$chatId/${UUID.randomUUID()}.$ext"
        )
        storageRef.putFile(uri).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()

        val chatRef = firestore.collection("chats").document(chatId)
        val previewLabel = if (type == ChatMessageDoc.TYPE_GIF) "[GIF]" else "[Photo]"

        chatRef.set(
            mapOf(
                "participants" to listOf(me, otherUid).sorted(),
                "lastMessage" to previewLabel,
                "lastTime" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()

        chatRef.collection("messages").add(
            mapOf(
                "senderId" to me,
                "type" to type,
                "mediaUrl" to downloadUrl,
                "mediaLabel" to label,
                "timestamp" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    /**
     * Wipes the conversation with [otherUid] — deletes every message in the
     * subcollection, then the parent chat doc. Firestore has no recursive
     * delete from the client, so we page through messages 100 at a time and
     * commit a batched delete for each page.
     *
     * Note: media uploaded into Firebase Storage for this chat is NOT pruned
     * — those files persist under `chats/{chatId}/...`. Add a Storage cleanup
     * pass if you want full evidence-free deletion.
     */
    suspend fun deleteChat(otherUid: String): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val chatId = ChatIds.chatIdFor(me, otherUid)
        val chatRef = firestore.collection("chats").document(chatId)

        while (true) {
            val page = chatRef.collection("messages")
                .limit(100)
                .get()
                .await()
            if (page.isEmpty) break
            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (page.size() < 100) break
        }

        chatRef.delete().await()
        Unit
    }
}
