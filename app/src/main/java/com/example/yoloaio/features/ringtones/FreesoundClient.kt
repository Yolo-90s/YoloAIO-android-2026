package com.example.yoloaio.features.ringtones

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class FreesoundTone(
    val id: String,
    val name: String,
    val username: String,
    val durationSec: Double,
    val tags: List<String>,
    val previewUrl: String
) {
    val durationFormatted: String
        get() {
            val s = durationSec.toInt()
            return if (s < 60) "${s}s" else "%d:%02d".format(s / 60, s % 60)
        }
}

enum class RingtoneCategory(val tag: String?, val label: String) {
    All(null, "All"),
    Ringtone("ringtone", "Ringtones"),
    Notification("notification", "Notifications"),
    Alarm("alarm", "Alarms"),
    Bell("bell", "Bells"),
    Beep("beep", "Beeps"),
    Chime("chime", "Chimes"),
    SoundEffect("sound-effect", "SFX")
}

object FreesoundClient {
    private const val TAG = "Freesound"
    private const val BASE = "https://freesound.org/apiv2"

    suspend fun search(
        query: String,
        category: RingtoneCategory,
        apiKey: String,
        pageSize: Int = 30
    ): Result<List<FreesoundTone>> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "Missing Freesound API key" }

            // Compose the filter: short clips + optional category tag.
            val filterParts = mutableListOf("duration:[0 TO 30]")
            category.tag?.let { filterParts += "tag:$it" }
            val filterValue = filterParts.joinToString(" ")
            val effectiveQuery = query.ifBlank { category.label }

            val params = linkedMapOf(
                "query" to effectiveQuery,
                "filter" to filterValue,
                "fields" to "id,name,username,duration,tags,previews",
                "page_size" to pageSize.toString(),
                "sort" to "rating_desc",
                "token" to apiKey
            )
            val query = params.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            val url = "$BASE/search/text/?$query"
            Log.d(TAG, "GET $url")
            val text = fetchString(url)
            parse(JSONObject(text))
        }.onFailure {
            Log.e(TAG, "search failed", it)
        }
    }

    private fun fetchString(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; YoloAIO) Mobile"
            )
        }
        try {
            val code = conn.responseCode
            Log.d(TAG, "← HTTP $code")
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.take(200) ?: "no body"
                error("HTTP $code · $err")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(root: JSONObject): List<FreesoundTone> {
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val previews = o.optJSONObject("previews")
                val preview = previews?.optString("preview-hq-mp3")?.takeIf { it.isNotBlank() }
                    ?: previews?.optString("preview-lq-mp3")?.takeIf { it.isNotBlank() }
                    ?: continue
                val tagsArr = o.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsArr != null) {
                    for (j in 0 until tagsArr.length()) {
                        val t = tagsArr.optString(j)
                        if (t.isNotBlank()) tags += t
                    }
                }
                add(
                    FreesoundTone(
                        id = o.optInt("id").toString(),
                        name = o.optString("name").ifBlank { "Untitled" },
                        username = o.optString("username"),
                        durationSec = o.optDouble("duration", 0.0),
                        tags = tags,
                        previewUrl = preview
                    )
                )
            }
        }
    }
}
