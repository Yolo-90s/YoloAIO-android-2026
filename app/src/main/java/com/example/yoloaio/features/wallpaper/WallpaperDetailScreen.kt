package com.example.yoloaio.features.wallpaper

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.ui.components.FeatureScaffold
import kotlinx.coroutines.launch

@Composable
fun WallpaperDetailScreen(
    wallpaperId: String,
    onBack: () -> Unit,
    onRelatedClick: (String) -> Unit
) {
    val context = LocalContext.current
    val config = LocalAppConfig.current
    val photo = remember(wallpaperId) { WallpaperCache.byId(wallpaperId) }
    val scope = rememberCoroutineScope()
    val favRepo = remember { WallpaperFavoritesRepository() }
    var isFavorite by remember(wallpaperId) { mutableStateOf(false) }
    var related by remember(wallpaperId) { mutableStateOf<List<UnsplashPhoto>>(emptyList()) }
    var loadingRelated by remember(wallpaperId) { mutableStateOf(true) }

    LaunchedEffect(wallpaperId) {
        isFavorite = favRepo.isFavorited(wallpaperId)
    }

    LaunchedEffect(wallpaperId, config.unsplashAccessKey) {
        if (photo == null || config.unsplashAccessKey.isBlank()) {
            loadingRelated = false
            return@LaunchedEffect
        }
        loadingRelated = true
        val query = photo.description.takeIf { it.isNotBlank() }
            ?: photo.authorName.takeIf { it.isNotBlank() }
            ?: "wallpaper"
        UnsplashClient.search(
            query = query,
            accessKey = config.unsplashAccessKey,
            perPage = 18,
            orientation = WallpaperOrientation.Portrait
        )
            .onSuccess { results ->
                val filtered = results.filter { it.id != wallpaperId }
                WallpaperCache.merge(filtered)
                related = filtered
            }
            .onFailure { related = emptyList() }
        loadingRelated = false
    }

    if (photo == null) {
        FeatureScaffold(title = "Wallpaper", onBack = onBack) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Wallpaper not loaded — go back and reopen.")
            }
        }
        return
    }

    var showApplyDialog by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    FeatureScaffold(title = "Wallpaper", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Main image with floating actions on its top-right
            val previewRatio = photo.aspectRatio.coerceIn(0.5f, 1.5f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .aspectRatio(previewRatio)
            ) {
                AsyncImage(
                    model = photo.regularUrl,
                    contentDescription = photo.description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FloatingAction(
                        icon = if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                        contentDescription = if (isFavorite) "Remove from favorites"
                        else "Add to favorites",
                        onClick = {
                            scope.launch {
                                val res = if (isFavorite) favRepo.removeFavorite(photo.id)
                                else favRepo.addFavorite(photo)
                                res.onSuccess { isFavorite = !isFavorite }
                            }
                        }
                    )
                    FloatingAction(
                        icon = Icons.Rounded.Wallpaper,
                        contentDescription = "Apply",
                        loading = applying,
                        onClick = { showApplyDialog = true }
                    )
                    FloatingAction(
                        icon = Icons.Rounded.Share,
                        contentDescription = "Share",
                        loading = sharing,
                        onClick = {
                            sharing = true
                            scope.launch {
                                WallpaperShare.share(context, photo)
                                sharing = false
                            }
                        }
                    )
                }
            }

            // Photo info row
            if (photo.description.isNotBlank() || photo.authorName.isNotBlank()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    if (photo.description.isNotBlank()) {
                        Text(
                            photo.description.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                    }
                    Text(
                        buildString {
                            if (photo.authorName.isNotBlank()) append("Photo by ").append(photo.authorName)
                            if (photo.width > 0 && photo.height > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("${photo.width}×${photo.height}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // More like this
            Text(
                "More like this",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))

            when {
                loadingRelated && related.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }

                related.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (config.unsplashAccessKey.isBlank())
                            "Set unsplashAccessKey in Firestore config to see related wallpapers."
                        else "Nothing similar found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(related, key = { it.id }) { rel ->
                        RelatedThumb(photo = rel, onClick = { onRelatedClick(rel.id) })
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        if (showApplyDialog) {
            TargetChooser(
                onDismiss = { showApplyDialog = false },
                onPick = { target ->
                    showApplyDialog = false
                    applying = true
                    scope.launch {
                        val result = WallpaperApplier.applyFromUrl(
                            context = context,
                            url = photo.fullUrl,
                            target = target
                        )
                        applying = false
                        val msg = if (result.isSuccess) "Wallpaper set successfully"
                        else "Couldn't set wallpaper: ${result.exceptionOrNull()?.message ?: "unknown"}"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun FloatingAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = Color.White,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = tint,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun RelatedThumb(photo: UnsplashPhoto, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = photo.smallUrl,
            contentDescription = photo.description.ifBlank { "Wallpaper" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun TargetChooser(
    onDismiss: () -> Unit,
    onPick: (WallpaperTarget) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set wallpaper", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetOption(Icons.Rounded.Home, "Home screen") { onPick(WallpaperTarget.Home) }
                TargetOption(Icons.Rounded.Lock, "Lock screen") { onPick(WallpaperTarget.Lock) }
                TargetOption(Icons.Rounded.Wallpaper, "Both") { onPick(WallpaperTarget.Both) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TargetOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
    }
}
