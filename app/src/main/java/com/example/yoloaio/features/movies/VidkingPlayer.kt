package com.example.yoloaio.features.movies

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Builds embed URLs for vidlink.pro — a free, TMDB-id-based movie/TV embed,
 * same category of service as the vidking.net embed this replaced. Switched
 * because vidking.net's DNS delegation broke (its nameservers stopped
 * answering entirely, confirmed independent of any local network), not a
 * code bug on our end.
 *
 * Query params below are vidlink.pro's own documented ones (confirmed
 * against vidlink.pro directly), not carried over from Vidking's — the two
 * services don't share a param vocabulary. `episodeSelector` is kept as a
 * no-op parameter so [TvPlayerScreen] doesn't need to change its call site;
 * vidlink.pro has no equivalent toggle (its player always shows one).
 *
 * Progress tracking does NOT depend on this provider's own postMessage
 * format — see PlayerWebView.kt's injected JS hook, which attaches directly
 * to the page's real <video> element regardless of which embed is loaded.
 *
 * [fallbackMovieEmbedUrl]/[fallbackTvEmbedUrl] point at vidsrc.to, used only
 * when vidlink.pro's page loads fine but never actually starts playback —
 * a per-title gap in its scraped source pool, not a dead provider (confirmed
 * by other titles playing fine on vidlink.pro the same day). No query
 * params are added for the fallback: vidsrc.to doesn't have independently
 * confirmed documentation for autoplay/color params, and guessing at ones
 * it might not support risks breaking the URL outright.
 */
object MovieEmbedPlayer {
    private const val BASE = "https://vidlink.pro"
    private const val FALLBACK_BASE = "https://vidsrc.to/embed"

    fun movieEmbedUrl(
        tmdbId: String,
        primaryColorHex: String = "B85AC1",
        autoPlay: Boolean = true,
        progressSeconds: Int? = null
    ): String {
        val params = buildList {
            add("primaryColor=$primaryColorHex")
            add("autoplay=$autoPlay")
            add("title=false")
            if (progressSeconds != null && progressSeconds > 0) add("startAt=$progressSeconds")
        }
        return "$BASE/movie/$tmdbId?${params.joinToString("&")}"
    }

    fun tvEmbedUrl(
        tmdbId: String,
        season: Int,
        episode: Int,
        primaryColorHex: String = "B85AC1",
        autoPlay: Boolean = true,
        nextEpisode: Boolean = true,
        @Suppress("UNUSED_PARAMETER") episodeSelector: Boolean = true,
        progressSeconds: Int? = null
    ): String {
        val params = buildList {
            add("primaryColor=$primaryColorHex")
            add("autoplay=$autoPlay")
            add("title=false")
            if (nextEpisode) add("nextbutton=true")
            if (progressSeconds != null && progressSeconds > 0) add("startAt=$progressSeconds")
        }
        return "$BASE/tv/$tmdbId/$season/$episode?${params.joinToString("&")}"
    }

    fun fallbackMovieEmbedUrl(tmdbId: String): String = "$FALLBACK_BASE/movie/$tmdbId"

    fun fallbackTvEmbedUrl(tmdbId: String, season: Int, episode: Int): String =
        "$FALLBACK_BASE/tv/$tmdbId/$season/$episode"

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
