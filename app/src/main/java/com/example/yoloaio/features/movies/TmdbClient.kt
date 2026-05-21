package com.example.yoloaio.features.movies

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

enum class MediaType(val path: String) {
    Movie("movie"),
    Tv("tv");

    companion object {
        fun fromPath(path: String): MediaType =
            if (path == "tv") Tv else Movie
    }
}

data class TmdbTitle(
    val id: Long,
    val mediaType: String,         // "movie" | "tv"
    val title: String,             // movie title or TV name
    val year: Int,
    val rating: Double,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val runtimeMinutes: Int = 0,   // movie runtime; for TV, episodeRunTime[0] if available
    val genres: List<String> = emptyList(),
    val seasons: List<TmdbSeason> = emptyList()    // populated for TV details
) {
    val idAsString: String get() = id.toString()
}

data class TmdbGenre(val id: Int, val name: String)

data class TmdbSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val airDate: String,
    val posterPath: String?
)

data class TmdbEpisode(
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val airDate: String,
    val overview: String,
    val stillPath: String?,
    val rating: Double,
    val runtimeMinutes: Int = 0
)

object TmdbClient {
    private const val API_BASE = "https://api.themoviedb.org/3"
    private const val IMG_BASE = "https://image.tmdb.org/t/p"

    fun posterUrl(path: String?, size: String = "w342"): String? =
        path?.takeIf { it.isNotBlank() && it != "null" }?.let { "$IMG_BASE/$size$it" }

    fun backdropUrl(path: String?, size: String = "w780"): String? =
        path?.takeIf { it.isNotBlank() && it != "null" }?.let { "$IMG_BASE/$size$it" }

    fun stillUrl(path: String?, size: String = "w300"): String? =
        path?.takeIf { it.isNotBlank() && it != "null" }?.let { "$IMG_BASE/$size$it" }

    suspend fun popular(media: MediaType, apiKey: String, page: Int = 1) =
        getList("/${media.path}/popular", apiKey, mapOf("page" to page.toString(), "language" to "en-US"), media)

    suspend fun topRated(media: MediaType, apiKey: String, page: Int = 1) =
        getList("/${media.path}/top_rated", apiKey, mapOf("page" to page.toString(), "language" to "en-US"), media)

    suspend fun trending(media: MediaType, apiKey: String, window: String = "week") =
        getList("/trending/${media.path}/$window", apiKey, mapOf("language" to "en-US"), media)

    suspend fun discoverByGenre(media: MediaType, genreId: Int, apiKey: String, page: Int = 1) =
        getList(
            "/discover/${media.path}",
            apiKey,
            mapOf(
                "page" to page.toString(),
                "language" to "en-US",
                "with_genres" to genreId.toString(),
                "sort_by" to "popularity.desc",
                "include_adult" to "false"
            ),
            media
        )

    suspend fun search(media: MediaType, query: String, apiKey: String, page: Int = 1): Result<List<TmdbTitle>> {
        if (query.isBlank()) return Result.success(emptyList())
        return getList(
            "/search/${media.path}",
            apiKey,
            mapOf(
                "query" to query,
                "page" to page.toString(),
                "include_adult" to "false",
                "language" to "en-US"
            ),
            media
        )
    }

