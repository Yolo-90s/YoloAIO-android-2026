package com.example.yoloaio.features.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
fun MovieDetailScreen(
    movieId: String,
    onBack: () -> Unit,
    onPlay: (String) -> Unit
) {
    val tmdbId = remember(movieId) { movieId.toLongOrNull() }
    val config = LocalAppConfig.current

    var movie by remember(movieId) { mutableStateOf(TmdbCache.byIdString(movieId)) }
    var loading by remember(movieId) { mutableStateOf(movie?.runtimeMinutes == 0) }
    var error by remember(movieId) { mutableStateOf<String?>(null) }
    var reloadKey by remember(movieId) { mutableStateOf(0) }

    LaunchedEffect(tmdbId, config.tmdbAuth, reloadKey) {
        val id = tmdbId ?: run { error = "Invalid movie id"; loading = false; return@LaunchedEffect }
        if (config.tmdbAuth.isBlank()) { loading = false; return@LaunchedEffect }
        // Always re-fetch to get runtime + genres (list response doesn't include them).
        loading = true
        TmdbClient.details(MediaType.Movie, id, config.tmdbAuth)
            .onSuccess {
                TmdbCache.put(it)
                movie = it
                loading = false
            }
            .onFailure {
                if (movie == null) error = it.message ?: "Couldn't load movie"
                loading = false
            }
    }

    val title = movie?.title ?: "Movie"

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
            loading && movie == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            movie == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        error ?: "Movie not found",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                DetailContent(
                    movie = movie!!,
                    onPlay = onPlay,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    movie: TmdbTitle,
    onPlay: (String) -> Unit,
    modifier: Modifier
) {
    val gradient = remember(movie.id) {
        listOf(Color(0xFF1A237E), Color(0xFF311B92))
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(gradient))
        ) {
            val backdrop = TmdbClient.backdropUrl(movie.backdropPath)
                ?: TmdbClient.posterUrl(movie.posterPath, "w780")
            if (backdrop != null) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = movie.title,
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
                    movie.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (movie.rating > 0) {
                        MetaItem(icon = Icons.Rounded.Star, label = "%.1f".format(movie.rating))
                    }
                    if (movie.year > 0) {
                        MetaItem(label = movie.year.toString())
                    }
                    if (movie.runtimeMinutes > 0) {
                        MetaItem(icon = Icons.Rounded.Schedule, label = "${movie.runtimeMinutes} min")
                    }
                    if (movie.genres.isNotEmpty()) {
                        MetaItem(label = movie.genres.first())
                    }
                }
                if (movie.overview.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(movie.overview, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Single action — Vidking's `?progress=` seek path is unreliable on
        // this device's HLS stack, so every tap starts the movie cleanly
        // from 0. Position is still recorded server-side for future use.
        Button(
            onClick = { onPlay(movie.idAsString) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Watch now", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
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

