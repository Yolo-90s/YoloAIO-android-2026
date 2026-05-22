package com.example.yoloaio.features.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yoloaio.cast.CastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** What happens when the current track finishes. */
enum class RepeatMode { Off, One, All }

/**
 * MediaPlayer wrapper for streaming music. Adds:
 *   - repeat (Off / One / All)
 *   - shuffle
 *   - manual "Play next" queue — entries here jump in front of the main queue
 *
 * State is all exposed as Compose mutableState so UIs recompose on changes.
 *
 * Singleton (`MusicPlayer.get(context)`) so playback survives screen
 * navigation. The lifetime is the app process — only the user explicitly
 * closing the system media notification calls [release].
 */
class MusicPlayer private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var prepareJob: Job? = null

    private var queue: List<SaavnTrack> = emptyList()
    private var index: Int = -1
    private var resolveStreamUrl: (suspend (SaavnTrack) -> String?)? = null

    /** Tracks the user explicitly queued via "Play next". FIFO. */
    val playNext = mutableStateListOf<SaavnTrack>()

    var current by mutableStateOf<SaavnTrack?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var isPrepared by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var repeatMode by mutableStateOf(RepeatMode.Off)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set

    /**
     * The MediaPlayer's audio session id. Used by [BeatAnalyzer] to attach a
     * `Visualizer` to this exact audio output, so the reactive bars sync to
     * the song the user is actually hearing.
     */
    val audioSessionId: Int get() = player.audioSessionId

    // ── Audio post-processing chain ────────────────────────────────
    // Lazy-attached on first play. AudioEffects need a valid session
    // id; the session is technically usable from MediaPlayer
    // construction, but some OEMs only assign a real id once the
    // first datasource is set, so we defer attachment to startTrack().
    private val effects: MusicEffects by lazy { MusicEffects(player.audioSessionId) }
    private var effectsAttached = false

    private val player: MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        setOnPreparedListener { mp ->
            this@MusicPlayer.durationMs = mp.duration.toLong()
            this@MusicPlayer.isPrepared = true
            this@MusicPlayer.isLoading = false
            mp.start()
            this@MusicPlayer.isPlaying = true
        }
        setOnCompletionListener {
            this@MusicPlayer.isPlaying = false
            handleCompletion()
        }
        setOnErrorListener { _, what, extra ->
            this@MusicPlayer.isPlaying = false
            this@MusicPlayer.isPrepared = false
            this@MusicPlayer.isLoading = false
            this@MusicPlayer.error = "Playback error ($what/$extra)"
            true
        }
    }

    fun bind(
        tracks: List<SaavnTrack>,
        urlResolver: suspend (SaavnTrack) -> String?
    ) {
        queue = tracks
        resolveStreamUrl = urlResolver
        if (current == null && tracks.isNotEmpty()) {
            current = tracks.first()
            index = 0
        } else {
            index = tracks.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        }
    }

    fun play(track: SaavnTrack) {
        index = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        current = track
        startTrack(track)
    }

    private fun startTrack(track: SaavnTrack) {
        prepareJob?.cancel()
        isLoading = true
        error = null
        isPrepared = false
        durationMs = 0L
        // First-play side effect: spin up the foreground service so the OS
        // keeps the process alive when the user navigates away or backgrounds
        // the app. The service is also responsible for the media notification.
        MusicPlaybackService.ensureStarted(context)
        // Attach the DSP chain on first play and start observing the
        // effect preferences. Repeated calls are no-ops (effectsAttached
        // gates the work).
        attachEffectsIfNeeded()
        prepareJob = scope.launch {
            try {
                val rawUrl = resolveStreamUrl?.invoke(track)
                if (rawUrl.isNullOrBlank()) {
                    isLoading = false
                    error = "Couldn't load track URL"
                    return@launch
                }
                // Apply the user's data-saver / HD bitrate choice. The
                // JioSaavn CDN serves three quality buckets keyed by a
                // suffix in the URL; we just swap that suffix at play
                // time so changes in Settings take effect on the next
                // track without invalidating cached SaavnTrack objects.
                val quality = MusicQualityPreferences.get(context)
                    .selected.value
                val url = applyMusicQuality(rawUrl, quality)

                // When a Cast session is connected, hand playback off to the
                // remote receiver instead of starting local playback. We still
                // surface the track in our UI as "now playing" so the user
                // can prev/next/pause from our controls.
                val cast = CastManager.get(context)
                if (cast.isInitialized && cast.isConnected) {
                    val sent = cast.loadAudio(
                        streamUrl = url,
                        title = track.title,
                        artist = track.artist,
                        albumArtUrl = track.artworkUrlLarge ?: track.artworkUrlSmall,
                        durationMs = track.durationSec * 1000L
                    )
                    if (sent) {
                        // Make sure the local MediaPlayer isn't fighting the
                        // cast device for the same audio.
                        runCatching { if (player.isPlaying) player.pause() }
                        isLoading = false
                        isPrepared = true
                        isPlaying = true
                        durationMs = track.durationSec * 1000L
                        return@launch
                    }
                    // If the cast load failed for some reason, fall through
                    // and play locally as the default.
                }

                player.reset()
                player.setDataSource(url)
                player.prepareAsync()
            } catch (e: Exception) {
                isLoading = false
                isPrepared = false
                isPlaying = false
                error = e.message ?: "Couldn't start playback"
            }
        }
    }

    fun toggle() {
        val track = current ?: queue.firstOrNull() ?: return
        // Cast takes priority — our local player is paused while a session
        // is active. Just flip the remote state and mirror it locally so the
        // UI shows the right play/pause icon.
        val cast = CastManager.get(context)
        if (cast.isInitialized && cast.isConnected) {
            cast.togglePlayPause()
            isPlaying = !isPlaying
            return
        }
        if (!isPrepared) {
            play(track)
            return
        }
        if (player.isPlaying) {
            player.pause()
            isPlaying = false
        } else {
            player.start()
            isPlaying = true
        }
    }

    /**
     * Advance one track forward. Priority order:
     *   1. Anything the user explicitly queued via [addToPlayNext]
     *   2. Random pick from `queue` if shuffle is on
     *   3. Sequential next; wraps to start if [repeatMode] is `All`
     *   4. No-op if at end with no repeat
     */
    fun next() {
        val track = pickNext() ?: return
        index = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: index
        current = track
        startTrack(track)
    }

    fun previous() {
        if (queue.isEmpty()) return
        // "Previous" walks the queue backward and ignores the manual play-next
        // list — matches every music app's behaviour.
        index = if (index <= 0) queue.size - 1 else index - 1
        val track = queue[index]
        current = track
        startTrack(track)
    }

    fun seekTo(positionMs: Long) {
        if (isPrepared) {
            player.seekTo(positionMs.toInt())
        }
    }

    fun currentPositionMs(): Long {
        return if (isPrepared) player.currentPosition.toLong() else 0L
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
    }

    /** Insert at the head of the [playNext] queue. Plays right after the current track. */
    fun addToPlayNext(track: SaavnTrack) {
        // Skip duplicates that are already next-up
        if (playNext.none { it.id == track.id }) {
            playNext.add(0, track)
        }
    }

    fun removeFromPlayNext(trackId: String) {
        playNext.removeAll { it.id == trackId }
    }

    private fun handleCompletion() {
        when (repeatMode) {
            RepeatMode.One -> current?.let { startTrack(it) }
            else -> next()
        }
    }

    private fun pickNext(): SaavnTrack? {
        if (playNext.isNotEmpty()) {
            return playNext.removeAt(0)
        }
        if (queue.isEmpty()) return null
        if (shuffleEnabled) {
            // Pick anything other than the current track when possible.
            val pool = if (queue.size > 1) queue.filter { it.id != current?.id } else queue
            return pool.random()
        }
        val nextIdx = index + 1
        return if (nextIdx >= queue.size) {
            if (repeatMode == RepeatMode.All) queue.first() else null
        } else {
            queue[nextIdx]
        }
    }

    /**
     * Attach the audio post-processing chain (equalizer + bass + virtualizer
     * + loudness) and subscribe to the user's saved settings. Idempotent —
     * the `effectsAttached` flag means repeated startTrack() calls don't
     * spin up duplicate collectors.
     */
    private fun attachEffectsIfNeeded() {
        if (effectsAttached) return
        if (player.audioSessionId == 0) return
        effects.attach()
        effectsAttached = true
        val store = MusicEffectsPreferences.get(context)
        scope.launch {
            store.settings.collectLatest { s ->
                // Apply every published change immediately. Effects
                // disabled state still applies a flat preset + 0 strengths
                // so toggling back off snaps the sound back to neutral.
                effects.setEnabled(s.enabled)
                if (s.enabled) {
                    effects.setPreset(s.preset)
                    effects.setBassStrength(s.bassStrength)
                    effects.setVirtualizerStrength(s.virtualizerStrength)
                    effects.setLoudnessGainMb(s.loudnessGainMb)
                } else {
                    effects.setPreset(EqPreset.Flat)
                    effects.setBassStrength(0)
                    effects.setVirtualizerStrength(0)
                    effects.setLoudnessGainMb(0)
                }
            }
        }
    }

    fun release() {
        scope.cancel()
        try {
            effects.release()
        } catch (_: Exception) {
        }
        try {
            player.release()
        } catch (_: Exception) {
        }
        isPlaying = false
        isPrepared = false
        isLoading = false
        current = null
        effectsAttached = false
        synchronized(MusicPlayer) { INSTANCE = null }
    }

    companion object {
        @Volatile
        private var INSTANCE: MusicPlayer? = null

        /** Process-wide singleton. Lifetime = until [release] is called
         *  (typically from the user dismissing the media notification). */
        fun get(context: Context): MusicPlayer =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicPlayer(context.applicationContext).also { INSTANCE = it }
            }

        /** Cheap accessor for code paths (notification action handlers, etc.)
         *  that don't want to materialise the player if it doesn't already
         *  exist — returns null instead. */
        fun peek(): MusicPlayer? = INSTANCE
    }
}
