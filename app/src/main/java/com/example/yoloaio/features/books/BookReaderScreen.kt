package com.example.yoloaio.features.books

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(
    bookId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { BookFavoritesRepository() }
    val book = remember(bookId) { BookCache.byId(bookId) }
    var isFavorited by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) {
        isFavorited = repo.isFavorited(bookId)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        book?.title ?: "Reader",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBackIos,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (book != null) {
                        IconButton(onClick = {
                            scope.launch {
                                if (isFavorited) {
                                    repo.removeFavorite(book.id)
                                    isFavorited = false
                                } else {
                                    repo.addFavorite(book)
                                    isFavorited = true
                                }
                            }
                        }) {
                            Icon(
                                if (isFavorited) Icons.Rounded.Bookmark
                                else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Favourite",
                                tint = if (isFavorited) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            val shareUrl =
                                book.readableUrl.ifBlank { "https://www.gutenberg.org/ebooks/${book.id}" }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "${book.title} — ${book.displayAuthor}"
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "${book.title} by ${book.displayAuthor}\n$shareUrl"
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "Share book"))
                        }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                book == null -> NotInCacheState(onBack = onBack)
                !book.hasReadableText -> NoReadableState(book = book, context = context)
                else -> BookContentView(book = book)
            }
        }
    }
}

private sealed interface BookFetchState {
    data object Loading : BookFetchState
    data class Ready(val body: String, val baseUrl: String, val isHtml: Boolean) : BookFetchState
    data class Failed(val message: String) : BookFetchState
}

@Composable
private fun BookContentView(book: Book) {
    var state by remember(book.id) { mutableStateOf<BookFetchState>(BookFetchState.Loading) }

    LaunchedEffect(book.id) {
        val isHtml = book.htmlUrl.isNotBlank()
        val url = if (isHtml) book.htmlUrl else book.textUrl
        val baseUrl = url.substringBeforeLast('/') + "/"
        GutendexClient.fetchBookBody(url)
            .onSuccess { state = BookFetchState.Ready(it, baseUrl, isHtml) }
            .onFailure {
                state = BookFetchState.Failed(it.message ?: "Couldn't load book")
            }
    }

    when (val s = state) {
        BookFetchState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is BookFetchState.Failed -> FetchFailedState(
            message = s.message,
            onRetry = { state = BookFetchState.Loading }
        )

        is BookFetchState.Ready -> PaginatedBookView(
            title = book.title,
            body = if (s.isHtml) extractBodyContent(s.body) else s.body,
            isHtml = s.isHtml,
            baseUrl = s.baseUrl
        )
    }
}

