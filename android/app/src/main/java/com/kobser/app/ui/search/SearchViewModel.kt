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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: KobserApi
) : ViewModel() {

    var query by mutableStateOf("")
    var results by mutableStateOf<List<SearchResult>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun search() {
        if (query.isBlank()) return
        
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                val response = api.search(SearchRequest(query))
                if (response.isSuccessful) {
                    results = response.body() ?: emptyList()
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

    fun download(result: SearchResult) {
        viewModelScope.launch {
            try {
                val response = api.download(DownloadRequest(
                    videoId = result.videoId,
                    title = result.title,
                    artist = result.channel
                ))
                if (!response.isSuccessful) {
                    error = "Download failed: ${response.code()}"
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
}
