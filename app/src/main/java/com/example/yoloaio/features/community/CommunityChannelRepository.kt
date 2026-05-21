package com.example.yoloaio.features.community

import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.data.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CommunityChannelRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    private val col = firestore.collection("communityMessages")

    // Cached on first send to avoid an extra `/users/{uid}` fetch per message.
    private var cachedSender: SenderInfo? = null

    private data class SenderInfo(
        val uid: String,
        val name: String,
        val avatarColor: Long
    )

    /**
     * Streams the most recent messages, chronologically ordered for display.
     * We query descending + limit so Firestore reads only the latest [limit]
     * docs, then reverse client-side so the newest sits at the bottom of
     * the list (chat-style).
     */
    fun observeMessages(limit: Long = 200): Flow<List<CommunityMessage>> = callbackFlow {
        val registration = col
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snap?.documents?.mapNotNull { doc ->
                    val data = doc.toObject(CommunityMessageDoc::class.java)
                        ?: return@mapNotNull null
                    // Filter out un-stamped local-pending writes — they have a
                    // null timestamp until the server confirms.
                    val ts = data.timestamp?.toDate()?.time ?: return@mapNotNull null
                    CommunityMessage(
                        id = doc.id,
                        senderId = data.senderId,
                        senderName = data.senderName,
                        senderAvatarColor = data.senderAvatarColor,
                        text = data.text,
                        timestampMs = ts
                    )
                }?.reversed() ?: emptyList()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendMessage(text: String): Result<Unit> = runCatching {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "Message can't be empty" }
        require(cleanText.length <= 2000) { "Message is too long (2000 char limit)" }

        val sender = sender()
        val data = mapOf(
            "senderId" to sender.uid,
            "senderName" to sender.name,
            "senderAvatarColor" to sender.avatarColor,
            "text" to cleanText,
            "timestamp" to FieldValue.serverTimestamp()
        )
        col.add(data).await()
        Unit
    }

    suspend fun deleteMessage(message: CommunityMessage): Result<Unit> = runCatching {
        val me = auth.currentUser?.uid ?: error("Not signed in")
        require(message.senderId == me) { "You can only delete your own messages" }
        col.document(message.id).delete().await()
        Unit
    }

    private suspend fun sender(): SenderInfo {
        cachedSender?.let { return it }
        val user = auth.currentUser ?: error("Not signed in")
        val profile = runCatching {
            firestore.collection("users").document(user.uid).get().await()
                .toObject(UserProfile::class.java)
        }.getOrNull()

        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: profile?.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "Anonymous"
        val color = profile?.avatarColor ?: UserProfile.AVATAR_PALETTE.random()

        val info = SenderInfo(uid = user.uid, name = name, avatarColor = color)
        cachedSender = info
        return info
    }
}
