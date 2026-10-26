package com.example.yoloaio.features.movies

import android.content.Intent
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.example.yoloaio.ui.components.FeatureScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long we wait, once the page has loaded, for a real play/timeupdate
 *  event before treating the source as stalled (page up, video never
 *  actually starts — vidlink.pro's per-title gap, not a network error). */
private const val STALL_TIMEOUT_MS = 15_000L

@Composable
fun MoviePlayerScreen(movieId: String, onBack: () -> Unit) {
    val cachedMovie = remember(movieId) { TmdbCache.byIdString(movieId) }
    val title = cachedMovie?.title ?: "Movie"
    val progressRepo = remember { WatchProgressRepository() }
    val scope = rememberCoroutineScope()

    if (movieId.isBlank()) {
        FeatureScaffold(title = "Movie", onBack = onBack) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("Movie not found", style = MaterialTheme.typography.bodyMedium) }
        }
        return
    }

    // Always start from 0 — we no longer pass a start-time param to the
    // embed player. The seek-on-autoplay path is what was getting the HLS
    // decoder stuck on this device; cold-start playback buffers cleanly.
    // Progress is still saved to Firestore in handleMoviePlayerEvent for
    // future use (history, analytics), it's just no longer used to resume.
    val primaryEmbedUrl = remember(movieId) {
        MovieEmbedPlayer.movieEmbedUrl(
            tmdbId = movieId,
            primaryColorHex = "B85AC1",
            autoPlay = true,
            progressSeconds = null
        )
    }
    val fallbackEmbedUrl = remember(movieId) { MovieEmbedPlayer.fallbackMovieEmbedUrl(movieId) }

    val lastSavedAt = remember { MovieLongRef(0L) }
    val webView = remember { MovieRef<WebView>() }
    val context = LocalContext.current
    var loadState by remember(movieId) { mutableStateOf<PlayerLoadState>(PlayerLoadState.Loading) }
    var usingFallback by remember(movieId) { mutableStateOf(false) }
    var hasPlaybackStarted by remember(movieId, usingFallback) { mutableStateOf(false) }
    val embedUrl = if (usingFallback) fallbackEmbedUrl else primaryEmbedUrl

    // The embed page loading (Ready) doesn't mean the video actually
    // started — vidlink.pro can load its page fine for a title it has no
    // working source for. If nothing plays within the timeout, surface the
    // vidsrc.to fallback instead of leaving a silent backdrop image forever.
    LaunchedEffect(loadState, usingFallback) {
        if (loadState == PlayerLoadState.Ready) {
            delay(STALL_TIMEOUT_MS)
            if (!hasPlaybackStarted) {
                loadState = PlayerLoadState.Stalled
            }
        }
    }

    FeatureScaffold(
        title = title,
        onBack = onBack,
        actions = {
            // Cast routes the user to system Cast Settings (the only
            // viable path on Android since the embed player gives us an
            // iframe, not a raw stream URL). PlayerCastButton wraps that
            // intent with a short explanation dialog.
            PlayerCastButton()
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    createPlayerWebView(
                        context = ctx,
                        url = embedUrl,
                        progressContextId = movieId,
                        mediaType = "movie",
                        onPlayerEvent = { json ->
                            val parsed = PlayerEventParser.parse(json)
                            if (parsed != null && (parsed.name == "play" || parsed.name == "timeupdate")) {
                                hasPlaybackStarted = true
                            }
                            scope.launch {
                                handleMoviePlayerEvent(
                                    json = json,
                                    tmdbId = movieId,
                                    repo = progressRepo,
                                    lastSavedAt = lastSavedAt
                                )
                            }
                        },
                        onLoadStateChanged = { loadState = it }
                    ).also { webView.value = it }
                },
                // Detach from parent BEFORE destroy(), wrapped in runCatching, to
                // avoid the "destroy called while still attached to window" warning
                // that can leave the underlying SurfaceView in a half-released
                // state on some devices (notably Vivo / Snapdragon Codec2).
                onRelease = { wv ->
                    runCatching {
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.onPause()
                        wv.removeJavascriptInterface("AndroidBridge")
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.destroy()
                    }
                    if (webView.value === wv) webView.value = null
                }
            )

            // Previously a load failure just left a black WebView with zero
            // feedback. Now: a spinner while loading, and a clear error +
            // Retry when the main frame fails (e.g. the embed host being
            // unreachable on this network).
            when (val state = loadState) {
                PlayerLoadState.Loading -> PlayerOverlay {
                    CircularProgressIndicator(color = Color.White)
                }
                is PlayerLoadState.Error -> PlayerOverlay {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Couldn't load the player",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.message,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            hasPlaybackStarted = false
                            loadState = PlayerLoadState.Loading
                            webView.value?.loadUrl(embedUrl)
                        }) { Text("Retry") }
                    }
                }
                PlayerLoadState.Stalled -> PlayerOverlay {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "This title isn't playing on this source",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (usingFallback) "Still not available right now — try again later."
                            else "Let's try another source.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            // Same action either way — switch to (or reload) the
                            // fallback. The label below just distinguishes a first
                            // switch from a retry of the fallback itself.
                            usingFallback = true
                            hasPlaybackStarted = false
                            loadState = PlayerLoadState.Loading
                            webView.value?.loadUrl(fallbackEmbedUrl)
                        }) { Text(if (usingFallback) "Retry" else "Try another source") }
                    }
                }
                PlayerLoadState.Ready -> Unit
            }
        }
    }
}

private suspend fun handleMoviePlayerEvent(
    json: String,
    tmdbId: String,
    repo: WatchProgressRepository,
    lastSavedAt: MovieLongRef
) {
    val event = PlayerEventParser.parse(json) ?: return
    if (!event.isProgressEvent) return
    if (event.currentTime <= 0.0) return

    val now = System.currentTimeMillis()
    val shouldSave = when (event.name) {
        "pause", "ended", "seeked" -> true
        "play" -> now - lastSavedAt.value > 5_000L
        "timeupdate" -> now - lastSavedAt.value > 10_000L
        else -> false
    }
    if (!shouldSave) return

    lastSavedAt.value = now
    repo.savePosition(
        tmdbId = tmdbId,
        mediaType = "movie",
        currentTime = event.currentTime,
        duration = event.duration
    )
}

private class MovieLongRef(var value: Long)
private class MovieRef<T> { var value: T? = null }
