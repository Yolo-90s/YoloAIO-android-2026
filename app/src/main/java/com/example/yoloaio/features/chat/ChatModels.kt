package com.example.yoloaio.features.chat

import com.example.yoloaio.data.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class ChatMessageDoc(
    val id: String = "",
    val senderId: String = "",
    val type: String = TYPE_TEXT,
    val text: String? = null,
    val mediaUrl: String? = null,
    val mediaLabel: String? = null,
    // Populated only when [type] is TYPE_CALL. Both ends of the chat
    // join the same hashed room, derived from sorted UIDs — no signaling
    // round-trip needed. `callVideo` distinguishes audio-only from video.
    val callRoom: String? = null,
    val callVideo: Boolean = false,
    // Populated only when [type] is TYPE_LOCATION. `locUpdatedAt` is the
    // wall-clock millis of the most recent refresh (NOT the doc's
    // `@ServerTimestamp` — that's pinned to original send time so the
    // message keeps its place in the conversation when the sender hits
    // refresh). UI shows "Updated 3 min ago" off this field.
    val locLat: Double = 0.0,
    val locLon: Double = 0.0,
    val locUpdatedAt: Long = 0L,
    @ServerTimestamp val timestamp: Timestamp? = null
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_GIF = "gif"
        const val TYPE_CALL = "call"
        const val TYPE_LOCATION = "location"
    }
}

data class ChatDoc(
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTime: Timestamp? = null
)

data class ChatPreview(
    val user: UserProfile,
    val lastMessage: String,
    val lastTimeMs: Long
)

object ChatIds {
    fun chatIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")
}
