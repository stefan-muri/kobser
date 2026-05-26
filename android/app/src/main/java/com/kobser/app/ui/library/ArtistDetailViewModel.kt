package com.kobser.app.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.ArtistDetail
import com.kobser.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val repository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: String = savedStateHandle.get<String>("id") ?: ""

    var artist by mutableStateOf<ArtistDetail?>(null)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    init { load() }

    fun load() {
        if (artistId.isBlank()) {
            error = "Missing artist id"
            isLoading = false
            return
        }
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.getArtist(artistId)
                .onSuccess { artist = it.artist }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun getCoverUrl(id: String, size: Int = 300): String =
        repository.getCoverArtUrl(id, size)
}
