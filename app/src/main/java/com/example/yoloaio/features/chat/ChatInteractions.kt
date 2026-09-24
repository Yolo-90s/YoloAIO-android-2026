package com.example.yoloaio.features.chat

import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.features.settings.PrivacyPreferenceStore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/**
 * The handful of chat operations that are schema-identical for 1:1 and
 * group chats — they only ever need a `chatId`, never an `otherUid` —
 * so they're defined once here and called from both [ChatRepository]
 * (1:1 screens) and [GroupChatRepository] (group screens) call sites,
 * instead of being duplicated in each.
 */
object ChatInteractions {
    private val firestore get() = FirebaseModule.firestore
    private val auth get() = FirebaseModule.auth
    private val currentUid: String? get() = auth.currentUser?.uid

    /**
     * Bumps my own read cursor on this chat. Gated on the "Read receipts"
     * privacy toggle — if the user has turned it off, we never write our
     * own key, so others never see we've read (mutual-disable: see
     * [seenTimestampFor]'s reciprocal gate on the reading side).
     */
    suspend fun markChatRead(context: android.content.Context, chatId: String): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        if (!PrivacyPreferenceStore.get(context).readReceipts) return@runCatching
        firestore.collection("chats").document(chatId)
            .update("lastRead.$me", FieldValue.serverTimestamp())
            .await()
        Unit
    }

    /** Sets (or replaces) my own reaction on a message. */
    suspend fun setReaction(chatId: String, messageId: String, emoji: String): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        firestore.collection("chats").document(chatId).collection("messages").document(messageId)
            .update("reactions.$me", emoji)
            .await()
        Unit
    }

    /** Removes my own reaction from a message (tapping the same emoji again). */
    suspend fun clearReaction(chatId: String, messageId: String): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        firestore.collection("chats").document(chatId).collection("messages").document(messageId)
            .update("reactions.$me", FieldValue.delete())
            .await()
        Unit
    }

    /** Toggle helper the UI calls directly: same emoji tapped again clears it, otherwise sets it. */
    suspend fun toggleReaction(chatId: String, messageId: String, emoji: String, myCurrentReaction: String?): Result<Unit> =
        if (myCurrentReaction == emoji) clearReaction(chatId, messageId) else setReaction(chatId, messageId, emoji)
}
