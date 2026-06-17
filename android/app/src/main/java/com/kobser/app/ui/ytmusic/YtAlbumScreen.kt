package com.kobser.app.ui.ytmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
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
import com.kobser.app.data.api.YtAlbumTrack
import com.kobser.app.ui.components.YtDownloadDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtAlbumScreen(
    onBack: () -> Unit,
    viewModel: YtAlbumViewModel = hiltViewModel(),
) {
    var dlTrack by remember { mutableStateOf<YtAlbumTrack?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(viewModel.album?.title ?: "Album", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                viewModel.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                viewModel.error != null -> Text(
                    viewModel.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                viewModel.album != null -> {
                    val album = viewModel.album!!
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        // Header
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                AsyncImage(
                                    model = album.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text(
                                        album.type.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    )
                                    Text(
                                        album.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val meta = buildString {
                                        append(album.artist)
                                        if (album.year.isNotBlank()) append(" · ${album.year}")
                                        append(" · ${album.tracks.size} tracks")
                                    }
                                    Text(
                                        meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    )
                                }
                            }
                        }

                        // Download album button
                        item {
                            Button(
                                onClick = { viewModel.downloadAlbum() },
                                enabled = !viewModel.downloadingAll,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                if (viewModel.downloadingAll) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Queuing…")
                                } else {
                                    Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download album")
                                }
                            }
                        }

                        item { Spacer(Modifier.height(8.dp)) }

                        // Tracklist
                        items(album.tracks, key = { it.videoId }) { track ->
                            TrackRow(
                                track = track,
                                index = album.tracks.indexOf(track) + 1,
                                state = viewModel.downloadStates[track.videoId],
                                onPlay = { viewModel.playPreview(track) },
                                onDownload = { dlTrack = track },
                            )
                        }
                    }
                }
            }
        }
    }

    dlTrack?.let { track ->
        YtDownloadDialog(
            initialArtist = track.artist,
            initialTitle = track.title,
            initialAlbum = viewModel.album?.title ?: "",
            onConfirm = { artist, title, album ->
                viewModel.download(track.videoId, artist, title, album)
                dlTrack = null
            },
            onDismiss = { dlTrack = null },
        )
    }

    viewModel.duplicateWarning?.let { w ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicateWarning() },
            title = { Text("Already in library") },
            text = { Text("\"${w.title}\" by ${w.artist} might already be in your library. Download anyway?") },
            confirmButton = {
                TextButton(onClick = { viewModel.forceDownload() }) { Text("Download anyway") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicateWarning() }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TrackRow(
    track: YtAlbumTrack,
    index: Int,
    state: YtDownloadState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (track.trackNumber.takeIf { it > 0 } ?: index).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (track.duration > 0) {
            Text(
                fmtDuration(track.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.width(4.dp))
        }
        DownloadButton(state, onDownload)
    }
}
