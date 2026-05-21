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
 * Persists the user's preferred music languages. Multi-select; the first
 * entry becomes the "primary" used when JioSaavn needs a single language
 * code for reordering. Backed by SharedPreferences so the user's pick
 * survives sign-out / app restart.
 */
class MusicLanguagePreferences private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _selected = MutableStateFlow(loadFromPrefs())
    val selected: StateFlow<Set<MusicLanguage>> = _selected.asStateFlow()

    fun set(languages: Set<MusicLanguage>) {
        val safe = languages.ifEmpty { setOf(MusicLanguage.Default) }
        prefs.edit()
            .putStringSet(KEY_LANGS, safe.map { it.code }.toSet())
            .apply()
        _selected.value = safe
    }

    private fun loadFromPrefs(): Set<MusicLanguage> {
        val stored = prefs.getStringSet(KEY_LANGS, null) ?: return setOf(MusicLanguage.Default)
        val parsed = stored.mapNotNull { code ->
            MusicLanguage.entries.firstOrNull { it.code == code }
        }.toSet()
        return parsed.ifEmpty { setOf(MusicLanguage.Default) }
    }

    companion object {
        private const val PREFS_NAME = "yolo_music_prefs"
        private const val KEY_LANGS = "selected_languages"

        @Volatile
        private var INSTANCE: MusicLanguagePreferences? = null

        fun get(context: Context): MusicLanguagePreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicLanguagePreferences(context).also { INSTANCE = it }
            }
    }
}

@Composable
fun rememberMusicLanguages(): State<Set<MusicLanguage>> {
    val context = LocalContext.current
    val store = remember { MusicLanguagePreferences.get(context) }
    return store.selected.collectAsState()
}
