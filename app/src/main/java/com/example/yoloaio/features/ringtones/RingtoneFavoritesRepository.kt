package com.example.yoloaio.features.ringtones

import com.example.yoloaio.data.FirebaseModule
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RingtoneFavoritesRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    private val currentUid: String? get() = auth.currentUser?.uid

    fun observeFavorites(): Flow<List<RingtoneFavorite>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid)
            .collection("favoriteRingtones")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val favs = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(RingtoneFavorite::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(favs)
            }
        awaitClose { registration.remove() }
    }

    suspend fun isFavorited(toneId: String): Boolean = runCatching {
        val uid = currentUid ?: return false
        firestore.collection("users").document(uid)
            .collection("favoriteRingtones").document(safeDocId(toneId))
            .get().await().exists()
    }.getOrDefault(false)

    suspend fun add(tone: Tone): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Not signed in")
        val fav = RingtoneFavorite.fromTone(tone)
        firestore.collection("users").document(uid)
            .collection("favoriteRingtones").document(safeDocId(tone.id))
            .set(fav).await()
        Unit
    }

    suspend fun remove(toneId: String): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Not signed in")
        firestore.collection("users").document(uid)
            .collection("favoriteRingtones").document(safeDocId(toneId))
            .delete().await()
        Unit
    }

    /**
     * Tone ids look like "freesound-12345" or "saavn-abc_xyz". Firestore document
     * ids must not contain "/" — everything else we allow passes through.
     */
    private fun safeDocId(toneId: String): String =
        toneId.replace("/", "_")
}
