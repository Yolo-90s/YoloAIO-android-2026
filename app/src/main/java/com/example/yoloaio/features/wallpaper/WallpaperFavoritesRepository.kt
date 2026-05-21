package com.example.yoloaio.features.wallpaper

import com.example.yoloaio.data.FirebaseModule
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class WallpaperFavoritesRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    private val currentUid: String?
        get() = auth.currentUser?.uid

    fun observeFavorites(): Flow<List<WallpaperFavorite>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid)
            .collection("favoriteWallpapers")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val favs = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(WallpaperFavorite::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(favs)
            }
        awaitClose { registration.remove() }
    }

    suspend fun fetchFavoritesOnce(uid: String): List<WallpaperFavorite> = runCatching {
        firestore.collection("users").document(uid)
            .collection("favoriteWallpapers")
            .get().await()
            .documents.mapNotNull { doc ->
                doc.toObject(WallpaperFavorite::class.java)?.copy(id = doc.id)
            }
    }.getOrDefault(emptyList())

    suspend fun isFavorited(photoId: String): Boolean = runCatching {
        val uid = currentUid ?: return false
        firestore.collection("users").document(uid)
            .collection("favoriteWallpapers").document(photoId)
            .get().await().exists()
    }.getOrDefault(false)

    suspend fun addFavorite(photo: UnsplashPhoto): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Not signed in")
        val fav = WallpaperFavorite.fromPhoto(photo)
        firestore.collection("users").document(uid)
            .collection("favoriteWallpapers").document(photo.id)
            .set(fav).await()
        Unit
    }

    suspend fun removeFavorite(photoId: String): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Not signed in")
        firestore.collection("users").document(uid)
            .collection("favoriteWallpapers").document(photoId)
            .delete().await()
        Unit
    }

    // ---- Rotation settings ----

    fun observeRotationSettings(): Flow<RotationSettings> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(RotationSettings())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid)
            .collection("settings").document("wallpaperRotation")
            .addSnapshotListener { snap, _ ->
                val s = snap?.toObject(RotationSettings::class.java) ?: RotationSettings()
                trySend(s)
            }
        awaitClose { registration.remove() }
    }

    suspend fun setRotationSettings(settings: RotationSettings): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Not signed in")
        firestore.collection("users").document(uid)
            .collection("settings").document("wallpaperRotation")
            .set(settings.copy(updatedAt = System.currentTimeMillis())).await()
        Unit
    }
}
