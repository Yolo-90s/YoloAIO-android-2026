package com.example.yoloaio.data

import androidx.compose.ui.graphics.Color

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val initials: String = "",
    val avatarColor: Long = 0xFF6A1B9AL,
    val createdAt: Long = 0L,
    // Last known location of THIS user — written by their own device
    // whenever they open the app or a chat (throttled to once per 5 min).
    // Visible to all chat partners via `users/{uid}` read access.
    // Defaults of 0/0 mean "never recorded".
    val lastLat: Double = 0.0,
    val lastLon: Double = 0.0,
    val lastLocationAt: Long = 0L
) {
    val avatarComposeColor: Color get() = Color(avatarColor.toInt())
    val hasLocation: Boolean get() = lastLocationAt > 0L

    companion object {
        val AVATAR_PALETTE = listOf(
            0xFF6A1B9AL, 0xFF00897BL, 0xFFE65100L, 0xFFAD1457L,
            0xFF1565C0L, 0xFF2E7D32L, 0xFF4527A0L, 0xFFBF360CL
        )

        fun computeInitials(name: String): String =
            name.split(" ", "\t").mapNotNull { it.firstOrNull()?.toString() }
                .take(2).joinToString("").uppercase().ifBlank { "?" }
    }
}
