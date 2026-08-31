package com.example.yoloaio.features.walkietalkie

import com.google.firebase.Timestamp

data class SdpPayload(
    val sdp: String = "",
    val type: String = ""
)

data class IcePayload(
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0,
    val candidate: String = ""
)

data class WalkieChannelDoc(
    val ownerUid: String = "",
    val ownerDisplayName: String = "",
    val createdAt: Timestamp? = null,
    val live: Boolean = false,
    val updatedAt: Timestamp? = null
)

data class WalkieSessionDoc(
    val offer: SdpPayload? = null,
    val answer: SdpPayload? = null,
    val createdAt: Timestamp? = null,
    // True when this session was opened by an admin browsing live channels
    // (see WalkieTalkieRepository.observeLiveChannels) rather than a
    // normal Receive-by-code flow. The transmitter's engine excludes
    // admin-flagged sessions from the listener count it displays — an
    // admin tuning in doesn't change what the broadcaster sees.
    val isAdminMonitor: Boolean = false
)

/** One currently-live channel, as surfaced to an admin browsing them. */
data class LiveChannel(
    val code: String = "",
    val ownerUid: String = "",
    val ownerDisplayName: String = ""
)

enum class WalkieRole { TRANSMIT, RECEIVE }

sealed class WalkieStatus {
    data object Idle : WalkieStatus()
    data object Connecting : WalkieStatus()
    data class Live(val listenerCount: Int) : WalkieStatus()
    data object Receiving : WalkieStatus()
    data class Error(val message: String) : WalkieStatus()
}
