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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val shuffleOn by player.shuffleOn.collectAsState()
    val playOrder by player.playOrder.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // With shuffle on, show the order tracks will actually play in. Each row keeps
    // its real queue index so tap/remove act on the right track; reordering by
    // drag only makes sense for the linear queue.
    val rows: List<Pair<Int, Song>> = remember(queue, playOrder, shuffleOn) {
        val order = if (shuffleOn && playOrder.size == queue.size) playOrder else queue.indices.toList()
        order.mapNotNull { i -> queue.getOrNull(i)?.let { i to it } }
    }
    val currentPos = rows.indexOfFirst { it.first == currentIndex }
    val hasUpcoming = currentPos >= 0 && currentPos < rows.size - 1

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
            if (shuffleOn) {
                Text(
                    text = "Shuffle is on — showing play order",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider()

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
                        items = rows,
                        key = { _, (_, song) -> System.identityHashCode(song) },
                    ) { _, (index, song) ->
                        ReorderableItem(
                            reorderableState,
                            key = System.identityHashCode(song),
                            enabled = !shuffleOn,
                        ) { _ ->
                            // draggableHandle() resolves on the reorderable item scope here.
                            QueueRow(
                                song = song,
                                isCurrent = index == currentIndex,
                                isPlayingNow = index == currentIndex && isPlaying,
                                getCoverUrl = { viewModel.getCoverUrl(it) },
                                onClick = { player.jumpTo(index) },
                                onRemove = { player.removeFromQueue(index) },
                                handleModifier = if (shuffleOn) Modifier else Modifier.draggableHandle(),
                                showHandle = !shuffleOn,
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
    showHandle: Boolean = true,
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
            QueueRowContent(song, coverUrl, isCurrent, isPlayingNow, onClick, handleModifier, showHandle)
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
    showHandle: Boolean,
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
        // Left drag handle — grab and drag to reorder (linear queue only).
        if (showHandle) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = handleModifier
                    .padding(start = 8.dp, end = 4.dp)
                    .size(28.dp),
            )
        } else {
            Spacer(Modifier.width(16.dp))
        }
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
