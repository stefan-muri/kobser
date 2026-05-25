package com.kobser.app.ui.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.Playlist
import com.kobser.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
    var filter by mutableStateOf("")
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    val filtered: List<Playlist>
        get() = if (filter.isBlank()) playlists
                else playlists.filter { it.name.contains(filter, ignoreCase = true) }

    init { load() }

    fun load() {
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.getPlaylists()
                .onSuccess { playlists = it.playlists?.playlist ?: emptyList() }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(trimmed)
                .onSuccess { load() }
                .onFailure { error = "Failed to create playlist: ${it.message}" }
        }
    }

    fun rename(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(id, trimmed)
                .onSuccess { load() }
                .onFailure { error = "Failed to rename: ${it.message}" }
        }
    }

    fun delete(id: String) {
        // Optimistic remove
        playlists = playlists.filter { it.id != id }
        viewModelScope.launch {
            repository.deletePlaylist(id)
                .onFailure { error = "Failed to delete: ${it.message}"; load() }
        }
    }
}
