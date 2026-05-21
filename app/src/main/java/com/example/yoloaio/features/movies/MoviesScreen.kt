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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.theme.YoloShapes
import kotlinx.coroutines.delay

private enum class Section(val label: String) {
    Popular("Popular"),
    TopRated("Top Rated"),
    Trending("Trending")
}

private sealed interface ContentState {
    data object Loading : ContentState
    data class Ready(val titles: List<TmdbTitle>) : ContentState
    data class Error(val message: String) : ContentState
    data object MissingKey : ContentState
}

@Composable
fun MoviesScreen(
    onBack: () -> Unit,
    onTitleClick: (mediaType: String, id: String) -> Unit
) {
    val config = LocalAppConfig.current

    var media by remember { mutableStateOf(MediaType.Movie) }
    var section by remember { mutableStateOf(Section.Popular) }
    var selectedGenre by remember { mutableStateOf<TmdbGenre?>(null) }
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<ContentState>(ContentState.Loading) }
    var genres by remember { mutableStateOf<List<TmdbGenre>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(query) {
        delay(400)
        debouncedQuery = query.trim()
    }

    LaunchedEffect(media, config.tmdbAuth) {
        if (config.tmdbAuth.isBlank()) return@LaunchedEffect
        TmdbClient.genres(media, config.tmdbAuth)
            .onSuccess { genres = it }
            .onFailure { genres = emptyList() }
    }

    LaunchedEffect(media, section, selectedGenre, debouncedQuery, config.tmdbAuth, reloadKey) {
        if (config.tmdbAuth.isBlank()) {
            state = ContentState.MissingKey
            return@LaunchedEffect
        }
        state = ContentState.Loading
        val key = config.tmdbAuth
        val result = when {
            debouncedQuery.isNotBlank() -> TmdbClient.search(media, debouncedQuery, key)
            selectedGenre != null -> TmdbClient.discoverByGenre(media, selectedGenre!!.id, key)
            section == Section.Popular -> TmdbClient.popular(media, key)
            section == Section.TopRated -> TmdbClient.topRated(media, key)
            else -> TmdbClient.trending(media, key)
        }
        result
            .onSuccess { titles ->
                TmdbCache.setList(titles)
                state = ContentState.Ready(titles)
            }
            .onFailure { state = ContentState.Error(it.message ?: "Failed to load") }
    }

    FeatureScaffold(
        title = "Movies & TV",
        onBack = onBack,
        actions = {
            IconButton(onClick = { reloadKey++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = if (media == MediaType.Movie) 0 else 1,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = media == MediaType.Movie,
                    onClick = { media = MediaType.Movie; selectedGenre = null },
                    text = { Text("Movies", fontWeight = FontWeight.Medium) }
                )
                Tab(
                    selected = media == MediaType.Tv,
                    onClick = { media = MediaType.Tv; selectedGenre = null },
                    text = { Text("TV Shows", fontWeight = FontWeight.Medium) }
                )
            }

            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" }
            )

            if (debouncedQuery.isBlank()) {
                SectionChips(
                    selected = section,
                    selectedGenreId = selectedGenre?.id,
                    onSectionPicked = {
                        section = it
                        selectedGenre = null
                    }
                )
                if (genres.isNotEmpty()) {
                    GenreChips(
                        genres = genres,
                        selectedId = selectedGenre?.id,
                        onPick = { genre ->
                            selectedGenre = if (selectedGenre?.id == genre.id) null else genre
                        }
                    )
                }
            }

            when (val s = state) {
                ContentState.Loading -> Centered { CircularProgressIndicator() }
                ContentState.MissingKey -> MissingKeyState(modifier = Modifier.fillMaxSize())
                is ContentState.Error -> ErrorState(
                    message = s.message,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = { reloadKey++ }
                )
                is ContentState.Ready -> {
                    if (s.titles.isEmpty()) {
                        EmptyResults(
                            query = debouncedQuery,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Hero treatment is reserved for the default browse
                        // experience — when the user starts searching or
                        // picking a genre, they want to scan the full grid
                        // immediately without an oversized first row.
                        val showHero = debouncedQuery.isBlank() && selectedGenre == null
                        MoviesGrid(
                            titles = s.titles,
                            sectionLabel = "${section.label} ${if (media == MediaType.Movie) "movies" else "shows"}",
                            showHero = showHero,
                            modifier = Modifier.fillMaxSize(),
                            onClick = { title -> onTitleClick(title.mediaType, title.idAsString) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                }
            }
        },
        placeholder = { Text("Search") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White.copy(alpha = 0.20f),
            focusedContainerColor = Color.White.copy(alpha = 0.30f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionChips(
    selected: Section,
    selectedGenreId: Int?,
    onSectionPicked: (Section) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(Section.entries) { s ->
            FilterChip(
                selected = selected == s && selectedGenreId == null,
                onClick = { onSectionPicked(s) },
                label = {
                    Text(
                        s.label,
                        fontWeight = if (selected == s && selectedGenreId == null)
                            FontWeight.Bold else FontWeight.Medium
                    )
                },
                shape = YoloShapes.Chip,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = Color.White.copy(alpha = 0.08f)
                )
            )
        }
    }
}

@Composable
private fun GenreChips(
    genres: List<TmdbGenre>,
    selectedId: Int?,
    onPick: (TmdbGenre) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(genres, key = { it.id }) { genre ->
            FilterChip(
                selected = selectedId == genre.id,
                onClick = { onPick(genre) },
                label = {
                    Text(
                        genre.name,
                        fontWeight = if (selectedId == genre.id)
                            FontWeight.Bold else FontWeight.Medium
                    )
                },
                shape = YoloShapes.Chip,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                    containerColor = Color.White.copy(alpha = 0.06f)
                )
            )
        }
    }
}

@Composable
private fun MoviesGrid(
    titles: List<TmdbTitle>,
    sectionLabel: String,
    showHero: Boolean,
    modifier: Modifier,
    onClick: (TmdbTitle) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 32.dp
        )
    ) {
        if (showHero) {
            item(
                key = "hero-${titles.first().id}",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                FeaturedHero(
                    title = titles.first(),
                    onClick = { onClick(titles.first()) }
                )
            }
            item(
                key = "section-${sectionLabel}",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                SectionHeading(
                    title = "More $sectionLabel",
                    subtitle = "${titles.size - 1} titles"
                )
            }
            items(
                items = titles.drop(1),
                key = { "${it.mediaType}-${it.id}" }
            ) { title ->
                TitleCard(title = title, onClick = { onClick(title) })
            }
        } else {
            items(titles, key = { "${it.mediaType}-${it.id}" }) { title ->
                TitleCard(title = title, onClick = { onClick(title) })
            }
        }
    }
}

// Drop shadow applied to every text element that sits on top of artwork.
// A soft, dark, slightly-down-shifted blur gives the type body regardless
// of what the backdrop image is doing underneath. Without this, bright
// posters/backdrops wash out the title even with a vertical scrim.
private val onArtworkShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 1.5f),
    blurRadius = 6f
)

