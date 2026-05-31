package com.example.yoloaio.features.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Netflix-style horizontal poster strip — one per row in the Movies
 * Browse view. Each instance fetches its own first page (~20 titles)
 * via the [fetchRow] lambda and renders them as a horizontally
 * scrolling LazyRow.
 *
 * - "See all →" launches the full-list grid mode in the parent.
 * - Rows that error out or return zero items hide themselves entirely
 *   rather than showing a sad placeholder — keeps the landing page
 *   from looking broken when one genre has no results.
 * - Skeleton tiles render during the initial fetch so the row
 *   doesn't pop in.
 */
@Composable
fun CategoryRow(
    title: String,
    fetchRow: suspend () -> Result<List<TmdbTitle>>,
    onSelect: (TmdbTitle) -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    var state by remember(title) { mutableStateOf<RowState>(RowState.Loading) }
    val listState = rememberLazyListState()

    LaunchedEffect(title) {
        // The `title` key here is the cache-bust signal. Re-creating the
        // row (e.g. when the user switches Movies/TV) re-runs the effect.
        fetchRow()
            .onSuccess { items ->
                state = if (items.isEmpty()) RowState.Empty else RowState.Ready(items)
            }
            .onFailure {
                state = RowState.Failed
            }
    }

    // Hide empty / failed rows entirely.
    if (state is RowState.Empty || state is RowState.Failed) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null && state is RowState.Ready) {
            TextButton(onClick = onSeeAll) {
                Text("See all", style = MaterialTheme.typography.labelMedium)
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        when (val s = state) {
            is RowState.Loading -> items(items = (1..8).toList()) {
                SkeletonTile()
            }
            is RowState.Ready -> items(items = s.titles, key = { "${it.mediaType}-${it.id}" }) {
                Box(modifier = Modifier.width(120.dp)) {
                    // Reuse the existing tile renderer — same look as the
                    // grid view, just shrunk to fit row scroll.
                    TitleCard(title = it, onClick = { onSelect(it) })
                }
            }
            else -> Unit
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun SkeletonTile() {
    Box(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
    )
}

private sealed interface RowState {
    data object Loading : RowState
    data class Ready(val titles: List<TmdbTitle>) : RowState
    data object Empty : RowState
    data object Failed : RowState
}
