package com.example.yoloaio.features.movies

import android.webkit.JavascriptInterface
import org.json.JSONObject

object VidkingPlayer {
    private const val BASE = "https://www.vidking.net/embed"

    fun movieEmbedUrl(
        tmdbId: String,
        primaryColorHex: String = "B85AC1",
        autoPlay: Boolean = true,
        progressSeconds: Int? = null
    ): String {
        val params = buildList {
            add("color=$primaryColorHex")
            if (autoPlay) add("autoPlay=true")
            if (progressSeconds != null && progressSeconds > 0) add("progress=$progressSeconds")
        }
        val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return "$BASE/movie/$tmdbId$query"
    }

    fun tvEmbedUrl(
        tmdbId: String,
        season: Int,
        episode: Int,
        primaryColorHex: String = "B85AC1",
        autoPlay: Boolean = true,
        nextEpisode: Boolean = true,
        episodeSelector: Boolean = true,
        progressSeconds: Int? = null
    ): String {
        val params = buildList {
            add("color=$primaryColorHex")
            if (autoPlay) add("autoPlay=true")
            if (nextEpisode) add("nextEpisode=true")
            if (episodeSelector) add("episodeSelector=true")
            if (progressSeconds != null && progressSeconds > 0) add("progress=$progressSeconds")
        }
        val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return "$BASE/tv/$tmdbId/$season/$episode$query"
    }

    fun wrapperHtml(embedUrl: String): String = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no, viewport-fit=cover">
  <style>
    html, body { margin: 0; padding: 0; background: #000; height: 100%; overflow: hidden; }
    iframe { width: 100%; height: 100%; border: 0; display: block; }
  </style>
</head>
<body>
  <iframe
    src="$embedUrl"
    allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
    allowfullscreen></iframe>
  <script>
    window.addEventListener("message", function(event) {
      try {
        var payload = (typeof event.data === "string") ? event.data : JSON.stringify(event.data);
        if (window.AndroidBridge && AndroidBridge.onPlayerEvent) {
          AndroidBridge.onPlayerEvent(payload);
        }
      } catch (e) { /* ignore */ }
    });
  </script>
</body>
</html>
""".trimIndent()
}

data class PlayerEvent(
    val name: String,
    val currentTime: Double,
    val duration: Double,
    val progress: Double,
    val tmdbId: String,
    val mediaType: String
) {
    val isProgressEvent: Boolean
        get() = name in setOf("timeupdate", "pause", "ended", "seeked", "play")
}

object PlayerEventParser {
    fun parse(json: String): PlayerEvent? = runCatching {
        val obj = JSONObject(json)
        if (obj.optString("type") != "PLAYER_EVENT") return null
        val data = obj.optJSONObject("data") ?: return null
        PlayerEvent(
            name = data.optString("event"),
            currentTime = data.optDouble("currentTime", 0.0),
            duration = data.optDouble("duration", 0.0),
            progress = data.optDouble("progress", 0.0),
            tmdbId = data.optString("id"),
            mediaType = data.optString("mediaType", "movie")
        )
    }.getOrNull()
}

class PlayerJsBridge(private val onRawEvent: (String) -> Unit) {
    @JavascriptInterface
    fun onPlayerEvent(json: String) {
        onRawEvent(json)
    }
}
