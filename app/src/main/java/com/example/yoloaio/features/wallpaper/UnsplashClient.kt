package com.example.yoloaio.features.wallpaper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class UnsplashPhoto(
    val id: String,
    val description: String,
    val authorName: String,
    val smallUrl: String,
    val regularUrl: String,
    val fullUrl: String,
    val width: Int = 0,
    val height: Int = 0
) {
    val aspectRatio: Float
        get() = if (height == 0) 1f else width.toFloat() / height.toFloat()
    val isFourK: Boolean get() = width >= 3840 || height >= 3840
    val is2K: Boolean get() = width >= 2048 || height >= 2048
}

enum class WallpaperOrientation(val apiValue: String?, val label: String) {
    Any(null, "Any"),
    Portrait("portrait", "Portrait"),
    Landscape("landscape", "Landscape"),
    Squarish("squarish", "Square")
}

enum class ResolutionFilter(val label: String) {
    Any("Any res"),
    TwoK("2K+"),
    FourK("4K+");

    fun accepts(photo: UnsplashPhoto): Boolean = when (this) {
        Any -> true
        TwoK -> photo.is2K
        FourK -> photo.isFourK
    }
}

object UnsplashClient {
    private const val BASE = "https://api.unsplash.com"

    suspend fun search(
        query: String,
        accessKey: String,
        perPage: Int = 30,
        orientation: WallpaperOrientation = WallpaperOrientation.Portrait
    ): Result<List<UnsplashPhoto>> = withContext(Dispatchers.IO) {
        runCatching {
            require(accessKey.isNotBlank()) { "Missing Unsplash access key" }
            val q = URLEncoder.encode(query.ifBlank { "nature" }, "UTF-8")
            val orientationParam = orientation.apiValue?.let { "&orientation=$it" }.orEmpty()
            val url = URL("$BASE/search/photos?query=$q&per_page=$perPage$orientationParam")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Client-ID $accessKey")
                setRequestProperty("Accept-Version", "v1")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                if (conn.responseCode !in 200..299) {
                    val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "HTTP ${conn.responseCode}"
                    error("Unsplash error: $errorMsg")
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(text).getJSONArray("results")
                buildList {
                    for (i in 0 until results.length()) {
                        val o = results.getJSONObject(i)
                        val urls = o.getJSONObject("urls")
                        val user = o.optJSONObject("user")
                        add(
                            UnsplashPhoto(
                                id = o.getString("id"),
                                description = o.optString("alt_description", "")
                                    .ifBlank { o.optString("description", "") },
                                authorName = user?.optString("name", "Unsplash") ?: "Unsplash",
                                smallUrl = urls.getString("small"),
                                regularUrl = urls.getString("regular"),
                                fullUrl = urls.getString("full"),
                                width = o.optInt("width", 0),
                                height = o.optInt("height", 0)
                            )
                        )
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}

object WallpaperCache {
    @Volatile
    private var photos: Map<String, UnsplashPhoto> = emptyMap()

    /** Replace the cache (used by the grid every time a new search comes back). */
    fun set(list: List<UnsplashPhoto>) {
        photos = list.associateBy { it.id }
    }

    /** Add to the cache without dropping existing entries (used for related fetches). */
    fun merge(list: List<UnsplashPhoto>) {
        photos = photos + list.associateBy { it.id }
    }

    fun byId(id: String): UnsplashPhoto? = photos[id]
}
