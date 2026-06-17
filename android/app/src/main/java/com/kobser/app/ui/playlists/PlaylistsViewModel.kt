package com.kobser.app.ui.playlists

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.ImportRequest
import com.kobser.app.data.api.ImportStatusResponse
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.api.Playlist
import com.kobser.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val api: KobserApi,
) : ViewModel() {

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
    var filter by mutableStateOf("")
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    // ── Playlist import ──────────────────────────────────────────────────────
    var importProgress by mutableStateOf<ImportStatusResponse?>(null)
        private set
    var importStarting by mutableStateOf(false)
        private set
    var importStartError by mutableStateOf<String?>(null)

    val filtered by derivedStateOf {
        if (filter.isBlank()) playlists
        else playlists.filter { it.name.contains(filter, ignoreCase = true) }
    }

    init { load() }

    fun load() {
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.getPlaylists()
                .onSuccess { playlists = it.playlists?.playlist ?: emptyList() }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(trimmed)
                .onSuccess { load() }
                .onFailure { error = "Failed to create playlist: ${it.message}" }
        }
    }

    fun rename(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(id, trimmed)
                .onSuccess { load() }
                .onFailure { error = "Failed to rename: ${it.message}" }
        }
    }

    fun delete(id: String) {
        // Optimistic remove
        playlists = playlists.filter { it.id != id }
        viewModelScope.launch {
            repository.deletePlaylist(id)
                .onFailure { error = "Failed to delete: ${it.message}"; load() }
        }
    }

    fun startImport(url: String) {
        val u = url.trim()
        if (u.isBlank() || importStarting) return
        importStarting = true
        importStartError = null
        viewModelScope.launch {
            try {
                val resp = api.importSpotify(ImportRequest(u))
                if (resp.isSuccessful && resp.body() != null) {
                    val b = resp.body()!!
                    importProgress = ImportStatusResponse(
                        importId = b.importId, name = b.name, status = "running",
                        total = b.total, current = 0, downloaded = 0, existing = 0,
                        failed = 0, playlistId = null, error = null, failures = emptyList(),
                    )
                    pollImport(b.importId)
                } else {
                    importStartError = parseDetail(resp.errorBody()?.string()) ?: "Import failed (${resp.code()})"
                }
            } catch (e: Exception) {
                importStartError = e.message ?: "Import failed"
            } finally {
                importStarting = false
            }
        }
    }

    private fun pollImport(id: String) {
        viewModelScope.launch {
            while (true) {
                delay(1500)
                val resp = try { api.importStatus(id) } catch (e: Exception) { break }
                if (!resp.isSuccessful || resp.body() == null) break
                val b = resp.body()!!
                importProgress = b
                if (b.status != "running") {
                    if (b.status == "done") load()
                    break
                }
            }
        }
    }

    fun dismissImport() { importProgress = null }

    private fun parseDetail(body: String?): String? =
        try { body?.let { JSONObject(it).optString("detail").ifBlank { null } } } catch (e: Exception) { null }
}
