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
 * Snapshot of every effect knob. Held in a single immutable data class
 * so [MusicEffectsPreferences.settings] emits one consolidated update
 * whenever anything changes — keeps the UI re-render cycle simple.
 */
data class MusicEffectSettings(
    val enabled: Boolean = false,
    val presetName: String = EqPreset.Default.name,
    val bassStrength: Int = 0,        // 0..1000
    val virtualizerStrength: Int = 0, // 0..1000
    val loudnessGainMb: Int = 0       // 0..2000 mB (= 0..20 dB)
) {
    val preset: EqPreset
        get() = EqPreset.entries.firstOrNull { it.name == presetName }
            ?: EqPreset.Default

    companion object {
        /**
         * "Quick enhance" preset — applied when the user flips the
         * master toggle on for the first time. Sensible defaults that
         * sound noticeably richer than flat AAC playback on most phones
         * without sounding obviously processed.
         */
        val Enhanced = MusicEffectSettings(
            enabled = true,
            presetName = EqPreset.BassPunch.name,
            bassStrength = 450,
            virtualizerStrength = 600,
            loudnessGainMb = 300
        )
    }
}

/**
 * SharedPreferences-backed store for the effects panel. Same singleton
 * + StateFlow pattern as [MusicQualityPreferences].
 */
class MusicEffectsPreferences private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(loadFromPrefs())
    val settings: StateFlow<MusicEffectSettings> = _settings.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        update { it.copy(enabled = enabled) }
    }

    fun setPreset(preset: EqPreset) {
        update { it.copy(presetName = preset.name) }
    }

    fun setBassStrength(strength: Int) {
        update { it.copy(bassStrength = strength.coerceIn(0, 1000)) }
    }

    fun setVirtualizerStrength(strength: Int) {
        update { it.copy(virtualizerStrength = strength.coerceIn(0, 1000)) }
    }

    fun setLoudnessGainMb(gain: Int) {
        update { it.copy(loudnessGainMb = gain.coerceIn(0, 2000)) }
    }

    /** Apply the "Quick enhance" preset — used by the master toggle. */
    fun applyEnhanced() {
        update { MusicEffectSettings.Enhanced }
    }

    /** Reset everything to flat. */
    fun reset() {
        update { MusicEffectSettings() }
    }

    private inline fun update(transform: (MusicEffectSettings) -> MusicEffectSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_PRESET, next.presetName)
            .putInt(KEY_BASS, next.bassStrength)
            .putInt(KEY_VIRT, next.virtualizerStrength)
            .putInt(KEY_LOUD, next.loudnessGainMb)
            .apply()
        _settings.value = next
    }

    private fun loadFromPrefs(): MusicEffectSettings = MusicEffectSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        presetName = prefs.getString(KEY_PRESET, EqPreset.Default.name)
            ?: EqPreset.Default.name,
        bassStrength = prefs.getInt(KEY_BASS, 0),
        virtualizerStrength = prefs.getInt(KEY_VIRT, 0),
        loudnessGainMb = prefs.getInt(KEY_LOUD, 0)
    )

    companion object {
        private const val PREFS_NAME = "yolo_music_effects_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PRESET = "preset"
        private const val KEY_BASS = "bass"
        private const val KEY_VIRT = "virt"
        private const val KEY_LOUD = "loud"

        @Volatile
        private var INSTANCE: MusicEffectsPreferences? = null

        fun get(context: Context): MusicEffectsPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicEffectsPreferences(context).also { INSTANCE = it }
            }
    }
}

@Composable
fun rememberMusicEffectSettings(): State<MusicEffectSettings> {
    val context = LocalContext.current
    val store = remember { MusicEffectsPreferences.get(context) }
    return store.settings.collectAsState()
}
