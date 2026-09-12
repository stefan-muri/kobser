package com.kobser.app.ui.offline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.Song
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.offline.OfflineCatalog
import com.kobser.app.offline.OfflineCollection
import com.kobser.app.offline.OfflineManager
import com.kobser.app.offline.OfflineProgress
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflineViewModel @Inject constructor(
    private val offline: OfflineManager,
    private val repository: LibraryRepository,
    val musicPlayer: MusicPlayer,
) : ViewModel() {

    var catalog by mutableStateOf(OfflineCatalog())
        private set
    var progress by mutableStateOf<Map<String, OfflineProgress>>(emptyMap())
        private set
    var selected by mutableStateOf<OfflineCollection?>(null)
        private set
    /** Ids of the selected collection's songs that are fully on disk (computed off the UI thread). */
    var cachedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        viewModelScope.launch {
            offline.catalog.collect { c ->
                catalog = c
                selected = selected?.let { c.find(it.key) }
            }
        }
        viewModelScope.launch {
            offline.progress.collect {
                progress = it
                refreshCached()
            }
        }
    }

    fun select(collection: OfflineCollection?) {
        selected = collection
        cachedIds = emptySet()
        refreshCached()
    }

    private fun refreshCached() {
        val c = selected ?: return
        viewModelScope.launch { cachedIds = offline.cachedSongIds(c.songs) }
    }

    fun getCoverUrl(coverArt: String): String = repository.getCoverArtUrl(coverArt)

    fun play(songs: List<Song>, index: Int) = musicPlayer.playQueue(songs, index)

    fun playShuffled(songs: List<Song>) {
        if (songs.isNotEmpty()) musicPlayer.playQueue(songs.shuffled(), 0)
    }

    fun remove(key: String) {
        offline.unpin(key)
        if (selected?.key == key) select(null)
    }
}
