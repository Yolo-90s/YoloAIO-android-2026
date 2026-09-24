package com.example.yoloaio.features.mindmatch

import com.example.yoloaio.data.FirebaseModule
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed pairing + answer exchange for MindMatch. Mirrors
 * [com.example.yoloaio.features.walkietalkie.WalkieTalkieRepository]'s
 * code-claim/live-listener/merge-write conventions.
 *
 * Schema:
 *   mindMatchSessions/{code}   hostUid, hostDisplayName, guestUid, guestDisplayName,
 *                              hostAnswers, guestAnswers, createdAt, updatedAt
 *
 * `guestUid` is `""` until a second person claims the open slot exactly
 * once; `hostAnswers`/`guestAnswers` are `[]` until each side submits its
 * own 5-element answer array in one write. See firestore.rules'
 * `mindMatchSessions` block for exactly what each party may write.
 */
class MindMatchRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    private val currentUid: String?
        get() = auth.currentUser?.uid

    private fun sessionsCol() = firestore.collection("mindMatchSessions")

    /** Creates a brand-new session as host, returning its freshly claimed code. */
    suspend fun createSession(): Result<String> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val displayName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
        repeat(5) {
            val code = MindMatchId.generate()
            val ref = sessionsCol().document(code)
            val claimed = runCatching {
                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)
                    if (snap.exists()) error("taken")
                    tx.set(
                        ref,
                        mapOf(
                            "hostUid" to me,
                            "hostDisplayName" to displayName,
                            "guestUid" to "",
                            "guestDisplayName" to "",
                            "hostAnswers" to emptyList<Int>(),
                            "guestAnswers" to emptyList<Int>(),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()
            }.isSuccess
            if (claimed) return@runCatching code
        }
        error("Couldn't start a MindMatch — try again")
    }

    fun observeSession(code: String): Flow<MindMatchSessionDoc?> = callbackFlow {
        val registration = sessionsCol().document(code).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snap?.takeIf { it.exists() }?.toObject(MindMatchSessionDoc::class.java))
        }
        awaitClose { registration.remove() }
    }

    /**
     * Claims the open guest slot on [code] for the signed-in user. Fails
     * (result is a failure, not an exception) if the doc doesn't exist,
     * the slot's already taken, or the caller is the host — the caller
     * should re-read the live doc afterward rather than trust this call's
     * own success/failure as the final word (see MindMatchSessionScreen's
     * role-resolution step, which re-checks the fresh snapshot on a lost
     * race).
     */
    suspend fun claimGuestSlot(code: String, displayName: String): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val ref = sessionsCol().document(code)
        firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) error("not found")
            if (snap.getString("hostUid") == me) error("host cannot join own session")
            if (!snap.getString("guestUid").isNullOrBlank()) error("already full")
            tx.update(
                ref,
                mapOf(
                    "guestUid" to me,
                    "guestDisplayName" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        Unit
    }

    suspend fun submitHostAnswers(code: String, answers: List<Int>): Result<Unit> = runCatching {
        sessionsCol().document(code).set(
            mapOf("hostAnswers" to answers, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
        Unit
    }

    suspend fun submitGuestAnswers(code: String, answers: List<Int>): Result<Unit> = runCatching {
        sessionsCol().document(code).set(
            mapOf("guestAnswers" to answers, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
        Unit
    }
}