    suspend fun details(media: MediaType, id: Long, apiKey: String): Result<TmdbTitle> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(apiKey.isNotBlank()) { "Missing TMDB API key" }
                val url = buildUrl("/${media.path}/$id", emptyMap(), apiKey)
                val text = fetchString(url, apiKey)
                parseDetails(parseJson(text), media)
            }
        }

    suspend fun seasonEpisodes(tvId: Long, seasonNumber: Int, apiKey: String): Result<List<TmdbEpisode>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(apiKey.isNotBlank()) { "Missing TMDB API key" }
                val url = buildUrl("/tv/$tvId/season/$seasonNumber", emptyMap(), apiKey)
                val text = fetchString(url, apiKey)
                val obj = parseJson(text)
                val arr = obj.optJSONArray("episodes") ?: return@runCatching emptyList<TmdbEpisode>()
                buildList {
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        add(
                            TmdbEpisode(
                                episodeNumber = e.optInt("episode_number"),
                                seasonNumber = e.optInt("season_number", seasonNumber),
                                name = e.optString("name"),
                                airDate = e.optString("air_date"),
                                overview = e.optString("overview"),
                                stillPath = e.optString("still_path", "")
                                    .takeIf { it.isNotBlank() && it != "null" },
                                rating = e.optDouble("vote_average", 0.0),
                                runtimeMinutes = e.optInt("runtime", 0)
                            )
                        )
                    }
                }
            }
        }

    suspend fun genres(media: MediaType, apiKey: String): Result<List<TmdbGenre>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(apiKey.isNotBlank()) { "Missing TMDB API key" }
                val url = buildUrl("/genre/${media.path}/list", mapOf("language" to "en-US"), apiKey)
                val text = fetchString(url, apiKey)
                val arr = parseJson(text).optJSONArray("genres") ?: return@runCatching emptyList<TmdbGenre>()
                buildList {
                    for (i in 0 until arr.length()) {
                        val g = arr.getJSONObject(i)
                        add(TmdbGenre(id = g.optInt("id"), name = g.optString("name")))
                    }
                }
            }
        }

    private suspend fun getList(
        path: String,
        apiKey: String,
        params: Map<String, String>,
        media: MediaType
    ): Result<List<TmdbTitle>> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "Missing TMDB API key" }
            val url = buildUrl(path, params, apiKey)
            val text = fetchString(url, apiKey)
            val obj = parseJson(text)
            val results = obj.optJSONArray("results") ?: return@runCatching emptyList<TmdbTitle>()
            buildList {
                for (i in 0 until results.length()) {
                    add(parseRow(results.getJSONObject(i), media))
                }
            }
        }
    }

    private fun buildUrl(path: String, params: Map<String, String>, apiKey: String): String {
        val isBearer = apiKey.startsWith("eyJ")
        val finalParams = if (isBearer) params else params + ("api_key" to apiKey)
        val query = if (finalParams.isEmpty()) "" else
            "?" + finalParams.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
        return "$API_BASE$path$query"
    }

    private fun fetchString(url: String, apiKey: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            // Prefer uncompressed responses — TMDB's CDN sometimes serves
            // *concatenated* gzip members that Java's stock single-member
            // GZIPInputStream rejects with "gzip finished without
            // exhausting source". But some upstream proxies may ignore this
            // header and gzip anyway, so we detect by magic bytes below and
            // decode ourselves (multi-member safe).
            setRequestProperty("Accept-Encoding", "identity")
            if (apiKey.startsWith("eyJ")) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
        try {
            val code = conn.responseCode
            val stream = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?: error("TMDB HTTP $code · empty response")
            val rawBytes = stream.use { it.readBytes() }

            val text = if (looksLikeGzip(rawBytes)) decodeGzip(rawBytes)
            else String(rawBytes, Charsets.UTF_8)

            if (code !in 200..299) {
                error("TMDB HTTP $code · ${text.take(300)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun looksLikeGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    /**
     * Multi-member-safe gzip decoder. Java's stock [GZIPInputStream] only
     * reads one gzip member, but CDNs (Cloudflare, Vercel) sometimes emit
     * chunked gzip as multiple concatenated members. We loop: peek for the
     * gzip magic 0x1f8b, wrap a fresh decoder, drain, repeat until empty.
     */
    private fun decodeGzip(bytes: ByteArray): String {
        val out = ByteArrayOutputStream(bytes.size * 3)
        val input = ByteArrayInputStream(bytes)
        while (input.available() >= 2) {
            input.mark(2)
            val b1 = input.read()
            val b2 = input.read()
            if (b1 != 0x1f || b2 != 0x8b) break        // trailing junk / padding
            input.reset()
            // GZIPInputStream.close() would close the underlying stream too;
            // wrap so the outer ByteArrayInputStream survives the next iter.
            val nonClosing = object : FilterInputStream(input) {
                override fun close() { /* no-op */ }
            }
            GZIPInputStream(nonClosing).use { it.copyTo(out) }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /**
     * Parse a TMDB JSON body, surfacing a useful preview when it isn't
     * actually JSON (HTML error page, captive portal, truncated stream…).
     */
    private fun parseJson(text: String): JSONObject {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) {
            val preview = text.take(200).replace("\n", "\\n").replace("\r", "")
            error(
                "TMDB returned non-JSON (${text.length} bytes). " +
                    "First 200: \"$preview${if (text.length > 200) "…" else ""}\""
            )
        }
        return JSONObject(text)
    }

    private fun parseRow(o: JSONObject, media: MediaType): TmdbTitle {
        val date = if (media == MediaType.Movie)
            o.optString("release_date", "")
        else
            o.optString("first_air_date", "")
        val year = date.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull() ?: 0
        return TmdbTitle(
            id = o.getLong("id"),
            mediaType = media.path,
            title = o.optString("title", "").ifBlank { o.optString("name", "") },
            year = year,
            rating = o.optDouble("vote_average", 0.0),
            overview = o.optString("overview", ""),
            posterPath = o.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" },
            backdropPath = o.optString("backdrop_path", "").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun parseDetails(o: JSONObject, media: MediaType): TmdbTitle {
        val base = parseRow(o, media)
        val genresArr = o.optJSONArray("genres")
        val genres = mutableListOf<String>()
        if (genresArr != null) {
            for (i in 0 until genresArr.length()) {
                val name = genresArr.getJSONObject(i).optString("name")
                if (name.isNotBlank()) genres.add(name)
            }
        }
        val runtime = if (media == MediaType.Movie) {
            o.optInt("runtime", 0)
        } else {
            val arr = o.optJSONArray("episode_run_time")
            if (arr != null && arr.length() > 0) arr.optInt(0, 0) else 0
        }
        val seasons = if (media == MediaType.Tv) {
            val arr = o.optJSONArray("seasons") ?: return@parseDetails base.copy(runtimeMinutes = runtime, genres = genres)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    val num = s.optInt("season_number", -1)
                    if (num < 1) continue   // skip "Specials" (season 0)
                    add(
                        TmdbSeason(
                            seasonNumber = num,
                            name = s.optString("name"),
                            episodeCount = s.optInt("episode_count", 0),
                            airDate = s.optString("air_date"),
                            posterPath = s.optString("poster_path", "")
                                .takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }
        } else emptyList()

        return base.copy(runtimeMinutes = runtime, genres = genres, seasons = seasons)
    }
}

object TmdbCache {
    @Volatile
    private var titles: Map<Long, TmdbTitle> = emptyMap()

    fun setList(list: List<TmdbTitle>) {
        titles = list.associateBy { it.id }
    }

    fun put(title: TmdbTitle) {
        titles = titles + (title.id to title)
    }

    fun byId(id: Long): TmdbTitle? = titles[id]

    fun byIdString(id: String): TmdbTitle? = id.toLongOrNull()?.let { byId(it) }
}
