package com.example.yoloaio.features.wallpaper

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads the wallpaper, writes it to the app's cache, and fires a system share
 * sheet with the JPEG as an attachment. Falls back to sharing the URL as plain
 * text if the image can't be downloaded.
 */
object WallpaperShare {
    suspend fun share(context: Context, photo: UnsplashPhoto) {
        val bitmap = loadBitmap(context, photo.regularUrl)
        if (bitmap == null) {
            shareText(context, photo)
            return
        }
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "wallpaper_${photo.id}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "Photo by ${photo.authorName} on Unsplash"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share wallpaper"))
    }

    private fun shareText(context: Context, photo: UnsplashPhoto) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Photo by ${photo.authorName} on Unsplash — ${photo.fullUrl}"
            )
        }
        context.startActivity(Intent.createChooser(intent, "Share wallpaper"))
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
}
