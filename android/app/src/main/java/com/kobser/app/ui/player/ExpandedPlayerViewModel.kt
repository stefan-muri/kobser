package com.kobser.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.DownloadRequest
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.data.repository.PreferencesRepository
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpandedPlayerViewModel @Inject constructor(
    val musicPlayer: MusicPlayer,
    private val libraryRepo: LibraryRepository,
    private val api: KobserApi,
    private val prefs: PreferencesRepository,
    private val downloadTracker: com.kobser.app.data.repository.DownloadTracker,
    private val offline: com.kobser.app.offline.OfflineManager,
) : ViewModel() {

    val offlineSongStates = offline.songStates

    /** Keeps the current song on the phone, or removes it if it's already kept on its own. */
    fun toggleCurrentOnPhone() {
        val song = musicPlayer.currentSong.value ?: return
        when (offline.songStates.value[song.id]) {
            null, com.kobser.app.offline.OfflineSongState.NONE -> offline.pinSong(song)
            com.kobser.app.offline.OfflineSongState.DOWNLOADING,
            com.kobser.app.offline.OfflineSongState.DOWNLOADED -> offline.unpinSong(song.id)
            com.kobser.app.offline.OfflineSongState.IN_COLLECTION -> Unit
        }
    }

    fun getCoverUrl(coverArt: String, size: Int = 1024): String =
        libraryRepo.getCoverArtUrl(coverArt, size)

    fun downloadPreview(artist: String, title: String, album: String?) {
        val song = musicPlayer.currentSong.value ?: return
        val videoId = song.id.substringAfterLast("|")
        viewModelScope.launch {
            runCatching {
                api.download(
                    DownloadRequest(
                        videoId = videoId,
                        title = title,
                        artist = artist,
                        source = prefs.searchSource.first(),
                        album = album?.takeIf { it.isNotBlank() },
                    )
                )
            }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.jobId?.let { jobId ->
                downloadTracker.track(videoId, jobId, title)
            }
        }
    }

    /** Closes the player right away; the server delete follows in the background. */
    fun deleteCurrentTrack() {
        val song = musicPlayer.currentSong.value ?: return
        musicPlayer.closePlayer()
        libraryRepo.deleteInBackground(song)
    }
}
