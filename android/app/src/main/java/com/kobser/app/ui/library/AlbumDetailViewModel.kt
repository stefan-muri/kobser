package com.kobser.app.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.AlbumDetail
import com.kobser.app.data.api.Song
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.offline.OfflineCollection
import com.kobser.app.offline.OfflineManager
import com.kobser.app.offline.OfflineProgress
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val musicPlayer: MusicPlayer,
    private val offline: OfflineManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: String = savedStateHandle.get<String>("id") ?: ""

    var album by mutableStateOf<AlbumDetail?>(null)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    // ── Keep offline ────────────────────────────────────────────────────────
    var isPinnedOffline by mutableStateOf(false)
        private set
    var offlineProgress by mutableStateOf<OfflineProgress?>(null)
        private set

    private val offlineKey get() = OfflineCollection.albumKey(albumId)

    init {
        viewModelScope.launch { offline.catalog.collect { isPinnedOffline = it.find(offlineKey) != null } }
        viewModelScope.launch { offline.progress.collect { offlineProgress = it[offlineKey] } }
    }

    fun toggleOffline() {
        if (isPinnedOffline) {
            offline.unpin(offlineKey)
            return
        }
        val a = album ?: return
        offline.pin(OfflineCollection(
            key = OfflineCollection.albumKey(albumId),
            title = a.name,
            subtitle = a.artist,
            coverArt = a.song.firstOrNull()?.coverArt,
            songs = a.song,
            pinnedAt = System.currentTimeMillis(),
        ))
    }

    var starredOverrides by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    init { load() }

    fun load() {
        if (albumId.isBlank()) {
            error = "Missing album id"
            isLoading = false
            return
        }
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.getAlbum(albumId)
                .onSuccess { album = it.album }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun playFromIndex(index: Int) {
        val list = album?.song ?: return
        musicPlayer.playQueue(list, index)
    }

    fun playAll() = playFromIndex(0)

    /** Plays the album in a one-shot randomized order (matches web playShuffled). */
    fun playShuffled() {
        val list = album?.song ?: return
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
            if (result.isSuccess) {
                album = album?.copy(song = album!!.song.filter { it.id != song.id })
                repository.notifyLibraryChanged()
            }
        }
    }

    fun getCoverUrl(id: String, size: Int = 512): String =
        repository.getCoverArtUrl(id, size)
}
