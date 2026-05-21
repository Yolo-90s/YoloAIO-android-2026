package com.example.yoloaio.features.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.yoloaio.features.quotes.Quote

/**
 * Local privacy preferences. SharedPreferences-backed so toggles persist
 * across launches without requiring a Firestore round-trip. Read in Compose
 * via [rememberPrivacyPreferences]; mutations go through [PrivacyPreferenceStore].
 */
class PrivacyPreferenceStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var defaultVisibility: String
        get() = prefs.getString(KEY_DEFAULT_VISIBILITY, Quote.VISIBILITY_PRIVATE)
            ?: Quote.VISIBILITY_PRIVATE
        set(value) {
            require(value == Quote.VISIBILITY_PRIVATE || value == Quote.VISIBILITY_PUBLIC) {
                "Invalid visibility: $value"
            }
            prefs.edit().putString(KEY_DEFAULT_VISIBILITY, value).apply()
        }

    var discoverableInChat: Boolean
        get() = prefs.getBoolean(KEY_DISCOVERABLE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DISCOVERABLE, value).apply()
        }

    var showProfilePhoto: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PHOTO, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_PHOTO, value).apply()
        }

    var showOnlineStatus: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_STATUS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ONLINE_STATUS, value).apply()
        }

    var readReceipts: Boolean
        get() = prefs.getBoolean(KEY_READ_RECEIPTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_READ_RECEIPTS, value).apply()
        }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "yolo_privacy_prefs"
        const val KEY_DEFAULT_VISIBILITY = "default_quote_visibility"
        const val KEY_DISCOVERABLE = "discoverable_in_chat"
        const val KEY_SHOW_PHOTO = "show_profile_photo"
        const val KEY_ONLINE_STATUS = "show_online_status"
        const val KEY_READ_RECEIPTS = "read_receipts"

        @Volatile
        private var INSTANCE: PrivacyPreferenceStore? = null

        fun get(context: Context): PrivacyPreferenceStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrivacyPreferenceStore(context).also { INSTANCE = it }
            }
    }
}

/**
 * Compose-friendly accessor. Returns a State<*> that recomposes when the
 * underlying SharedPreferences key changes — so toggles update instantly
 * across all screens reading the same value.
 */
@Composable
fun rememberPrivacyPrefs(): PrivacyPrefsSnapshot {
    val context = LocalContext.current
    val store = remember { PrivacyPreferenceStore.get(context) }

    val state = remember(store) {
        PrivacyPrefsSnapshot(
            defaultVisibility = mutableStateOf(store.defaultVisibility),
            discoverableInChat = mutableStateOf(store.discoverableInChat),
            showProfilePhoto = mutableStateOf(store.showProfilePhoto),
            showOnlineStatus = mutableStateOf(store.showOnlineStatus),
            readReceipts = mutableStateOf(store.readReceipts),
            store = store
        )
    }

    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PrivacyPreferenceStore.KEY_DEFAULT_VISIBILITY ->
                    state.defaultVisibility.value = store.defaultVisibility
                PrivacyPreferenceStore.KEY_DISCOVERABLE ->
                    state.discoverableInChat.value = store.discoverableInChat
                PrivacyPreferenceStore.KEY_SHOW_PHOTO ->
                    state.showProfilePhoto.value = store.showProfilePhoto
                PrivacyPreferenceStore.KEY_ONLINE_STATUS ->
                    state.showOnlineStatus.value = store.showOnlineStatus
                PrivacyPreferenceStore.KEY_READ_RECEIPTS ->
                    state.readReceipts.value = store.readReceipts
            }
        }
        store.registerListener(listener)
        onDispose { store.unregisterListener(listener) }
    }

    return state
}

class PrivacyPrefsSnapshot(
    val defaultVisibility: androidx.compose.runtime.MutableState<String>,
    val discoverableInChat: androidx.compose.runtime.MutableState<Boolean>,
    val showProfilePhoto: androidx.compose.runtime.MutableState<Boolean>,
    val showOnlineStatus: androidx.compose.runtime.MutableState<Boolean>,
    val readReceipts: androidx.compose.runtime.MutableState<Boolean>,
    private val store: PrivacyPreferenceStore
) {
    fun setDefaultVisibility(value: String) { store.defaultVisibility = value }
    fun setDiscoverable(value: Boolean) { store.discoverableInChat = value }
    fun setShowPhoto(value: Boolean) { store.showProfilePhoto = value }
    fun setShowOnline(value: Boolean) { store.showOnlineStatus = value }
    fun setReadReceipts(value: Boolean) { store.readReceipts = value }
}
