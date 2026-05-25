package com.kobser.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kobser.app.data.api.Song
import com.kobser.app.ui.components.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    isFavorites: Boolean = false,
    onSongClick: (Song) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val items = if (isFavorites) viewModel.favorites else viewModel.songs

    var deleteTarget by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isFavorites) "Favorites" else "Library",
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    if (!isFavorites) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.error != null) {
                Text(
                    text = viewModel.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (items.isEmpty()) {
                Text(
                    text = if (isFavorites) "No favorites yet" else "Library is empty",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn {
                    itemsIndexed(items, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            isStarred = viewModel.isStarred(song, defaultStarred = isFavorites),
                            getCoverUrl = { viewModel.getCoverArtUrl(it) },
                            onClick = {
                                viewModel.playFromList(items, index)
                                onSongClick(song)
                            },
                            onToggleStar = {
                                viewModel.toggleStar(song, defaultStarred = isFavorites)
                            },
                            onAddToQueue = { viewModel.addToQueue(song) },
                            onPlay = {
                                viewModel.playFromList(items, index)
                                onSongClick(song)
                            },
                            onDelete = { deleteTarget = song },
                        )
                    }
                }
            }
        }
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
