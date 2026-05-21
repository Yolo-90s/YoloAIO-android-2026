package com.example.yoloaio.features.community

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * Single Firestore-collection model: `communityMessages`.
 *
 * Denormalises sender info (name + avatar color) so rendering doesn't
 * need a join against `/users/{uid}` per message. If a user later changes
 * their display name, only future messages reflect the change — past
 * messages keep the name as it was at send time. That's fine for a
 * forum-style feed.
 */
data class CommunityMessageDoc(
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarColor: Long = 0xFF6A1B9AL,
    val text: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null
)

data class CommunityMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarColor: Long,
    val text: String,
    val timestampMs: Long
)
