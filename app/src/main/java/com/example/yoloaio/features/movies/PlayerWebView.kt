package com.example.yoloaio.features.movies

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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
 * Builds a WebView that loads the Vidking embed URL directly (no iframe wrapper).
 * Direct loading sidesteps the SurfaceView/compositing issues that affect inline
 * <video> elements rendered inside iframes inside a Compose AndroidView.
 *
 * Progress tracking is restored by injecting a JS hook on every page load that
 * attaches event listeners (pause/play/ended/seeked/timeupdate) to whatever
 * <video> element Vidking creates, then forwards them to AndroidBridge.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createPlayerWebView(
    context: Context,
    url: String,
    progressContextId: String,
    mediaType: String,
    onPlayerEvent: (String) -> Unit
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

    val hookScript = progressHookScript(progressContextId, mediaType)
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
            super.onPageFinished(view, finishedUrl)
            view?.evaluateJavascript(hookScript, null)
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
