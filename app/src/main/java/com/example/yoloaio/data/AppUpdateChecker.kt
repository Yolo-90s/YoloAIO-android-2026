package com.example.yoloaio.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.appdistribution.FirebaseAppDistribution
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around Firebase App Distribution's in-app update SDK.
 *
 * Two entry points:
 *  - [maybeCheckOnLaunch] — silent background check from MainActivity.
 *    Debounced to once every 24 h so testers don't see the dialog every
 *    time they foreground the app.
 *  - [forceCheck] — user-initiated check from Settings → "Check for
 *    updates". Returns a [Status] the caller renders ("Up to date",
 *    "Error", or "Update available — flow handed off to SDK").
 *
 * The full update UI (download progress, install prompt) is provided by
 * the SDK itself — we just trigger it and report the result.
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val PREFS = "yolo_update_check"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val MIN_INTERVAL_MS = 24L * 60 * 60 * 1000   // 24 hours

    sealed class Status {
        data object UpToDate : Status()
        data class UpdatePending(val versionName: String) : Status()
        data class Failed(val message: String) : Status()
    }

    /**
     * Fire-and-forget check, suitable for `MainActivity.onCreate`. Skips
     * if a check ran in the last 24 h. The SDK shows its own UI if an
     * update is available — we set nothing else on the screen.
     */
    fun maybeCheckOnLaunch(activity: Activity) {
        val prefs = activity.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        if (now - last < MIN_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()

        runCatching {
            FirebaseAppDistribution.getInstance()
                .updateIfNewReleaseAvailable()
                .addOnFailureListener { e ->
                    // Common reasons: tester not signed in yet, no network,
                    // App Distribution disabled. All benign — don't disturb
                    // the user; they'll see it the next time they tap
                    // "Check for updates" in Settings.
                    Log.w(TAG, "auto update check failed: ${e.message}")
                }
        }.onFailure {
            Log.w(TAG, "auto update check init failed", it)
        }
    }

    /**
     * User-initiated check. Use from Settings → "Check for updates".
     * Suspending so the caller can show a spinner + toast on result.
     */
    suspend fun forceCheck(activity: Activity): Status {
        return try {
            val sdk = FirebaseAppDistribution.getInstance()
            val release = sdk.checkForNewRelease().await()
            if (release == null) {
                Status.UpToDate
            } else {
                // Hand off to the SDK to show the "Update available" UI,
                // download the APK, and prompt the system installer.
                runCatching {
                    sdk.updateApp().addOnFailureListener {
                        Log.w(TAG, "updateApp() failed: ${it.message}")
                    }
                }
                Status.UpdatePending(release.displayVersion ?: "")
            }
        } catch (e: Exception) {
            Log.w(TAG, "forceCheck failed", e)
            Status.Failed(e.message ?: "Couldn't check for updates")
        }
    }
}
