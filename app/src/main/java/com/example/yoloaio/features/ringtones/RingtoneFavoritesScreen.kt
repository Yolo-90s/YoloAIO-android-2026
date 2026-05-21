package com.example.yoloaio.features.ringtones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yoloaio.ui.components.FeatureScaffold
import kotlinx.coroutines.launch

@Composable
fun RingtoneFavoritesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { RingtonePlayer(context) }
    val favRepo = remember { RingtoneFavoritesRepository() }
    val scope = rememberCoroutineScope()
    DisposableEffect(player) { onDispose { player.release() } }

    val favorites by favRepo.observeFavorites().collectAsState(initial = emptyList())
    val favoriteIds = remember(favorites) { favorites.map { it.toneId }.toSet() }
    var activeSheetTone by remember { mutableStateOf<Tone?>(null) }

    FeatureScaffold(title = "Saved tones", onBack = onBack) { padding ->
        if (favorites.isEmpty()) {
            EmptyFavorites(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(favorites, key = { it.id }) { fav ->
                    val tone = fav.toTone()
                    val isCurrent = player.playingId == tone.id
                    ToneRow(
                        tone = tone,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && player.isPlaying,
                        isLoading = isCurrent && player.isLoading,
                        onToggle = { player.toggle(tone.id, tone.streamUrl) },
                        onMoreClick = { activeSheetTone = tone }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }

        activeSheetTone?.let { tone ->
            val isFav = tone.id in favoriteIds
            ToneActionsSheet(
                tone = tone,
                isFavorite = isFav,
                onDismiss = { activeSheetTone = null },
                onToggleFavorite = {
                    scope.launch {
                        if (isFav) favRepo.remove(tone.id) else favRepo.add(tone)
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyFavorites(modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
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
            "No saved tones yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap ⋮ on any tone and choose \"Add to favorites\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
