package com.kobser.app.ui.offline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.kobser.app.offline.OfflineCollection
import com.kobser.app.offline.OfflineProgress
import com.kobser.app.ui.components.NowPlayingBars

/** Albums and playlists kept on the phone; works without the server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineScreen(viewModel: OfflineViewModel = hiltViewModel()) {
    val selected = viewModel.selected
    BackHandler(enabled = selected != null) { viewModel.select(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selected?.title ?: "Offline music",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = { viewModel.select(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selected != null) {
                        IconButton(onClick = { viewModel.remove(selected.key) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove from offline")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selected != null) {
                CollectionDetail(selected, viewModel)
            } else {
                CollectionList(viewModel)
            }
        }
    }
}

@Composable
private fun CollectionList(viewModel: OfflineViewModel) {
    val collections = viewModel.catalog.collections.sortedByDescending { it.pinnedAt }
    val all = remember(viewModel.catalog) { viewModel.catalog.allSongs() }
    if (collections.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text("Nothing kept offline yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Open an album or playlist and tap “Keep offline”. It will play here without a connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { viewModel.play(all, 0) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play all")
                }
                OutlinedButton(onClick = { viewModel.playShuffled(all) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
            }
            HorizontalDivider()
        }
        itemsIndexed(collections, key = { _, c -> c.key }) { _, c ->
            CollectionRow(c, viewModel.progress[c.key], viewModel::getCoverUrl) { viewModel.select(c) }
        }
    }
}

@Composable
private fun CollectionRow(
    c: OfflineCollection,
    progress: OfflineProgress?,
    getCoverUrl: (String) -> String,
    onClick: () -> Unit,
) {
    val coverUrl = remember(c.coverArt) { c.coverArt?.let(getCoverUrl) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when {
                progress?.running == true -> "Downloading ${progress.done}/${progress.total}"
                (progress?.failed ?: 0) > 0 -> "${c.songs.size} tracks · ${progress!!.failed} failed"
                else -> "${c.songs.size} tracks"
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        if (progress?.running == true) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CollectionDetail(c: OfflineCollection, viewModel: OfflineViewModel) {
    val currentSong by viewModel.musicPlayer.currentSong.collectAsState()
    val isPlaying by viewModel.musicPlayer.isPlaying.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { viewModel.play(c.songs, 0) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play all")
                }
                OutlinedButton(onClick = { viewModel.playShuffled(c.songs) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
            }
            HorizontalDivider()
        }
        itemsIndexed(c.songs, key = { i, s -> "$i:${s.id}" }) { index, song ->
            OfflineSongRow(
                song = song,
                cached = song.id in viewModel.cachedIds,
                isCurrent = currentSong?.id == song.id,
                isPlayingNow = currentSong?.id == song.id && isPlaying,
                coverUrl = song.coverArt?.let(viewModel::getCoverUrl),
                onClick = { viewModel.play(c.songs, index) },
            )
        }
    }
}

@Composable
private fun OfflineSongRow(
    song: Song,
    cached: Boolean,
    isCurrent: Boolean,
    isPlayingNow: Boolean,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            if (isPlayingNow) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    NowPlayingBars(color = MaterialTheme.colorScheme.primary, modifier = Modifier.height(16.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (cached) {
            Icon(Icons.Default.DownloadDone, contentDescription = "Available offline", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Default.CloudOff, contentDescription = "Not downloaded yet", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
        }
    }
}
