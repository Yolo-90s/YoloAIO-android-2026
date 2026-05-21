package com.example.yoloaio.features.music

import com.example.yoloaio.data.FirebaseModule
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed favourite tracks store under `users/{uid}/favoriteTracks`.
 * Streams via Flow so the Music screen's heart-toggle reflects live changes
 * across devices.
 */
class FavoriteTracksRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    private val currentUid: String? get() = auth.currentUser?.uid

    fun observeFavorites(): Flow<List<FavoriteTrack>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid)
            .collection("favoriteTracks")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val favs = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(FavoriteTrack::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(favs)
            }
        awaitClose { registration.remove() }
    }

    suspend fun add(track: SaavnTrack): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Not signed in")
        val fav = FavoriteTrack.fromTrack(track)
        firestore.collection("users").document(uid)
            .collection("favoriteTracks").document(safeDocId(track.id))
            .set(fav).await()
        Unit
    }

    suspend fun remove(trackId: String): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Not signed in")
        firestore.collection("users").document(uid)
            .collection("favoriteTracks").document(safeDocId(trackId))
            .delete().await()
        Unit
    }

    private fun safeDocId(id: String): String = id.replace("/", "_")
}
