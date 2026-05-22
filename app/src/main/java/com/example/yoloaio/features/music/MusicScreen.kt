package com.example.yoloaio.features.music

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yoloaio.cast.CastButton
import com.example.yoloaio.cast.CastManager
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.components.GlassCard
import com.example.yoloaio.ui.components.yoloSurfaceColor
import com.example.yoloaio.ui.theme.LocalGlass
import com.example.yoloaio.ui.theme.YoloShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MusicTab(val label: String, val icon: ImageVector) {
    Songs("Songs", Icons.Rounded.MusicNote),
    Albums("Albums", Icons.Rounded.Album),
    Playlists("Playlists", Icons.Rounded.PlaylistPlay),
    Favorites("Favorites", Icons.Rounded.Favorite)
}

private sealed interface TabContent<out T> {
    data object Loading : TabContent<Nothing>
    data class Ready<T>(val items: List<T>) : TabContent<T>
    data class Error(val message: String) : TabContent<Nothing>
}

@Composable
fun MusicScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    // Singleton — survives screen navigation so playback keeps running when
    // the user opens Home / Chat / etc. Released only by MusicPlaybackService
    // when the user dismisses the media notification.
    val player = remember { MusicPlayer.get(context) }

    val scope = rememberCoroutineScope()
    val langs by rememberMusicLanguages()
    val primaryLang = langs.firstOrNull() ?: MusicLanguage.Default

    val favRepo = remember { FavoriteTracksRepository() }
    val favorites by favRepo.observeFavorites().collectAsState(initial = emptyList())
    val favoriteIds = remember(favorites) { favorites.map { it.trackId }.toSet() }

    var tab by remember { mutableStateOf(MusicTab.Songs) }
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var playerExpanded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    var songsState by remember { mutableStateOf<TabContent<SaavnTrack>>(TabContent.Loading) }
    var albumsState by remember { mutableStateOf<TabContent<SaavnAlbum>>(TabContent.Loading) }
    var playlistsState by remember { mutableStateOf<TabContent<SaavnPlaylist>>(TabContent.Loading) }

    LaunchedEffect(query) {
        delay(400)
        debouncedQuery = query.trim()
    }

    LaunchedEffect(tab, debouncedQuery, primaryLang, reloadKey) {
        when (tab) {
            MusicTab.Songs -> {
                songsState = TabContent.Loading
                JioSaavnClient.search(debouncedQuery, primaryLang)
                    .onSuccess { tracks ->
                        songsState = TabContent.Ready(tracks)
                        player.bind(tracks) { it.streamUrl }
                    }
                    .onFailure {
                        songsState = TabContent.Error(it.message ?: "Failed to load")
                    }
            }
            MusicTab.Albums -> {
                albumsState = TabContent.Loading
                JioSaavnClient.searchAlbums(debouncedQuery, primaryLang)
                    .onSuccess { albumsState = TabContent.Ready(it) }
                    .onFailure { albumsState = TabContent.Error(it.message ?: "Failed to load") }
            }
            MusicTab.Playlists -> {
                playlistsState = TabContent.Loading
                JioSaavnClient.searchPlaylists(debouncedQuery, primaryLang)
                    .onSuccess { playlistsState = TabContent.Ready(it) }
                    .onFailure { playlistsState = TabContent.Error(it.message ?: "Failed to load") }
            }
            MusicTab.Favorites -> {
                // Driven by the Flow above. Re-bind the player to the favourites
                // list when this tab is the active surface so next/prev walk
                // favourites, not the songs queue.
                player.bind(favorites.map { it.toTrack() }) { it.streamUrl }
            }
        }
    }

    // Re-bind to favourites whenever the favourites Flow emits, but only if
    // the user is actively on that tab — otherwise we'd clobber the Songs queue.
    LaunchedEffect(favorites, tab) {
        if (tab == MusicTab.Favorites) {
            player.bind(favorites.map { it.toTrack() }) { it.streamUrl }
        }
    }

    BackHandler(enabled = playerExpanded) { playerExpanded = false }

    FeatureScaffold(
        title = "Music",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Tune, contentDescription = "Music settings")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                MusicTabBar(selected = tab, onSelect = { tab = it })

                if (tab != MusicTab.Favorites) {
                    SearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onClear = { query = "" }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (tab) {
                        MusicTab.Songs -> SongsView(
                            state = songsState,
                            currentTrackId = player.current?.id,
                            isPlaying = player.isPlaying,
                            favoriteIds = favoriteIds,
                            playNextCount = player.playNext.size,
                            bottomInset = if (player.current != null) 96.dp else 12.dp,
                            onPlay = { player.play(it) },
                            onFavoriteToggle = { track ->
                                scope.launch {
                                    if (track.id in favoriteIds) favRepo.remove(track.id)
                                    else favRepo.add(track)
                                }
                            },
                            onPlayNext = { player.addToPlayNext(it) },
                            onRetry = { reloadKey++ }
                        )

                        MusicTab.Albums -> AlbumsView(
                            state = albumsState,
                            onTap = { album ->
                                query = album.title
                                tab = MusicTab.Songs
                            },
                            onRetry = { reloadKey++ }
                        )

                        MusicTab.Playlists -> PlaylistsView(
                            state = playlistsState,
                            onTap = { pl ->
                                query = pl.title
                                tab = MusicTab.Songs
                            },
                            onRetry = { reloadKey++ }
                        )

                        MusicTab.Favorites -> FavoritesView(
                            favorites = favorites,
                            currentTrackId = player.current?.id,
                            isPlaying = player.isPlaying,
                            bottomInset = if (player.current != null) 96.dp else 12.dp,
                            onPlay = { player.play(it) },
                            onUnfavorite = { trackId ->
                                scope.launch { favRepo.remove(trackId) }
                            }
                        )
                    }
                }
            }

            if (player.current != null && !playerExpanded) {
                MiniPlayerBar(
                    player = player,
                    onExpand = { playerExpanded = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }

            AnimatedVisibility(
                visible = playerExpanded && player.current != null,
                enter = slideInVertically(
                    animationSpec = tween(350),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(250)),
                exit = slideOutVertically(
                    animationSpec = tween(300),
                    targetOffsetY = { it }
                ) + fadeOut(animationSpec = tween(200))
            ) {
                MaxPlayer(
                    player = player,
                    isFavorited = player.current?.id in favoriteIds,
                    onFavoriteToggle = {
                        val t = player.current ?: return@MaxPlayer
                        scope.launch {
                            if (t.id in favoriteIds) favRepo.remove(t.id)
                            else favRepo.add(t)
                        }
                    },
                    onCollapse = { playerExpanded = false }
                )
            }
        }
    }

    // (Language picker bottom sheet was here. It now lives in
    // MusicSettingsScreen reachable via the gear icon up top.)
}

