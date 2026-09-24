package com.example.yoloaio.features.chat

import com.google.firebase.Timestamp

/**
 * `chats/{chatId}` doc shape for a GROUP chat — chatId is a fresh
 * auto-id here (never a uid join, since there can be >2 members).
 * `adminUids` (not [ownerUid]) is what actually gates admin-only writes
 * — see firestore.rules' `chats/{chatId}` update clauses (2)/(3).
 * `participants` are CONFIRMED/joined members only, same field/semantics
 * as a 1:1 [ChatDoc] — someone invited-but-not-yet-accepted is tracked
 * separately in [GroupInviteDoc], not in this list.
 */
data class GroupChatDoc(
    val isGroup: Boolean = false,
    val groupName: String = "",
    val groupPhotoUrl: String = "",
    val ownerUid: String = "",
    val adminUids: List<String> = emptyList(),
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTime: Timestamp? = null,
    val lastRead: Map<String, Timestamp> = emptyMap(),
    val createdAt: Timestamp? = null
)

/** One row in the chat list for a group the current user has joined. */
data class GroupChatPreview(
    val chatId: String,
    val groupName: String,
    val groupPhotoUrl: String,
    val memberCount: Int,
    val lastMessage: String,
    val lastTimeMs: Long
)

/**
 * `groupInvites/{chatId}_{uid}` — deterministic id (not an auto-id) so a
 * security rule can reference one specific invite by constructing its
 * path from `chatId` + the requester's own uid. See firestore.rules.
 */
data class GroupInviteDoc(
    val chatId: String = "",
    val groupName: String = "",
    val invitedUid: String = "",
    val invitedByUid: String = "",
    val invitedByName: String = "",
    val status: String = STATUS_PENDING,
    val createdAt: Timestamp? = null,
    val respondedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
    }
}

/**
 * One row the user is pending a decision on, shown in [PendingInvitesBanner].
 * Also reused, from the inviter's side, by [GroupInfoScreen]'s "pending
 * invites I sent" list — [invitedUid] is blank for the invitee-facing
 * query (not needed there) and populated for the inviter-facing one.
 */
data class PendingInvite(
    val inviteId: String,
    val chatId: String,
    val groupName: String,
    val invitedByName: String,
    val invitedUid: String = ""
)

/** Union of a 1:1 row and a group row, so the Chat list can render both in one merged, sorted list. */
sealed interface ConversationItem {
    val lastTimeMs: Long

    data class Direct(val preview: ChatPreview) : ConversationItem {
        override val lastTimeMs: Long get() = preview.lastTimeMs
    }

    data class Group(val preview: GroupChatPreview) : ConversationItem {
        override val lastTimeMs: Long get() = preview.lastTimeMs
    }
}
