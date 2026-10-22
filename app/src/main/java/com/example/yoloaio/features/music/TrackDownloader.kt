package com.example.yoloaio.features.music

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Per-track download progress, keyed by [SaavnTrack.id]. */
sealed interface DownloadState {
    data class Downloading(val percent: Int) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Downloads a JioSaavn track to [DownloadedTracksStore]'s local storage for
 * offline playback. Streams the HTTP response straight to disk rather than
 * buffering the whole file in memory first — songs run a few MB each,
 * larger than the ringtone clips [com.example.yoloaio.features.ringtones.RingtoneInstaller]
 * downloads, and several could be in flight at once.
 */
object TrackDownloader {
    private const val TAG = "TrackDownloader"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; YoloAIO) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val _progress = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadState>> = _progress.asStateFlow()

    suspend fun download(context: Context, track: SaavnTrack): Result<Unit> =
        withContext(Dispatchers.IO) {
            val store = DownloadedTracksStore.get(context)
            val destFile = store.fileFor(track.id)
            setState(track.id, DownloadState.Downloading(0))

            runCatching {
                val quality = MusicQualityPreferences.get(context).selected.value
                val url = applyMusicQuality(track.streamUrl, quality)
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                try {
                    if (conn.responseCode !in 200..299) {
                        error("HTTP ${conn.responseCode}")
                    }
                    val total = conn.contentLengthLong
                    var written = 0L
                    conn.inputStream.use { input ->
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                                    setState(track.id, DownloadState.Downloading(percent))
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }

                store.add(
                    DownloadedTrack.fromTrack(
                        track = track,
                        localFilePath = destFile.absolutePath,
                        fileSizeBytes = destFile.length()
                    )
                ).getOrThrow()

                setState(track.id, DownloadState.Done)
            }.onFailure { e ->
                Log.e(TAG, "download failed for ${track.id}: ${e.message}", e)
                destFile.delete()
                setState(track.id, DownloadState.Failed(e.message ?: "Download failed"))
            }
        }

    /** Clears a track's entry from the progress map — call after the UI has
     *  reacted to a terminal [DownloadState.Done]/[DownloadState.Failed]
     *  so a later re-download starts from a clean Downloading(0) state. */
    fun clearState(trackId: String) {
        _progress.update { it - trackId }
    }

    private fun setState(trackId: String, state: DownloadState) {
        _progress.update { it + (trackId to state) }
    }
}
