package com.example.yoloaio.features.music

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JioSaavn serves the same track at three audio bitrates. The decrypted
 * stream URL ends in `_320.mp4` by default; swapping the suffix gives
 * `_160.mp4` or `_96.mp4`. Lower bitrate = smaller download, lower
 * fidelity. Most users won't notice the difference between 160 and 320
 * on a phone speaker; 96 is for cellular-saving emergencies.
 */
enum class MusicQuality(
    val code: String,
    val label: String,
    val description: String
) {
    Low("96", "Data Saver", "≈ 0.7 MB / minute"),
    Medium("160", "Standard", "≈ 1.2 MB / minute"),
    High("320", "HD", "≈ 2.4 MB / minute");

    companion object {
        val Default: MusicQuality = High
        val all: List<MusicQuality> = entries.toList()
    }
}

/**
 * Replaces the bitrate suffix on a JioSaavn AAC URL. The CDN paths look
 * like `https://aac.saavncdn.com/.../abc123_320.mp4` — we just swap the
 * `320` for `160` or `96`. If the URL doesn't match the expected pattern
 * (e.g. legacy host), it's returned unchanged.
 */
fun applyMusicQuality(url: String, quality: MusicQuality): String {
    val regex = Regex("_(96|160|320)\\.mp4(\\?.*)?$")
    return regex.replace(url) { match ->
        val query = match.groupValues.getOrNull(2).orEmpty()
        "_${quality.code}.mp4$query"
    }
}

/**
 * Persists the user's audio-quality choice in SharedPreferences so it
 * survives sign-out / reinstall via app-data-backup. Read once on track
 * start by [MusicPlayer].
 */
class MusicQualityPreferences private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _selected = MutableStateFlow(loadFromPrefs())
    val selected: StateFlow<MusicQuality> = _selected.asStateFlow()

    fun set(quality: MusicQuality) {
        prefs.edit().putString(KEY_QUALITY, quality.code).apply()
        _selected.value = quality
    }

    private fun loadFromPrefs(): MusicQuality {
        val code = prefs.getString(KEY_QUALITY, MusicQuality.Default.code)
        return MusicQuality.entries.firstOrNull { it.code == code }
            ?: MusicQuality.Default
    }

    companion object {
        private const val PREFS_NAME = "yolo_music_quality_prefs"
        private const val KEY_QUALITY = "quality"

        @Volatile
        private var INSTANCE: MusicQualityPreferences? = null

        fun get(context: Context): MusicQualityPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicQualityPreferences(context).also { INSTANCE = it }
            }
    }
}

@Composable
fun rememberMusicQuality(): State<MusicQuality> {
    val context = LocalContext.current
    val store = remember { MusicQualityPreferences.get(context) }
    return store.selected.collectAsState()
}