/**
 * Paginated reader. Flows the book body into viewport-sized CSS columns
 * inside a WebView, then synthesises page-turn UX in Compose:
 *
 *   - swipe left → next page, swipe right → previous
 *   - tap right third → next, tap left third → previous
 *   - during drag, the WebView gets a 3D rotationY tilt to suggest a
 *     folding page; on commit, an animation finishes the flip and the
 *     underlying WebView scrolls to the new column
 *
 * Page count is queried from the WebView after layout via
 * `evaluateJavascript` — `Math.round(scrollWidth / innerWidth)`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PaginatedBookView(
    title: String,
    body: String,
    isHtml: Boolean,
    baseUrl: String
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }
    // CSS-pixel viewport width reported by `window.innerWidth`. Drives the
    // `window.scrollTo` calls — using Android view px would scroll the
    // wrong amount on devices with high DPR.
    var pageWidthCssPx by remember { mutableFloatStateOf(0f) }

    // Compose-layer drag offset. Animatable so we can spring the page
    // back if the drag doesn't pass the commit threshold.
    val dragX = remember { Animatable(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var ready by remember { mutableStateOf(false) }

    // Navigate by setting #book's CSS transform — much more reliable than
    // `window.scrollTo` when the WebView has overflow:hidden on html/body.
    fun applyPageInWebView(page: Int) {
        val offset = page * pageWidthCssPx.toInt()
        webView?.evaluateJavascript(
            "document.getElementById('book').style.transform='translateX(-${offset}px)';",
            null
        )
    }

    fun goToPage(target: Int) {
        val clamped = target.coerceIn(0, max(0, pageCount - 1))
        if (clamped == currentPage) return
        val direction = if (clamped > currentPage) 1 else -1
        scope.launch {
            dragX.animateTo(
                targetValue = -direction * containerWidthPx,
                animationSpec = tween(durationMillis = 220)
            )
            currentPage = clamped
            applyPageInWebView(clamped)
            dragX.snapTo(direction * containerWidthPx)
            dragX.animateTo(0f, tween(durationMillis = 220))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Camera distance keeps the rotation looking like a
                    // gentle fold instead of a sharp pivot. Without this,
                    // big rotationY values clip badly.
                    cameraDistance = 16f * density.density
                    translationX = dragX.value
                    // ±18° max tilt — past that the WebView becomes hard
                    // to read mid-drag.
                    rotationY = if (containerWidthPx > 0f) {
                        (dragX.value / containerWidthPx) * -18f
                    } else 0f
                }
                // Tap zones first (gestures detected in order). Compose
                // routes single taps here before the drag detector fires.
                .pointerInput(pageCount, containerWidthPx) {
                    detectTapGestures(
                        onTap = { offset: Offset ->
                            if (!ready) return@detectTapGestures
                            val w = size.width.toFloat()
                            when {
                                offset.x < w * 0.33f -> goToPage(currentPage - 1)
                                offset.x > w * 0.67f -> goToPage(currentPage + 1)
                            }
                        }
                    )
                }
                .pointerInput(pageCount, containerWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            if (!ready) return@detectHorizontalDragGestures
                            val threshold = containerWidthPx * 0.22f
                            val v = dragX.value
                            when {
                                v < -threshold && currentPage < pageCount - 1 -> {
                                    scope.launch {
                                        dragX.animateTo(
                                            -containerWidthPx,
                                            tween(durationMillis = 180)
                                        )
                                        currentPage++
                                        applyPageInWebView(currentPage)
                                        dragX.snapTo(containerWidthPx)
                                        dragX.animateTo(0f, tween(durationMillis = 220))
                                    }
                                }
                                v > threshold && currentPage > 0 -> {
                                    scope.launch {
                                        dragX.animateTo(
                                            containerWidthPx,
                                            tween(durationMillis = 180)
                                        )
                                        currentPage--
                                        applyPageInWebView(currentPage)
                                        dragX.snapTo(-containerWidthPx)
                                        dragX.animateTo(0f, tween(durationMillis = 220))
                                    }
                                }
                                else -> scope.launch {
                                    dragX.animateTo(0f, tween(durationMillis = 180))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragX.animateTo(0f) }
                        },
                        onHorizontalDrag = { _, dx ->
                            scope.launch { dragX.snapTo(dragX.value + dx) }
                        }
                    )
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            // JS is needed for the page-count query +
                            // scrollTo navigation.
                            javaScriptEnabled = true
                            loadsImagesAutomatically = true
                            useWideViewPort = true
                            loadWithOverviewMode = false
                            textZoom = 100
                        }
                        // We control scrolling ourselves — the user
                        // shouldn't be able to drag the WebView's content.
                        isHorizontalScrollBarEnabled = false
                        isVerticalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        setOnTouchListener { _, _ -> false }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                finishedUrl: String?
                            ) {
                                if (view == null) return
                                // Measure page count + viewport width.
                                // Reads the *book element's* scrollWidth
                                // (not documentElement.scrollWidth, which
                                // ignores the multi-column overflow when
                                // html has overflow:hidden).
                                // We retry once after 400ms because some
                                // WebViews paint before column layout has
                                // fully settled — the first measurement
                                // can report 1 page on a 50-page book.
                                fun measure(retriesLeft: Int) {
                                    view.evaluateJavascript(
                                        """
                                        (function(){
                                          var vp = document.getElementById('viewport');
                                          var book = document.getElementById('book');
                                          if (!vp || !book) return '0,0';
                                          // page width = #viewport's visible width (the area
                                          // inside its inset positioning). Each CSS column ends
                                          // up exactly this wide, so translateX by N*pageWidth
                                          // aligns the next column flush against the left edge.
                                          var w = vp.clientWidth;
                                          var sw = book.scrollWidth;
                                          var count = Math.max(1, Math.ceil(sw / w));
                                          return w + ',' + count;
                                        })();
                                        """.trimIndent()
                                    ) { result ->
                                        val parsed = result?.trim('"')?.split(',')
                                        val w = parsed?.getOrNull(0)?.toFloatOrNull() ?: 0f
                                        val n = parsed?.getOrNull(1)?.toIntOrNull() ?: 1
                                        if (n <= 1 && retriesLeft > 0) {
                                            view.postDelayed({ measure(retriesLeft - 1) }, 400)
                                            return@evaluateJavascript
                                        }
                                        pageWidthCssPx = w
                                        pageCount = n
                                        currentPage = 0
                                        ready = w > 0f
                                    }
                                }
                                view.postDelayed({ measure(retriesLeft = 2) }, 120)
                            }
                        }
                        loadDataWithBaseURL(
                            baseUrl,
                            paginatedHtml(title, body, isHtml),
                            "text/html",
                            "UTF-8",
                            null
                        )
                        webView = this
                    }
                }
            )
        }

        // Page indicator. While layout / pagination is still settling we
        // show a spinner instead of a stale "1 / 1" — important because
        // the first measurement can legitimately report 1 page on a
        // 50-page book if the column flow hasn't finished.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .background(
                    Color.Black.copy(alpha = 0.55f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            if (!ready) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Preparing pages…",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Text(
                    "${currentPage + 1} / $pageCount",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Wrap the book body in a CSS-paginated HTML shell. The `column-width`
 * rule pushes text into columns of viewport width; `column-fill: auto`
 * fills columns one after another (the default `balance` would short
 * the last column). `overflow: hidden` on body hides the native
 * scrollbar — `window.scrollTo` from Kotlin still scrolls the viewport.
 *
 * Plain-text books get the same shell but the body is wrapped in a
 * `<pre>`-style block so paragraph breaks survive without HTML markup.
 */
