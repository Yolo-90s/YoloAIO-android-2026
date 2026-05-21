package com.example.yoloaio.features.music

import android.text.Html
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESKeySpec

/**
 * Language filter for the music catalog. `code` is the value JioSaavn's API
 * expects; `label` is what's shown in the chip.
 */
enum class MusicLanguage(val code: String, val label: String) {
    Telugu("telugu", "Telugu"),
    Hindi("hindi", "Hindi"),
    Tamil("tamil", "Tamil"),
    Kannada("kannada", "Kannada"),
    Malayalam("malayalam", "Malayalam"),
    English("english", "English"),
    Punjabi("punjabi", "Punjabi"),
    Marathi("marathi", "Marathi"),
    Bengali("bengali", "Bengali"),
    Bhojpuri("bhojpuri", "Bhojpuri"),
    Gujarati("gujarati", "Gujarati"),
    Urdu("urdu", "Urdu"),
    Haryanvi("haryanvi", "Haryanvi"),
    Rajasthani("rajasthani", "Rajasthani"),
    Odia("odia", "Odia"),
    Assamese("assamese", "Assamese");

    companion object {
        val Default: MusicLanguage = Telugu
        val all: List<MusicLanguage> = entries.toList()
    }
}

data class SaavnTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val artworkUrlSmall: String?,
    val artworkUrlLarge: String?,
    val language: String,
    val year: String,
    val streamUrl: String
) {
    val durationFormatted: String
        get() {
            val m = durationSec / 60
            val s = durationSec % 60
            return "%d:%02d".format(m, s)
        }
}

/** A JioSaavn album result — used by the Albums tab grid. */
data class SaavnAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrlSmall: String?,
    val artworkUrlLarge: String?,
    val year: String,
    val songCount: Int
)

/** A JioSaavn playlist result — used by the Playlists tab grid. */
data class SaavnPlaylist(
    val id: String,
    val title: String,
    val curator: String,
    val artworkUrlSmall: String?,
    val artworkUrlLarge: String?,
    val songCount: Int
)

