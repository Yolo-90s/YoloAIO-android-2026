package com.example.yoloaio.features.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yoloaio.features.music.JioSaavnClient
import com.example.yoloaio.features.music.MusicLanguage
import com.example.yoloaio.features.music.SaavnTrack
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.components.GlassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun AudioTrimmerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalTrimmedTonesStore.get(context) }
    val player = remember { TrimPreviewPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }

    var source by remember { mutableStateOf<TrimSource?>(null) }
    var totalSec by remember { mutableStateOf(0.0) }
    var range by remember { mutableStateOf(0f..15f) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var isPreparingSource by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var showSongPicker by remember { mutableStateOf(false) }
    var formatDialog by remember { mutableStateOf<String?>(null) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isPreparingSource = true
            val fileName = queryDisplayName(context, uri) ?: "Device file"
            val durationSec = AudioTrimmer.probeDurationSec(context, uri)
                .takeIf { it > 0 } ?: 60.0
            source = TrimSource.Local(uri = uri, fileName = fileName, knownDurationSec = durationSec)
            totalSec = durationSec
            range = 0f..durationSec.toFloat().coerceAtMost(15f).coerceAtLeast(1f)
            previewUri = uri
            name = nameSuggestion(fileName)
            isPreparingSource = false
        }
    }

    // When source changes to Saavn, download the stream to cache so MediaPlayer
    // and MediaExtractor both have a stable local file.
    LaunchedEffect(source) {
        val current = source ?: return@LaunchedEffect
        if (current is TrimSource.Saavn) {
            isPreparingSource = true
            val file = AudioTrimmer.downloadToCache(
                context = context,
                url = current.track.streamUrl,
                extension = "m4a"
            )
            previewUri = file?.let { Uri.fromFile(it) }
            isPreparingSource = false
            if (file == null) {
                Toast.makeText(context, "Couldn't download this song", Toast.LENGTH_SHORT).show()
                source = null
            }
        }
    }

    LaunchedEffect(previewUri) {
        previewUri?.let { player.bind(it) }
    }

    FeatureScaffold(title = "Audio Trimmer", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                source == null -> SourceChooser(
                    onPickDevice = { pickFileLauncher.launch("audio/*") },
                    onPickSong = { showSongPicker = true }
                )

                else -> {
                    SourceCard(
                        source = source!!,
                        onChange = {
                            player.pause()
                            source = null
                            previewUri = null
                            name = ""
                        }
                    )

                    if (isPreparingSource || previewUri == null) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    } else {
                        val totalSecF = totalSec.toFloat().coerceAtLeast(1f)
                        val playheadFraction = if (player.isPlaying) {
                            (player.currentPositionMs / 1000f / totalSecF).coerceIn(0f, 1f)
                        } else null
                        val elapsedInTrimSec = if (player.isPlaying) {
                            (player.currentPositionMs / 1000f - range.start).coerceAtLeast(0f)
                        } else 0f
                        TrimControls(
                            totalSec = totalSecF,
                            range = range,
                            playheadFraction = playheadFraction,
                            onRangeChange = { newRange ->
                                if (player.isPlaying) player.pause()
                                range = newRange
                            },
                            previewPlaying = player.isPlaying,
                            elapsedInTrimSec = elapsedInTrimSec,
                            onPreviewToggle = {
                                if (player.isPlaying) player.pause()
                                else player.play(range.start.toDouble(), range.endInclusive.toDouble())
                            }
                        )

                        SaveCard(
                            name = name,
                            onNameChange = { name = it },
                            isSaving = isSaving,
                            saveEnabled = !isSaving && range.endInclusive - range.start >= 1f &&
                                previewUri != null,
                            onSave = {
                                scope.launch {
                                    isSaving = true
                                    runSaveFlow(
                                        context = context,
                                        store = store,
                                        source = source!!,
                                        sourceFileUri = previewUri!!,
                                        startSec = range.start.toDouble(),
                                        endSec = range.endInclusive.toDouble(),
                                        suggestedName = name,
                                        onUnsupported = { mime -> formatDialog = mime },
                                        onComplete = { ok ->
                                            isSaving = false
                                            if (ok) onBack()
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSongPicker) {
        SongPickerSheet(
            onDismiss = { showSongPicker = false },
            onPick = { track ->
                showSongPicker = false
                player.pause()
                source = TrimSource.Saavn(track)
                totalSec = track.durationSec.toDouble()
                range = 0f..15f.coerceAtMost(track.durationSec.toFloat()).coerceAtLeast(1f)
                previewUri = null  // will be set after download
                name = nameSuggestion(track.title)
            }
        )
    }

    formatDialog?.let { mime ->
        AlertDialog(
            onDismissRequest = { formatDialog = null },
            title = { Text("Format not supported yet") },
            text = {
                Text(
                    "This file is $mime. The trimmer currently only handles AAC " +
                        "audio (.m4a / .mp4). Pick an .m4a file or trim from " +
                        "the Songs catalog instead."
                )
            },
            confirmButton = {
                TextButton(onClick = { formatDialog = null }) { Text("Got it") }
            }
        )
    }
}

@Composable
private fun SourceChooser(
    onPickDevice: () -> Unit,
    onPickSong: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Pick a song to trim",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Choose an audio file from your device, or trim a track from " +
                    "the Songs catalog. Your trims save to the Music Tones " +
                    "tab in Ringtones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onPickDevice,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Browse device files", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onPickSong,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pick from Songs")
            }
        }
    }
}

@Composable
private fun SourceCard(source: TrimSource, onChange: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFFF7AB6), Color(0xFFB85AC1)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (source.artUrl != null) {
                    AsyncImage(
                        model = source.artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    source.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "${source.sourceLabel} · ${formatTime(source.knownDurationSec.toInt())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onChange) {
                Icon(
                    Icons.Rounded.SwapHoriz,
                    contentDescription = "Change source",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TrimControls(
    totalSec: Float,
    range: ClosedFloatingPointRange<Float>,
    playheadFraction: Float?,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    previewPlaying: Boolean,
    elapsedInTrimSec: Float,
    onPreviewToggle: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        TrimWaveform(
            rangeStart = range.start / totalSec,
            rangeEnd = range.endInclusive / totalSec,
            playheadFraction = playheadFraction
        )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Trim range",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            RangeSlider(
                value = range,
                onValueChange = onRangeChange,
                valueRange = 0f..totalSec
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(range.start.toInt()))
                Text(
                    "Length: ${formatTime((range.endInclusive - range.start).toInt())}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(formatTime(range.endInclusive.toInt()))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPreviewToggle,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (previewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (previewPlaying) "Stop preview" else "Preview trim")
            }
            if (previewPlaying) {
                Spacer(Modifier.height(6.dp))
                val trimLengthSec = (range.endInclusive - range.start).coerceAtLeast(0f)
                Text(
                    "Playing ${formatTime(elapsedInTrimSec.toInt())} / " +
                        formatTime(trimLengthSec.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SaveCard(
    name: String,
    onNameChange: (String) -> Unit,
    isSaving: Boolean,
    saveEnabled: Boolean,
    onSave: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Name your clip",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("My trim") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save trim", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongPickerSheet(
    onDismiss: () -> Unit,
    onPick: (SaavnTrack) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(MusicLanguage.Default) }
    var loading by remember { mutableStateOf(true) }
    var tracks by remember { mutableStateOf<List<SaavnTrack>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        delay(400)
        debouncedQuery = query.trim()
    }
    LaunchedEffect(debouncedQuery, language) {
        loading = true
        errorMessage = null
        JioSaavnClient.search(query = debouncedQuery, language = language)
            .onSuccess { tracks = it }
            .onFailure { errorMessage = it.message ?: "Failed to load" }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(560.dp)
        ) {
            Text(
                "Pick a song",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                placeholder = { Text("Search songs") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White.copy(alpha = 0.20f),
                    focusedContainerColor = Color.White.copy(alpha = 0.30f),
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MusicLanguage.all) { lang ->
                    FilterChip(
                        selected = language == lang,
                        onClick = { language = lang },
                        label = { Text(lang.label) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                errorMessage != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                tracks.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (debouncedQuery.isBlank()) "No songs"
                        else "No results for \"$debouncedQuery\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tracks, key = { it.id }) { t ->
                        SongPickerRow(track = t, onClick = { onPick(t) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SongPickerRow(track: SaavnTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF7C9CFF), Color(0xFF1A237E)))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (track.artworkUrlSmall != null) {
                AsyncImage(
                    model = track.artworkUrlSmall,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            track.durationFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { Text("Trim") }
    }
}

@Composable
private fun TrimWaveform(
    rangeStart: Float,
    rangeEnd: Float,
    playheadFraction: Float?
) {
    val bars = 48
    val activeColor = MaterialTheme.colorScheme.primary
    val playedColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.35f)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerWidth = maxWidth
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(bars) { i ->
                val pct = (i + 0.5f) / bars.toFloat()
                val inRange = pct in rangeStart..rangeEnd
                val passed = playheadFraction != null && pct <= playheadFraction
                val seed = ((i * 73) % 100) / 100f
                val heightFraction = 0.3f + seed * 0.7f
                val color = when {
                    passed && inRange -> playedColor
                    inRange -> activeColor
                    else -> inactiveColor
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction = heightFraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
        if (playheadFraction != null) {
            val playheadDp = containerWidth * playheadFraction.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .offset(x = playheadDp - 1.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }
    }
}

private suspend fun runSaveFlow(
    context: Context,
    store: LocalTrimmedTonesStore,
    source: TrimSource,
    sourceFileUri: Uri,
    startSec: Double,
    endSec: Double,
    suggestedName: String,
    onUnsupported: (String) -> Unit,
    onComplete: (Boolean) -> Unit
) {
    val outputFile = File(context.cacheDir, "trim_out_${System.currentTimeMillis()}.m4a")
    val trimResult = AudioTrimmer.trim(
        context = context,
        sourceUri = sourceFileUri,
        startSec = startSec,
        endSec = endSec,
        outputFile = outputFile
    )
    when (trimResult) {
        is AudioTrimmer.TrimResult.UnsupportedFormat -> {
            onUnsupported(trimResult.mime)
            onComplete(false)
            return
        }
        is AudioTrimmer.TrimResult.Failed -> {
            Toast.makeText(context, "Couldn't trim: ${trimResult.message}", Toast.LENGTH_LONG).show()
            onComplete(false)
            return
        }
        is AudioTrimmer.TrimResult.Success -> {
            val finalName = suggestedName.trim().ifBlank { nameSuggestion(source.displayName) }
            val saveResult = AudioTrimmer.saveToMusicLibrary(
                context = context,
                sourceFile = trimResult.file,
                displayName = finalName
            )
            outputFile.delete()
            when (saveResult) {
                AudioTrimmer.SaveResult.NeedsAndroid10 -> {
                    Toast.makeText(
                        context,
                        "Saving trims needs Android 10 or newer.",
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete(false)
                }
                is AudioTrimmer.SaveResult.Failed -> {
                    Toast.makeText(
                        context,
                        "Couldn't save to library: ${saveResult.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete(false)
                }
                is AudioTrimmer.SaveResult.Success -> {
                    store.add(
                        TrimmedTone(
                            id = UUID.randomUUID().toString(),
                            name = finalName,
                            sourceName = source.displayName,
                            durationSec = trimResult.durationSec,
                            streamUrl = saveResult.uri.toString(),
                            artUrl = source.artUrl,
                            mimeType = "audio/mp4",
                            fileExtension = "m4a",
                            createdAt = System.currentTimeMillis()
                        )
                    ).onSuccess {
                        Toast.makeText(
                            context,
                            "Saved to Music library · Music Tones",
                            Toast.LENGTH_SHORT
                        ).show()
                        onComplete(true)
                    }.onFailure {
                        Toast.makeText(
                            context,
                            "Saved file but couldn't index: ${it.message ?: "unknown"}",
                            Toast.LENGTH_LONG
                        ).show()
                        onComplete(false)
                    }
                }
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
    } catch (_: Exception) {
        null
    }
}

private fun nameSuggestion(raw: String): String {
    val base = raw.substringBeforeLast('.').trim()
    val cleaned = base.replace(Regex("\\s+"), " ")
    return if (cleaned.length > 40) cleaned.take(40) else cleaned
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
