package com.kobser.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kobser.app.data.api.Song
import com.kobser.app.ui.components.AddToPlaylistSheet
import com.kobser.app.ui.components.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    isFavorites: Boolean = false,
    onSongClick: (Song) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val items by remember(isFavorites) {
        derivedStateOf { if (isFavorites) viewModel.filteredFavorites else viewModel.filteredSongs }
    }

    var deleteTarget by remember { mutableStateOf<Song?>(null) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            if (searchOpen) {
                TopAppBar(
                    title = {
                        TextField(
                            value = viewModel.filterQuery,
                            onValueChange = { viewModel.filterQuery = it },
                            placeholder = { Text("Search songs…") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            searchOpen = false
                            viewModel.filterQuery = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            if (isFavorites) "Favorites" else "Library",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { padding ->
        val pullState = rememberPullToRefreshState()

        PullToRefreshBox(
            isRefreshing = viewModel.isLoading,
            onRefresh = { viewModel.loadAll() },
            state = pullState,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (viewModel.isLoading && items.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.error != null && items.isEmpty()) {
                Text(
                    text = viewModel.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (items.isEmpty()) {
                Text(
                    text = when {
                        viewModel.filterQuery.isNotBlank() -> "No results for \"${viewModel.filterQuery}\""
                        isFavorites -> "No favorites yet"
                        else -> "Library is empty"
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (!isFavorites && viewModel.filterQuery.isBlank()) {
                        item {
                            PlayAllHeader(
                                onPlayAll = { viewModel.playFromList(items, 0) },
                                onShuffle = {
                                    val shuffled = items.shuffled()
                                    viewModel.playFromList(shuffled, 0)
                                },
                            )
                        }
                    }
                    itemsIndexed(items, key = { _, song -> song.id }) { index, song ->
                        val isStarred = viewModel.isStarred(song, defaultStarred = isFavorites)

                        val onToggleStar = remember(song) {
                            { viewModel.toggleStar(song, defaultStarred = isFavorites) }
                        }
                        val onAddToQueue = remember(song) { { viewModel.addToQueue(song) } }
                        val onClick = remember(index) {
                            {
                                viewModel.playFromList(items, index)
                                onSongClick(song)
                            }
                        }
                        val onDelete = remember(song) { { deleteTarget = song } }
                        val onAddToPlaylist = remember(song) { { playlistTarget = song } }

                        SongRow(
                            song = song,
                            isStarred = isStarred,
                            getCoverUrl = { viewModel.getCoverArtUrl(it) },
                            onClick = onClick,
                            onToggleStar = onToggleStar,
                            onAddToQueue = onAddToQueue,
                            onPlay = onClick,
                            onDelete = onDelete,
                            onAddToPlaylist = onAddToPlaylist,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) focusRequester.requestFocus()
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
}

@Composable
private fun PlayAllHeader(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
}
