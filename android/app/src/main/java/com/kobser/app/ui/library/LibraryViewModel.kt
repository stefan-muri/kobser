package com.kobser.app.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.Artist
import com.kobser.app.data.api.Song
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val musicPlayer: MusicPlayer
) : ViewModel() {

    var artists by mutableStateOf<List<Artist>>(emptyList())
    var songs by mutableStateOf<List<Song>>(emptyList())
    var favorites by mutableStateOf<List<Song>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // Local override of starred state so the heart flips immediately on tap
    // without needing to reload the list. Mirrors web's `starredOverrides`.
    var starredOverrides by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    init {
        loadAll()
    }

    fun loadAll() {
        isLoading = true
        error = null
        viewModelScope.launch {
            loadArtists()
            loadSongs()
            loadFavorites()
            isLoading = false
        }
    }

    private suspend fun loadArtists() {
        repository.getArtists()
            .onSuccess { response ->
                artists = response.artists?.index?.flatMap { it.artist } ?: emptyList()
            }
            .onFailure { error = it.message }
    }

    private suspend fun loadSongs() {
        repository.getSongs()
            .onSuccess { list -> songs = list }
            .onFailure { error = it.message }
    }

    private suspend fun loadFavorites() {
        repository.getFavorites()
            .onSuccess { response ->
                favorites = response.starred?.song ?: emptyList()
            }
    }

    /** Sets the player queue to `list` and starts playing at `index`. */
    fun playFromList(list: List<Song>, index: Int) {
        musicPlayer.playQueue(list, index)
    }

    fun addToQueue(song: Song) {
        musicPlayer.addToQueue(song)
    }

    fun isStarred(song: Song, defaultStarred: Boolean = false): Boolean {
        return starredOverrides[song.id]
            ?: (defaultStarred || !song.starred.isNullOrEmpty())
    }

    fun toggleStar(song: Song, defaultStarred: Boolean = false) {
        val was = isStarred(song, defaultStarred)
        starredOverrides = starredOverrides + (song.id to !was)
        viewModelScope.launch {
            val result = if (was) repository.unstar(song.id) else repository.star(song.id)
            if (result.isFailure) {
                // Revert on failure
                starredOverrides = starredOverrides + (song.id to was)
            }
        }
    }

    fun deleteSong(song: Song, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteTrack(song.id)
            if (result.isSuccess) {
                songs = songs.filter { it.id != song.id }
                favorites = favorites.filter { it.id != song.id }
            }
            onComplete(result)
        }
    }

    suspend fun getCoverArtUrl(id: String) = repository.getCoverArtUrl(id)
}
