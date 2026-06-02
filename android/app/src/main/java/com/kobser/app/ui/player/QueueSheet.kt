package com.kobser.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kobser.app.data.api.Song
import com.kobser.app.ui.components.NowPlayingBars
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    onDismiss: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val player = viewModel.musicPlayer
    val queue by player.queue.collectAsState()
    val currentIndex by player.currentIndex.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasUpcoming = queue.size > (currentIndex + 1).coerceAtLeast(0)

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        player.moveInQueue(from.index, to.index)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { player.clearUpcoming() },
                    enabled = hasUpcoming,
                ) { Text("Clear upcoming") }
            }
            Divider()

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Queue is empty",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { _, song -> System.identityHashCode(song) },
                    ) { index, song ->
                        ReorderableItem(reorderableState, key = System.identityHashCode(song)) { _ ->
                            // draggableHandle() resolves on the reorderable item scope here.
                            QueueRow(
                                song = song,
                                isCurrent = index == currentIndex,
                                isPlayingNow = index == currentIndex && isPlaying,
                                getCoverUrl = { viewModel.getCoverUrl(it) },
                                onClick = { player.jumpTo(index) },
                                onRemove = { player.removeFromQueue(index) },
                                handleModifier = Modifier.draggableHandle(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    isPlayingNow: Boolean,
    getCoverUrl: (String) -> String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    handleModifier: Modifier,
) {
    val coverUrl = remember(song.coverArt) { song.coverArt?.let { getCoverUrl(it) } }

    // Swipe in either direction removes the track. No background/icon — the row
    // just slides away (matches the request: swipe to delete, no delete button).
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onRemove()
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        content = {
            QueueRowContent(song, coverUrl, isCurrent, isPlayingNow, onClick, handleModifier)
        },
    )
}

@Composable
private fun QueueRowContent(
    song: Song,
    coverUrl: String?,
    isCurrent: Boolean,
    isPlayingNow: Boolean,
    onClick: () -> Unit,
    handleModifier: Modifier,
) {
    // Opaque background so a swiped row slides cleanly over the sheet (and the
    // currently-playing row gets a subtle tint).
    val bgColor =
        if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left drag handle — grab and drag to reorder.
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = handleModifier
                .padding(start = 8.dp, end = 4.dp)
                .size(28.dp),
        )
        Box(modifier = Modifier.size(44.dp)) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlayingNow) {
                        NowPlayingBars(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(16.dp),
                        )
                    } else {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "Now playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
