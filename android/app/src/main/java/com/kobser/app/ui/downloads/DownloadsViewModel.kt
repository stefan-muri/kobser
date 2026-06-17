package com.kobser.app.ui.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.DownloadRecord
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backend job stages that mean a download is still in flight. */
internal val ACTIVE_DOWNLOAD_STATUSES = setOf("pending", "downloading", "tagging", "scanning")

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val api: KobserApi,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    var downloads by mutableStateOf<List<DownloadRecord>>(emptyList())
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            isLoading = downloads.isEmpty()
            doLoad()
            isLoading = false
        }
    }

    private suspend fun doLoad(): Boolean {
        error = null
        return try {
            val response = api.listDownloads()
            if (response.isSuccessful) {
                downloads = response.body()?.downloads ?: emptyList()
                true
            } else {
                error = "Failed to load downloads"
                false
            }
        } catch (e: Exception) {
            error = e.message
            false
        }
    }

    fun cancel(jobId: String) {
        viewModelScope.launch {
            api.cancelJob(jobId)
            load()
        }
    }

    fun delete(jobId: String) {
        downloads = downloads.filter { it.id != jobId }
        viewModelScope.launch {
            val result = api.deleteDownload(jobId)
            if (!result.isSuccessful) load()
        }
    }

    fun retry(jobId: String) {
        viewModelScope.launch {
            val result = api.retryDownload(jobId)
            if (result.isSuccessful) load() else error = "Retry failed"
        }
    }

    fun pollUntilDone() {
        val hasPending = downloads.any { it.status in ACTIVE_DOWNLOAD_STATUSES }
        if (!hasPending) return
        viewModelScope.launch {
            delay(3000)
            val prevDoneIds = downloads.filter { it.status == "done" }.map { it.id }.toSet()
            doLoad()
            val newDoneIds = downloads.filter { it.status == "done" }.map { it.id }.toSet()
            if ((newDoneIds - prevDoneIds).isNotEmpty()) {
                libraryRepository.notifyLibraryChanged()
            }
            pollUntilDone()
        }
    }
}
