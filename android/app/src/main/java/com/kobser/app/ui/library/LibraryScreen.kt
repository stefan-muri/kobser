package com.kobser.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kobser.app.R
import com.kobser.app.data.api.ArtistResult
import com.kobser.app.data.api.SearchResult
import com.kobser.app.data.api.Song
import com.kobser.app.ui.components.AddToPlaylistSheet
import com.kobser.app.ui.components.MarqueeText
import com.kobser.app.ui.components.SongRow
import com.kobser.app.ui.components.YtDownloadDialog
import com.kobser.app.ui.ytmusic.DownloadButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    isFavorites: Boolean = false,
    onSongClick: (Song) -> Unit,
    onOpenSettings: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val items by remember(isFavorites) {
        derivedStateOf { if (isFavorites) viewModel.sortedFavorites else viewModel.sortedSongs }
    }

    var deleteTarget by remember { mutableStateOf<Song?>(null) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    var dlTarget by remember { mutableStateOf<SearchResult?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var sortSheetOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val playingSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    // Online search applies to the main Library only, not Favorites.
    val onlineEnabled = !isFavorites
    val searching by remember { derivedStateOf { viewModel.filterQuery.isNotBlank() } }

    // Show the search UI whenever the bar is explicitly open OR a query persists.
    // `searchOpen` is plain UI state that resets when the composable is recreated
    // (returning from an artist page, switching tabs); the ViewModel's query
    // survives. Deriving from both keeps an exit (back arrow / BackHandler) visible
    // so the user is never stranded in search results with the normal top bar.
    val inSearch = searchOpen || searching

    val closeSearch = {
        searchOpen = false
        viewModel.filterQuery = ""
        viewModel.clearOnline()
    }

    // System back exits the search view (instead of leaving the Library tab).
    BackHandler(enabled = inSearch) { closeSearch() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (inSearch) {
                TopAppBar(
                    title = {
                        TextField(
                            value = viewModel.filterQuery,
                            onValueChange = {
                                viewModel.filterQuery = it
                                if (onlineEnabled) viewModel.clearOnline()
                            },
                            placeholder = { Text(if (onlineEnabled) "Search library & YouTube Music…" else "Search favorites…") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboard?.hide()
                                if (onlineEnabled) viewModel.runOnlineSearch()
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = closeSearch) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_app_logo),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isFavorites) "Favorites" else "Library",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
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
            if (viewModel.isLoading && items.isEmpty() && !searching) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                // Always host content in a LazyColumn so pull-to-refresh works even
                // when the list (e.g. empty Favorites) has nothing to scroll.
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (viewModel.error != null && items.isEmpty() && !searching) {
                        item { CenteredMessage(viewModel.error!!, MaterialTheme.colorScheme.error) }
                    } else if (!searching && items.isEmpty()) {
                        item {
                            CenteredMessage(
                                text = if (isFavorites) "No favorites yet" else "Library is empty",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    } else if (!searching) {
                        // ── Normal library browse ──────────────────────────
                        if (!isFavorites) {
                            item {
                                PlayAllHeader(
                                    onPlayAll = { viewModel.playFromList(items, 0) },
                                    onShuffle = { viewModel.playFromList(items.shuffled(), 0) },
                                )
                            }
                        }
                        item {
                            SortButton(
                                sortKey = viewModel.sortKey,
                                onOpen = { sortSheetOpen = true },
                            )
                        }
                        librarySongs(items, viewModel, isFavorites, playingSong?.id, isPlaying, onSongClick,
                            onDelete = { deleteTarget = it }, onAddToPlaylist = { playlistTarget = it })
                    } else {
                        // ── Search mode: local matches first ────────────────
                        if (items.isNotEmpty()) {
                            item { SectionHeader("In your library") }
                            librarySongs(items, viewModel, isFavorites, playingSong?.id, isPlaying, onSongClick,
                                onDelete = { deleteTarget = it }, onAddToPlaylist = { playlistTarget = it })
                        }

                        // ── Then online results ─────────────────────────────
                        if (onlineEnabled) {
                            val sourceLabel = if (viewModel.searchSource == "youtube_music") "YouTube Music" else "YouTube"
                            when {
                                !viewModel.onlineSearched -> item {
                                    OnlineHint(
                                        query = viewModel.filterQuery.trim(),
                                        sourceLabel = sourceLabel,
                                        onClick = { viewModel.runOnlineSearch() },
                                    )
                                }
                                viewModel.onlineLoading -> {
                                    item { SectionHeader("From $sourceLabel") }
                                    item {
                                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }
                                viewModel.onlineError != null -> {
                                    item { SectionHeader("From $sourceLabel") }
                                    item {
                                        Text(
                                            "Search failed: ${viewModel.onlineError}",
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                }
                                else -> {
                                    item { SectionHeader("From $sourceLabel") }
                                    if (viewModel.onlineArtists.isNotEmpty()) {
                                        item { ArtistShelf(viewModel.onlineArtists, onArtistClick) }
                                    }
                                    items(viewModel.onlineSongs, key = { it.videoId }) { result ->
                                        OnlineSongRow(
                                            result = result,
                                            state = viewModel.downloadStates[result.videoId],
                                            onPlay = { viewModel.playPreview(result) },
                                            onDownload = { dlTarget = result },
                                        )
                                    }
                                    if (viewModel.onlineSongs.isEmpty() && viewModel.onlineArtists.isEmpty()) {
                                        item {
                                            Text(
                                                "No results on $sourceLabel",
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                modifier = Modifier.padding(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (items.isEmpty()) {
                            item {
                                Text(
                                    "No results for \"${viewModel.filterQuery}\"",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) focusRequester.requestFocus()
    }

    if (sortSheetOpen) {
        SortBottomSheet(
            sortKey = viewModel.sortKey,
            onSelect = { viewModel.sortKey = it; sortSheetOpen = false },
            onDismiss = { sortSheetOpen = false },
        )
    }

    playlistTarget?.let { song ->
        AddToPlaylistSheet(song = song, onDismiss = { playlistTarget = null })
    }

    dlTarget?.let { result ->
        val initialAlbum = if (viewModel.searchSource == "youtube_music") result.album.orEmpty() else ""
        YtDownloadDialog(
            initialArtist = result.channel,
            initialTitle = result.title,
            initialAlbum = initialAlbum,
            onConfirm = { artist, title, album ->
                viewModel.downloadOnline(result, artist, title, album)
                dlTarget = null
            },
            onDismiss = { dlTarget = null },
        )
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

/** Emits the local song rows shared by browse and search modes. */
private fun androidx.compose.foundation.lazy.LazyListScope.librarySongs(
    items: List<Song>,
    viewModel: LibraryViewModel,
    isFavorites: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onDelete: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    itemsIndexed(items, key = { _, song -> song.id }) { index, song ->
        val isStarred = viewModel.isStarred(song, defaultStarred = isFavorites)
        val isCurrent = song.id == currentSongId
        SongRow(
            song = song,
            isStarred = isStarred,
            getCoverUrl = { viewModel.getCoverArtUrl(it) },
            onClick = {
                viewModel.playFromList(items, index)
                onSongClick(song)
            },
            onToggleStar = { viewModel.toggleStar(song, defaultStarred = isFavorites) },
            onAddToQueue = { viewModel.addToQueue(song) },
            onPlay = {
                viewModel.playFromList(items, index)
                onSongClick(song)
            },
            onDelete = { onDelete(song) },
            onAddToPlaylist = { onAddToPlaylist(song) },
            isCurrent = isCurrent,
            isPlayingNow = isCurrent && isPlaying,
        )
    }
}

/** Full-viewport centered message; used inside the LazyColumn so it stays pull-to-refreshable. */
@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.CenteredMessage(text: String, color: Color) {
    Box(
        modifier = Modifier.fillParentMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun OnlineHint(query: String, sourceLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Search $sourceLabel for \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ArtistShelf(artists: List<ArtistResult>, onArtistClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(artists, key = { it.channelId }) { artist ->
            ArtistCard(artist = artist, onClick = { onArtistClick(artist.channelId) })
        }
    }
}

@Composable
private fun ArtistCard(artist: ArtistResult, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        if (artist.thumbnail != null) {
            AsyncImage(
                model = artist.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnlineSongRow(
    result: SearchResult,
    state: com.kobser.app.ui.ytmusic.YtDownloadState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = result.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentScale = ContentScale.Crop,
            )
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Preview",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(result.title, style = MaterialTheme.typography.bodyMedium)
            MarqueeText(
                result.channel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        DownloadButton(state, onDownload)
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

private val SORT_OPTIONS = listOf(
    SortKey.ADDED_DESC    to "Recently added",
    SortKey.ARTIST_ASC    to "Artist A → Z",
    SortKey.ARTIST_DESC   to "Artist Z → A",
    SortKey.TITLE_ASC     to "Title A → Z",
    SortKey.TITLE_DESC    to "Title Z → A",
    SortKey.DURATION_ASC  to "Shortest first",
    SortKey.DURATION_DESC to "Longest first",
)

private fun sortLabel(key: SortKey) = SORT_OPTIONS.first { it.first == key }.second

@Composable
private fun SortButton(sortKey: SortKey, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onOpen) {
            Text(
                text = "Sort: ${sortLabel(sortKey)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    sortKey: SortKey,
    onSelect: (SortKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Sort by",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        SORT_OPTIONS.forEach { (key, label) ->
            val selected = key == sortKey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(key) }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
