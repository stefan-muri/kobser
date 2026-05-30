package com.kobser.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Editable artist/title/album confirmation dialog shared by Search, YT Artist,
 * and YT Album screens. Pre-fills with the supplied metadata; the album is
 * optional (blank → backend tags it "Singles").
 */
@Composable
fun YtDownloadDialog(
    initialArtist: String,
    initialTitle: String,
    initialAlbum: String,
    onConfirm: (artist: String, title: String, album: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var artist by remember { mutableStateOf(initialArtist) }
    var title by remember { mutableStateOf(initialTitle) }
    var album by remember { mutableStateOf(initialAlbum) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(artist.trim(), title.trim(), album.trim().ifBlank { null }) },
                enabled = artist.isNotBlank() && title.isNotBlank(),
            ) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
