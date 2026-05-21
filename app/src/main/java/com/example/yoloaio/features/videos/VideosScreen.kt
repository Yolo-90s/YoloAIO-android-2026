package com.example.yoloaio.features.videos

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.features.settings.rememberPlaygroundUrl
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.components.GlassCard

private sealed interface VideosState {
    data object Loading : VideosState
    data object NotConfigured : VideosState
    data class Loaded(val videos: List<Video>) : VideosState
    data class Error(val message: String) : VideosState
}

/**
 * Top-of-screen switch between the curated Drive library and a free-form
 * in-screen web browser. The browser renders any URL the user types via
 * an embedded WebView, with normal forward/back navigation inside the page.
 */
private enum class VideosMode { Library, Browser }

@Composable
fun VideosScreen(
    onBack: () -> Unit,
    onOpenVideo: (videoId: String) -> Unit
) {
    val config = LocalAppConfig.current
    var state by remember { mutableStateOf<VideosState>(VideosState.Loading) }
    var query by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(VideosMode.Library) }

    LaunchedEffect(config.videosApiBaseUrl, reloadKey) {
        if (config.videosApiBaseUrl.isBlank()) {
            state = VideosState.NotConfigured
            return@LaunchedEffect
        }
        state = VideosState.Loading
        VideosClient.listVideos(config.videosApiBaseUrl)
            .onSuccess { state = VideosState.Loaded(it) }
            .onFailure { state = VideosState.Error(it.message ?: "Failed to load videos") }
    }

    val filtered by remember(state, query) {
        derivedStateOf {
            val list = (state as? VideosState.Loaded)?.videos ?: emptyList()
            val q = query.trim().lowercase()
            if (q.isBlank()) list else list.filter { it.name.lowercase().contains(q) }
        }
    }

    FeatureScaffold(
        title = "PlayGround",
        onBack = onBack,
        actions = {
            // Refresh only makes sense in the library; the browser has its
            // own reload via re-entering the URL.
            if (mode == VideosMode.Library) {
                IconButton(
                    onClick = { reloadKey++ },
                    enabled = state !is VideosState.Loading
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ModeToggle(mode = mode, onChange = { mode = it })

            when (mode) {
                VideosMode.Browser -> {
                    BrowserPanel(modifier = Modifier.weight(1f).fillMaxWidth())
                    return@Column     // ↓ library content below is library-only
                }
                VideosMode.Library -> Unit
            }

            SearchBar(query = query, onChange = { query = it }, onClear = { query = "" })

            when (val s = state) {
                VideosState.Loading -> Centered {
                    CircularProgressIndicator()
                }
                VideosState.NotConfigured -> NotConfiguredCard()
                is VideosState.Error -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.VideoFile,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Couldn't load videos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is VideosState.Loaded -> {
                    if (filtered.isEmpty()) {
                        Centered {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Rounded.VideoFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (query.isBlank()) "No videos in the shared folder yet."
                                    else "No videos match \"$query\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (query.isBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Drop video files into the Drive folder you shared with " +
                                            "the service account, then tap refresh.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 180.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(filtered, key = { it.id }) { v ->
                                VideoTile(v) { onOpenVideo(v.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                }
            }
        },
        placeholder = { Text("Search videos") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun VideoTile(video: Video, onClick: () -> Unit) {
    val duration = formatVideoDuration(video.durationMs)
    val size = formatFileSize(video.sizeBytes)
    val meta = listOf(duration, size).filter { it.isNotBlank() }.joinToString(" · ")

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.VideoFile,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                // Gradient scrim + play badge bottom-right
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
                Icon(
                    Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
                if (duration.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            duration,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    video.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NotConfiguredCard() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "Videos proxy isn't configured",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Set `videosApiBaseUrl` in Firestore config/app to your deployed " +
                        "Vercel proxy URL. The proxy is the same one the web app uses — " +
                        "see videos-proxy/README.md in the YoloAIO-2026 repo for the " +
                        "Drive service-account setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ───────────────────────── mode toggle ─────────────────────────

@Composable
private fun ModeToggle(mode: VideosMode, onChange: (VideosMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModePill(
            label = "Library",
            icon = Icons.Rounded.VideoLibrary,
            selected = mode == VideosMode.Library,
            onClick = { onChange(VideosMode.Library) },
            modifier = Modifier.weight(1f)
        )
        ModePill(
            label = "Browser",
            icon = Icons.Rounded.Language,
            selected = mode == VideosMode.Browser,
            onClick = { onChange(VideosMode.Browser) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModePill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

// ───────────────────────── browser panel ─────────────────────────

/**
 * In-screen web browser. Auto-loads the PlayGround URL configured in
 * Settings → PlayGround URL. Video-friendly WebView settings:
 *
 *  - Modern Chrome-on-Android user-agent so sites serve mobile pages
 *    instead of falling back to "android-app" defaults.
 *  - Autoplay enabled (`mediaPlaybackRequiresUserGesture = false`).
 *  - Persistent cookies via [CookieManager] so logins survive across launches.
 *  - True HTML5 fullscreen: when a `<video>` requests fullscreen, we attach
 *    the WebChromeClient's custom view to the Activity's decorView, lock to
 *    landscape, and hide the system bars. Exit restores everything.
 *  - `window.open` / target=_blank loads in the same WebView instead of
 *    being lost to the system browser.
 *  - System back rewinds page history before leaving the screen.
 */
@Composable
private fun BrowserPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }

    val playgroundUrlRaw by rememberPlaygroundUrl()
    val playgroundUrl = remember(playgroundUrlRaw) {
        normalizeUrl(playgroundUrlRaw.trim()).takeIf { it.isNotBlank() }
    }

    var loadingProgress by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    // Fullscreen state — populated when the page requests HTML5 fullscreen.
    val fs = remember { FullscreenHolder() }
    var fullscreenActive by remember { mutableStateOf(false) }

    // Back priority: exit fullscreen → step WebView history → fall through
    BackHandler(enabled = fullscreenActive) {
        fs.exit(activity)
        fullscreenActive = false
    }
    BackHandler(enabled = !fullscreenActive && canGoBack) { webViewRef?.goBack() }

    Column(modifier = modifier) {
        if (loadingProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }

        val url = playgroundUrl
        if (url == null) {
            EmptyBrowserHint()
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        // ── settings tuned for video sites ──
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mediaPlaybackRequiresUserGesture = false   // ▶ autoplay
                            // window.open + target=_blank → routed via
                            // onCreateWindow below to keep navigation in-app.
                            setSupportMultipleWindows(true)
                            javaScriptCanOpenWindowsAutomatically = true
                            // Make Cloudflare / video sites send us the
                            // mobile-rendered page rather than the bot fallback.
                            userAgentString = userAgentString
                                ?.replace("; wv)", ")") // drop the "wv" hint
                                ?: CHROME_ANDROID_UA
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean = false   // stay inside the WebView
                            override fun onPageFinished(view: WebView, url: String?) {
                                canGoBack = view.canGoBack()
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress
                            }

                            // HTML5 video fullscreen — the page calls
                            // .requestFullscreen() on a <video>; the framework
                            // hands us back an opaque View that contains the
                            // playing video. We attach it to the decor view.
                            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                fs.enter(activity, view, callback)
                                fullscreenActive = true
                            }

                            override fun onHideCustomView() {
                                fs.exit(activity)
                                fullscreenActive = false
                            }

                            // window.open / target=_blank — feed the new
                            // URL back into the same WebView.
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message
                            ): Boolean {
                                val transport = resultMsg.obj as? WebView.WebViewTransport
                                transport?.webView = view
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        loadUrl(url)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    if (wv.url != url) wv.loadUrl(url)
                },
                onRelease = { wv ->
                    runCatching {
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.onPause()
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.destroy()
                    }
                    if (webViewRef === wv) webViewRef = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { webViewRef?.stopLoading() }
            // If we leave the screen mid-fullscreen, restore activity state.
            if (fullscreenActive) fs.exit(activity)
            webViewRef = null
        }
    }
}

private const val CHROME_ANDROID_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * Owns the WebChromeClient `onShowCustomView` lifecycle: attaches the
 * fullscreen view to the activity's decorView, locks landscape, hides
 * system bars; reverses everything on exit.
 */
private class FullscreenHolder {
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var prevOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var prevSysBarsVisible: Boolean = true

    fun enter(activity: Activity?, view: View, callback: WebChromeClient.CustomViewCallback) {
        if (activity == null || customView != null) {
            // Already showing a custom view, or no activity — refuse politely.
            runCatching { callback.onCustomViewHidden() }
            return
        }
        val decor = activity.window.decorView as? ViewGroup ?: run {
            runCatching { callback.onCustomViewHidden() }
            return
        }
        customView = view
        customCallback = callback

        prevOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val controller = WindowInsetsControllerCompat(activity.window, decor)
        prevSysBarsVisible = controller.isAppearanceLightStatusBars   // not the same flag, but fine for restore
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        decor.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun exit(activity: Activity?) {
        val view = customView ?: return
        val decor = activity?.window?.decorView as? ViewGroup
        decor?.removeView(view)
        runCatching { customCallback?.onCustomViewHidden() }
        customView = null
        customCallback = null

        if (activity != null) {
            activity.requestedOrientation = prevOrientation
            val controller = WindowInsetsControllerCompat(
                activity.window, activity.window.decorView
            )
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun EmptyBrowserHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Language,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No PlayGround URL set yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Open Settings → PlayGround URL and paste any link you want to " +
                "load here. The Browser will render that page automatically " +
                "the next time you visit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Normalise whatever the user typed:
 *  - has scheme  → use as-is
 *  - has a dot   → prefix `https://`
 *  - otherwise   → treat as a Google search
 */
private fun normalizeUrl(raw: String): String {
    if (raw.isBlank()) return ""
    val hasScheme = raw.startsWith("http://", ignoreCase = true) ||
        raw.startsWith("https://", ignoreCase = true) ||
        raw.startsWith("file://", ignoreCase = true) ||
        raw.startsWith("about:")
    if (hasScheme) return raw
    val looksLikeDomain = raw.contains('.') && !raw.contains(' ')
    if (looksLikeDomain) return "https://$raw"
    return "https://www.google.com/search?q=" +
        java.net.URLEncoder.encode(raw, "UTF-8")
}
