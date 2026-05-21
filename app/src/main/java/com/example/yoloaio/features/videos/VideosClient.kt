package com.example.yoloaio.features.videos

import android.util.Log
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

/**
 * Thin client for the videos-proxy Vercel deployment — Kotlin twin of
 * `driveVideosClient.js` in the web app. Talks to the same `/list` and
 * `/stream/{id}` endpoints. The base URL comes from Firestore
 * `config/app.videosApiBaseUrl`, set once and consumed by both clients.
 */
object VideosClient {

    private const val TAG = "VideosClient"

    /**
     * GET {baseUrl}/list → list of videos in the shared Drive folder. The
     * proxy applies the service-account auth on the server side, so this
     * call needs no API key from the client.
     */
    suspend fun listVideos(baseUrl: String): Result<List<Video>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(baseUrl.isNotBlank()) {
                    "Videos proxy isn't configured. Set `videosApiBaseUrl` in " +
                        "Firestore config/app — see videos-proxy/README.md."
                }
                val url = "${baseUrl.trimEnd('/')}/list"
                val body = httpGet(url)
                parseList(parseJson(body))
            }.onFailure { Log.e(TAG, "listVideos failed", it) }
        }

    /** The streamable URL for the given video — passed straight to ExoPlayer / VideoView. */
    fun streamUrlFor(baseUrl: String, videoId: String): String {
        require(baseUrl.isNotBlank()) { "Videos proxy not configured" }
        return "${baseUrl.trimEnd('/')}/stream/${URLEncoder.encode(videoId, "UTF-8")}"
    }

    private fun parseList(root: JSONObject): List<Video> {
        val arr = root.optJSONArray("videos") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Video(
                id = id,
                name = obj.optString("name", "Untitled video"),
                mimeType = obj.optString("mimeType", "video/mp4"),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                durationMs = obj.optLong("durationMs", 0L),
                width = obj.optInt("width", 0),
                height = obj.optInt("height", 0),
                thumbnailUrl = obj.optString("thumbnailUrl", "").ifBlank { null },
                modifiedAt = obj.optString("modifiedAt", "").ifBlank { null }
            )
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val code = conn.responseCode
            Log.d(TAG, "← HTTP $code  $url")
            val stream = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?: error("HTTP $code · empty response")
            val rawBytes = stream.use { it.readBytes() }
            val text = if (looksLikeGzip(rawBytes)) decodeGzip(rawBytes)
            else String(rawBytes, Charsets.UTF_8)
            if (code !in 200..299) {
                error("HTTP $code · ${text.take(300)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun looksLikeGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    /** Multi-member-safe gzip decoder — see TmdbClient.decodeGzip docstring. */
    private fun decodeGzip(bytes: ByteArray): String {
        val out = ByteArrayOutputStream(bytes.size * 3)
        val input = ByteArrayInputStream(bytes)
        while (input.available() >= 2) {
            input.mark(2)
            val b1 = input.read()
            val b2 = input.read()
            if (b1 != 0x1f || b2 != 0x8b) break
            input.reset()
            val nonClosing = object : FilterInputStream(input) {
                override fun close() { /* keep underlying stream open */ }
            }
            GZIPInputStream(nonClosing).use { it.copyTo(out) }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun parseJson(text: String): JSONObject {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) {
            val preview = text.take(200).replace("\n", "\\n").replace("\r", "")
            error(
                "Videos proxy returned non-JSON (${text.length} bytes). " +
                    "First 200: \"$preview${if (text.length > 200) "…" else ""}\""
            )
        }
        return JSONObject(text)
    }
}
