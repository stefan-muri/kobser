package com.kobser.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.DownloadRequest
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.api.SearchRequest
import com.kobser.app.data.api.SearchResult
import com.kobser.app.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: KobserApi,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    var query by mutableStateOf("")
    var results by mutableStateOf<List<SearchResult>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var downloadStates by mutableStateOf<Map<String, DownloadState>>(emptyMap())
    var searchSource by mutableStateOf("youtube")
        private set

    init {
        viewModelScope.launch {
            prefs.searchSource.collect { searchSource = it }
        }
    }

    fun search() {
        if (query.isBlank()) return
        isLoading = true
        error = null
        results = emptyList()
        viewModelScope.launch {
            try {
                val response = api.search(SearchRequest(query = query, source = searchSource))
                if (response.isSuccessful) {
                    results = response.body()?.songs ?: emptyList()
                } else {
                    error = "Search failed: ${response.code()}"
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun download(result: SearchResult, artist: String, title: String, album: String?) {
        downloadStates = downloadStates + (result.videoId to DownloadState.LOADING)
        viewModelScope.launch {
            try {
                val response = api.download(DownloadRequest(
                    videoId = result.videoId,
                    title = title,
                    artist = artist,
                    source = searchSource,
                    album = album?.takeIf { it.isNotBlank() },
                ))
                downloadStates = downloadStates + (result.videoId to
                    if (response.isSuccessful) DownloadState.DONE else DownloadState.ERROR)
            } catch (e: Exception) {
                downloadStates = downloadStates + (result.videoId to DownloadState.ERROR)
            }
        }
    }
}

enum class DownloadState { LOADING, DONE, ERROR }
