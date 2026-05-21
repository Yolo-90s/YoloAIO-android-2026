package com.example.yoloaio.features.chat

import android.content.Context
import com.example.yoloaio.data.FirebaseModule
import org.jitsi.meet.sdk.JitsiMeet
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.jitsi.meet.sdk.JitsiMeetUserInfo
import java.net.URL

/**
 * Launcher for the Jitsi Meet **native** SDK conference activity. This
 * replaces the previous WebView-based call surface, which couldn't
 * enumerate cameras reliably on Vivo / Android 16 OEM WebViews.
 *
 * Why a launcher and not a Compose screen? `JitsiMeetActivity` is a
 * standalone, full-screen Activity that owns its own lifecycle, UI,
 * audio routing, and orientation handling. The cleanest integration is
 * to launch it via Intent from the chat header — control returns to
 * the chat after the user hangs up.
 */
object JitsiCallLauncher {

    /**
     * One-time process-level default config (server URL + feature flags).
     * Server URL is configurable per-call so a Firestore-driven change
     * to `config/app.jitsiServerUrl` takes effect on the next call
     * without rebuilding the app. We call [ensureDefaults] before every
     * launch — it's cheap and self-skips on the no-change path.
     */
    private fun ensureDefaults(serverUrl: String) {
        // Re-apply if the server URL changed since last call. Jitsi
        // caches the default; without this it'd silently keep using
        // the URL from the first call of the process.
        val existing = JitsiMeet.getDefaultConferenceOptions()
        if (existing != null) {
            JitsiMeet.setDefaultConferenceOptions(
                JitsiMeetConferenceOptions.Builder()
                    .setServerURL(URL(serverUrl))
                    .setFeatureFlag("welcomepage.enabled", false)
                    .setFeatureFlag("prejoinpage.enabled", false)
                    .setFeatureFlag("invite.enabled", false)
                    .setFeatureFlag("add-people.enabled", false)
                    .setFeatureFlag("calendar.enabled", false)
                    .setFeatureFlag("call-integration.enabled", false)
                    .setFeatureFlag("recording.enabled", false)
                    .setFeatureFlag("live-streaming.enabled", false)
                    .build()
            )
            return
        }
        val defaults = JitsiMeetConferenceOptions.Builder()
            .setServerURL(URL(serverUrl))
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("prejoinpage.enabled", false)
            .setFeatureFlag("invite.enabled", false)
            .setFeatureFlag("add-people.enabled", false)
            .setFeatureFlag("calendar.enabled", false)
            .setFeatureFlag("call-integration.enabled", false) // avoid ConnectionService quirks
            .setFeatureFlag("recording.enabled", false)
            .setFeatureFlag("live-streaming.enabled", false)
            .build()
        JitsiMeet.setDefaultConferenceOptions(defaults)
    }

    fun launch(
        context: Context,
        roomName: String,
        video: Boolean,
        serverUrl: String = "https://meet.jit.si"
    ) {
        val effectiveUrl = serverUrl.takeIf { it.isNotBlank() } ?: "https://meet.jit.si"
        ensureDefaults(effectiveUrl)

        val user = FirebaseModule.auth.currentUser
        val userInfo = JitsiMeetUserInfo().apply {
            displayName = user?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore('@')
                ?: "Yolo user"
            user?.email?.let { email = it }
        }

        val options = JitsiMeetConferenceOptions.Builder()
            .setRoom(roomName)
            .setUserInfo(userInfo)
            .setAudioOnly(!video)
            .setAudioMuted(false)
            .setVideoMuted(!video)
            .build()

        JitsiMeetActivity.launch(context, options)
    }
}