// =========================================================================
// Tab bar
// =========================================================================

@Composable
private fun MusicTabBar(selected: MusicTab, onSelect: (MusicTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        MusicTab.entries.forEach { t ->
            Tab(
                selected = selected == t,
                onClick = { onSelect(t) },
                text = {
                    Text(
                        t.label,
                        fontWeight = if (selected == t) FontWeight.Bold else FontWeight.Medium
                    )
                },
                icon = {
                    Icon(t.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }
    }
}

// =========================================================================
// Songs view
// =========================================================================

@Composable
private fun SongsView(
    state: TabContent<SaavnTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    playNextCount: Int,
    bottomInset: androidx.compose.ui.unit.Dp,
    onPlay: (SaavnTrack) -> Unit,
    onFavoriteToggle: (SaavnTrack) -> Unit,
    onPlayNext: (SaavnTrack) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        TabContent.Loading -> Centered { CircularProgressIndicator() }
        is TabContent.Error -> ErrorState(state.message, onRetry = onRetry)
        is TabContent.Ready -> {
            if (state.items.isEmpty()) {
                EmptyResults("No songs")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomInset)
                ) {
                    items(state.items, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = currentTrackId == track.id,
                            isPlayingNow = currentTrackId == track.id && isPlaying,
                            isFavorited = track.id in favoriteIds,
                            onClick = { onPlay(track) },
                            onFavoriteToggle = { onFavoriteToggle(track) },
                            onPlayNext = { onPlayNext(track) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        )
                    }
                    if (playNextCount > 0) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "$playNextCount track${if (playNextCount == 1) "" else "s"} queued to play next",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: SaavnTrack,
    isCurrent: Boolean,
    isPlayingNow: Boolean,
    isFavorited: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onPlayNext: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(gradientFor(track.id))),
            contentAlignment = Alignment.Center
        ) {
            val art = track.artworkUrlSmall ?: track.artworkUrlLarge
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
            }
            if (isPlayingNow) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onFavoriteToggle) {
            Icon(
                if (isFavorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorited) "Remove from favourites" else "Favourite",
                tint = if (isFavorited) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                    },
                    onClick = {
                        onPlayNext()
                        menuOpen = false
                    }
                )
            }
        }
    }
}

// =========================================================================
// Albums view
// =========================================================================