@Composable
private fun FeaturedHero(title: TmdbTitle, onClick: () -> Unit) {
    val gradient = remember(title.id) { gradientFor(title.id) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 11f),
        shape = YoloShapes.Hero,
        color = Color.Transparent,
        shadowElevation = 12.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val backdrop = TmdbClient.backdropUrl(title.backdropPath)
                ?: TmdbClient.posterUrl(title.posterPath, size = "w500")
            if (backdrop != null) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = title.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gradient))
                )
            }

            // Heavier scrim — bottom half goes almost fully opaque so the
            // text block lives on a near-solid surface, then fades to clear
            // at the top so the artwork remains visible.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.20f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.96f)
                            ),
                            startY = 0f
                        )
                    )
            )

            // Featured pill
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "FEATURED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Black
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    title.title,
                    style = MaterialTheme.typography.displaySmall.copy(shadow = onArtworkShadow),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (title.rating > 0) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "%.1f".format(title.rating),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = onArtworkShadow),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    if (title.year > 0) {
                        Text(
                            title.year.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = onArtworkShadow),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (title.mediaType == "tv") "SERIES" else "MOVIE",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                if (title.overview.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        title.overview,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = onArtworkShadow),
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = onClick,
                    shape = YoloShapes.Button,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Watch now",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TitleCard(title: TmdbTitle, onClick: () -> Unit) {
    val gradient = remember(title.id) { gradientFor(title.id) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TmdbClient.posterUrl(title.posterPath)?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = title.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gradient))
                )
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gradient))
                )
                Icon(
                    Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                )
            }

            // Rating pill in the top-right corner. Solid dark background +
            // bold figure → always readable, no shadow needed.
            if (title.rating > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "%.1f".format(title.rating),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Bottom-up scrim — pushed harder so the title block sits on a
            // near-solid base regardless of poster brightness.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.50f),
                                Color.Black.copy(alpha = 0.88f),
                                Color.Black.copy(alpha = 0.97f)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    title.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(shadow = onArtworkShadow),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                if (title.year > 0) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        title.year.toString(),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium.copy(shadow = onArtworkShadow),
                        fontWeight = FontWeight.SemiBold
                    )
                }
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

@Composable
private fun MissingKeyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Movie,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "TMDB key missing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Add tmdbApiKey to the Firestore config/app document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(message: String, modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Couldn't load",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Retry") }
    }
}

@Composable
private fun EmptyResults(query: String, modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (query.isBlank()) "Nothing here" else "No results for \"$query\"",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private val cardGradients = listOf(
    listOf(Color(0xFF1A237E), Color(0xFF311B92)),
    listOf(Color(0xFF263238), Color(0xFF000000)),
    listOf(Color(0xFFB71C1C), Color(0xFFE65100)),
    listOf(Color(0xFF6A1B9A), Color(0xFF4A148C)),
    listOf(Color(0xFF004D40), Color(0xFF263238)),
    listOf(Color(0xFFAD1457), Color(0xFF880E4F)),
    listOf(Color(0xFFBF360C), Color(0xFF3E2723)),
    listOf(Color(0xFF0D47A1), Color(0xFF01579B))
)

private fun gradientFor(id: Long): List<Color> {
    val idx = (id and 0x7FFFFFFFL).toInt() % cardGradients.size
    return cardGradients[idx]
}
