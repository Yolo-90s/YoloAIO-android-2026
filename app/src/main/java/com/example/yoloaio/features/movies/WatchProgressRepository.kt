package com.example.yoloaio.features.movies

import com.example.yoloaio.data.FirebaseModule
import kotlinx.coroutines.tasks.await

data class WatchProgress(
    val tmdbId: String = "",
    val mediaType: String = "movie",
    val currentTime: Double = 0.0,
    val duration: Double = 0.0,
    val progress: Double = 0.0,
    val updatedAt: Long = 0L
)

class WatchProgressRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    suspend fun savePosition(
        tmdbId: String,
        mediaType: String,
        currentTime: Double,
        duration: Double
    ) {
        val uid = auth.currentUser?.uid ?: return
        val percent = if (duration > 0) (currentTime / duration) * 100.0 else 0.0
        val data = WatchProgress(
            tmdbId = tmdbId,
            mediaType = mediaType,
            currentTime = currentTime,
            duration = duration,
            progress = percent,
            updatedAt = System.currentTimeMillis()
        )
        runCatching {
            firestore.collection("users").document(uid)
                .collection("watchHistory").document(tmdbId)
                .set(data)
                .await()
        }
    }

    suspend fun getProgress(tmdbId: String): WatchProgress? = runCatching {
        val uid = auth.currentUser?.uid ?: return null
        firestore.collection("users").document(uid)
            .collection("watchHistory").document(tmdbId)
            .get().await()
            .toObject(WatchProgress::class.java)
    }.getOrNull()
}
