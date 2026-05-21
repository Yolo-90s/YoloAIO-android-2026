package com.example.yoloaio.features.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Stores the user-chosen PlayGround URL — the page that the PlayGround
 * screen's Browser mode auto-loads. Set in Settings → PlayGround URL,
 * consumed by VideosScreen.kt. SharedPreferences-backed so it survives
 * launches without a Firestore round-trip.
 */
class PlaygroundPreferenceStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var url: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_URL, value).apply()
        }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "yolo_playground_prefs"
        const val KEY_URL = "playground_url"

        @Volatile
        private var INSTANCE: PlaygroundPreferenceStore? = null

        fun get(context: Context): PlaygroundPreferenceStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaygroundPreferenceStore(context).also { INSTANCE = it }
            }
    }
}

/**
 * Compose-friendly accessor that recomposes whenever the URL changes.
 * Updates are pushed via [SharedPreferences.OnSharedPreferenceChangeListener]
 * so a save in Settings reflects instantly on the PlayGround screen.
 */
@Composable
fun rememberPlaygroundUrl(): State<String> {
    val context = LocalContext.current
    val store = remember { PlaygroundPreferenceStore.get(context) }
    val state = remember(store) { mutableStateOf(store.url) }

    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PlaygroundPreferenceStore.KEY_URL) state.value = store.url
        }
        store.registerListener(listener)
        onDispose { store.unregisterListener(listener) }
    }
    return state
}
