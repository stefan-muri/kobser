package com.kobser.app.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kobser.app.R
import com.kobser.app.data.api.ImportStatusResponse
import com.kobser.app.data.api.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (String) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    var createOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    var importOpen by remember { mutableStateOf(false) }
    var importStep by remember { mutableStateOf("source") }  // source | url | progress
    var importUrl by remember { mutableStateOf("") }

    // Once the import job is registered, jump the dialog to the progress view.
    LaunchedEffect(viewModel.importProgress) {
        if (viewModel.importProgress != null) importStep = "progress"
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
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
                            "Playlists",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        importStep = if (viewModel.importProgress != null) "progress" else "source"
                        importUrl = ""
                        viewModel.importStartError = null
                        importOpen = true
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Import playlist")
                    }
                    IconButton(onClick = { createOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New playlist")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = viewModel.filter,
                onValueChange = { viewModel.filter = it },
                placeholder = { Text("Search playlists…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = viewModel.isLoading,
                onRefresh = { viewModel.load() },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    viewModel.isLoading && viewModel.filtered.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    viewModel.error != null -> Text(
                        text = viewModel.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    viewModel.filtered.isEmpty() -> Text(
                        text = if (viewModel.filter.isBlank()) "No playlists yet" else "No matches",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(viewModel.filtered, key = { it.id }) { p ->
                            PlaylistRow(
                                playlist = p,
                                onClick = { onPlaylistClick(p.id) },
                                onRename = { renameTarget = p },
                                onDelete = { deleteTarget = p },
                            )
                        }
                    }
                }
            }
        }
    }

        // Re-entry pill while an import keeps running after "Run in background".
        val importProg = viewModel.importProgress
        if (importProg != null && !importOpen) {
            ImportPill(
                progress = importProg,
                onClick = { importStep = "progress"; importOpen = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (importOpen) {
        ImportDialog(
            step = importStep,
            url = importUrl,
            onUrlChange = { importUrl = it },
            starting = viewModel.importStarting,
            startError = viewModel.importStartError,
            progress = viewModel.importProgress,
            onPickSpotify = { importStep = "url"; viewModel.importStartError = null },
            onBack = { importStep = "source" },
            onStart = { viewModel.startImport(importUrl) },
            onRunInBackground = { importOpen = false },
            onDone = { viewModel.dismissImport(); importOpen = false },
            onDismiss = { importOpen = false },
        )
    }

    if (createOpen) {
        NameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            initialName = "",
            onConfirm = { name ->
                viewModel.create(name)
                createOpen = false
            },
            onDismiss = { createOpen = false },
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initialName = target.name,
            onConfirm = { name ->
                viewModel.rename(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete playlist?") },
            text = { Text("\"${target.name}\" will be permanently removed. The songs themselves remain in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    deleteTarget = null
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
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.songCount} track${if (playlist.songCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menuOpen = false; onRename() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImportPill(
    progress: ImportStatusResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (progress.status) {
                "running" -> {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Importing ${progress.current}/${progress.total}", style = MaterialTheme.typography.labelLarge)
                }
                "done" -> {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import complete", style = MaterialTheme.typography.labelLarge)
                }
                else -> {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import failed", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ImportDialog(
    step: String,
    url: String,
    onUrlChange: (String) -> Unit,
    starting: Boolean,
    startError: String?,
    progress: ImportStatusResponse?,
    onPickSpotify: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRunInBackground: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import playlist") },
        text = {
            when (step) {
                "source" -> Column {
                    Text("Import from", style = MaterialTheme.typography.bodySmall, color = muted)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = onPickSpotify,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null, tint = Color(0xFF1DB954))
                            Spacer(Modifier.width(12.dp))
                            Text("Spotify", fontWeight = FontWeight.Medium)
                        }
                    }
                }
                "url" -> Column {
                    Text("Paste a public Spotify playlist link", style = MaterialTheme.typography.bodySmall, color = muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        placeholder = { Text("https://open.spotify.com/playlist/…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    startError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> Column {
                    Text(progress?.name ?: "", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    when (progress?.status) {
                        "done" -> {
                            Text(
                                "Imported ${progress.downloaded + progress.existing} of ${progress.total} tracks" +
                                    if (progress.playlistId != null) " — playlist created." else "",
                                style = MaterialTheme.typography.bodySmall, color = muted,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${progress.downloaded} downloaded · ${progress.existing} already there" +
                                    if (progress.failed > 0) " · ${progress.failed} not found" else "",
                                style = MaterialTheme.typography.bodySmall, color = muted,
                            )
                            if (progress.failures.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Couldn't find:", style = MaterialTheme.typography.labelMedium)
                                Column(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                                    progress.failures.forEach {
                                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = muted)
                                    }
                                }
                            }
                        }
                        "error" -> Text(progress.error ?: "Import failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        else -> {
                            Text(
                                if (progress == null) "Starting…" else "Importing ${progress.current} / ${progress.total}…",
                                style = MaterialTheme.typography.bodySmall, color = muted,
                            )
                            Spacer(Modifier.height(8.dp))
                            val frac = if (progress != null && progress.total > 0) progress.current.toFloat() / progress.total else 0f
                            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                            progress?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${it.downloaded} new · ${it.existing} existing" + if (it.failed > 0) " · ${it.failed} failed" else "",
                                    style = MaterialTheme.typography.bodySmall, color = muted,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                "url" -> TextButton(onClick = onStart, enabled = url.isNotBlank() && !starting) {
                    Text(if (starting) "Starting…" else "Import")
                }
                "progress" -> {
                    if (progress == null || progress.status == "running") {
                        TextButton(onClick = onRunInBackground) { Text("Run in background") }
                    } else {
                        TextButton(onClick = onDone) { Text("Done") }
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            when (step) {
                "source" -> TextButton(onClick = onDismiss) { Text("Cancel") }
                "url" -> TextButton(onClick = onBack) { Text("Back") }
                else -> {}
            }
        },
    )
}
