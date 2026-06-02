package com.kobser.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.kobser.app.ui.components.MarqueeText
import com.kobser.app.ui.components.YtDownloadDialog
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kobser.app.playback.RepeatMode
import com.kobser.app.playback.isPreview

private fun fmtTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedPlayerScreen(
    onClose: () -> Unit,
    viewModel: ExpandedPlayerViewModel = hiltViewModel(),
) {
    val player = viewModel.musicPlayer
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isLiked by player.isCurrentLiked.collectAsState()
    val shuffleOn by player.shuffleOn.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val progress by player.progress.collectAsState()

    // Auto-close if the queue empties (e.g., user deletes the current track).
    LaunchedEffect(song) { if (song == null) onClose() }
    val currentSong = song ?: return
    val isPreview = currentSong.isPreview()

    val coverUrl = remember(currentSong.coverArt) { currentSong.coverArt?.let { viewModel.getCoverUrl(it, 1024) } }

    var menuOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    var queueSheetOpen by remember { mutableStateOf(false) }
    var downloadDialogOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Blurred backdrop
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(48.dp),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "NOW PLAYING",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("View queue") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            onClick = {
                                menuOpen = false
                                queueSheetOpen = true
                            },
                        )
                        if (isPreview) {
                            DropdownMenuItem(
                                text = { Text("Download") },
                                leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = {
                                    menuOpen = false
                                    downloadDialogOpen = true
                                },
                            )
                        }
                        if (!isPreview) {
                            DropdownMenuItem(
                                text = { Text("Delete from library") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = {
                                    menuOpen = false
                                    deleteConfirmOpen = true
                                },
                            )
                        }
                    }
                }
            }

            // ── Album art ─────────────────────────────────────────────────
            Spacer(Modifier.weight(1f))
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.weight(1f))

            // ── Title + artist + like ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = currentSong.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    MarqueeText(
                        text = currentSong.artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                    )
                }
                if (!isPreview) {
                    IconButton(onClick = { player.toggleLike() }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // ── Progress ──────────────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            val frac = if (progress.durationMs > 0)
                (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
            else 0f
            Slider(
                value = frac,
                onValueChange = { player.seekFraction(it) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = fmtTime(progress.positionMs),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = fmtTime(progress.durationMs),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }

            // ── Controls ──────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleOn) MaterialTheme.colorScheme.primary
                               else Color.White.copy(alpha = 0.7f),
                    )
                }
                IconButton(
                    onClick = { player.prev() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                FilledIconButton(
                    onClick = { player.togglePlayPause() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(
                    onClick = { player.next() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { player.toggleRepeat() }) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne
                                      else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                               else Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ── Queue sheet ───────────────────────────────────────────────────────
    if (queueSheetOpen) {
        QueueSheet(onDismiss = { queueSheetOpen = false })
    }

    // ── Download dialog (preview tracks only) ─────────────────────────────
    if (downloadDialogOpen) {
        YtDownloadDialog(
            initialArtist = currentSong.artist,
            initialTitle = currentSong.title,
            initialAlbum = currentSong.album.orEmpty(),
            onConfirm = { artist, title, album ->
                viewModel.downloadPreview(artist, title, album)
                downloadDialogOpen = false
            },
            onDismiss = { downloadDialogOpen = false },
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────
    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("Delete from library?") },
            text = { Text("\"${currentSong.title}\" will be permanently removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmOpen = false
                    viewModel.deleteCurrentTrack { result ->
                        if (result.isFailure) {
                            // Will be replaced by app-wide snackbar in Phase 5.
                            // For now the inline SnackbarHost is fine.
                        }
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }
}
