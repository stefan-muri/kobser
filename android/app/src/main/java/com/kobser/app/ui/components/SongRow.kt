package com.kobser.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.kobser.app.data.api.Song

/**
 * Reusable song row used across Library, Favorites, Album detail, and Playlist detail.
 *
 * Each screen wires its own callbacks for the actions — typically delegating to a
 * ViewModel that talks to LibraryRepository + MusicPlayer.
 */
@Composable
fun SongRow(
    song: Song,
    isStarred: Boolean,
    getCoverUrl: (String) -> String,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    extraMenuItems: List<MenuAction> = emptyList(),
) {
    val coverUrl = remember(song.coverArt) { song.coverArt?.let { getCoverUrl(it) } }

    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp) // Fixed height helps LazyColumn measure items
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onToggleStar,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = if (isStarred) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isStarred) "Unlike" else "Like",
                tint = if (isStarred) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                // ... (menu items same as before)
                DropdownMenuItem(
                    text = { Text("Play") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                    onClick = { menuOpen = false; onPlay() },
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    onClick = { menuOpen = false; onAddToQueue() },
                )
                if (onAddToPlaylist != null) {
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        onClick = { menuOpen = false; onAddToPlaylist() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (isStarred) "Unlike" else "Like") },
                    leadingIcon = {
                        Icon(
                            if (isStarred) Icons.Default.HeartBroken else Icons.Default.Favorite,
                            null,
                        )
                    },
                    onClick = { menuOpen = false; onToggleStar() },
                )
                extraMenuItems.fastForEach { extra ->
                    DropdownMenuItem(
                        text = { Text(extra.label) },
                        leadingIcon = extra.icon?.let { { Icon(it, null) } },
                        onClick = { menuOpen = false; extra.onClick() },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            "Delete from library",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

data class MenuAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val onClick: () -> Unit,
)
