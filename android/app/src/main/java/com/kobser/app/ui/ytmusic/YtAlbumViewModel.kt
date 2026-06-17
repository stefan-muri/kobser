package com.kobser.app.ui.ytmusic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.YtAlbum
import com.kobser.app.data.api.YtAlbumTrack
import com.kobser.app.data.repository.YtMusicRepository
import com.kobser.app.ui.ytmusic.DuplicateException
import com.kobser.app.ui.ytmusic.YtDownloadState
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YtAlbumViewModel @Inject constructor(
    private val repo: YtMusicRepository,
    private val musicPlayer: MusicPlayer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val browseId: String = savedStateHandle.get<String>("browseId") ?: ""

    var album by mutableStateOf<YtAlbum?>(null)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var downloadingAll by mutableStateOf(false)
        private set

    var downloadStates by mutableStateOf<Map<String, YtDownloadState>>(emptyMap())
        private set

    init { load() }

    fun load() {
        if (browseId.isBlank()) {
            error = "Missing album id"
            isLoading = false
            return
        }
        isLoading = true
        error = null
        viewModelScope.launch {
            repo.getAlbum(browseId)
                .onSuccess { album = it }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun playPreview(track: YtAlbumTrack) {
        musicPlayer.playPreview(
            videoId = track.videoId,
            title = track.title,
            artist = track.artist,
            thumbnailUrl = album?.thumbnail,
            durationSeconds = track.duration,
        )
    }

    fun download(videoId: String, artist: String, title: String, album: String?) {
        downloadStates = downloadStates + (videoId to YtDownloadState.LOADING)
        viewModelScope.launch {
            val result = repo.download(videoId, artist, title, album)
            downloadStates = downloadStates + (videoId to when {
                result.isSuccess -> YtDownloadState.DONE
                result.exceptionOrNull() is DuplicateException -> YtDownloadState.DUPLICATE
                else -> YtDownloadState.ERROR
            })
        }
    }

    fun downloadAlbum() {
        val a = album ?: return
        if (downloadingAll) return
        downloadingAll = true
        viewModelScope.launch {
            for (track in a.tracks) {
                val state = downloadStates[track.videoId]
                if (state == YtDownloadState.DONE || state == YtDownloadState.DUPLICATE) continue
                downloadStates = downloadStates + (track.videoId to YtDownloadState.LOADING)
                val result = repo.download(track.videoId, track.artist, track.title, a.title)
                downloadStates = downloadStates + (track.videoId to when {
                    result.isSuccess -> YtDownloadState.DONE
                    result.exceptionOrNull() is DuplicateException -> YtDownloadState.DUPLICATE
                    else -> YtDownloadState.ERROR
                })
            }
            downloadingAll = false
        }
    }
}
