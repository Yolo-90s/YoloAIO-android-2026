package com.example.yoloaio.features.quotes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Renders a quote with its style. Used both for the grid cards and the editor preview.
 * `fontScale` shrinks the configured `fontSize` for the smaller grid cards while the
 * editor preview shows the text at full size.
 */
@Composable
fun QuoteContent(
    quote: Quote,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    contentPadding: Dp = 20.dp
) {
    val style = quote.style
    val horizontalAlignment = when (style.textAlign) {
        TextAlign.Start -> Alignment.Start
        TextAlign.End -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val quoteIconAlignment = when (style.textAlign) {
        TextAlign.End -> Alignment.TopEnd
        else -> Alignment.TopStart
    }

    Box(modifier = modifier) {
        // background layer
        Box(modifier = Modifier.fillMaxSize().drawBackground(style))

        // subtle quote-icon ornament
        Icon(
            Icons.Rounded.FormatQuote,
            contentDescription = null,
            tint = style.textComposeColor.copy(alpha = 0.35f),
            modifier = Modifier
                .align(quoteIconAlignment)
                .padding(contentPadding)
                .size((36 * fontScale).coerceAtLeast(18f).dp)
        )

        // text + author
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.Center
        ) {
            val effectiveSize = style.fontSize * fontScale
            Text(
                text = quote.text,
                color = style.textComposeColor,
                fontSize = effectiveSize.sp,
                // Generous leading — Compose's default (~1.15×) looks cramped
                // for large italic display text; 1.4× reads like a poster.
                lineHeight = (effectiveSize * 1.4f).sp,
                fontWeight = style.textFontWeight,
                fontStyle = style.textFontStyle,
                textAlign = style.textAlign
            )
            if (quote.author.isNotBlank()) {
                Spacer(Modifier.height((16 * fontScale).coerceAtLeast(8f).dp))
                val authorSize = (style.fontSize * 0.55f * fontScale).coerceAtLeast(10f)
                Text(
                    text = "— ${quote.author}",
                    color = style.textComposeColor.copy(alpha = 0.8f),
                    fontSize = authorSize.sp,
                    lineHeight = (authorSize * 1.3f).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = style.textAlign
                )
            }
        }
    }
}

@Composable
private fun Modifier.drawBackground(style: QuoteStyle): Modifier {
    return when (style.bgType) {
        BackgroundType.Gradient -> this.background(Brush.linearGradient(style.backgroundComposeColors))
        BackgroundType.Solid -> this.background(style.backgroundComposeColors.firstOrNull() ?: Color.Black)
        // For Image backgrounds the picture is painted *underneath* this composable
        // by QuoteBackgroundImage(). Painting anything here would cover it up.
        BackgroundType.Image -> this
    }
}

/**
 * Image-backgrounded quotes need an `AsyncImage` painted underneath the text.
 * Call this *before* [QuoteContent] in the same parent Box for image styles.
 */
@Composable
fun QuoteBackgroundImage(quote: Quote, modifier: Modifier = Modifier) {
    if (quote.style.bgType != BackgroundType.Image) return
    val url = quote.style.backgroundImageUrl ?: return
    Box(modifier = modifier) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
    }
}
