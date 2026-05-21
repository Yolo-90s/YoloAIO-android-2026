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
        VidkingPlayer.tvEmbedUrl(
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

    FeatureScaffold(
        title = title,
        onBack = onBack,
        actions = {
            // See MoviePlayerScreen — Vidking's WebView player can't be
            // app-cast to Chromecast directly. This routes to screen-mirror
            // as the realistic fallback.
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
                    progressContextId = watchKey,
                    mediaType = "tv",
                    onPlayerEvent = { json ->
                        scope.launch {
                            handleTvPlayerEvent(
                                json = json,
                                watchKey = watchKey,
                                repo = progressRepo,
                                lastSavedAt = lastSavedAt
                            )
                        }
                    }
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
