package com.example.yoloaio.features.ringtones

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tiny single-track MediaPlayer wrapper for the Ringtones screen.
 * Toggle on the same id pauses/resumes; toggle on a different id stops the
 * previous track and starts the new one.
 */
class RingtonePlayer(private val context: Context) {

    private val player: MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        setOnPreparedListener { mp ->
            this@RingtonePlayer.isLoading = false
            mp.start()
            this@RingtonePlayer.isPlaying = true
        }
        setOnCompletionListener {
            this@RingtonePlayer.isPlaying = false
            this@RingtonePlayer.playingId = null
        }
        setOnErrorListener { _, _, _ ->
            this@RingtonePlayer.isPlaying = false
            this@RingtonePlayer.isLoading = false
            true
        }
    }

    var playingId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun toggle(id: String, url: String) {
        // Same track currently playing → pause.
        if (playingId == id && isPlaying) {
            player.pause()
            isPlaying = false
            return
        }
        // Same track, paused but prepared → resume.
        if (playingId == id && !isPlaying && !isLoading) {
            player.start()
            isPlaying = true
            return
        }
        // Different track (or first play) → load fresh.
        playingId = id
        isLoading = true
        isPlaying = false
        try {
            player.reset()
            player.setDataSource(url)
            player.prepareAsync()
        } catch (_: Exception) {
            isLoading = false
            playingId = null
        }
    }

    fun stop() {
        try {
            if (player.isPlaying) player.stop()
        } catch (_: Exception) {
        }
        isPlaying = false
        playingId = null
        isLoading = false
    }

    fun release() {
        try {
            player.release()
        } catch (_: Exception) {
        }
        isPlaying = false
        isLoading = false
        playingId = null
    }
}
