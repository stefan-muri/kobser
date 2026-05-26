package com.kobser.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { idx, song -> "${idx}_${song.id}" },
                    ) { index, song ->
                        QueueRow(
                            song = song,
                            isCurrent = index == currentIndex,
                            isPlayingNow = index == currentIndex && isPlaying,
                            isUpcoming = index > currentIndex,
                            getCoverUrl = { viewModel.getCoverUrl(it) },
                            onClick = { player.jumpTo(index) },
                            onRemove = { player.removeFromQueue(index) },
                        )
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
    isUpcoming: Boolean,
    getCoverUrl: (String) -> String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val coverUrl = remember(song.coverArt) { song.coverArt?.let { getCoverUrl(it) } }

    // Swipe-to-remove only for upcoming tracks; the currently playing track
    // can't be removed (matches the web's removeFromQueue guard).
    if (isUpcoming) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart ||
                    value == SwipeToDismissBoxValue.StartToEnd) {
                    onRemove()
                    true
                } else false
            },
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            },
            content = {
                QueueRowContent(
                    song = song,
                    coverUrl = coverUrl,
                    isCurrent = isCurrent,
                    isPlayingNow = isPlayingNow,
                    onClick = onClick,
                )
            },
        )
    } else {
        QueueRowContent(
            song = song,
            coverUrl = coverUrl,
            isCurrent = isCurrent,
            isPlayingNow = isPlayingNow,
            onClick = onClick,
        )
    }
}

@Composable
private fun QueueRowContent(
    song: Song,
    coverUrl: String?,
    isCurrent: Boolean,
    isPlayingNow: Boolean,
    onClick: () -> Unit,
) {
    val bgColor =
        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            if (isPlayingNow) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Now playing",
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
