package com.example.yoloaio.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage

/**
 * Compose-friendly facade over the Cast SDK. Singleton — same instance is
 * read by the music player (to route playback) and the UI (to render
 * connected-state UI). Surfaces just enough state to drive a Cast button
 * and a "now casting to X" badge.
 *
 * Init: call [init] once from MainActivity. Safe to call multiple times.
 * If the device doesn't have Google Play Services, [init] returns false and
 * everything else no-ops cleanly — the UI just shows no Cast affordance.
 */
class CastManager private constructor(context: Context) {

    private val tag = "CastManager"
    private val appContext = context.applicationContext

    var isConnected by mutableStateOf(false)
        private set
    var deviceName by mutableStateOf<String?>(null)
        private set
    var isInitialized by mutableStateOf(false)
        private set

    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            onSessionConnected(session)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            onSessionConnected(session)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            currentSession = null
            isConnected = false
            deviceName = null
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
    }

    fun init(): Boolean {
        if (isInitialized) return true
        return try {
            val ctx = CastContext.getSharedInstance(appContext)
            castContext = ctx
            ctx.sessionManager.addSessionManagerListener(
                sessionListener, CastSession::class.java
            )
            ctx.sessionManager.currentCastSession?.let { existing ->
                if (existing.isConnected) onSessionConnected(existing)
            }
            isInitialized = true
            true
        } catch (e: Exception) {
            // No Google Play Services, or the device's Play Services build is
            // too old to support Cast Framework. Stay un-initialised — every
            // other entry-point checks `isInitialized` and no-ops gracefully.
            Log.w(tag, "Cast init failed (likely no GPS): ${e.message}")
            false
        }
    }

    private fun onSessionConnected(session: CastSession) {
        currentSession = session
        isConnected = true
        deviceName = session.castDevice?.friendlyName
    }

    /**
     * Push a track to the connected cast device. Returns true when the load
     * request was queued, false if there's no active session (caller should
     * keep playing locally).
     */
    fun loadAudio(
        streamUrl: String,
        title: String,
        artist: String,
        albumArtUrl: String? = null,
        durationMs: Long = 0L,
        contentType: String = "audio/mp4"
    ): Boolean {
        val client = currentSession?.remoteMediaClient ?: return false

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_ARTIST, artist)
            if (!albumArtUrl.isNullOrBlank()) {
                addImage(WebImage(Uri.parse(albumArtUrl)))
            }
        }
        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .apply { if (durationMs > 0) setStreamDuration(durationMs) }
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        return try {
            client.load(request)
            true
        } catch (e: Exception) {
            Log.w(tag, "remote load failed: ${e.message}")
            false
        }
    }

    fun togglePlayPause() {
        try {
            currentSession?.remoteMediaClient?.togglePlayback()
        } catch (_: Exception) {
        }
    }

    fun stopRemote() {
        try {
            currentSession?.remoteMediaClient?.stop()
        } catch (_: Exception) {
        }
    }

    /** Disconnect from the current cast device and return audio to the phone. */
    fun endCurrentSession() {
        try {
            castContext?.sessionManager?.endCurrentSession(true)
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: CastManager? = null

        fun get(context: Context): CastManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CastManager(context).also { INSTANCE = it }
            }
    }
}
