package com.example.yoloaio.features.audio

import android.content.Context
import android.net.Uri
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
 * Local-only store for trimmed tones. Nothing leaves the device — metadata
 * lives in `filesDir/trimmed_tones.json`, audio files live in MediaStore
 * under `Music/YoloAIO Trims/` (the trim's `streamUrl` is the content URI).
 *
 * Singleton so a single in-memory [StateFlow] feeds every observer
 * (Ringtones screen, Trimmer screen) without races.
 */
class LocalTrimmedTonesStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "trimmed_tones.json")

    private val _tones = MutableStateFlow(loadFromDisk())
    val tones: StateFlow<List<TrimmedTone>> = _tones.asStateFlow()

    suspend fun add(tone: TrimmedTone): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val next = listOf(tone) + _tones.value
            persist(next)
            _tones.value = next
        }
    }

    /**
     * Removes the metadata entry and deletes the underlying MediaStore file.
     * If the MediaStore delete fails (e.g. the user wiped it from a file
     * manager already) we still drop the metadata.
     */
    suspend fun remove(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tone = _tones.value.firstOrNull { it.id == id } ?: return@runCatching
            try {
                val uri = Uri.parse(tone.streamUrl)
                appContext.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "couldn't remove MediaStore entry ${tone.streamUrl}: ${e.message}")
            }
            val next = _tones.value.filterNot { it.id == id }
            persist(next)
            _tones.value = next
        }
    }

    private fun loadFromDisk(): List<TrimmedTone> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                TrimmedTone(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    sourceName = o.optString("sourceName"),
                    durationSec = o.optDouble("durationSec", 0.0),
                    streamUrl = o.optString("streamUrl"),
                    artUrl = o.optString("artUrl").takeIf { it.isNotBlank() },
                    mimeType = o.optString("mimeType", "audio/mp4"),
                    fileExtension = o.optString("fileExtension", "m4a"),
                    createdAt = o.optLong("createdAt", 0L)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to read trimmed tones json", e)
            emptyList()
        }
    }

    private fun persist(tones: List<TrimmedTone>) {
        val arr = JSONArray()
        tones.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("sourceName", t.sourceName)
                    put("durationSec", t.durationSec)
                    put("streamUrl", t.streamUrl)
                    put("artUrl", t.artUrl ?: "")
                    put("mimeType", t.mimeType)
                    put("fileExtension", t.fileExtension)
                    put("createdAt", t.createdAt)
                }
            )
        }
        file.writeText(arr.toString())
    }

    companion object {
        private const val TAG = "TrimmedTonesStore"

        @Volatile
        private var INSTANCE: LocalTrimmedTonesStore? = null

        fun get(context: Context): LocalTrimmedTonesStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalTrimmedTonesStore(context).also { INSTANCE = it }
            }
    }
}
