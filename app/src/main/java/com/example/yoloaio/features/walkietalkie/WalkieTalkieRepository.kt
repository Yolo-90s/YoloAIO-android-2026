package com.example.yoloaio.features.walkietalkie

import com.example.yoloaio.data.FirebaseModule
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed signaling for WalkieTalkie: channel-code claim/refresh,
 * live/heartbeat state, and the offer/answer/ICE-candidate exchange
 * consumed by [WalkieTalkieEngine]. Mirrors the realtime-listener and
 * merge-write conventions used throughout `ChatRepository`.
 *
 * Schema:
 *   walkieChannels/{code}                          ownerUid, createdAt, live, updatedAt
 *   walkieChannels/{code}/sessions/{receiverUid}    offer, answer, createdAt
 *     .../receiverCandidates/{autoId}
 *     .../transmitterCandidates/{autoId}
 */
class WalkieTalkieRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    private val currentUid: String?
        get() = auth.currentUser?.uid

    private fun channelsCol() = firestore.collection("walkieChannels")
    private fun sessionsCol(code: String) = channelsCol().document(code).collection("sessions")

    /** Returns the caller's existing code, claiming a fresh one on first use. */
    suspend fun ensureChannelCode(): Result<String> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val existing = firestore.collection("users").document(me).get().await()
            .getString("walkieId")
        if (!existing.isNullOrBlank()) return@runCatching existing
        claimNewCode(me)
    }

    /** Retires the old code (best-effort) and claims a brand new one. */
    suspend fun refreshChannelCode(): Result<String> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val old = firestore.collection("users").document(me).get().await()
            .getString("walkieId")
        if (!old.isNullOrBlank()) {
            runCatching { channelsCol().document(old).delete().await() }
        }
        claimNewCode(me)
    }

    private suspend fun claimNewCode(uid: String): String {
        repeat(5) {
            val code = WalkieChannelId.generate()
            val ref = channelsCol().document(code)
            val claimed = runCatching {
                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)
                    if (snap.exists()) error("taken")
                    tx.set(
                        ref,
                        mapOf(
                            "ownerUid" to uid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "live" to false
                        )
                    )
                }.await()
            }.isSuccess
            if (claimed) {
                firestore.collection("users").document(uid)
                    .set(mapOf("walkieId" to code), SetOptions.merge())
                    .await()
                return code
            }
        }
        error("Couldn't claim a WalkieTalkie code — try again")
    }

    suspend fun setLive(code: String, live: Boolean): Result<Unit> = runCatching {
        channelsCol().document(code).set(
            mapOf("live" to live, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
        Unit
    }

    suspend fun heartbeat(code: String): Result<Unit> = runCatching {
        channelsCol().document(code).set(
            mapOf("updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
        Unit
    }

    suspend fun fetchChannel(code: String): WalkieChannelDoc? = runCatching {
        channelsCol().document(code).get().await().toObject(WalkieChannelDoc::class.java)
    }.getOrNull()

    /** Transmitter side: fires whenever a receiver's session doc is added/changed/removed. */
    fun observeSessions(code: String): Flow<List<DocumentChange>> = callbackFlow {
        val registration = sessionsCol(code).addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
            trySend(snap?.documentChanges ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /** Receiver side: watches its own session doc for the transmitter's answer. */
    fun observeSession(code: String, receiverUid: String): Flow<WalkieSessionDoc?> = callbackFlow {
        val registration = sessionsCol(code).document(receiverUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(WalkieSessionDoc::class.java))
            }
        awaitClose { registration.remove() }
    }

    suspend fun writeOffer(code: String, receiverUid: String, offer: SdpPayload): Result<Unit> = runCatching {
        sessionsCol(code).document(receiverUid).set(
            mapOf(
                "offer" to mapOf("sdp" to offer.sdp, "type" to offer.type),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    suspend fun writeAnswer(code: String, receiverUid: String, answer: SdpPayload): Result<Unit> = runCatching {
        sessionsCol(code).document(receiverUid).set(
            mapOf("answer" to mapOf("sdp" to answer.sdp, "type" to answer.type)),
            SetOptions.merge()
        ).await()
        Unit
    }

    suspend fun addIceCandidate(
        code: String,
        receiverUid: String,
        from: WalkieRole,
        candidate: IcePayload
    ): Result<Unit> = runCatching {
        sessionsCol(code).document(receiverUid).collection(candidateSubcollection(from)).add(
            mapOf(
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "candidate" to candidate.candidate
            )
        ).await()
        Unit
    }

    /** [from] is the role whose candidates we want to *receive* (the peer's role). */
    fun observeIceCandidates(code: String, receiverUid: String, from: WalkieRole): Flow<List<IcePayload>> =
        callbackFlow {
            val registration = sessionsCol(code).document(receiverUid)
                .collection(candidateSubcollection(from))
                .addSnapshotListener { snap, err ->
                    if (err != null) return@addSnapshotListener
                    val added = snap?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.ADDED }
                        ?.mapNotNull { it.document.toObject(IcePayload::class.java) }
                        ?: emptyList()
                    if (added.isNotEmpty()) trySend(added)
                }
            awaitClose { registration.remove() }
        }

    private fun candidateSubcollection(from: WalkieRole) = when (from) {
        WalkieRole.RECEIVE -> "receiverCandidates"
        WalkieRole.TRANSMIT -> "transmitterCandidates"
    }

    /** Deletes a session doc and both candidate subcollections underneath it. */
    suspend fun endSession(code: String, receiverUid: String): Result<Unit> = runCatching {
        val sessionRef = sessionsCol(code).document(receiverUid)
        for (sub in listOf("receiverCandidates", "transmitterCandidates")) {
            val docs = sessionRef.collection(sub).get().await()
            if (!docs.isEmpty) {
                val batch = firestore.batch()
                docs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        sessionRef.delete().await()
        Unit
    }
}
