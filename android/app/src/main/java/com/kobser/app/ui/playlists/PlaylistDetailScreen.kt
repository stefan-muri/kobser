package com.kobser.app.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kobser.app.data.api.Song
import com.kobser.app.ui.components.AddToPlaylistSheet
import com.kobser.app.ui.components.MenuAction
import com.kobser.app.ui.components.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist = viewModel.playlist
    var deleteTarget by remember { mutableStateOf<Song?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlist?.name ?: "Playlist",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadLibrarySongs()
                        pickerOpen = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add songs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = viewModel.isLoading,
            onRefresh = { viewModel.load() },
            state = pullState,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                viewModel.isLoading && playlist == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                viewModel.error != null -> Text(
                    text = viewModel.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                playlist == null -> Text(
                    text = "Playlist not found",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
                playlist.entry.orEmpty().isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No tracks yet.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        viewModel.loadLibrarySongs()
                        pickerOpen = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add songs")
                    }
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        PlaylistHeader(
                            songCount = playlist.entry.orEmpty().size,
                            onPlayAll = { viewModel.playAll() },
                            onShuffle = { viewModel.playShuffled() },
                        )
                    }
                    itemsIndexed(
                        items = playlist.entry.orEmpty(),
                        key = { idx, song -> "${idx}_${song.id}" },
                    ) { index, song ->
                        val isStarred = remember(song.id, viewModel.starredOverrides) {
                            viewModel.isStarred(song)
                        }
                        val onToggleStar = remember(song) { { viewModel.toggleStar(song) } }
                        val onAddToQueue = remember(song) { { viewModel.addToQueue(song) } }
                        val onClick = remember(index) { { viewModel.playFromIndex(index) } }
                        val onDelete = remember(song) { { deleteTarget = song } }
                        val onAddToPlaylist = remember(song) { { playlistTarget = song } }
                        val extraItems = remember(index) {
                            listOf(
                                MenuAction(
                                    label = "Remove from playlist",
                                    icon = Icons.Default.RemoveCircleOutline,
                                    onClick = { viewModel.removeFromPlaylist(index) },
                                ),
                            )
                        }

                        SongRow(
                            song = song,
                            isStarred = isStarred,
                            getCoverUrl = { viewModel.getCoverUrl(it) },
                            onClick = onClick,
                            onToggleStar = onToggleStar,
                            onAddToQueue = onAddToQueue,
                            onPlay = onClick,
                            onDelete = onDelete,
                            onAddToPlaylist = onAddToPlaylist,
                            extraMenuItems = extraItems,
                        )
                    }
                }
            }
        }
    }

    playlistTarget?.let { song ->
        AddToPlaylistSheet(song = song, onDismiss = { playlistTarget = null })
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete from library?") },
            text = {
                Text("\"${target.title}\" will be permanently removed. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = target
                    deleteTarget = null
                    viewModel.deleteSong(toDelete)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (pickerOpen) {
        SongPickerSheet(
            songs = viewModel.allLibrarySongs,
            isLoading = viewModel.pickerLoading,
            onConfirm = { ids ->
                viewModel.addSongs(ids) { pickerOpen = false }
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun PlaylistHeader(
    songCount: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "$songCount track${if (songCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onPlayAll, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play all")
            }
            OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongPickerSheet(
    songs: List<Song>,
    isLoading: Boolean,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(filter, songs) {
        if (filter.isBlank()) songs
        else songs.filter {
            it.title.contains(filter, ignoreCase = true) ||
                it.artist.contains(filter, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 700.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Add songs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${selected.size} selected",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Search by title or artist…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Box(modifier = Modifier.weight(1f, fill = false).heightIn(max = 480.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filtered, key = { it.id }) { song ->
                            val isSelected = song.id in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (isSelected) selected - song.id
                                                   else selected + song.id
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selected = if (isSelected) selected - song.id
                                                   else selected + song.id
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium,
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
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                ) { Text("Add ${selected.size}") }
            }
        }
    }
}
