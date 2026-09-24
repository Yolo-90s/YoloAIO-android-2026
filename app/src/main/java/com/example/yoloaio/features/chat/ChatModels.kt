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
    // Populated only when [type] is TYPE_MINDMATCH — the pairing code the
    // recipient taps into to join the sender's MindMatch session.
    val mindMatchCode: String? = null,
    // Populated only when [type] is TYPE_SYSTEM — a plain group-lifecycle
    // announcement ("X joined the group", "Y removed Z", "Group renamed
    // to W"); rendered as a centered caption, not a bubble. `text` still
    // carries the human-readable line so notification fallback works.
    // Set once at send time, immutable. Denormalized (not a live lookup)
    // so rendering a reply never needs an extra read, and the snippet
    // stays stable even if the original message is later edited/deleted.
    val replyToId: String? = null,
    val replyToSenderId: String? = null,
    val replyToText: String? = null,
    // uid -> emoji, one reaction per user per message. Written via a
    // dotted-path update (`reactions.<uid>`) touching only the reacting
    // user's own key — same idiom as config/app.menuMinRole.<key>.
    val reactions: Map<String, String> = emptyMap(),
    @ServerTimestamp val timestamp: Timestamp? = null
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_GIF = "gif"
        const val TYPE_CALL = "call"
        const val TYPE_LOCATION = "location"
        const val TYPE_MINDMATCH = "mindmatch"
        const val TYPE_SYSTEM = "system"
    }
}

data class ChatDoc(
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTime: Timestamp? = null,
    // Per-participant read cursor — shared by 1:1 and group chats, used
    // to compute sent/seen ticks client-side (a message is "seen" by a
    // participant once their lastRead entry is >= the message's own
    // timestamp). One write per "opened/scrolled this chat" event, not
    // one write per message read.
    val lastRead: Map<String, Timestamp> = emptyMap()
)

data class ChatPreview(
    val user: UserProfile,
    val lastMessage: String,
    val lastTimeMs: Long
)

/**
 * A single reply-to-this-message affordance's resolved snapshot — built
 * from whichever message the user long-pressed "Reply" on, then passed
 * down to a send call so the new message can denormalize
 * `replyToId/replyToSenderId/replyToText` onto itself.
 */
data class ReplyPreview(
    val messageId: String,
    val senderId: String,
    val text: String
)

object ChatIds {
    fun chatIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")
}
