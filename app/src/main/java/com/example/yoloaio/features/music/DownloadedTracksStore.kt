package com.example.yoloaio.features.music

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local-only store for tracks downloaded for offline playback. Nothing
 * leaves the device — metadata lives in `filesDir/downloaded_tracks.json`,
 * audio files live in `filesDir/downloads/`. Modeled directly on
 * [com.example.yoloaio.features.audio.LocalTrimmedTonesStore].
 *
 * Singleton so a single in-memory [StateFlow] feeds every observer
 * (Music screen's Downloads tab, the download menu item, [MusicPlayer])
 * without races.
 */
class DownloadedTracksStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val metadataFile = File(appContext.filesDir, "downloaded_tracks.json")
    val downloadsDir: File = File(appContext.filesDir, "downloads").apply { mkdirs() }

    private val _tracks = MutableStateFlow(loadFromDisk())
    val tracks: StateFlow<List<DownloadedTrack>> = _tracks.asStateFlow()

    fun isDownloaded(trackId: String): Boolean =
        _tracks.value.any { it.trackId == trackId }

    fun localFileFor(trackId: String): File? {
        val entry = _tracks.value.firstOrNull { it.trackId == trackId } ?: return null
        val file = File(entry.localFilePath)
        return file.takeIf { it.exists() }
    }

    fun fileFor(trackId: String): File = File(downloadsDir, "$trackId.m4a")

    suspend fun add(entry: DownloadedTrack): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val next = listOf(entry) + _tracks.value.filterNot { it.trackId == entry.trackId }
            persist(next)
            _tracks.value = next
        }
    }

    /**
     * Removes the metadata entry and deletes the underlying audio file.
     * If the file is already gone (e.g. cleared by the user via a file
     * manager) we still drop the metadata.
     */
    suspend fun remove(trackId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = _tracks.value.firstOrNull { it.trackId == trackId } ?: return@runCatching
            try {
                File(entry.localFilePath).delete()
            } catch (e: Exception) {
                Log.w(TAG, "couldn't delete downloaded file ${entry.localFilePath}: ${e.message}")
            }
            val next = _tracks.value.filterNot { it.trackId == trackId }
            persist(next)
            _tracks.value = next
        }
    }

    private fun loadFromDisk(): List<DownloadedTrack> {
        if (!metadataFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(metadataFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                DownloadedTrack(
                    id = o.optString("id"),
                    trackId = o.optString("trackId"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    durationSec = o.optInt("durationSec", 0),
                    artworkUrlSmall = o.optString("artworkUrlSmall").takeIf { it.isNotBlank() },
                    artworkUrlLarge = o.optString("artworkUrlLarge").takeIf { it.isNotBlank() },
                    language = o.optString("language"),
                    year = o.optString("year"),
                    localFilePath = o.optString("localFilePath"),
                    fileSizeBytes = o.optLong("fileSizeBytes", 0L),
                    downloadedAt = o.optLong("downloadedAt", 0L)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to read downloaded tracks json", e)
            emptyList()
        }
    }

    private fun persist(entries: List<DownloadedTrack>) {
        val arr = JSONArray()
        entries.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("trackId", t.trackId)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("durationSec", t.durationSec)
                    put("artworkUrlSmall", t.artworkUrlSmall ?: "")
                    put("artworkUrlLarge", t.artworkUrlLarge ?: "")
                    put("language", t.language)
                    put("year", t.year)
                    put("localFilePath", t.localFilePath)
                    put("fileSizeBytes", t.fileSizeBytes)
                    put("downloadedAt", t.downloadedAt)
                }
            )
        }
        metadataFile.writeText(arr.toString())
    }

    companion object {
        private const val TAG = "DownloadedTracksStore"

        @Volatile
        private var INSTANCE: DownloadedTracksStore? = null

        fun get(context: Context): DownloadedTracksStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadedTracksStore(context).also { INSTANCE = it }
            }
    }
}
