package com.example.yoloaio.features.music

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.yoloaio.MainActivity
import com.example.yoloaio.R
import com.example.yoloaio.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps [MusicPlayer] alive while the user navigates
 * away from the Music screen and surfaces a system media notification with
 * play / pause / next / prev / close controls. Also drives a
 * [MediaSessionCompat] so the lockscreen widget, Bluetooth headset buttons,
 * and the Quick Settings media tile all control the same playback.
 *
 * Lifecycle:
 *   1. [MusicPlayer.startTrack] calls [ensureStarted] on first play
 *   2. Service onCreate spins up the MediaSession and a [snapshotFlow] that
 *      observes the singleton player's Compose state
 *   3. Every state change → rebuild MediaSession state + notification
 *   4. User dismisses the notification (delete intent → ACTION_CLOSE) →
 *      service releases the player + stops itself
 */
class MusicPlaybackService : Service() {

    companion object {
        private const val TAG = "MusicPlayback"
        private const val NOTIFICATION_ID = 101101

        const val ACTION_TOGGLE = "com.example.yoloaio.music.TOGGLE"
        const val ACTION_NEXT = "com.example.yoloaio.music.NEXT"
        const val ACTION_PREV = "com.example.yoloaio.music.PREV"
        const val ACTION_CLOSE = "com.example.yoloaio.music.CLOSE"

        fun ensureStarted(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                Log.w(TAG, "startForegroundService failed: ${it.message}")
            }
        }
    }

    private val player by lazy { MusicPlayer.get(applicationContext) }
    private var mediaSession: MediaSessionCompat? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)

        mediaSession = MediaSessionCompat(this, "YoloMusic").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player.toggle() }
                override fun onPause() { player.toggle() }
                override fun onSkipToNext() { player.next() }
                override fun onSkipToPrevious() { player.previous() }
                override fun onStop() { stopAndRelease() }
                override fun onSeekTo(pos: Long) { player.seekTo(pos) }
            })
            isActive = true
        }

        observerJob = scope.launch {
            // snapshotFlow watches our Compose state holders from a regular
            // coroutine — same trick the Snapshot system uses for derivedState.
            snapshotFlow {
                PlayerSnapshot(
                    track = player.current,
                    isPlaying = player.isPlaying,
                    isLoading = player.isLoading,
                    durationMs = player.durationMs,
                    positionMs = if (player.isPrepared) player.currentPositionMs() else 0L
                )
            }.collectLatest { snapshot ->
                refreshNotificationAndState(snapshot)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> player.toggle()
            ACTION_NEXT -> player.next()
            ACTION_PREV -> player.previous()
            ACTION_CLOSE -> {
                stopAndRelease()
                return START_NOT_STICKY
            }
            else -> {
                // First start — promote to foreground immediately so the OS
                // doesn't kill us before snapshotFlow fires its first emission.
                val track = player.current
                if (track != null) {
                    startForegroundWith(buildNotification(track))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun refreshNotificationAndState(snapshot: PlayerSnapshot) {
        val track = snapshot.track ?: return

        val stateCode = when {
            snapshot.isLoading -> PlaybackStateCompat.STATE_BUFFERING
            snapshot.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(stateCode, snapshot.positionMs, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, track.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snapshot.durationMs)
            .apply {
                val art = track.artworkUrlLarge ?: track.artworkUrlSmall
                if (!art.isNullOrBlank()) {
                    putString(MediaMetadataCompat.METADATA_KEY_ART_URI, art)
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, art)
                }
            }
            .build()
        mediaSession?.setMetadata(metadata)

        startForegroundWith(buildNotification(track))
    }

    private fun startForegroundWith(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun buildNotification(track: SaavnTrack): Notification {
        val playPauseIcon = if (player.isPlaying) android.R.drawable.ic_media_pause
        else android.R.drawable.ic_media_play
        val playPauseLabel = if (player.isPlaying) "Pause" else "Play"

        val mediaStyle = MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(mediaSession?.sessionToken)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPi = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationChannels.MUSIC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setContentIntent(openAppPi)
            .setDeleteIntent(pendingActionIntent(ACTION_CLOSE))
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                pendingActionIntent(ACTION_PREV)
            )
            .addAction(playPauseIcon, playPauseLabel, pendingActionIntent(ACTION_TOGGLE))
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                pendingActionIntent(ACTION_NEXT)
            )
            .setStyle(mediaStyle)
            .setOngoing(player.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun pendingActionIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopAndRelease() {
        runCatching { player.release() }
        runCatching { mediaSession?.isActive = false }
        runCatching { mediaSession?.release() }
        mediaSession = null
        observerJob?.cancel()
        scope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        runCatching { mediaSession?.release() }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class PlayerSnapshot(
        val track: SaavnTrack?,
        val isPlaying: Boolean,
        val isLoading: Boolean,
        val durationMs: Long,
        val positionMs: Long
    )
}
