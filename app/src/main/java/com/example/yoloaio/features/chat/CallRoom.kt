package com.example.yoloaio.features.chat

import java.security.MessageDigest

/**
 * Deterministic Jitsi room name shared by the two participants in a chat.
 * Same UIDs → same room name regardless of who initiates, so the recipient
 * joining via the chat invite always lands in the same call as the caller.
 *
 * Hashed via SHA-1 with a "yolo-" prefix; 16 hex chars after the prefix is
 * 64 bits of entropy — practically un-guessable. Public Jitsi rooms aren't
 * cryptographically private regardless, but this keeps random URL probes
 * from finding active calls.
 */
object CallRoom {
    fun forUsers(uidA: String, uidB: String): String {
        require(uidA.isNotBlank() && uidB.isNotBlank()) { "uids required" }
        val canonical = listOf(uidA, uidB).sorted().joinToString("-")
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "yolo-${hash.take(16)}"
    }
}
