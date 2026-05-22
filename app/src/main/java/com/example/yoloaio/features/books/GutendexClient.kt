package com.example.yoloaio.features.books

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Single book metadata record returned by the Gutendex API
 * (https://gutendex.com) — a free, no-auth REST wrapper over Project
 * Gutenberg's catalogue of ~70 000 public-domain books.
 *
 * `textUrl` is the preferred read-anywhere format (plain UTF-8 text or
 * HTML). `coverUrl` may be empty for books that never had a cover scanned.
 */
data class Book(
    val id: String,
    val title: String,
    val authors: String,
    val subjects: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val downloadCount: Int = 0,
    val coverUrl: String = "",
    val textUrl: String = "",
    val htmlUrl: String = "",
    val epubUrl: String = ""
) {
    val displayAuthor: String
        get() = authors.ifBlank { "Unknown author" }

    /** Best-effort URL to render in a WebView. HTML preferred (formatting),
     *  falls back to plain text. */
    val readableUrl: String
        get() = when {
            htmlUrl.isNotBlank() -> htmlUrl
            textUrl.isNotBlank() -> textUrl
            else -> ""
        }

    val hasReadableText: Boolean
        get() = readableUrl.isNotBlank()
}

object GutendexClient {
    private const val BASE = "https://gutendex.com"

    // Project Gutenberg blocks the default Android WebView User-Agent
    // (anti-scraping policy). Sending a Chrome UA from our own
    // HttpURLConnection slips through; we then feed the bytes back to
    // the WebView via loadDataWithBaseURL, which never touches PG itself.
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /**
     * Upgrade a plain-http URL to https. Project Gutenberg serves the
     * same content on both schemes, and Android API 28+ blocks cleartext
     * by default. Used everywhere a URL crosses our app→OS boundary
     * (initial fetch, redirect target, body URLs handed to the WebView).
     */
    private fun httpsify(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7)
        else url

    /**
     * Download a Gutenberg-hosted book's HTML or plain-text body.
     * Follows up to 5 redirects manually — HttpURLConnection refuses
     * cross-protocol redirects (HTTPS→HTTP, HTTP→HTTPS) even with
     * `instanceFollowRedirects = true`, and Gutenberg uses such hops
     * on its canonical mirrors. The Chrome UA defeats PG's bot guard
     * that blocks the default Android WebView UA.
     */
    suspend fun fetchBookBody(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.isNotBlank()) { "Empty book URL" }
            // Force HTTPS up-front. Android API 28+ blocks cleartext by
            // default and PG redirects routinely include a plain-http hop.
            var current = httpsify(url)
            var hops = 0
            while (hops < 5) {
                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", UA)
                    setRequestProperty("Accept", "text/html,text/plain,*/*")
                    setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                    connectTimeout = 12_000
                    readTimeout = 30_000
                    // Turn off auto-follow so we can handle cross-protocol
                    // hops ourselves below.
                    instanceFollowRedirects = false
                }
                try {
                    val code = conn.responseCode
                    when (code) {
                        in 200..299 -> {
                            return@withContext Result.success(
                                conn.inputStream.bufferedReader().use { it.readText() }
                            )
                        }
                        301, 302, 303, 307, 308 -> {
                            val loc = conn.getHeaderField("Location")
                                ?: error("Redirect ($code) without Location header")
                            // Resolve relative URIs against the current URL,
                            // then force https so cleartext isn't blocked
                            // partway through a chain.
                            current = httpsify(URL(URL(current), loc).toString())
                            hops++
                        }
                        else -> {
                            val err = conn.errorStream?.bufferedReader()
                                ?.use { it.readText() } ?: "HTTP $code"
                            error("Gutenberg fetch failed: $err")
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            }
            error("Too many redirects (5+) fetching $url")
        }
    }

    /**
     * Search the catalogue. Empty query returns the most-downloaded books,
     * which makes a good landing page. Gutendex paginates 32 results per
     * page; we currently only fetch page 1 — the user can refine with the
     * search box if they want something specific.
     */
    suspend fun search(
        query: String = "",
        topic: String? = null,
        languageCode: String? = "en"
    ): Result<List<Book>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildList {
                if (query.isNotBlank()) {
                    add("search=${URLEncoder.encode(query.trim(), "UTF-8")}")
                }
                if (!topic.isNullOrBlank()) {
                    add("topic=${URLEncoder.encode(topic.trim(), "UTF-8")}")
                }
                if (!languageCode.isNullOrBlank()) {
                    add("languages=$languageCode")
                }
            }.joinToString("&")
            val url = URL(if (params.isBlank()) "$BASE/books" else "$BASE/books?$params")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                if (conn.responseCode !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "HTTP ${conn.responseCode}"
                    error("Gutendex error: $err")
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(text).getJSONArray("results")
                buildList {
                    for (i in 0 until results.length()) {
                        add(parseBook(results.getJSONObject(i)))
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parseBook(o: JSONObject): Book {
        val authors = o.optJSONArray("authors")
            ?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.optString("name")?.let { name ->
                            if (name.isNotBlank()) add(name)
                        }
                    }
                }
            }
            .orEmpty()
            .joinToString(", ")

        val subjects = o.optJSONArray("subjects")?.let { arr ->
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }.orEmpty()

        val languages = o.optJSONArray("languages")?.let { arr ->
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }.orEmpty()

        // `formats` is a map of MIME type → URL. We pick out the formats we
        // can actually render (HTML / plain text) plus the cover image and
        // an EPUB for the "open externally" fallback.
        val formats = o.optJSONObject("formats")
        fun firstUrl(vararg keys: String): String {
            if (formats == null) return ""
            for (k in keys) {
                val v = formats.optString(k, "")
                if (v.isNotBlank() && !v.endsWith(".zip")) return v
            }
            return ""
        }

        val coverUrl = httpsify(firstUrl("image/jpeg", "image/png"))
        // Prefer UTF-8 text & HTML. Gutendex serves multiple charsets;
        // utf-8 is the one our WebView reads cleanly.
        val htmlUrl = httpsify(firstUrl(
            "text/html; charset=utf-8",
            "text/html"
        ))
        val textUrl = httpsify(firstUrl(
            "text/plain; charset=utf-8",
            "text/plain; charset=us-ascii",
            "text/plain"
        ))
        val epubUrl = httpsify(firstUrl("application/epub+zip"))

        return Book(
            id = o.optInt("id", 0).toString(),
            title = o.optString("title", "Untitled"),
            authors = authors,
            subjects = subjects,
            languages = languages,
            downloadCount = o.optInt("download_count", 0),
            coverUrl = coverUrl,
            textUrl = textUrl,
            htmlUrl = htmlUrl,
            epubUrl = epubUrl
        )
    }
}

/**
 * In-memory cache so the Reader / Detail screens can pick up a book that
 * was just shown in the grid without re-hitting Gutendex. Mirrors
 * WallpaperCache.
 */
object BookCache {
    @Volatile
    private var books: Map<String, Book> = emptyMap()

    fun set(list: List<Book>) {
        books = list.associateBy { it.id }
    }

    fun merge(list: List<Book>) {
        books = books + list.associateBy { it.id }
    }

    fun byId(id: String): Book? = books[id]
}
