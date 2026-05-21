package com.example.yoloaio.ui.theme

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
 * Persists the user's chosen [ThemePalette] in SharedPreferences and exposes
 * it as a [StateFlow] so the Compose hierarchy recomposes the second a new
 * palette is picked.
 *
 * Singleton — same instance feeds the root theme and the settings UI, no
 * race between writer and observers.
 */
class ThemePreferenceStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _palette = MutableStateFlow(
        ThemePalette.fromKey(prefs.getString(KEY_PALETTE, ThemePalette.Default.key) ?: "")
    )
    val palette: StateFlow<ThemePalette> = _palette.asStateFlow()

    fun setPalette(palette: ThemePalette) {
        prefs.edit().putString(KEY_PALETTE, palette.key).apply()
        _palette.value = palette
    }

    companion object {
        private const val PREFS_NAME = "yolo_theme_prefs"
        private const val KEY_PALETTE = "selected_palette"

        @Volatile
        private var INSTANCE: ThemePreferenceStore? = null

        fun get(context: Context): ThemePreferenceStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferenceStore(context).also { INSTANCE = it }
            }
    }
}

@Composable
fun rememberThemePalette(): State<ThemePalette> {
    val context = LocalContext.current
    val store = remember { ThemePreferenceStore.get(context) }
    return store.palette.collectAsState()
}
