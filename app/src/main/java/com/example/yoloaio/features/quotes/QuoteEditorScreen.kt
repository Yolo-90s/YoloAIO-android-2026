package com.example.yoloaio.features.quotes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.features.settings.PrivacyPreferenceStore
import com.example.yoloaio.features.wallpaper.UnsplashClient
import com.example.yoloaio.features.wallpaper.UnsplashPhoto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TextColorPalette = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFFFFC36BL, 0xFFFF7AB6L,
    0xFFE0AAFFL, 0xFFA8C7FFL, 0xFFB85AC1L, 0xFF7C9CFFL,
    0xFF00BFA5L, 0xFFFFD740L, 0xFFFF6E40L, 0xFF8C9EFFL
)

private val SolidPalette = listOf(
    0xFF1A237EL, 0xFF4A148CL, 0xFFB71C1CL, 0xFFE65100L,
    0xFF263238L, 0xFF000000L, 0xFFAD1457L, 0xFF6A1B9AL,
    0xFF004D40L, 0xFF1B5E20L, 0xFF0D47A1L, 0xFF3E2723L
)

private val GradientPalette: List<List<Long>> = listOf(
    listOf(0xFF1A237EL, 0xFF4A148CL),
    listOf(0xFFB71C1CL, 0xFFE65100L),
    listOf(0xFF004D40L, 0xFF263238L),
    listOf(0xFF6A1B9AL, 0xFFAD1457L),
    listOf(0xFF0D47A1L, 0xFF01579BL),
    listOf(0xFFE65100L, 0xFFBF360CL),
    listOf(0xFFAD1457L, 0xFF880E4FL),
    listOf(0xFF263238L, 0xFF000000L),
    listOf(0xFFFF7AB6L, 0xFFB85AC1L),
    listOf(0xFFFFC36BL, 0xFFE65100L)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val repo = remember { QuoteRepository() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val privacyStore = remember { PrivacyPreferenceStore.get(context) }
    val defaultVisibility = remember { privacyStore.defaultVisibility }

    var text by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(QuoteStyle.Default) }
    var visibility by remember { mutableStateOf(defaultVisibility) }
    var selectedTab by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val previewQuote = remember(text, author, style) {
        Quote(
            id = "preview",
            text = text.ifBlank { "Your quote will appear here." },
            author = author,
            style = style,
            isCustom = true
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("New quote", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (text.isBlank() || saving) return@IconButton
                            saving = true
                            error = null
                            scope.launch {
                                val res = repo.saveQuote(text, author, style, visibility)
                                saving = false
                                res.onSuccess { onSaved() }
                                    .onFailure { error = it.message ?: "Couldn't save" }
                            }
                        },
                        enabled = text.isNotBlank() && !saving
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Save",
                                tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                QuoteBackgroundImage(quote = previewQuote, modifier = Modifier.fillMaxSize())
                QuoteContent(
                    quote = previewQuote,
                    modifier = Modifier.fillMaxSize()
                )
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Text") },
                    icon = { Icon(Icons.Rounded.TextFields, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Style") },
                    icon = { Icon(Icons.Rounded.Palette, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Background") },
                    icon = { Icon(Icons.Rounded.Image, contentDescription = null) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> TextTab(
                        text = text, onTextChange = { text = it },
                        author = author, onAuthorChange = { author = it },
                        style = style, onStyleChange = { style = it },
                        visibility = visibility, onVisibilityChange = { visibility = it }
                    )
                    1 -> StyleTab(
                        style = style,
                        onStyleChange = { style = it }
                    )
                    2 -> BackgroundTab(
                        style = style,
                        onStyleChange = { style = it }
                    )
                }
            }
        }
    }
}

// -------------------- TEXT TAB --------------------

@Composable
private fun TextTab(
    text: String,
    onTextChange: (String) -> Unit,
    author: String,
    onAuthorChange: (String) -> Unit,
    style: QuoteStyle,
    onStyleChange: (QuoteStyle) -> Unit,
    visibility: String,
    onVisibilityChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LabeledRow("Visibility") {
            VisibilityToggle(
                selected = visibility,
                onPick = onVisibilityChange
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Quote") },
            placeholder = { Text("Type your quote…") },
            shape = RoundedCornerShape(14.dp),
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
            colors = glassFieldColors()
        )
        OutlinedTextField(
            value = author,
            onValueChange = onAuthorChange,
            label = { Text("Author (optional)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = glassFieldColors()
        )

        LabeledRow("Alignment") {
            AlignmentButtons(
                selected = style.alignment,
                onPick = { onStyleChange(style.copy(alignment = it)) }
            )
        }

        LabeledRow("Font size · ${style.fontSize}sp") {
            Slider(
                value = style.fontSize.toFloat(),
                valueRange = 16f..48f,
                onValueChange = { onStyleChange(style.copy(fontSize = it.toInt())) },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VisibilityToggle(
    selected: String,
    onPick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VisibilityOption(
            icon = Icons.Rounded.Lock,
            label = "Private",
            sublabel = "Only you",
            selected = selected == Quote.VISIBILITY_PRIVATE,
            onClick = { onPick(Quote.VISIBILITY_PRIVATE) },
            modifier = Modifier.weight(1f)
        )
        VisibilityOption(
            icon = Icons.Rounded.Public,
            label = "Public",
            sublabel = "Everyone",
            selected = selected == Quote.VISIBILITY_PUBLIC,
            onClick = { onPick(Quote.VISIBILITY_PUBLIC) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VisibilityOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else Color.White.copy(alpha = 0.15f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val subFg = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            sublabel,
            color = subFg,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AlignmentButtons(
    selected: String,
    onPick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AlignmentButton(
            icon = Icons.AutoMirrored.Rounded.FormatAlignLeft,
            label = "Left",
            selected = selected == QuoteStyle.ALIGN_START,
            onClick = { onPick(QuoteStyle.ALIGN_START) },
            modifier = Modifier.weight(1f)
        )
        AlignmentButton(
            icon = Icons.Rounded.FormatAlignCenter,
            label = "Center",
            selected = selected == QuoteStyle.ALIGN_CENTER,
            onClick = { onPick(QuoteStyle.ALIGN_CENTER) },
            modifier = Modifier.weight(1f)
        )
        AlignmentButton(
            icon = Icons.AutoMirrored.Rounded.FormatAlignRight,
            label = "Right",
            selected = selected == QuoteStyle.ALIGN_END,
            onClick = { onPick(QuoteStyle.ALIGN_END) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AlignmentButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = fg)
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

// -------------------- STYLE TAB --------------------

@Composable
private fun StyleTab(
    style: QuoteStyle,
    onStyleChange: (QuoteStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LabeledRow("Text color") {
            ColorRow(
                palette = TextColorPalette,
                selected = style.textColor,
                onPick = { onStyleChange(style.copy(textColor = it)) }
            )
        }

        LabeledRow("Weight & emphasis") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleChip(
                    icon = Icons.Rounded.FormatBold,
                    label = "Bold",
                    selected = style.bold,
                    onClick = { onStyleChange(style.copy(bold = !style.bold)) }
                )
                ToggleChip(
                    icon = Icons.Rounded.FormatItalic,
                    label = "Italic",
                    selected = style.italic,
                    onClick = { onStyleChange(style.copy(italic = !style.italic)) }
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ColorRow(
    palette: List<Long>,
    selected: Long?,
    onPick: (Long) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(palette) { color ->
            val isSelected = selected == color
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color.toInt()))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable { onPick(color) }
            )
        }
    }
}

// -------------------- BACKGROUND TAB --------------------

@Composable
private fun BackgroundTab(
    style: QuoteStyle,
    onStyleChange: (QuoteStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LabeledRow("Background type") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BgTypeChip("Gradient", style.bgType == BackgroundType.Gradient) {
                    onStyleChange(
                        style.copy(
                            backgroundType = QuoteStyle.BG_GRADIENT,
                            backgroundColors = style.backgroundColors.takeIf { it.size >= 2 }
                                ?: GradientPalette.first(),
                            backgroundImageUrl = null
                        )
                    )
                }
                BgTypeChip("Solid", style.bgType == BackgroundType.Solid) {
                    onStyleChange(
                        style.copy(
                            backgroundType = QuoteStyle.BG_SOLID,
                            backgroundColors = listOf(style.backgroundColors.firstOrNull() ?: SolidPalette.first()),
                            backgroundImageUrl = null
                        )
                    )
                }
                BgTypeChip("Image", style.bgType == BackgroundType.Image) {
                    onStyleChange(style.copy(backgroundType = QuoteStyle.BG_IMAGE))
                }
            }
        }

        when (style.bgType) {
            BackgroundType.Gradient -> GradientPicker(
                selected = style.backgroundColors,
                onPick = { onStyleChange(style.copy(backgroundColors = it)) }
            )
            BackgroundType.Solid -> ColorRow(
                palette = SolidPalette,
                selected = style.backgroundColors.firstOrNull(),
                onPick = { onStyleChange(style.copy(backgroundColors = listOf(it))) }
            )
            BackgroundType.Image -> ImagePicker(
                selectedUrl = style.backgroundImageUrl,
                onPick = { onStyleChange(style.copy(backgroundImageUrl = it)) }
            )
        }
    }
}

@Composable
private fun BgTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        label,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun GradientPicker(
    selected: List<Long>,
    onPick: (List<Long>) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(GradientPalette) { gradient ->
            val isSelected = gradient == selected
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(gradient.map { Color(it.toInt()) }))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onPick(gradient) }
            )
        }
    }
}

@Composable
private fun ImagePicker(
    selectedUrl: String?,
    onPick: (String) -> Unit
) {
    val config = LocalAppConfig.current
    var query by remember { mutableStateOf(config.unsplashQuery) }
    var debouncedQuery by remember { mutableStateOf(query) }
    var photos by remember { mutableStateOf<List<UnsplashPhoto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        delay(400)
        debouncedQuery = query.trim()
    }

    LaunchedEffect(debouncedQuery, config.unsplashAccessKey) {
        if (config.unsplashAccessKey.isBlank()) {
            error = "Set unsplashAccessKey in Firestore config/app to browse images."
            return@LaunchedEffect
        }
        if (debouncedQuery.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        UnsplashClient.search(debouncedQuery, config.unsplashAccessKey, perPage = 20)
            .onSuccess { photos = it }
            .onFailure { error = it.message ?: "Failed to load images" }
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search Unsplash") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(),
            shape = RoundedCornerShape(14.dp),
            colors = glassFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )

            photos.isEmpty() -> Text(
                "No images yet — type a search above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    val isSelected = selectedUrl == photo.regularUrl
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onPick(photo.regularUrl) }
                    ) {
                        AsyncImage(
                            model = photo.smallUrl,
                            contentDescription = photo.description,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// -------------------- shared bits --------------------

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun glassFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Color.White.copy(alpha = 0.18f),
    focusedContainerColor = Color.White.copy(alpha = 0.28f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
    focusedBorderColor = MaterialTheme.colorScheme.primary
)
