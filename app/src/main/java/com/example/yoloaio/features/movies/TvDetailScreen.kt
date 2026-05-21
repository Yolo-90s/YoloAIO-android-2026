package com.example.yoloaio.features.movies

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.components.GlassCard

@Composable
fun TvDetailScreen(
    tvId: String,
    onBack: () -> Unit,
    onPlayEpisode: (id: String, season: Int, episode: Int) -> Unit
) {
    val tmdbId = remember(tvId) { tvId.toLongOrNull() }
    val config = LocalAppConfig.current

    var show by remember(tvId) { mutableStateOf(TmdbCache.byIdString(tvId)) }
    var loadingShow by remember(tvId) {
        mutableStateOf(show?.seasons.isNullOrEmpty())
    }
    var error by remember(tvId) { mutableStateOf<String?>(null) }
    var reloadKey by remember(tvId) { mutableStateOf(0) }

    LaunchedEffect(tmdbId, config.tmdbAuth, reloadKey) {
        val id = tmdbId ?: run {
            error = "Invalid TV id"; loadingShow = false; return@LaunchedEffect
        }
        if (config.tmdbAuth.isBlank()) {
            loadingShow = false
            return@LaunchedEffect
        }
        loadingShow = true
        TmdbClient.details(MediaType.Tv, id, config.tmdbAuth)
            .onSuccess {
                TmdbCache.put(it)
                show = it
                loadingShow = false
            }
            .onFailure {
                if (show == null) error = it.message ?: "Couldn't load show"
                loadingShow = false
            }
    }

    val title = show?.title ?: "Show"

    FeatureScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                error = null
                reloadKey++
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }
    ) { padding ->
        when {
            loadingShow && show == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            show == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(error ?: "Show not found", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                ShowContent(
                    show = show!!,
                    apiKey = config.tmdbAuth,
                    onPlayEpisode = onPlayEpisode,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ShowContent(
    show: TmdbTitle,
    apiKey: String,
    onPlayEpisode: (id: String, season: Int, episode: Int) -> Unit,
    modifier: Modifier
) {
    var selectedSeason by remember(show.id) {
        mutableStateOf(show.seasons.firstOrNull()?.seasonNumber ?: 1)
    }
    var episodes by remember { mutableStateOf<List<TmdbEpisode>>(emptyList()) }
    var loadingEpisodes by remember { mutableStateOf(true) }
    var episodeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(show.id, selectedSeason, apiKey) {
        if (apiKey.isBlank()) {
            loadingEpisodes = false
            return@LaunchedEffect
        }
        loadingEpisodes = true
        episodeError = null
        TmdbClient.seasonEpisodes(show.id, selectedSeason, apiKey)
            .onSuccess { episodes = it }
            .onFailure {
                episodes = emptyList()
                episodeError = it.message
            }
        loadingEpisodes = false
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Backdrop(show = show)
        }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (show.rating > 0) {
                            MetaItem(icon = Icons.Rounded.Star, label = "%.1f".format(show.rating))
                        }
                        if (show.year > 0) {
                            MetaItem(label = show.year.toString())
                        }
                        if (show.runtimeMinutes > 0) {
                            MetaItem(icon = Icons.Rounded.Schedule, label = "${show.runtimeMinutes} min/ep")
                        }
                        if (show.genres.isNotEmpty()) {
                            MetaItem(label = show.genres.first())
                        }
                    }
                    if (show.overview.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(show.overview, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            SeasonPicker(
                seasons = show.seasons,
                selectedSeason = selectedSeason,
                onPick = { selectedSeason = it }
            )
        }
        when {
            loadingEpisodes -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            episodes.isEmpty() -> item {
                Text(
                    episodeError ?: "No episodes for this season",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            else -> items(episodes, key = { "${it.seasonNumber}-${it.episodeNumber}" }) { ep ->
                EpisodeRow(
                    episode = ep,
                    onClick = { onPlayEpisode(show.idAsString, ep.seasonNumber, ep.episodeNumber) }
                )
            }
        }
    }
}

@Composable
private fun Backdrop(show: TmdbTitle) {
    val gradient = listOf(Color(0xFF1A237E), Color(0xFF311B92))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
    ) {
        val backdrop = TmdbClient.backdropUrl(show.backdropPath)
            ?: TmdbClient.posterUrl(show.posterPath, "w780")
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = show.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                show.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SeasonPicker(
    seasons: List<TmdbSeason>,
    selectedSeason: Int,
    onPick: (Int) -> Unit
) {
    if (seasons.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val current = seasons.firstOrNull { it.seasonNumber == selectedSeason } ?: seasons.first()

    Box {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        current.name.ifBlank { "Season ${current.seasonNumber}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${current.episodeCount} episode${if (current.episodeCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick season")
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            seasons.forEach { season ->
                DropdownMenuItem(
                    text = {
                        Text(
                            season.name.ifBlank { "Season ${season.seasonNumber}" }
                        )
                    },
                    onClick = {
                        onPick(season.seasonNumber)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: TmdbEpisode, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                TmdbClient.stillUrl(episode.stillPath)?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "E${episode.episodeNumber} · ${episode.name.ifBlank { "Untitled" }}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (episode.airDate.isNotBlank()) {
                    Text(
                        episode.airDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (episode.overview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        episode.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MetaItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