private fun paginatedHtml(title: String, body: String, isHtml: Boolean): String {
    val content = if (isHtml) body else {
        val escaped = body
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        // <pre> preserves line breaks; word-wrap so long lines fold.
        "<pre style=\"white-space:pre-wrap;word-wrap:break-word;margin:0;font-family:inherit\">$escaped</pre>"
    }
    val safeTitle = title.replace("<", "").replace(">", "")
    // Two-layer structure:
    //   #viewport  — fixed 100vw × 100vh, clips overflow (the visible window)
    //   #book      — column-paginated child, grows wider than viewport;
    //                we slide it with `transform: translateX(...)` to flip pages
    //
    // Why not just `body { column-* }` + `window.scrollTo`? Because
    // `html { overflow: hidden }` (which we need to suppress native
    // scrollbars) silently disables `window.scrollTo` in many Android
    // WebViews. Transforms work regardless of overflow rules.
    return """
<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
<title>$safeTitle</title>
<style>
  html, body {
    margin: 0;
    padding: 0;
    height: 100vh;
    width: 100vw;
    overflow: hidden;
    background: #14101e;
    color: #e6e0f3;
    font-family: 'Georgia', 'Times New Roman', serif;
    -webkit-text-size-adjust: 100%;
  }
  /* The viewport is the visible reading area — its inset positioning
     (top/bottom/left/right) provides ALL the page padding. #book inside
     it has no padding of its own, so each CSS column ends up exactly
     #viewport.clientWidth wide. That width is what we use both as the
     measurement basis (`book.scrollWidth / vp.clientWidth`) and as the
     translateX step for page navigation — they have to match for
     content to land flush at the page edges instead of getting
     cropped on every flip. */
  #viewport {
    position: absolute;
    top: 32px;
    bottom: 64px;   /* leaves room for the overlay page indicator */
    left: 28px;
    right: 28px;
    overflow: hidden;
  }
  #book {
    width: 100%;
    height: 100%;
    /* `column-width: 9999px` is the standard trick to force exactly
       one column at the container's content width. The browser tries
       to honor 9999px, can't (container is much smaller), and falls
       back to a single column of width = container width. Critically
       this guarantees `actual column width == #book.clientWidth`. */
    -webkit-column-width: 9999px;
    column-width: 9999px;
    -webkit-column-gap: 0;
    column-gap: 0;
    -webkit-column-fill: auto;
    column-fill: auto;
    font-size: 17px;
    line-height: 1.65;
    transform: translateX(0);
    will-change: transform;
    /* Anything that gets pushed past the column's right edge gets
       hidden by #viewport's overflow:hidden — but we want it to flow
       into the NEXT column instead. column-fill:auto handles that. */
  }
  img { max-width: 100%; height: auto; -webkit-column-break-inside: avoid; break-inside: avoid; }
  p { margin: 0 0 0.85em 0; }
  h1, h2, h3 { -webkit-column-break-after: avoid; break-after: avoid; line-height: 1.25; }
  a { color: #c5a7ff; }
  /* Override Gutenberg's stock HTML — many of its pages constrain
     content to a fixed width that fights our column flow. */
  div, section, article, table {
    max-width: 100% !important;
    width: auto !important;
  }
</style>
</head><body>
<div id="viewport"><div id="book">$content</div></div>
</body></html>
    """.trimIndent()
}

/**
 * Pull the body of a full HTML page so it can be inlined into our shell
 * without duplicating <head>. If anything looks off (no <body> tag at
 * all), fall back to returning the whole document — the WebView will
 * still render it, just with its own styles.
 */
private fun extractBodyContent(html: String): String {
    val lower = html.lowercase()
    val bodyOpen = lower.indexOf("<body")
    if (bodyOpen < 0) return html
    val afterTag = html.indexOf('>', bodyOpen)
    if (afterTag < 0) return html
    val bodyClose = lower.lastIndexOf("</body>")
    return if (bodyClose > afterTag) html.substring(afterTag + 1, bodyClose)
    else html.substring(afterTag + 1)
}

@Composable
private fun NotInCacheState(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text("Open this book from the list", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "We lost the book metadata between screens. Go back and tap it again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoReadableState(book: Book, context: android.content.Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text("No readable version", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "This title doesn't ship with an HTML or plain-text version on " +
                "Gutenberg. You can still open it externally — most ebook " +
                "readers accept EPUB.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (book.epubUrl.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            AssistChip(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(book.epubUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                },
                label = { Text("Open EPUB externally") },
                leadingIcon = {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                }
            )
        }
    }
}

@Composable
private fun FetchFailedState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text("Couldn't load book", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