@Composable
private fun AlbumsView(
    state: TabContent<SaavnAlbum>,
    onTap: (SaavnAlbum) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        TabContent.Loading -> Centered { CircularProgressIndicator() }
        is TabContent.Error -> ErrorState(state.message, onRetry = onRetry)
        is TabContent.Ready -> {
            if (state.items.isEmpty()) {
                EmptyResults("No albums")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = { onTap(album) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: SaavnAlbum, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = YoloShapes.Card,
        contentPadding = PaddingValues(10.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(gradientFor(album.id))),
                contentAlignment = Alignment.Center
            ) {
                val art = album.artworkUrlLarge ?: album.artworkUrlSmall
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.Album,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                album.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// =========================================================================
// Playlists view
// =========================================================================

@Composable
private fun PlaylistsView(
    state: TabContent<SaavnPlaylist>,
    onTap: (SaavnPlaylist) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        TabContent.Loading -> Centered { CircularProgressIndicator() }
        is TabContent.Error -> ErrorState(state.message, onRetry = onRetry)
        is TabContent.Ready -> {
            if (state.items.isEmpty()) {
                EmptyResults("No playlists")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.id }) { playlist ->
                        PlaylistCard(playlist = playlist, onClick = { onTap(playlist) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: SaavnPlaylist, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = YoloShapes.Card,
        contentPadding = PaddingValues(10.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(gradientFor(playlist.id))),
                contentAlignment = Alignment.Center
            ) {
                val art = playlist.artworkUrlLarge ?: playlist.artworkUrlSmall
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = playlist.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                playlist.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                playlist.curator,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// =========================================================================
// Favorites view
// =========================================================================

@Composable
private fun FavoritesView(
    favorites: List<FavoriteTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
    onPlay: (SaavnTrack) -> Unit,
    onUnfavorite: (String) -> Unit
) {
    if (favorites.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No favourites yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap the heart on any track in Songs to save it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomInset)
    ) {
        items(favorites, key = { it.trackId }) { fav ->
            val track = remember(fav.id) { fav.toTrack() }
            FavoriteRow(
                track = track,
                isPlayingNow = currentTrackId == fav.trackId && isPlaying,
                isCurrent = currentTrackId == fav.trackId,
                onClick = { onPlay(track) },
                onUnfavorite = { onUnfavorite(fav.trackId) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                color = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}

@Composable
private fun FavoriteRow(
    track: SaavnTrack,
    isPlayingNow: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onUnfavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(gradientFor(track.id))),
            contentAlignment = Alignment.Center
        ) {
            val art = track.artworkUrlSmall ?: track.artworkUrlLarge
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
            }
            if (isPlayingNow) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onUnfavorite) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = "Remove from favourites",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// =========================================================================
// Search bar
// =========================================================================

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
        placeholder = { Text("Search artists, songs, albums") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

// =========================================================================
// Mini player
// =========================================================================

@Composable
private fun MiniPlayerBar(
    player: MusicPlayer,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = player.current ?: return
    val position by produceState(initialValue = 0L, player.isPlaying, player.isPrepared) {
        while (true) {
            value = player.currentPositionMs()
            delay(500)
        }
    }
    val duration = player.durationMs.coerceAtLeast(1L)
    val progressFraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = YoloShapes.Hero,
        contentPadding = PaddingValues(0.dp),
        strong = true,
        onClick = onExpand,
        accentColors = gradientFor(track.id)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(gradientFor(track.id))),
                    contentAlignment = Alignment.Center
                ) {
                    val art = track.artworkUrlLarge ?: track.artworkUrlSmall
                    if (art != null) {
                        AsyncImage(
                            model = art,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { player.toggle() }) {
                    if (player.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (player.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                IconButton(onClick = { player.next() }) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.10f)
            )
        }
    }
}

// =========================================================================
// Max player
// =========================================================================

@Composable
private fun MaxPlayer(
    player: MusicPlayer,
    isFavorited: Boolean,
    onFavoriteToggle: () -> Unit,
    onCollapse: () -> Unit
) {
    val track = player.current ?: return
    val glass = LocalGlass.current
    val backdrop = yoloSurfaceColor(strong = false, isDark = glass.isDark)
    val context = LocalContext.current

    val position by produceState(initialValue = 0L, player.isPlaying, player.isPrepared) {
        while (true) {
            value = player.currentPositionMs()
            delay(500)
        }
    }
    val duration = player.durationMs.coerceAtLeast(1L)
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableLongStateOf(0L) }
    val displayed = if (scrubbing) scrubValue else position

    val accent = gradientFor(track.id)

    // Real-time audio analyser. Tied to this composable's lifecycle:
    // recreated when the audio session id changes (new track), torn down
    // when the max view closes. RECORD_AUDIO permission is requested on
    // first entry — if denied, bandMagnitudes stays null and the visualiser
    // falls back to its synthetic animation.
    val analyzer = remember { BeatAnalyzer() }
    val sessionId = player.audioSessionId
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && player.isPrepared && sessionId > 0) {
            analyzer.start(sessionId)
        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(sessionId, player.isPrepared) {
        if (player.isPrepared && sessionId > 0 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            analyzer.start(sessionId)
        }
        onDispose { analyzer.stop() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.first().copy(alpha = 0.30f),
                            Color.Transparent,
                            accent.last().copy(alpha = 0.22f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Minimise",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                val cast = remember { CastManager.get(context) }
                val castLabel = if (cast.isConnected) {
                    "Casting to ${cast.deviceName ?: "device"}"
                } else "Now playing"
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        castLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cast.isConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (cast.isConnected) FontWeight.SemiBold
                        else FontWeight.Normal
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                // Cast picker — hosts the standard MediaRouteButton from
                // the Cast SDK. Tapping opens the system device picker;
                // selecting a device begins routing playback to it.
                CastButton(
                    modifier = Modifier.size(44.dp).padding(end = 4.dp)
                )
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        if (isFavorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint = if (isFavorited) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.weight(0.35f))

            // Circular visualiser + album art combined. The bars radiate
            // outward from the album perimeter; the inner 70% of this Box is
            // the circular album cover. Driven by live FFT from BeatAnalyzer
            // when permission is granted; falls back to the synthetic
            // sin-wave animation otherwise.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularBeatVisualizer(
                    isPlaying = player.isPlaying,
                    color = MaterialTheme.colorScheme.primary,
                    barCount = 72,
                    innerRadiusFraction = 0.72f,
                    liveBands = analyzer.bandMagnitudes,
                    pulse = analyzer.pulse,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.70f)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(accent)),
                    contentAlignment = Alignment.Center
                ) {
                    val art = track.artworkUrlLarge ?: track.artworkUrlSmall
                    if (art != null) {
                        AsyncImage(
                            model = art,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Title + meta
            Text(
                track.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 220.dp)
                )
                if (track.year.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            track.year,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Scrubber
            Slider(
                value = displayed.toFloat(),
                valueRange = 0f..duration.toFloat(),
                onValueChange = {
                    scrubbing = true
                    scrubValue = it.toLong()
                },
                onValueChangeFinished = {
                    player.seekTo(scrubValue)
                    scrubbing = false
                },
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    thumbColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatMs(displayed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatMs(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // Transport row — shuffle | prev | play | next | repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (player.shuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = { player.previous() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { player.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (player.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (player.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { player.next() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { player.cycleRepeatMode() }) {
                    val (icon, tint, descr) = when (player.repeatMode) {
                        RepeatMode.Off -> Triple(
                            Icons.Rounded.Repeat,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            "Repeat off"
                        )
                        RepeatMode.All -> Triple(
                            Icons.Rounded.Repeat,
                            MaterialTheme.colorScheme.primary,
                            "Repeat all"
                        )
                        RepeatMode.One -> Triple(
                            Icons.Rounded.RepeatOne,
                            MaterialTheme.colorScheme.primary,
                            "Repeat one"
                        )
                    }
                    Icon(
                        icon,
                        contentDescription = descr,
                        tint = tint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Up-next preview
            if (player.playNext.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val nextTrack = player.playNext.first()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Up next",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${nextTrack.title} · ${nextTrack.artist}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    if (player.playNext.size > 1) {
                        Text(
                            "+${player.playNext.size - 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            player.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

// =========================================================================
// Language settings sheet
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSettingsSheet(
    current: Set<MusicLanguage>,
    onConfirm: (Set<MusicLanguage>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draft = remember { mutableStateOf(current.toMutableSet()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                "Preferred languages",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Songs, albums and playlists are biased toward the languages you select. The first language in your list is used as the primary search filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                MusicLanguage.all.forEach { lang ->
                    val checked = lang in draft.value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val next = draft.value.toMutableSet()
                                if (checked) next -= lang else next += lang
                                if (next.isEmpty()) next += MusicLanguage.Default
                                draft.value = next
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            lang.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(draft.value) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Save") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// =========================================================================
// Empty / error / spinner helpers
// =========================================================================

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
private fun EmptyResults(label: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

// =========================================================================
// Gradient helpers + duration formatter
// =========================================================================

private val tileGradients = listOf(
    listOf(Color(0xFF7C9CFF), Color(0xFF1A237E)),
    listOf(Color(0xFFE0AAFF), Color(0xFF6A1B9A)),
    listOf(Color(0xFF263238), Color(0xFF000000)),
    listOf(Color(0xFFFF7AB6), Color(0xFFAD1457)),
    listOf(Color(0xFFA8C7FF), Color(0xFF263238)),
    listOf(Color(0xFF00BFA5), Color(0xFF1B5E20)),
    listOf(Color(0xFFFFC36B), Color(0xFFE65100)),
    listOf(Color(0xFFB85AC1), Color(0xFF311B92))
)

private fun gradientFor(id: String): List<Color> {
    val bucket = (id.hashCode() and Int.MAX_VALUE) % tileGradients.size
    return tileGradients[bucket]
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
