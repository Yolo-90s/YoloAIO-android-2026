package com.example.yoloaio.features.ringtones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.features.audio.LocalTrimmedTonesStore
import com.example.yoloaio.features.music.JioSaavnClient
import com.example.yoloaio.features.music.MusicLanguage
import com.example.yoloaio.ui.components.FeatureScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ToneTab(val label: String) {
    Music("Music Tones"),
    Sfx("Sound Effects")
}

private sealed interface ToneListState {
    data object Loading : ToneListState
    data class Ready(val tones: List<Tone>) : ToneListState
    data class Error(val message: String) : ToneListState
    data object MissingKey : ToneListState
}

@Composable
fun RingtonesScreen(
    onBack: () -> Unit,
    onFavoritesClick: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalAppConfig.current
    val player = remember { RingtonePlayer(context) }
    val favRepo = remember { RingtoneFavoritesRepository() }
    val trimmedStore = remember { LocalTrimmedTonesStore.get(context) }
    val scope = rememberCoroutineScope()
    DisposableEffect(player) { onDispose { player.release() } }

    val favorites by favRepo.observeFavorites().collectAsState(initial = emptyList())
    val favoriteIds = remember(favorites) { favorites.map { it.toneId }.toSet() }

    val trimmedTones by trimmedStore.tones.collectAsState()
    val trimmedById = remember(trimmedTones) {
        trimmedTones.associateBy { "${ToneSource.Trimmed.key}-${it.id}" }
    }

    var tab by remember { mutableStateOf(ToneTab.Music) }
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(MusicLanguage.Default) }
    var category by remember { mutableStateOf(RingtoneCategory.Ringtone) }
    var state by remember { mutableStateOf<ToneListState>(ToneListState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    var activeSheetTone by remember { mutableStateOf<Tone?>(null) }

    LaunchedEffect(query) {
        delay(400)
        debouncedQuery = query.trim()
    }

    LaunchedEffect(tab, debouncedQuery, language, category, config.freesoundApiKey, reloadKey) {
        state = ToneListState.Loading
        when (tab) {
            ToneTab.Music -> {
                // Bias blank queries toward ringtone-style clips; users typing a song
                // name should get an exact-ish search.
                val q = if (debouncedQuery.isBlank()) "ringtone ${language.label}" else debouncedQuery
                JioSaavnClient.search(query = q, language = language)
                    .onSuccess { tracks ->
                        state = ToneListState.Ready(tracks.map { it.toTone() })
                    }
                    .onFailure {
                        state = ToneListState.Error(it.message ?: "Failed to load")
                    }
            }
            ToneTab.Sfx -> {
                if (config.freesoundApiKey.isBlank()) {
                    state = ToneListState.MissingKey
                    return@LaunchedEffect
                }
                FreesoundClient.search(
                    query = debouncedQuery,
                    category = category,
                    apiKey = config.freesoundApiKey
                )
                    .onSuccess { tones ->
                        state = ToneListState.Ready(tones.map { it.toTone() })
                    }
                    .onFailure {
                        state = ToneListState.Error(it.message ?: "Failed to load")
                    }
            }
        }
    }

    FeatureScaffold(
        title = "Ringtones",
        onBack = onBack
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onFavoritesClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Bookmark,
                        contentDescription = "Favorites",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                SearchBar(
                    modifier = Modifier.padding(end = 48.dp),
                    query = query,
                    onChange = { query = it },
                    onClear = { query = "" }
                )
            }

            TabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                ToneTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = {
                            if (tab != t) {
                                player.stop()
                                tab = t
                            }
                        },
                        text = {
                            Text(
                                t.label,
                                fontWeight = if (tab == t) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            when (tab) {
                ToneTab.Music -> LanguageChips(selected = language, onPick = { language = it })
                ToneTab.Sfx -> CategoryChips(selected = category, onPick = { category = it })
            }

            when (val s = state) {
                ToneListState.Loading -> Centered { CircularProgressIndicator() }
                ToneListState.MissingKey -> MissingKeyState(modifier = Modifier.fillMaxSize())
                is ToneListState.Error -> ErrorState(
                    message = s.message,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = { reloadKey++ }
                )
                is ToneListState.Ready -> {
                    val combined = if (tab == ToneTab.Music) {
                        val filter = debouncedQuery
                        val mine = trimmedTones
                            .map { it.toTone() }
                            .filter {
                                filter.isBlank() ||
                                    it.name.contains(filter, ignoreCase = true)
                            }
                        mine + s.tones
                    } else {
                        s.tones
                    }
                    if (combined.isEmpty()) {
                        EmptyState(query = debouncedQuery, modifier = Modifier.fillMaxSize())
                    } else {
                        ToneList(
                            tones = combined,
                            player = player,
                            onMoreClick = { activeSheetTone = it }
                        )
                    }
                }
            }
        }

        activeSheetTone?.let { tone ->
            val isFav = tone.id in favoriteIds
            val trimmedDoc = trimmedById[tone.id]
            ToneActionsSheet(
                tone = tone,
                isFavorite = isFav,
                onDismiss = { activeSheetTone = null },
                onToggleFavorite = {
                    scope.launch {
                        if (isFav) favRepo.remove(tone.id) else favRepo.add(tone)
                    }
                },
                onDelete = if (trimmedDoc != null) {
                    {
                        if (player.playingId == tone.id) player.stop()
                        scope.launch { trimmedStore.remove(trimmedDoc.id) }
                    }
                } else null
            )
        }
    }
}

@Composable
private fun SearchBar(
    modifier: Modifier = Modifier,
    query: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit
) {
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
        placeholder = { Text("Search tones") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White.copy(alpha = 0.20f),
            focusedContainerColor = Color.White.copy(alpha = 0.30f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun LanguageChips(
    selected: MusicLanguage,
    onPick: (MusicLanguage) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(MusicLanguage.all) { lang ->
            FilterChip(
                selected = selected == lang,
                onClick = { onPick(lang) },
                label = { Text(lang.label) },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun CategoryChips(
    selected: RingtoneCategory,
    onPick: (RingtoneCategory) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(RingtoneCategory.entries) { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onPick(cat) },
                label = { Text(cat.label) },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun ToneList(
    tones: List<Tone>,
    player: RingtonePlayer,
    onMoreClick: (Tone) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(tones, key = { it.id }) { tone ->
            val isCurrent = player.playingId == tone.id
            ToneRow(
                tone = tone,
                isCurrent = isCurrent,
                isPlaying = isCurrent && player.isPlaying,
                isLoading = isCurrent && player.isLoading,
                onToggle = { player.toggle(tone.id, tone.streamUrl) },
                onMoreClick = { onMoreClick(tone) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                color = Color.White.copy(alpha = 0.15f)
            )
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
            Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Freesound key missing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Get a free token at freesound.org/apiv2/apply and add freesoundApiKey to the Firestore config/app document.",
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
private fun EmptyState(query: String, modifier: Modifier) {
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
            if (query.isBlank()) "No tones here" else "No results for \"$query\"",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