object JioSaavnClient {
    private const val TAG = "JioSaavn"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; YoloAIO) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private const val DIRECT_BASE = "https://www.jiosaavn.com/api.php"

    /**
     * JioSaavn's web frontend decrypts `encrypted_media_url` via DES-ECB with this
     * fixed public key. It's hard-coded in their public JavaScript bundle.
     */
    private const val DECRYPT_KEY = "38346591"

    suspend fun search(
        query: String,
        language: MusicLanguage?,
        limit: Int = 30
    ): Result<List<SaavnTrack>> = withContext(Dispatchers.IO) {
        val effectiveQuery = query.ifBlank { language?.label ?: "top" }
        Log.d(TAG, "search start · query='$effectiveQuery' lang=${language?.code}")
        runCatching {
            val tracks = searchDirect(effectiveQuery, language, limit)
            Log.d(TAG, "✓ direct returned ${tracks.size} tracks")
            tracks
        }.onFailure {
            Log.e(TAG, "✗ direct jiosaavn.com failed", it)
        }
    }

    suspend fun searchAlbums(
        query: String,
        language: MusicLanguage?,
        limit: Int = 24
    ): Result<List<SaavnAlbum>> = withContext(Dispatchers.IO) {
        val effectiveQuery = query.ifBlank { language?.label ?: "top albums" }
        Log.d(TAG, "albums start · query='$effectiveQuery' lang=${language?.code}")
        runCatching {
            val albums = searchAlbumsDirect(effectiveQuery, limit)
            Log.d(TAG, "✓ albums returned ${albums.size}")
            albums
        }.onFailure {
            Log.e(TAG, "✗ albums failed", it)
        }
    }

    suspend fun searchPlaylists(
        query: String,
        language: MusicLanguage?,
        limit: Int = 24
    ): Result<List<SaavnPlaylist>> = withContext(Dispatchers.IO) {
        val effectiveQuery = query.ifBlank { language?.label ?: "trending playlists" }
        Log.d(TAG, "playlists start · query='$effectiveQuery' lang=${language?.code}")
        runCatching {
            val playlists = searchPlaylistsDirect(effectiveQuery, limit)
            Log.d(TAG, "✓ playlists returned ${playlists.size}")
            playlists
        }.onFailure {
            Log.e(TAG, "✗ playlists failed", it)
        }
    }

    private fun searchAlbumsDirect(query: String, limit: Int): List<SaavnAlbum> {
        val params = linkedMapOf(
            "__call" to "search.getAlbumResults",
            "q" to query,
            "n" to limit.toString(),
            "p" to "1",
            "api_version" to "4",
            "_format" to "json",
            "_marker" to "0",
            "ctx" to "web6dot0"
        )
        val url = "$DIRECT_BASE?" + params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val text = httpGet(url)
        return parseAlbumsResponse(text)
    }

    private fun searchPlaylistsDirect(query: String, limit: Int): List<SaavnPlaylist> {
        val params = linkedMapOf(
            "__call" to "search.getPlaylistResults",
            "q" to query,
            "n" to limit.toString(),
            "p" to "1",
            "api_version" to "4",
            "_format" to "json",
            "_marker" to "0",
            "ctx" to "web6dot0"
        )
        val url = "$DIRECT_BASE?" + params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val text = httpGet(url)
        return parsePlaylistsResponse(text)
    }

    private fun parseAlbumsResponse(rawText: String): List<SaavnAlbum> {
        var text = rawText.trim()
        if (text.startsWith("(") && text.endsWith(")")) {
            text = text.substring(1, text.length - 1).trim()
        }
        val obj = JSONObject(text)
        val results = obj.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<SaavnAlbum>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val id = o.optString("id").nullIfEmptyOrLiteralNull() ?: continue
            val title = decodeHtml(
                o.optString("title").nullIfEmptyOrLiteralNull()
                    ?: o.optString("name")
            ).ifBlank { "Untitled album" }
            val rawImage = o.optString("image").nullIfEmptyOrLiteralNull()
            val largeArt = rawImage?.replace("50x50.jpg", "500x500.jpg")
            val smallArt = rawImage?.replace("50x50.jpg", "150x150.jpg")
            val more = o.optJSONObject("more_info")
            val artist = decodeHtml(
                more?.optString("music")?.nullIfEmptyOrLiteralNull()
                    ?: o.optString("subtitle").nullIfEmptyOrLiteralNull()
                    ?: "Various artists"
            )
            val year = o.optString("year").nullIfEmptyOrLiteralNull().orEmpty()
            val songCount = more?.optString("song_count")?.toIntOrNull() ?: 0
            out += SaavnAlbum(
                id = id,
                title = title,
                artist = artist,
                artworkUrlSmall = smallArt,
                artworkUrlLarge = largeArt,
                year = year,
                songCount = songCount
            )
        }
        return out
    }

    private fun parsePlaylistsResponse(rawText: String): List<SaavnPlaylist> {
        var text = rawText.trim()
        if (text.startsWith("(") && text.endsWith(")")) {
            text = text.substring(1, text.length - 1).trim()
        }
        val obj = JSONObject(text)
        val results = obj.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<SaavnPlaylist>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val id = o.optString("id").nullIfEmptyOrLiteralNull() ?: continue
            val title = decodeHtml(
                o.optString("title").nullIfEmptyOrLiteralNull()
                    ?: o.optString("name")
            ).ifBlank { "Untitled playlist" }
            val rawImage = o.optString("image").nullIfEmptyOrLiteralNull()
            val largeArt = rawImage?.replace("150x150.jpg", "500x500.jpg")
                ?.replace("50x50.jpg", "500x500.jpg")
            val smallArt = rawImage?.replace("50x50.jpg", "150x150.jpg")
            val more = o.optJSONObject("more_info")
            val curator = decodeHtml(
                more?.optString("firstname")?.nullIfEmptyOrLiteralNull()
                    ?: o.optString("subtitle").nullIfEmptyOrLiteralNull()
                    ?: "JioSaavn"
            )
            val songCount = more?.optString("song_count")?.toIntOrNull() ?: 0
            out += SaavnPlaylist(
                id = id,
                title = title,
                curator = curator,
                artworkUrlSmall = smallArt,
                artworkUrlLarge = largeArt,
                songCount = songCount
            )
        }
        return out
    }

    // ---- Direct JioSaavn API ----

    private fun searchDirect(
        query: String,
        language: MusicLanguage?,
        limit: Int
    ): List<SaavnTrack> {
        val params = linkedMapOf(
            "__call" to "search.getResults",
            "q" to query,            // JioSaavn expects "q", not "query"
            "n" to limit.toString(),
            "p" to "1",
            "api_version" to "4",
            "_format" to "json",
            "_marker" to "0",
            "ctx" to "web6dot0"
        )
        val url = "$DIRECT_BASE?" + params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val text = httpGet(url)
        return parseDirectResponse(text, language)
    }

    private fun parseDirectResponse(rawText: String, requested: MusicLanguage?): List<SaavnTrack> {
        // The endpoint occasionally wraps the JSON in a JSONP callback. Strip it.
        var text = rawText.trim()
        if (text.startsWith("(") && text.endsWith(")")) {
            text = text.substring(1, text.length - 1).trim()
        }

        val obj = JSONObject(text)
        val results = obj.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<SaavnTrack>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val type = o.optString("type")
            if (type.isNotBlank() && type != "song") continue
            val id = o.optString("id").nullIfEmptyOrLiteralNull() ?: continue
            val moreInfo = o.optJSONObject("more_info") ?: continue
            val encrypted = moreInfo.optString("encrypted_media_url").nullIfEmptyOrLiteralNull()
                ?: continue
            val streamUrl = decryptStreamUrl(encrypted) ?: continue

            val title = decodeHtml(
                o.optString("title").nullIfEmptyOrLiteralNull()
                    ?: o.optString("name")
            ).ifBlank { "Untitled" }

            val rawImage = o.optString("image").nullIfEmptyOrLiteralNull()
            val largeArt = rawImage?.replace("50x50.jpg", "500x500.jpg")
            val smallArt = rawImage?.replace("50x50.jpg", "150x150.jpg")

            val language = o.optString("language").nullIfEmptyOrLiteralNull().orEmpty()
            val year = o.optString("year").nullIfEmptyOrLiteralNull().orEmpty()
            val durationSec = moreInfo.optString("duration").toIntOrNull() ?: 0

            val rawArtist = moreInfo.optString("primary_artists").nullIfEmptyOrLiteralNull()
                ?: o.optString("subtitle").nullIfEmptyOrLiteralNull()
                ?: "Unknown"
            val artist = decodeHtml(rawArtist)

            out += SaavnTrack(
                id = id,
                title = title,
                artist = artist,
                durationSec = durationSec,
                artworkUrlSmall = smallArt,
                artworkUrlLarge = largeArt,
                language = language,
                year = year,
                streamUrl = streamUrl
            )
        }
        return reorderByLanguage(out, requested)
    }

    /**
     * DES-ECB / PKCS5Padding decrypt of the base64 ciphertext, then upgrade the
     * resulting http URL to https on the AAC CDN host so it plays on modern
     * Android (which blocks cleartext traffic by default).
     */
    private fun decryptStreamUrl(encrypted: String): String? = try {
        val keySpec = DESKeySpec(DECRYPT_KEY.toByteArray(Charsets.US_ASCII))
        val secretKey = SecretKeyFactory.getInstance("DES").generateSecret(keySpec)
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE, secretKey)
        }
        val raw = String(
            cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)),
            Charsets.UTF_8
        )
        raw
            .replace("http://", "https://")
            .replace("https://h.saavncdn.com", "https://aac.saavncdn.com")
    } catch (_: Exception) {
        null
    }

    // ---- shared HTTP + helpers ----

    private fun httpGet(url: String): String {
        Log.d(TAG, "GET $url")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            // Some endpoints serve gzipped bodies that the default HttpURLConnection
            // doesn't auto-decode; force identity to keep parsing deterministic.
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Referer", "https://www.jiosaavn.com/")
            setRequestProperty("Cookie", "L=english; gdpr_acceptance=true; DL=english")
        }
        try {
            val code = conn.responseCode
            Log.d(TAG, "← HTTP $code from ${URL(url).host}")
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.take(200) ?: "no body"
                Log.e(TAG, "HTTP $code body: $errBody")
                error("HTTP $code · $errBody")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "body length=${body.length} preview='${body.take(120).replace("\n", " ")}'")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun reorderByLanguage(
        tracks: List<SaavnTrack>,
        requested: MusicLanguage?
    ): List<SaavnTrack> {
        if (requested == null) return tracks
        val matched = tracks.filter { it.language.equals(requested.code, ignoreCase = true) }
        val others = tracks - matched.toSet()
        return matched + others
    }

    private fun decodeHtml(value: String): String {
        if (value.isBlank()) return value
        return try {
            Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (_: Exception) {
            value
        }
    }

    private fun String?.nullIfEmptyOrLiteralNull(): String? =
        if (this.isNullOrBlank() || this == "null") null else this
}
