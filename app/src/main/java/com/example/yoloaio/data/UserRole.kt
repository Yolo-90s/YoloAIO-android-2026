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

/**
 * The app-wide access tier, read from `users/{uid}.role` — the same field
 * the shared Firestore rules' `isAdmin()` already checks (`role in
 * ['admin', 'developer']`), and the same convention WalkieTalkie's
 * admin-listen feature introduced. This formalizes it into a real 4-value
 * type used for menu visibility (see AppConfig.minRoleFor) instead of a
 * one-off boolean local to a single feature.
 *
 * Ordinal order matters — comparisons like `role >= Role.ADMIN` rely on
 * declaration order, so don't reorder these without checking call sites.
 */
enum class Role(val wireValue: String) {
    GUEST("guest"),
    USER("user"),
    ADMIN("admin"),
    DEVELOPER("developer");

    companion object {
        /** Unknown, blank, or missing values default to the lowest tier. */
        fun fromWire(value: String?): Role = entries.firstOrNull { it.wireValue == value } ?: GUEST
    }
}

val LocalUserRole = compositionLocalOf<Role> { Role.GUEST }

/**
 * Live role for the signed-in user. Mirrors [AppConfigRepository]'s
 * auth-gated-listener pattern (see its doc comment) for the same reason:
 * `users/{uid}` reads require being signed in, and Firebase Auth's
 * persisted-session restoration is itself async, so attaching the
 * Firestore listener unconditionally at app start can race it.
 */
@Composable
fun rememberUserRole(): State<Role> {
    val flow = remember { observeUserRole() }
    return flow.collectAsState(initial = Role.GUEST)
}

private fun observeUserRole(): Flow<Role> = callbackFlow {
    val firestore = FirebaseModule.firestore
    val auth = FirebaseModule.auth
    var docRegistration: ListenerRegistration? = null

    fun attach(uid: String) {
        docRegistration?.remove()
        docRegistration = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w("UserRole", "role listen failed: ${err.message}")
                    return@addSnapshotListener
                }
                trySend(Role.fromWire(snap?.getString("role")))
            }
    }

    val authListener = FirebaseAuth.AuthStateListener { a ->
        val uid = a.currentUser?.uid
        if (uid != null) {
            attach(uid)
        } else {
            docRegistration?.remove()
            docRegistration = null
            trySend(Role.GUEST)
        }
    }
    auth.addAuthStateListener(authListener)

    awaitClose {
        docRegistration?.remove()
        auth.removeAuthStateListener(authListener)
    }
}
