package com.example.yoloaio.features.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.data.rememberCurrentUser
import com.example.yoloaio.navigation.Routes
import com.example.yoloaio.ui.theme.YoloShapes

private data class FeatureTile(
    val key: String,
    val title: String,
    val tagline: String,
    val icon: ImageVector,
    val route: String,
    val accent: List<Color>
)

private val allTiles = listOf(
    FeatureTile(
        "movies", "Movies", "Stream anywhere, instantly",
        Icons.Rounded.Movie, Routes.MOVIES,
        listOf(Color(0xFF7C9CFF), Color(0xFF1A237E))
    ),
    FeatureTile(
        "music", "Music", "Your library",
        Icons.Rounded.LibraryMusic, Routes.MUSIC,
        listOf(Color(0xFFFF9F73), Color(0xFFE65100))
    ),
    FeatureTile(
        "chat", "Chat", "Conversations",
        Icons.Rounded.Forum, Routes.CHAT,
        listOf(Color(0xFF5A8DEE), Color(0xFF3F61C7))
    ),
    FeatureTile(
        "weather", "Weather", "Right where you are",
        Icons.Rounded.WbCloudy, Routes.WEATHER,
        listOf(Color(0xFF4FC3F7), Color(0xFF1565C0))
    ),
    FeatureTile(
        "wallpaper", "Wallpaper", "Beautify",
        Icons.Rounded.Wallpaper, Routes.WALLPAPER,
        listOf(Color(0xFF00BFA5), Color(0xFF1B5E20))
    ),
    FeatureTile(
        "quotes", "Quotes", "Daily wisdom",
        Icons.Rounded.FormatQuote, Routes.QUOTES,
        listOf(Color(0xFFFFC36B), Color(0xFFAD1457))
    ),
    FeatureTile(
        "ringtones", "Ringtones", "Tones for every mood",
        Icons.Rounded.MusicNote, Routes.RINGTONES,
        listOf(Color(0xFFE0AAFF), Color(0xFF6A1B9A))
    ),
    FeatureTile(
        "books", "Books", "Free classics · read anywhere",
        Icons.AutoMirrored.Rounded.MenuBook, Routes.BOOKS,
        listOf(Color(0xFFFFB088), Color(0xFF5D4037))
    ),
    FeatureTile(
        "beat_analyser", "Beat Analyser", "Noise meter · reactive visuals",
        Icons.Rounded.Equalizer, Routes.BEAT_ANALYSER,
        listOf(Color(0xFF42E6B4), Color(0xFF311B92))
    ),
    FeatureTile(
        "audio", "Audio Trimmer", "Cut & save",
        Icons.Rounded.ContentCut, Routes.AUDIO_TRIMMER,
        listOf(Color(0xFFFF7AB6), Color(0xFFB85AC1))
    ),
    FeatureTile(
        "wifi_lab", "Wi-Fi Lab", "Concept demo · educational",
        Icons.Rounded.Wifi, Routes.WIFI_LAB,
        listOf(Color(0xFF00E5A8), Color(0xFF004D40))
    ),
    FeatureTile(
        "community", "Community", "Open channel · all members",
        Icons.Rounded.Groups, Routes.COMMUNITY,
        listOf(Color(0xFFFFC36B), Color(0xFFB85AC1))
    ),
    FeatureTile(
        "videos", "PlayGround", "Library + in-app browser",
        Icons.Rounded.SportsEsports, Routes.VIDEOS,
        listOf(Color(0xFF4FC3F7), Color(0xFF1A237E))
    )
)

@Composable
fun HomeScreen(
    onTileClick: (String) -> Unit,
    onUserIconClick: () -> Unit
) {
    val config = LocalAppConfig.current
    val user by rememberCurrentUser()
    val tiles = allTiles.filter { tile ->
        when (tile.key) {
            "music" -> config.showMusicMenu
            "movies" -> config.showMoviesMenu
            "wallpaper" -> config.showWallpapersMenu
            "weather" -> config.showWeatherMenu
            "books" -> config.showBooksMenu
            "beat_analyser" -> config.showBeatAnalyserMenu
            else -> true
        }
    }

    val firstName = (user?.displayName?.takeIf { it.isNotBlank() } ?: "Friend")
        .substringBefore(' ')
    val greeting = greetingFor(java.util.Calendar.getInstance()
        .get(java.util.Calendar.HOUR_OF_DAY))

    // Bento layout — header spans both columns, first tile is a hero spanning
    // both columns, the rest fall into a normal 2-col grid. We use a transparent
    // Scaffold purely for system-bar inset handling.
    Scaffold(containerColor = Color.Transparent) { systemInsets ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(systemInsets),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 32.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GreetingHeader(
                    greeting = greeting,
                    name = firstName,
                    onAccountClick = onUserIconClick
                )
            }

            if (tiles.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HeroTile(
                        tile = tiles.first(),
                        onClick = { onTileClick(tiles.first().route) }
                    )
                }
            }

            items(items = tiles.drop(1), key = { it.key }) { tile ->
                StandardTile(tile = tile, onClick = { onTileClick(tile.route) })
            }
        }
    }
}

@Composable
private fun GreetingHeader(
    greeting: String,
    name: String,
    onAccountClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                greeting,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                name,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(
            onClick = onAccountClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
        ) {
            Icon(
                Icons.Rounded.AccountCircle,
                contentDescription = "Account & settings",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HeroTile(tile: FeatureTile, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = YoloShapes.Hero,
        color = Color.Transparent,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(tile.accent))
        ) {
            // Decorative oversized icon in the upper-right — bleeds off the
            // edge slightly for a magazine-cover feel.
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 16.dp)
                    .size(140.dp)
            )

            // Foreground content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            tile.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Featured",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column {
                    Text(
                        tile.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tile.tagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Rounded.ArrowOutward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardTile(tile: FeatureTile, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = YoloShapes.Card,
        color = Color.Transparent,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(tile.accent))
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp)
                    .size(80.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        tile.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        tile.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tile.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}
