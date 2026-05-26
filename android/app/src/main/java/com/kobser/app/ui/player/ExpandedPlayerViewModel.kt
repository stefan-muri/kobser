package com.kobser.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpandedPlayerViewModel @Inject constructor(
    val musicPlayer: MusicPlayer,
    private val libraryRepo: LibraryRepository,
) : ViewModel() {

    fun getCoverUrl(coverArt: String, size: Int = 1024): String =
        libraryRepo.getCoverArtUrl(coverArt, size)

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
