package com.example.yoloaio.features.wallpaper

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.components.GlassCard
import kotlinx.coroutines.launch

@Composable
fun WallpaperFavoritesScreen(
    onBack: () -> Unit,
    onWallpaperClick: (String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { WallpaperFavoritesRepository() }
    val scope = rememberCoroutineScope()

    val favorites by repo.observeFavorites().collectAsState(initial = emptyList())
    val rotation by repo.observeRotationSettings().collectAsState(initial = RotationSettings())

    FeatureScaffold(title = "Favorites", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            RotationControl(
                settings = rotation,
                favoriteCount = favorites.size,
                onToggle = { enabled ->
                    val next = rotation.copy(enabled = enabled)
                    scope.launch {
                        repo.setRotationSettings(next)
                        WallpaperRotationManager.apply(context, next)
                    }
                },
                onIntervalChange = { intervalMinutes ->
                    val next = rotation.copy(intervalMinutes = intervalMinutes)
                    scope.launch {
                        repo.setRotationSettings(next)
                        if (next.enabled) {
                            WallpaperRotationManager.apply(context, next)
                        }
                    }
                }
            )

            if (favorites.isEmpty()) {
                EmptyFavorites()
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    items(favorites, key = { it.id }) { fav ->
                        // Stash in cache so detail screen can find it.
                        FavoriteCard(
                            fav = fav,
                            onClick = {
                                WallpaperCache.set(
                                    listOf(fav.toUnsplashPhoto())
                                        .plus(favorites.filter { it.id != fav.id }
                                            .map { it.toUnsplashPhoto() })
                                )
                                onWallpaperClick(fav.photoId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RotationControl(
    settings: RotationSettings,
    favoriteCount: Int,
    onToggle: (Boolean) -> Unit,
    onIntervalChange: (Long) -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentPadding = PaddingValues(16.dp),
        strong = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Random rotation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            favoriteCount == 0 -> "Save some favorites first"
                            settings.enabled -> "A favorite is picked every ${formatInterval(settings.intervalMinutes)}"
                            else -> "$favoriteCount favorite${if (favoriteCount == 1) "" else "s"} ready"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.enabled,
                    enabled = favoriteCount > 0,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF34C759)
                    )
                )
            }

            Text(
                "Interval",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(RotationSettings.INTERVAL_OPTIONS) { mins ->
                    FilterChip(
                        selected = settings.intervalMinutes == mins,
                        onClick = { onIntervalChange(mins) },
                        label = { Text(formatInterval(mins)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}

private fun formatInterval(minutes: Long): String = when {
    minutes < 60 -> "${minutes}m"
    minutes < 1440 && minutes % 60 == 0L -> "${minutes / 60}h"
    minutes == 1440L -> "24h"
    else -> "${minutes}m"
}

@Composable
private fun FavoriteCard(fav: WallpaperFavorite, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = fav.smallUrl,
            contentDescription = fav.description.ifBlank { "Wallpaper" },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (fav.height > 0 && fav.width > 0)
                        Modifier.aspectRatioCompat(fav.aspectRatio)
                    else Modifier.aspectRatioCompat(0.75f)
                )
        )
    }
}

private fun Modifier.aspectRatioCompat(ratio: Float): Modifier =
    this.aspectRatio(ratio.coerceAtLeast(0.4f))

@Composable
private fun EmptyFavorites() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No favorites yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap the bookmark icon on any wallpaper to save it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
