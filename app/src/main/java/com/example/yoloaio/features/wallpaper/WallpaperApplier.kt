package com.example.yoloaio.features.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class WallpaperTarget { Home, Lock, Both }

object WallpaperApplier {

    suspend fun applyFromUrl(
        context: Context,
        url: String,
        target: WallpaperTarget
    ): Result<Unit> = runCatching {
        val bitmap = loadBitmap(context, url)
            ?: error("Couldn't download image")
        applyBitmap(context, bitmap, target)
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            when (val result = loader.execute(request)) {
                is SuccessResult -> (result.drawable as? BitmapDrawable)?.bitmap
                else -> null
            }
        }

    private fun applyBitmap(
        context: Context,
        bitmap: Bitmap,
        target: WallpaperTarget
    ) {
        val wm = WallpaperManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags = when (target) {
                WallpaperTarget.Home -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.Lock -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.Both ->
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            wm.setBitmap(bitmap, null, true, flags)
        } else {
            @Suppress("DEPRECATION")
            wm.setBitmap(bitmap)
        }
    }
}
