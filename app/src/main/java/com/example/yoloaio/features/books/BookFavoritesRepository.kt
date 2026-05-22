package com.example.yoloaio.features.books

import com.example.yoloaio.data.FirebaseModule
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed favourites for books. Same layout as the wallpaper /
 * ringtone favourites — collection path `users/{uid}/favoriteBooks`,
 * doc id == bookId (Gutendex numeric id as a String).
 */
class BookFavoritesRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    private val currentUid: String?
        get() = auth.currentUser?.uid

    fun observeFavorites(): Flow<List<BookFavorite>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid)
            .collection("favoriteBooks")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val favs = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(BookFavorite::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(favs)
            }
        awaitClose { registration.remove() }
    }

    suspend fun isFavorited(bookId: String): Boolean = runCatching {
        val uid = currentUid ?: return false
        firestore.collection("users").document(uid)
            .collection("favoriteBooks").document(bookId)
            .get().await().exists()
    }.getOrDefault(false)

    suspend fun addFavorite(book: Book): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Not signed in")
        val fav = BookFavorite.fromBook(book)
        firestore.collection("users").document(uid)
            .collection("favoriteBooks").document(book.id)
            .set(fav).await()
        Unit
    }

    suspend fun removeFavorite(bookId: String): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Not signed in")
        firestore.collection("users").document(uid)
            .collection("favoriteBooks").document(bookId)
            .delete().await()
        Unit
    }
}
