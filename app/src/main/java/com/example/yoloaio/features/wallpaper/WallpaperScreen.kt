package com.example.yoloaio.features.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yoloaio.data.LocalAppConfig
import kotlinx.coroutines.delay

private sealed interface WallpaperState {
    data object Loading : WallpaperState
    data class Ready(val photos: List<UnsplashPhoto>) : WallpaperState
    data class Error(val message: String) : WallpaperState
    data object MissingKey : WallpaperState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(
    onBack: () -> Unit,
    onWallpaperClick: (String) -> Unit,
    onFavoritesClick: () -> Unit
) {
    val config = LocalAppConfig.current
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var orientation by remember { mutableStateOf(WallpaperOrientation.Portrait) }
    var resolution by remember { mutableStateOf(ResolutionFilter.Any) }
    var state by remember { mutableStateOf<WallpaperState>(WallpaperState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(query) {
        delay(400)
        debouncedQuery = query.trim()
    }

    LaunchedEffect(debouncedQuery, orientation, config.unsplashAccessKey, reloadKey) {
        if (config.unsplashAccessKey.isBlank()) {
            state = WallpaperState.MissingKey
            return@LaunchedEffect
        }
        state = WallpaperState.Loading
        val effectiveQuery = debouncedQuery.ifBlank { config.unsplashQuery }
        UnsplashClient.search(
            query = effectiveQuery,
            accessKey = config.unsplashAccessKey,
            perPage = 30,
            orientation = orientation
        )
            .onSuccess { photos ->
                WallpaperCache.set(photos)
                state = WallpaperState.Ready(photos)
            }
            .onFailure { state = WallpaperState.Error(it.message ?: "Failed to load") }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Wallpaper", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onFavoritesClick) {
                        Icon(
                            Icons.Rounded.Bookmark,
                            contentDescription = "Favorites & rotation",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(
                query = query,
                onChange = { query = it },
                onClear = { query = "" }
            )
            FilterRow(
                orientation = orientation,
                onOrientationChange = { orientation = it },
                resolution = resolution,
                onResolutionChange = { resolution = it }
            )

            when (val s = state) {
                WallpaperState.Loading -> Centered { CircularProgressIndicator() }
                WallpaperState.MissingKey -> MissingKeyState(modifier = Modifier.fillMaxSize())
                is WallpaperState.Error -> ErrorState(
                    message = s.message,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = { reloadKey++ }
                )
                is WallpaperState.Ready -> {
                    val filtered = remember(s.photos, resolution) {
                        s.photos.filter { resolution.accepts(it) }
                    }
                    if (filtered.isEmpty()) {
                        EmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        PinterestGrid(
                            photos = filtered,
                            modifier = Modifier.fillMaxSize(),
                            onClick = onWallpaperClick
                        )
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
        placeholder = { Text("Search wallpapers") },
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
private fun FilterRow(
    orientation: WallpaperOrientation,
    onOrientationChange: (WallpaperOrientation) -> Unit,
    resolution: ResolutionFilter,
    onResolutionChange: (ResolutionFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(WallpaperOrientation.entries) { o ->
            FilterChip(
                selected = orientation == o,
                onClick = { onOrientationChange(o) },
                label = { Text(o.label) },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        items(ResolutionFilter.entries) { r ->
            FilterChip(
                selected = resolution == r,
                onClick = { onResolutionChange(r) },
                label = { Text(r.label) },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                )
            )
        }
    }
}

@Composable
private fun PinterestGrid(
    photos: List<UnsplashPhoto>,
    modifier: Modifier,
    onClick: (String) -> Unit
) {
    LazyVerticalStaggeredGrid(
        // Adaptive width so on tablets / foldables we get 3+ columns
        // without changing the file. On regular phones still ends up
        // 2-wide because of the 170 dp minimum.
        columns = StaggeredGridCells.Adaptive(minSize = 170.dp),
        modifier = modifier.padding(horizontal = 10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp
    ) {
        items(photos, key = { it.id }) { photo ->
            WallpaperCard(photo = photo, onClick = { onClick(photo.id) })
        }
    }
}

@Composable
private fun WallpaperCard(photo: UnsplashPhoto, onClick: () -> Unit) {
    val ratio = if (photo.height > 0 && photo.width > 0) photo.aspectRatio else 0.75f
    AsyncImage(
        model = photo.smallUrl,
        contentDescription = photo.description.ifBlank { "Wallpaper" },
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .aspectRatioCompat(ratio)
            .clickable(onClick = onClick)
    )
}

private fun Modifier.aspectRatioCompat(ratio: Float): Modifier =
    this.aspectRatio(ratio.coerceAtLeast(0.4f))

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
            Icons.Rounded.Wallpaper,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Wallpapers not configured",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Set unsplashAccessKey in the Firestore config/app document to browse images.",
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
        Text("Couldn't load", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
private fun EmptyState(modifier: Modifier) {
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
            "Nothing matches these filters",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
