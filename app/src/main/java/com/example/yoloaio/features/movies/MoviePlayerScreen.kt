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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.yoloaio.ui.components.FeatureScaffold
import kotlinx.coroutines.launch

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

    // Always start from 0 — we no longer pass a `?progress=` param to Vidking.
    // The seek-on-autoplay path is what was getting the HLS decoder stuck on
    // this device; cold-start playback buffers cleanly. Progress is still
    // saved to Firestore in handleMoviePlayerEvent for future use (history,
    // analytics), it's just no longer used to resume.
    val embedUrl = remember(movieId) {
        VidkingPlayer.movieEmbedUrl(
            tmdbId = movieId,
            primaryColorHex = "B85AC1",
            autoPlay = true,
            progressSeconds = null
        )
    }

    val lastSavedAt = remember { MovieLongRef(0L) }
    val webView = remember { MovieRef<WebView>() }
    val context = LocalContext.current

    FeatureScaffold(
        title = title,
        onBack = onBack,
        actions = {
            // Vidking renders its player inside a WebView (we don't have the
            // raw HLS stream), so true app-level Chromecast isn't possible
            // here. The realistic alternative is screen-mirror — this opens
            // system Cast Settings where the user picks a device to mirror
            // the entire screen onto.
            IconButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_CAST_SETTINGS).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    )
                }.onFailure {
                    Toast.makeText(
                        context,
                        "Cast settings not available on this device",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }) {
                Icon(Icons.Rounded.Cast, contentDescription = "Cast to TV")
            }
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { ctx ->
                createPlayerWebView(
                    context = ctx,
                    url = embedUrl,
                    progressContextId = movieId,
                    mediaType = "movie",
                    onPlayerEvent = { json ->
                        scope.launch {
                            handleMoviePlayerEvent(
                                json = json,
                                tmdbId = movieId,
                                repo = progressRepo,
                                lastSavedAt = lastSavedAt
                            )
                        }
                    }
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
