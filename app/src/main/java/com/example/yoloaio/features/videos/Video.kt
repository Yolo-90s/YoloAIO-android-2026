package com.example.yoloaio.features.videos

/**
 * One entry returned by the videos-proxy `/list` endpoint. Mirrors the JSON
 * shape produced by the same proxy that the web app consumes — both clients
 * read the same Google-Drive-backed catalog via the same Vercel function.
 */
data class Video(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumbnailUrl: String?,
    val modifiedAt: String?
)

/** "1:23", "12:34", or "1:02:34" depending on length. Empty for unknown. */
fun formatVideoDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalSec = (ms / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

/** "1.2 MB" / "934 KB" etc. Empty for unknown / zero. */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var n = bytes.toDouble()
    var i = 0
    while (n >= 1024.0 && i < units.size - 1) {
        n /= 1024.0
        i++
    }
    return if (n < 10.0 && i > 0) "%.1f %s".format(n, units[i])
    else "%.0f %s".format(n, units[i])
}
