package com.example.yoloaio.features.wallpaper

data class WallpaperFavorite(
    val id: String = "",
    val photoId: String = "",
    val smallUrl: String = "",
    val regularUrl: String = "",
    val fullUrl: String = "",
    val authorName: String = "",
    val description: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val addedAt: Long = 0L
) {
    val aspectRatio: Float
        get() = if (height == 0) 1f else width.toFloat() / height.toFloat()

    fun toUnsplashPhoto(): UnsplashPhoto = UnsplashPhoto(
        id = photoId,
        description = description,
        authorName = authorName,
        smallUrl = smallUrl,
        regularUrl = regularUrl,
        fullUrl = fullUrl,
        width = width,
        height = height
    )

    companion object {
        fun fromPhoto(photo: UnsplashPhoto): WallpaperFavorite = WallpaperFavorite(
            id = photo.id,
            photoId = photo.id,
            smallUrl = photo.smallUrl,
            regularUrl = photo.regularUrl,
            fullUrl = photo.fullUrl,
            authorName = photo.authorName,
            description = photo.description,
            width = photo.width,
            height = photo.height,
            addedAt = System.currentTimeMillis()
        )
    }
}

data class RotationSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Long = 60L,
    val updatedAt: Long = 0L
) {
    companion object {
        val MIN_INTERVAL_MINUTES = 15L     // WorkManager minimum
        val INTERVAL_OPTIONS = listOf(15L, 30L, 60L, 240L, 720L, 1440L)
    }
}
