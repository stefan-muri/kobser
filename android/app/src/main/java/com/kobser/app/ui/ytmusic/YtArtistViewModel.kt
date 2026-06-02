package com.kobser.app.ui.ytmusic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.YtArtist
import com.kobser.app.data.api.YtSong
import com.kobser.app.data.repository.YtMusicRepository
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class YtDownloadState { LOADING, DONE, ERROR }

@HiltViewModel
class YtArtistViewModel @Inject constructor(
    private val repo: YtMusicRepository,
    private val musicPlayer: MusicPlayer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId: String = savedStateHandle.get<String>("channelId") ?: ""

    var artist by mutableStateOf<YtArtist?>(null)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    // Top 5 (from getArtist) vs the full list (lazily loaded via "show all songs")
    var allSongs by mutableStateOf<List<YtSong>?>(null)
        private set
    var loadingMore by mutableStateOf(false)
        private set

    var downloadStates by mutableStateOf<Map<String, YtDownloadState>>(emptyMap())
        private set

    init { load() }

    fun load() {
        if (channelId.isBlank()) {
            error = "Missing artist id"
            isLoading = false
            return
        }
        isLoading = true
        error = null
        viewModelScope.launch {
            repo.getArtist(channelId)
                .onSuccess { artist = it }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun showAllSongs() {
        if (loadingMore || allSongs != null) return
        loadingMore = true
        viewModelScope.launch {
            repo.getArtistSongs(channelId)
                .onSuccess { allSongs = it }
                .onFailure { error = it.message }
            loadingMore = false
        }
    }

    fun playPreview(song: YtSong) {
        musicPlayer.playPreview(
            videoId = song.videoId,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnail,
            durationSeconds = song.duration,
        )
    }

    fun download(videoId: String, artist: String, title: String, album: String?) {
        downloadStates = downloadStates + (videoId to YtDownloadState.LOADING)
        viewModelScope.launch {
            val result = repo.download(videoId, artist, title, album)
            downloadStates = downloadStates + (videoId to
                if (result.isSuccess) YtDownloadState.DONE else YtDownloadState.ERROR)
        }
    }
}
