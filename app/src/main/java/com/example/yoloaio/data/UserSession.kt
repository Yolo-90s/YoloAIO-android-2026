package com.example.yoloaio.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object UserSession {
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        FirebaseModule.auth.addAuthStateListener(listener)
        awaitClose { FirebaseModule.auth.removeAuthStateListener(listener) }
    }

    val currentUser: FirebaseUser?
        get() = FirebaseModule.auth.currentUser

    val isSignedIn: Boolean
        get() = currentUser != null
}

@Composable
fun rememberCurrentUser(): State<FirebaseUser?> =
    UserSession.authStateFlow.collectAsState(initial = UserSession.currentUser)
