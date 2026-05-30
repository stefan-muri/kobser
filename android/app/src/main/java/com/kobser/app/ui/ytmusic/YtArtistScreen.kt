package com.kobser.app.ui.ytmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
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
import com.kobser.app.data.api.YtRelease
import com.kobser.app.data.api.YtSong
import com.kobser.app.ui.components.YtDownloadDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtArtistScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    viewModel: YtArtistViewModel = hiltViewModel(),
) {
    var dlSong by remember { mutableStateOf<YtSong?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.artist?.name ?: "Artist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                viewModel.artist != null -> {
                    val artist = viewModel.artist!!
                    val songs = viewModel.allSongs ?: artist.topSongs

                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        // Header
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            ) {
                                AsyncImage(
                                    model = artist.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    artist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        // Top songs
                        if (songs.isNotEmpty()) {
                            item {
                                SectionHeader(if (viewModel.allSongs != null) "Songs" else "Top Songs")
                            }
                            items(songs, key = { it.videoId }) { song ->
                                YtSongRow(
                                    song = song,
                                    state = viewModel.downloadStates[song.videoId],
                                    onDownload = { dlSong = song },
                                )
                            }
                            if (viewModel.allSongs == null) {
                                item {
                                    TextButton(
                                        onClick = { viewModel.showAllSongs() },
                                        enabled = !viewModel.loadingMore,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        if (viewModel.loadingMore) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Loading…")
                                        } else {
                                            Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Show all songs")
                                        }
                                    }
                                }
                            }
                        }

                        // Albums
                        if (artist.albums.isNotEmpty()) {
                            item { SectionHeader("Albums") }
                            item { ReleaseShelf(artist.albums, onAlbumClick) }
                        }

                        // Singles
                        if (artist.singles.isNotEmpty()) {
                            item { SectionHeader("Singles & EPs") }
                            item { ReleaseShelf(artist.singles, onAlbumClick) }
                        }
                    }
                }
            }
        }
    }

    dlSong?.let { song ->
        YtDownloadDialog(
            initialArtist = song.artist,
            initialTitle = song.title,
            initialAlbum = song.album,
            onConfirm = { artist, title, album ->
                viewModel.download(song.videoId, artist, title, album)
                dlSong = null
            },
            onDismiss = { dlSong = null },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ReleaseShelf(releases: List<YtRelease>, onClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(releases, key = { it.browseId }) { release ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClick(release.browseId) }
                    .padding(4.dp),
            ) {
                AsyncImage(
                    model = release.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    release.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (release.year.isNotBlank()) {
                    Text(
                        release.year,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun YtSongRow(
    song: YtSong,
    state: YtDownloadState?,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (song.album.isNotBlank()) {
                Text(
                    song.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (song.duration > 0) {
            Text(
                fmtDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.width(4.dp))
        }
        DownloadButton(state, onDownload)
    }
}

@Composable
internal fun DownloadButton(state: YtDownloadState?, onDownload: () -> Unit) {
    when (state) {
        YtDownloadState.LOADING -> CircularProgressIndicator(
            modifier = Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp,
        )
        YtDownloadState.DONE -> Icon(
            Icons.Default.CheckCircle, null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp),
        )
        YtDownloadState.ERROR -> Icon(
            Icons.Default.Error, null,
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp),
        )
        null -> IconButton(onClick = onDownload) {
            Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

internal fun fmtDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
