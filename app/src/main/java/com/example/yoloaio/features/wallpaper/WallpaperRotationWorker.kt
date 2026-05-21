package com.example.yoloaio.features.wallpaper

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yoloaio.data.FirebaseModule
import java.util.concurrent.TimeUnit

class WallpaperRotationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseModule.auth.currentUser?.uid
            ?: return Result.success()  // Signed out; quietly no-op until next cycle.

        val repo = WallpaperFavoritesRepository()
        val favorites = repo.fetchFavoritesOnce(uid)
        if (favorites.isEmpty()) return Result.success()

        val pick = favorites.random()
        val photo = pick.toUnsplashPhoto()
        val result = WallpaperApplier.applyFromUrl(
            context = applicationContext,
            url = photo.fullUrl,
            target = WallpaperTarget.Both
        )
        return if (result.isSuccess) Result.success() else Result.retry()
    }
}

object WallpaperRotationManager {
    private const val WORK_NAME = "yolo.wallpaper-rotation"

    fun apply(context: Context, settings: RotationSettings) {
        val wm = WorkManager.getInstance(context)
        if (!settings.enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val interval = settings.intervalMinutes
            .coerceAtLeast(RotationSettings.MIN_INTERVAL_MINUTES)

        val work = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(
            interval, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }
}
