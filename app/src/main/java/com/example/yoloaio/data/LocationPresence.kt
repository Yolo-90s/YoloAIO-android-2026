package com.example.yoloaio.data

import android.content.Context
import android.util.Log
import com.example.yoloaio.features.weather.LocationProvider
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Writes the current user's location into their own Firestore user doc
 * (`users/{uid}.lastLat / .lastLon / .lastLocationAt`). Called whenever
 * the app or a chat is opened so chat partners see a recently-updated
 * "last known location" on the profile screen.
 *
 * Throttled to once every 5 minutes — opening the app 20× a day does
 * NOT mean 20 Firestore writes. The throttle state lives in SharedPrefs
 * so it survives process death.
 *
 * Silently no-ops if:
 *   - the user isn't signed in,
 *   - location permission isn't granted,
 *   - the last cached fix is unavailable.
 *
 * No UI is shown for failures — this is best-effort presence.
 */
object LocationPresence {

    private const val TAG = "LocationPresence"
    private const val PREFS = "yolo_loc_presence"
    private const val KEY_LAST_WRITE_MS = "last_write_ms"
    private const val MIN_INTERVAL_MS = 5L * 60 * 1000   // 5 min

    // Module-scoped scope so callers can fire-and-forget from
    // synchronous lifecycle hooks like onResume.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun maybeUpdate(context: Context) {
        val uid = FirebaseModule.auth.currentUser?.uid ?: return
        if (!LocationProvider.hasPermission(context)) return
        val prefs = context.applicationContext.getSharedPreferences(
            PREFS, Context.MODE_PRIVATE
        )
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_WRITE_MS, 0L) < MIN_INTERVAL_MS) return
        // Mark the timestamp BEFORE the write so concurrent calls don't
        // pile up if the network is slow — a single write per cycle is
        // the goal even if maybeUpdate is invoked repeatedly.
        prefs.edit().putLong(KEY_LAST_WRITE_MS, now).apply()

        scope.launch {
            val loc = LocationProvider.lastKnown(context) ?: return@launch
            runCatching {
                FirebaseModule.firestore
                    .collection("users")
                    .document(uid)
                    .set(
                        mapOf(
                            "lastLat" to loc.latitude,
                            "lastLon" to loc.longitude,
                            "lastLocationAt" to now
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }.onFailure {
                Log.w(TAG, "presence write failed: ${it.message}")
            }
        }
    }
}
