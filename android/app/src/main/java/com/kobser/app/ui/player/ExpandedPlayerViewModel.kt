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
) : ViewModel() {

    fun getCoverUrl(coverArt: String, size: Int = 1024): String =
        libraryRepo.getCoverArtUrl(coverArt, size)

    fun downloadPreview(artist: String, title: String, album: String?) {
        val song = musicPlayer.currentSong.value ?: return
        val videoId = song.id.substringAfterLast("|")
        viewModelScope.launch {
            api.download(
                DownloadRequest(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    source = prefs.searchSource.first(),
                    album = album?.takeIf { it.isNotBlank() },
                )
            )
        }
    }

    fun deleteCurrentTrack(onResult: (Result<Unit>) -> Unit) {
        val song = musicPlayer.currentSong.value ?: return
        viewModelScope.launch {
            val result = libraryRepo.deleteTrack(song.id)
            if (result.isSuccess) {
                musicPlayer.closePlayer()
                libraryRepo.notifyLibraryChanged()
            }
            onResult(result)
        }
    }
}
