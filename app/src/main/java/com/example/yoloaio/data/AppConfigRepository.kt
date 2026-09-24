package com.example.yoloaio.data

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AppConfigRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    /**
     * `config/app` requires `isSignedIn()` per firestore.rules, but Firebase
     * Auth's persisted-session restoration is itself async — on a cold
     * start it can genuinely still be null for the first tens of
     * milliseconds. Attaching the Firestore listener unconditionally at
     * app start races that: lose it, and the listener fails with
     * PERMISSION_DENIED. The old code discarded that error and treated the
     * failed read as "loaded successfully, config is blank" — which is
     * exactly what showed up as "TMDB key missing" (etc.) despite the key
     * being set, and inconsistently ("most of the time") depending on how
     * slow/contended that particular cold start was.
     *
     * Fix: only ever attach the Firestore listener once
     * [FirebaseAuth.AuthStateListener] confirms a signed-in user, and on
     * sign-out, detach it and reset to blank (correct — there IS no config
     * to read while signed out). Genuine post-attachment errors (a rare
     * network blip) are logged and otherwise ignored rather than
     * overwriting an already-good config with blanks.
     */
    fun observeConfig(): Flow<AppConfig> = callbackFlow {
        var docRegistration: ListenerRegistration? = null

        fun attach() {
            docRegistration?.remove()
            docRegistration = firestore.collection("config").document("app")
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.w("AppConfigRepository", "config/app listen failed: ${err.message}")
                        return@addSnapshotListener
                    }
                    trySend(snap?.toObject(AppConfig::class.java) ?: AppConfig())
                }
        }

        val authListener = FirebaseAuth.AuthStateListener { a ->
            if (a.currentUser != null) {
                attach()
            } else {
                docRegistration?.remove()
                docRegistration = null
                trySend(AppConfig())
            }
        }
        auth.addAuthStateListener(authListener)

        awaitClose {
            docRegistration?.remove()
            auth.removeAuthStateListener(authListener)
        }
    }

    /**
     * Sets the minimum [Role] required to see one Home menu tile. Uses a
     * dotted field-path `update()` (`menuMinRole.<key>`) rather than
     * `set(..., SetOptions.merge())` — Firestore's merge() replaces a
     * nested map field wholesale, which would wipe out every other tile's
     * setting; `update()` with a dotted path touches only that one nested
     * key. Requires the caller to be an admin — enforced by
     * firestore.rules (`allow write: if isAdmin()` on `config/{doc}`), not
     * just by this function hiding the button in the UI.
     */
    suspend fun setMenuMinRole(key: String, role: Role): Result<Unit> = runCatching {
        firestore.collection("config").document("app")
            .update("menuMinRole.$key", role.wireValue)
            .await()
        Unit
    }
}

val LocalAppConfig = compositionLocalOf<AppConfig> { AppConfig() }

@Composable
fun rememberAppConfig(): State<AppConfig> {
    val repo = remember { AppConfigRepository() }
    return repo.observeConfig().collectAsState(initial = AppConfig())
}
