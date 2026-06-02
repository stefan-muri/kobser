package com.kobser.app.ui.library

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.Artist
import com.kobser.app.data.api.ArtistResult
import com.kobser.app.data.api.DownloadRequest
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.api.SearchRequest
import com.kobser.app.data.api.SearchResult
import com.kobser.app.data.api.Song
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.data.repository.PreferencesRepository
import com.kobser.app.playback.MusicPlayer
import com.kobser.app.ui.ytmusic.YtDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortKey { ADDED_DESC, ARTIST_ASC, ARTIST_DESC, TITLE_ASC, TITLE_DESC, DURATION_ASC, DURATION_DESC }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val musicPlayer: MusicPlayer,
    private val api: KobserApi,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    var artists by mutableStateOf<List<Artist>>(emptyList())
    var songs by mutableStateOf<List<Song>>(emptyList())
    var favorites by mutableStateOf<List<Song>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var filterQuery by mutableStateOf("")

    // ── Online (YouTube / YT Music) search ───────────────────────────────────
    // Fired on submit; results are shown below the local library matches.
    var searchSource by mutableStateOf("youtube_music")
        private set
    var onlineSongs by mutableStateOf<List<SearchResult>>(emptyList())
        private set
    var onlineArtists by mutableStateOf<List<ArtistResult>>(emptyList())
        private set
    var onlineLoading by mutableStateOf(false)
        private set
    var onlineError by mutableStateOf<String?>(null)
        private set
    var onlineSearched by mutableStateOf(false)
        private set
    var downloadStates by mutableStateOf<Map<String, YtDownloadState>>(emptyMap())
        private set

    val filteredSongs by derivedStateOf {
        if (filterQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(filterQuery, ignoreCase = true) ||
            it.artist.contains(filterQuery, ignoreCase = true)
        }
    }

    val filteredFavorites by derivedStateOf {
        if (filterQuery.isBlank()) favorites
        else favorites.filter {
            it.title.contains(filterQuery, ignoreCase = true) ||
            it.artist.contains(filterQuery, ignoreCase = true)
        }
    }

    var sortKey by mutableStateOf(SortKey.ADDED_DESC)

    val sortedSongs by derivedStateOf { applySorting(filteredSongs, sortKey) }
    val sortedFavorites by derivedStateOf { applySorting(filteredFavorites, sortKey) }

    private fun applySorting(songs: List<Song>, key: SortKey): List<Song> = when (key) {
        SortKey.ADDED_DESC    -> songs.sortedByDescending { it.created ?: "" }
        SortKey.ARTIST_ASC    -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        SortKey.ARTIST_DESC   -> songs.sortedWith(compareByDescending<Song> { it.artist.lowercase() }.thenByDescending { it.title.lowercase() })
        SortKey.TITLE_ASC     -> songs.sortedBy { it.title.lowercase() }
        SortKey.TITLE_DESC    -> songs.sortedByDescending { it.title.lowercase() }
        SortKey.DURATION_ASC  -> songs.sortedBy { it.duration }
        SortKey.DURATION_DESC -> songs.sortedByDescending { it.duration }
    }

    // Playback state, surfaced so list rows can show a "now playing" indicator.
    val currentSong = musicPlayer.currentSong
    val isPlaying = musicPlayer.isPlaying

    // Local override of starred state so the heart flips immediately on tap
    // without needing to reload the list. Mirrors web's `starredOverrides`.
    var starredOverrides = mutableStateMapOf<String, Boolean>()
        private set

    init {
        if (songs.isEmpty() && artists.isEmpty()) {
            loadAll()
        }
        viewModelScope.launch {
            // Reload silently (no spinner) when something elsewhere changes the library,
            // e.g. liking a song from the player or another tab.
            repository.libraryChanged.collect { loadAll(showLoading = false) }
        }
        viewModelScope.launch {
            prefs.searchSource.collect { searchSource = it }
        }
    }

    /** Runs the online search for the current [filterQuery] (called on submit). */
    fun runOnlineSearch() {
        val q = filterQuery.trim()
        if (q.isBlank()) return
        onlineLoading = true
        onlineError = null
        onlineSearched = true
        onlineSongs = emptyList()
        onlineArtists = emptyList()
        viewModelScope.launch {
            try {
                val resp = api.search(SearchRequest(query = q, source = searchSource))
                if (resp.isSuccessful) {
                    onlineSongs = resp.body()?.songs ?: emptyList()
                    onlineArtists = resp.body()?.artists ?: emptyList()
                } else {
                    onlineError = "Search failed: ${resp.code()}"
                }
            } catch (e: Exception) {
                onlineError = e.message
            } finally {
                onlineLoading = false
            }
        }
    }

    /** Clears online results — used when the query changes or search is dismissed. */
    fun clearOnline() {
        onlineSongs = emptyList()
        onlineArtists = emptyList()
        onlineError = null
        onlineLoading = false
        onlineSearched = false
    }

    /** Streams an online search result without downloading it. */
    fun playPreview(result: SearchResult) {
        musicPlayer.playPreview(
            videoId = result.videoId,
            title = result.title,
            artist = result.channel,
            thumbnailUrl = result.thumbnail,
            durationSeconds = result.duration,
        )
    }

    fun downloadOnline(result: SearchResult, artist: String, title: String, album: String?) {
        downloadStates = downloadStates + (result.videoId to YtDownloadState.LOADING)
        viewModelScope.launch {
            try {
                val resp = api.download(
                    DownloadRequest(
                        videoId = result.videoId,
                        title = title,
                        artist = artist,
                        source = searchSource,
                        album = album?.takeIf { it.isNotBlank() },
                    )
                )
                downloadStates = downloadStates + (result.videoId to
                    if (resp.isSuccessful) YtDownloadState.DONE else YtDownloadState.ERROR)
            } catch (e: Exception) {
                downloadStates = downloadStates + (result.videoId to YtDownloadState.ERROR)
            }
        }
    }

    fun loadAll(showLoading: Boolean = true) {
        if (showLoading) isLoading = true
        error = null
        viewModelScope.launch {
            awaitAll(
                async { loadArtists() },
                async { loadSongs() },
                async { loadFavorites() },
            )
            if (showLoading) isLoading = false
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

    /** Plays from `list` at `index`, or jumps to the song if it's already in the queue. */
    fun playFromList(list: List<Song>, index: Int) {
        musicPlayer.playOrJumpTo(list, index)
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
        starredOverrides[song.id] = !was
        viewModelScope.launch {
            val result = if (was) repository.unstar(song.id) else repository.star(song.id)
            if (result.isSuccess) {
                // Reflect the change in the favorites list immediately…
                favorites = when {
                    was -> favorites.filter { it.id != song.id }
                    favorites.none { it.id == song.id } -> favorites + song
                    else -> favorites
                }
                // …then let other screens (e.g. the Favorites tab) reload too.
                repository.notifyLibraryChanged()
            } else {
                // Revert on failure
                starredOverrides[song.id] = was
            }
        }
    }

    fun deleteSong(song: Song, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteTrack(song.id)
            if (result.isSuccess) {
                songs = songs.filter { it.id != song.id }
                favorites = favorites.filter { it.id != song.id }
                repository.notifyLibraryChanged()
            }
            onComplete(result)
        }
    }

    fun getCoverArtUrl(id: String) = repository.getCoverArtUrl(id)
}
