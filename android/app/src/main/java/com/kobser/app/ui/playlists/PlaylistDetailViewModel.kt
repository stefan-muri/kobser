package com.kobser.app.ui.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.PlaylistDetail
import com.kobser.app.data.api.Song
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val musicPlayer: MusicPlayer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val playlistId: String = savedStateHandle.get<String>("id") ?: ""

    var playlist by mutableStateOf<PlaylistDetail?>(null)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var allLibrarySongs by mutableStateOf<List<Song>>(emptyList())
    var pickerLoading by mutableStateOf(false)

    var starredOverrides by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    init { load() }

    fun load() {
        if (playlistId.isBlank()) {
            error = "Missing playlist id"
            isLoading = false
            return
        }
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.getPlaylist(playlistId)
                .onSuccess { playlist = it.playlist }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun playFromIndex(index: Int) {
        val list = playlist?.entry ?: return
        musicPlayer.playQueue(list, index)
    }

    fun playAll() = playFromIndex(0)

    fun playShuffled() {
        val list = playlist?.entry ?: return
        if (list.isEmpty()) return
        musicPlayer.playQueue(list.shuffled(), 0)
    }

    fun addToQueue(song: Song) {
        musicPlayer.addToQueue(song)
    }

    fun isStarred(song: Song): Boolean {
        return starredOverrides[song.id] ?: !song.starred.isNullOrEmpty()
    }

    fun toggleStar(song: Song) {
        val was = isStarred(song)
        starredOverrides = starredOverrides + (song.id to !was)
        viewModelScope.launch {
            val result = if (was) repository.unstar(song.id) else repository.star(song.id)
            if (result.isFailure) {
                starredOverrides = starredOverrides + (song.id to was)
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            val result = repository.deleteTrack(song.id)
            if (result.isSuccess) load()
        }
    }

    fun removeFromPlaylist(index: Int) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, index)
                .onSuccess { load() }
                .onFailure { error = "Failed to remove: ${it.message}" }
        }
    }

    /** Loads the full library list for the "Add songs" picker. */
    fun loadLibrarySongs() {
        if (allLibrarySongs.isNotEmpty() || pickerLoading) return
        pickerLoading = true
        viewModelScope.launch {
            repository.getSongs()
                .onSuccess { list ->
                    allLibrarySongs = list.sortedWith(
                        compareBy({ it.artist }, { it.title })
                    )
                }
            pickerLoading = false
        }
    }

    fun addSongs(songIds: List<String>, onComplete: () -> Unit = {}) {
        if (songIds.isEmpty()) return
        viewModelScope.launch {
            repository.addSongsToPlaylist(playlistId, songIds)
                .onSuccess { load(); onComplete() }
                .onFailure { error = "Failed to add songs: ${it.message}" }
        }
    }

    suspend fun getCoverUrl(id: String, size: Int = 128): String =
        repository.getCoverArtUrl(id, size)
}
