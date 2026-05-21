package com.example.yoloaio.features.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MediaPlayer wrapper that plays a [startSec, endSec] window. Used by the
 * audio trimmer's "Preview trim" button — calling [play] seeks to startSec,
 * begins playback, and auto-pauses when currentPosition crosses endSec.
 *
 * State is bound to a single Uri at a time via [bind]. Switching sources
 * resets the underlying player.
 */
class TrimPreviewPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitorJob: Job? = null
    private var boundUri: Uri? = null
    private var prepared = false

    var isPlaying by mutableStateOf(false)
        private set
    var isPreparing by mutableStateOf(false)
        private set
    var currentPositionMs by mutableStateOf(0)
        private set

    fun bind(uri: Uri) {
        if (boundUri == uri) return
        boundUri = uri
        prepared = false
        rebuildPlayer(uri)
    }

    private fun rebuildPlayer(uri: Uri) {
        releasePlayer()
        isPreparing = true
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnPreparedListener {
                this@TrimPreviewPlayer.prepared = true
                this@TrimPreviewPlayer.isPreparing = false
            }
            setOnErrorListener { _, _, _ ->
                this@TrimPreviewPlayer.isPreparing = false
                this@TrimPreviewPlayer.isPlaying = false
                true
            }
            try {
                setDataSource(context, uri)
                prepareAsync()
            } catch (_: Exception) {
                isPreparing = false
            }
        }
    }

    fun play(startSec: Double, endSec: Double) {
        val p = player ?: return
        if (!prepared) return
        monitorJob?.cancel()
        val startMs = (startSec * 1000).toInt().coerceAtLeast(0)
        val endMs = (endSec * 1000).toInt().coerceAtLeast(startMs + 100)
        try {
            p.seekTo(startMs)
            p.start()
            isPlaying = true
            currentPositionMs = startMs
        } catch (_: Exception) {
            return
        }
        monitorJob = scope.launch {
            while (isPlaying) {
                delay(40)
                val pos = try {
                    player?.currentPosition ?: 0
                } catch (_: Exception) {
                    endMs
                }
                currentPositionMs = pos
                if (pos >= endMs) {
                    pause()
                    break
                }
            }
        }
    }

    fun pause() {
        monitorJob?.cancel()
        monitorJob = null
        try {
            player?.takeIf { it.isPlaying }?.pause()
        } catch (_: Exception) {
        }
        isPlaying = false
    }

    private fun releasePlayer() {
        monitorJob?.cancel()
        monitorJob = null
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        isPlaying = false
        prepared = false
    }

    fun release() {
        releasePlayer()
        scope.cancel()
    }
}
