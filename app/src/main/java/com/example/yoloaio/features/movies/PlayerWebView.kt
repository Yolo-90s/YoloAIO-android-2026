package com.example.yoloaio.features.movies

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Surfaced to Compose so the player screen can show a spinner/error
 *  overlay instead of a silent black WebView when a load fails. */
sealed interface PlayerLoadState {
    data object Loading : PlayerLoadState
    data object Ready : PlayerLoadState
    data class Error(val message: String) : PlayerLoadState

    /** The page loaded fine (no navigation error) but no play/timeupdate
     *  event arrived within the stall window — the screen-level code
     *  watches for this and reacts (offer the vidsrc.to fallback). */
    data object Stalled : PlayerLoadState
}

/** Shared full-bleed overlay for the Loading/Error states above — used by
 *  both MoviePlayerScreen and TvPlayerScreen so they stay in sync. */
@Composable
fun PlayerOverlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

class FullScreenWebChromeClient(
    private val activity: Activity
) : WebChromeClient() {
    private var customView: View? = null
    private var callback: CustomViewCallback? = null
    private var savedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onShowCustomView(view: View?, cb: CustomViewCallback?) {
        if (customView != null) {
            cb?.onCustomViewHidden()
            return
        }
        customView = view ?: return
        callback = cb
        savedOrientation = activity.requestedOrientation

        val decor = activity.window.decorView as ViewGroup
        decor.addView(
            customView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val controller = WindowCompat.getInsetsController(activity.window, decor)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onHideCustomView() {
        customView?.let {
            val decor = activity.window.decorView as ViewGroup
            decor.removeView(it)
        }
        customView = null
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = savedOrientation
        callback?.onCustomViewHidden()
        callback = null
    }
}

/**
 * Builds a WebView that loads the embed player's URL directly (no iframe
 * wrapper). Direct loading sidesteps the SurfaceView/compositing issues
 * that affect inline <video> elements rendered inside iframes inside a
 * Compose AndroidView.
 *
 * Progress tracking is restored by injecting a JS hook on every page load
 * that attaches event listeners (pause/play/ended/seeked/timeupdate) to
 * whatever <video> element the embed page creates, then forwards them to
 * AndroidBridge. This is provider-agnostic by design — it doesn't depend
 * on any particular embed's own postMessage format, so swapping providers
 * (see MovieEmbedPlayer) never requires touching this file.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createPlayerWebView(
    context: Context,
    url: String,
    progressContextId: String,
    mediaType: String,
    onPlayerEvent: (String) -> Unit,
    onLoadStateChanged: (PlayerLoadState) -> Unit = {}
): WebView = WebView(context).apply {
    setBackgroundColor(android.graphics.Color.BLACK)
    setLayerType(View.LAYER_TYPE_HARDWARE, null)

    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.allowFileAccess = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

    val activity = context as? Activity
    webChromeClient =
        if (activity != null) FullScreenWebChromeClient(activity) else WebChromeClient()

    addJavascriptInterface(PlayerJsBridge(onPlayerEvent), "AndroidBridge")

    // Free ad-supported embeds like this one commonly bundle popunder/redirect
    // ad networks (e.g. intellipopup.com-style scripts) that try to hijack the
    // whole page via a top-level navigation. Locking navigation to the embed's
    // own host — and anything our own loadUrl() calls, which never go through
    // shouldOverrideUrlLoading at all — stops that without needing to
    // maintain a blocklist of every ad domain that might attempt it.
    val allowedHost = Uri.parse(url).host

    val hookScript = progressHookScript(progressContextId, mediaType)
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            if (request?.isForMainFrame != true) return false
            val requestHost = request.url?.host ?: return false
            val isAllowed = allowedHost != null &&
                (requestHost == allowedHost || requestHost.endsWith(".$allowedHost"))
            return !isAllowed
        }

        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, pageUrl, favicon)
            onLoadStateChanged(PlayerLoadState.Loading)
        }

        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
            super.onPageFinished(view, finishedUrl)
            view?.evaluateJavascript(hookScript, null)
            onLoadStateChanged(PlayerLoadState.Ready)
        }

        // Previously silent: a failed load just left a black WebView with no
        // feedback at all. Only the main-frame request counts as a real
        // failure — sub-resources (ads, trackers, analytics beacons the
        // embed page pulls in) fail constantly without the player itself
        // being broken, and would otherwise trigger false-positive errors.
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame == true) {
                onLoadStateChanged(
                    PlayerLoadState.Error(error?.description?.toString() ?: "Couldn't load the player")
                )
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            val status = errorResponse?.statusCode ?: 0
            if (request?.isForMainFrame == true && status >= 400) {
                onLoadStateChanged(PlayerLoadState.Error("Server error ($status)"))
            }
        }
    }

    loadUrl(url)
}

private fun progressHookScript(contextId: String, mediaType: String): String {
    val escContext = contextId.replace("\\", "\\\\").replace("\"", "\\\"")
    val escType = mediaType.replace("\\", "\\\\").replace("\"", "\\\"")
    return """
        (function() {
            if (window.__yoloPlayerHooks) return;
            window.__yoloPlayerHooks = true;
            var ctxId = "$escContext";
            var mediaType = "$escType";
            var lastUpdateMs = 0;
            function send(video, eventName) {
                try {
                    if (!window.AndroidBridge || !video) return;
                    var payload = JSON.stringify({
                        type: "PLAYER_EVENT",
                        data: {
                            event: eventName,
                            currentTime: video.currentTime || 0,
                            duration: video.duration || 0,
                            progress: video.duration ? (video.currentTime / video.duration * 100) : 0,
                            id: ctxId,
                            mediaType: mediaType
                        }
                    });
                    AndroidBridge.onPlayerEvent(payload);
                } catch (e) {}
            }
            function attach(video) {
                if (video.__yoloAttached) return;
                video.__yoloAttached = true;
                video.addEventListener('pause',    function() { send(video, 'pause'); });
                video.addEventListener('play',     function() { send(video, 'play'); });
                video.addEventListener('ended',    function() { send(video, 'ended'); });
                video.addEventListener('seeked',   function() { send(video, 'seeked'); });
                video.addEventListener('timeupdate', function() {
                    var now = Date.now();
                    if (now - lastUpdateMs > 5000) {
                        lastUpdateMs = now;
                        send(video, 'timeupdate');
                    }
                });
            }
            setInterval(function() {
                document.querySelectorAll('video').forEach(attach);
            }, 2000);
        })();
    """.trimIndent()
}
