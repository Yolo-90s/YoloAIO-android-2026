package com.example.yoloaio.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Single source of truth for the app's notification channels. Idempotent —
 * calling [ensure] more than once is a no-op on Android 8+ (channels exist
 * for the lifetime of the install) and a no-op on older Android (no concept
 * of channels).
 */
object NotificationChannels {

    const val CHAT_CHANNEL_ID = "chat_messages"
    private const val CHAT_CHANNEL_NAME = "Chat messages"
    private const val CHAT_CHANNEL_DESCRIPTION =
        "Heads-up notifications when someone sends you a chat message."

    const val MUSIC_CHANNEL_ID = "music_playback"
    private const val MUSIC_CHANNEL_NAME = "Music playback"
    private const val MUSIC_CHANNEL_DESCRIPTION =
        "Ongoing notification with play/pause/next controls for the music player."

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(CHAT_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHAT_CHANNEL_ID,
                    CHAT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHAT_CHANNEL_DESCRIPTION
                    enableLights(true)
                    enableVibration(true)
                }
            )
        }

        if (nm.getNotificationChannel(MUSIC_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                // Low importance so the media notification doesn't buzz or
                // pop up over the user's content — it's ambient transport.
                NotificationChannel(
                    MUSIC_CHANNEL_ID,
                    MUSIC_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = MUSIC_CHANNEL_DESCRIPTION
                    setShowBadge(false)
                }
            )
        }
    }
}
