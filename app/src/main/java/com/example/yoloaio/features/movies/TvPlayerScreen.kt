package com.example.yoloaio.features.movies

import android.content.Intent
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.example.yoloaio.ui.components.FeatureScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** See MoviePlayerScreen — same stall-timeout rationale. */
private const val STALL_TIMEOUT_MS = 15_000L

@Composable
fun TvPlayerScreen(
    tvId: String,
    season: Int,
    episode: Int,
    onBack: () -> Unit
) {
    val cached = remember(tvId) { TmdbCache.byIdString(tvId) }
    val title = cached?.title?.let { "$it · S${season}E$episode" } ?: "S${season}E$episode"

    val progressRepo = remember { WatchProgressRepository() }
    val scope = rememberCoroutineScope()
    val watchKey = remember(tvId, season, episode) { "${tvId}_s${season}e$episode" }

    if (tvId.isBlank()) {
        FeatureScaffold(title = "TV", onBack = onBack) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("Show not found", style = MaterialTheme.typography.bodyMedium) }
        }
        return
    }

    // Always start from 0 — see MoviePlayerScreen for the why.
    val embedUrl = remember(tvId, season, episode) {
        MovieEmbedPlayer.tvEmbedUrl(
            tmdbId = tvId,
            season = season,
            episode = episode,
            primaryColorHex = "B85AC1",
            autoPlay = true,
            nextEpisode = true,
            episodeSelector = true,
            progressSeconds = null
        )
    }

    val lastSavedAt = remember { TvLongRef(0L) }
    val webView = remember { TvRef<WebView>() }
    val context = LocalContext.current
    var loadState by remember(watchKey) { mutableStateOf<PlayerLoadState>(PlayerLoadState.Loading) }
    var hasPlaybackStarted by remember(watchKey) { mutableStateOf(false) }

    LaunchedEffect(loadState) {
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
            // Cast routes to system Cast Settings for screen mirror.
            // PlayerCastButton wraps the intent with an explanation
            // dialog so users aren't surprised by the redirect.
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
                        progressContextId = watchKey,
                        mediaType = "tv",
                        onPlayerEvent = { json ->
                            val parsed = PlayerEventParser.parse(json)
                            if (parsed != null && (parsed.name == "play" || parsed.name == "timeupdate")) {
                                hasPlaybackStarted = true
                            }
                            scope.launch {
                                handleTvPlayerEvent(
                                    json = json,
                                    watchKey = watchKey,
                                    repo = progressRepo,
                                    lastSavedAt = lastSavedAt
                                )
                            }
                        },
                        onLoadStateChanged = { loadState = it }
                    ).also { webView.value = it }
                },
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
                            "This title isn't available right now",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "It may just be a temporary gap in this source — try again, or come back later.",
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
                PlayerLoadState.Ready -> Unit
            }
        }
    }
}

private suspend fun handleTvPlayerEvent(
    json: String,
    watchKey: String,
    repo: WatchProgressRepository,
    lastSavedAt: TvLongRef
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
        tmdbId = watchKey,
        mediaType = "tv",
        currentTime = event.currentTime,
        duration = event.duration
    )
}

private class TvLongRef(var value: Long)
private class TvRef<T> { var value: T? = null }
