package com.example.yoloaio.features.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.data.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseModule.auth
    private val firestore = FirebaseModule.firestore

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            Unit
        } catch (e: Throwable) {
            throw AuthError(mapSignInError(e), e)
        }
    }

    suspend fun signUp(name: String, email: String, password: String): Result<Unit> = runCatching {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        require(cleanName.isNotEmpty()) { "Please enter your name" }
        require(cleanEmail.isNotEmpty()) { "Please enter your email" }
        require(password.length >= 6) { "Password must be at least 6 characters" }

        try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val user = result.user ?: error("Sign up succeeded but user is null")

            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
            ).await()

            val profile = UserProfile(
                uid = user.uid,
                email = cleanEmail,
                displayName = cleanName,
                initials = UserProfile.computeInitials(cleanName),
                avatarColor = UserProfile.AVATAR_PALETTE.random(),
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("users").document(user.uid).set(profile).await()
            Unit
        } catch (e: Throwable) {
            throw AuthError(mapSignUpError(e), e)
        }
    }

    private fun mapSignInError(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidUserException ->
            "No account exists with that email. Tap Sign Up to create one."
        is FirebaseAuthInvalidCredentialsException ->
            "Email or password is incorrect. If you don't have an account yet, tap Sign Up."
        is FirebaseNetworkException ->
            "Network error. Check your connection and try again."
        else -> e.message ?: "Sign in failed"
    }

    private fun mapSignUpError(e: Throwable): String = when (e) {
        is FirebaseAuthUserCollisionException ->
            "An account already exists with this email. Tap Sign In."
        is FirebaseAuthWeakPasswordException ->
            "Password is too weak. Use at least 6 characters with a mix of letters and numbers."
        is FirebaseAuthInvalidCredentialsException ->
            "That email address looks invalid."
        is FirebaseNetworkException ->
            "Network error. Check your connection and try again."
        else -> e.message ?: "Sign up failed"
    }

    fun signOut() {
        auth.signOut()
    }

    /**
     * Google Sign-In via Credential Manager + Google Identity Services. We
     * request a Google ID token, exchange it for a Firebase credential, and
     * sign in. First-time Google sign-in also seeds the Firestore user
     * profile so the rest of the app (Settings, Chat, etc.) sees a name.
     *
     * Pass the hosting [Activity] — Credential Manager needs an Activity
     * context to launch its picker UI.
     */
    suspend fun signInWithGoogle(activity: Activity, webClientId: String): Result<Unit> = runCatching {
        require(webClientId.isNotBlank()) {
            "Google Web Client ID missing. Add `googleWebClientId` to the " +
                "Firestore config/app document (Firebase Console > Project " +
                "settings > General > Your apps > Web client ID)."
        }

        val credentialManager = CredentialManager.create(activity)

        // Two-phase attempt:
        //   1. GetSignInWithGoogleOption — designed for an explicit "Continue
        //      with Google" tap. Always shows the full Google sign-in sheet,
        //      including an "Add account" affordance when the device has no
        //      Google account configured. This is the right primary option
        //      for sign-up.
        //   2. GetGoogleIdOption (filterByAuthorizedAccounts=false) — older
        //      one-tap-style option. Used here only as a fallback in case the
        //      installed Identity library is too old to know about (1).
        val signInWithGoogle = GetSignInWithGoogleOption.Builder(webClientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogle)
            .build()

        val response = try {
            credentialManager.getCredential(activity, primaryRequest)
        } catch (e: GetCredentialCancellationException) {
            throw AuthError("Sign-in cancelled.", e)
        } catch (firstErr: GetCredentialException) {
            // Retry with the legacy GetGoogleIdOption flow. If THAT also
            // returns NoCredentialException, the device genuinely has no
            // Google account configured.
            val fallback = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val fallbackRequest = GetCredentialRequest.Builder()
                .addCredentialOption(fallback)
                .build()
            try {
                credentialManager.getCredential(activity, fallbackRequest)
            } catch (e: GetCredentialCancellationException) {
                throw AuthError("Sign-in cancelled.", e)
            } catch (e: NoCredentialException) {
                throw AuthError(
                    "No Google account is set up on this device. Open " +
                        "Settings → Passwords & accounts → Add account → " +
                        "Google, sign in, then come back and try again.",
                    e
                )
            } catch (e: GetCredentialException) {
                // Surface the *first* error since the fallback is internal —
                // first error is more informative for the user.
                throw AuthError(
                    "Google sign-in failed: ${firstErr.message ?: firstErr.javaClass.simpleName}",
                    firstErr
                )
            }
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw AuthError("Unexpected credential type from Google.")
        }
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken.idToken, null)
        val authResult = auth.signInWithCredential(firebaseCredential).await()
        val user = authResult.user ?: error("Sign-in returned null user")
        ensureUserProfileExists(user, googleIdToken.displayName)
        Unit
    }

    private suspend fun ensureUserProfileExists(user: FirebaseUser, googleDisplayName: String?) {
        val doc = firestore.collection("users").document(user.uid)
        val existing = doc.get().await()
        if (existing.exists()) return

        val name = googleDisplayName?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Yolo User"

        val profile = UserProfile(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = name,
            initials = UserProfile.computeInitials(name),
            avatarColor = UserProfile.AVATAR_PALETTE.random(),
            createdAt = System.currentTimeMillis()
        )
        doc.set(profile).await()
    }

    suspend fun updateDisplayName(newName: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val cleanName = newName.trim()
        require(cleanName.isNotEmpty()) { "Name cannot be empty" }

        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
        ).await()

        firestore.collection("users").document(user.uid).update(
            mapOf(
                "displayName" to cleanName,
                "initials" to UserProfile.computeInitials(cleanName)
            )
        ).await()
        Unit
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val email = user.email ?: error("Account has no email")
        // Surface the Google-only case with a clear message instead of letting
        // it fall through into FirebaseAuthInvalidCredentialsException, which
        // would otherwise read as "Current password is incorrect".
        val hasPasswordProvider = user.providerData.any { it.providerId == "password" }
        require(hasPasswordProvider) {
            "This account signed in with Google. Manage your Google Account " +
                "password at myaccount.google.com — there's no separate " +
                "password for this app to change."
        }
        require(newPassword.length >= 6) { "New password must be at least 6 characters" }
        require(currentPassword.isNotEmpty()) { "Enter your current password" }

        try {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            Unit
        } catch (e: Throwable) {
            throw AuthError(mapPasswordChangeError(e), e)
        }
    }

    private fun mapPasswordChangeError(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidCredentialsException ->
            "Current password is incorrect."
        is FirebaseAuthWeakPasswordException ->
            "New password is too weak. Use at least 6 characters."
        is FirebaseAuthRecentLoginRequiredException ->
            "Please sign out and sign in again, then retry."
        is FirebaseNetworkException ->
            "Network error. Check your connection and try again."
        else -> e.message ?: "Couldn't change password"
    }

    class AuthError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    suspend fun fetchProfile(uid: String): UserProfile? = runCatching {
        val snap = firestore.collection("users").document(uid).get().await()
        snap.toObject(UserProfile::class.java)
    }.getOrNull()
}
