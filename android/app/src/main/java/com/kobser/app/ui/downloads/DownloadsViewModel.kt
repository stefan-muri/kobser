package com.kobser.app.ui.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.DownloadRecord
import com.kobser.app.data.api.KobserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val api: KobserApi,
) : ViewModel() {

    var downloads by mutableStateOf<List<DownloadRecord>>(emptyList())
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            isLoading = downloads.isEmpty()
            error = null
            try {
                val response = api.listDownloads()
                if (response.isSuccessful) {
                    downloads = response.body()?.downloads ?: emptyList()
                } else {
                    error = "Failed to load downloads"
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
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

    fun pollUntilDone() {
        val hasPending = downloads.any { it.status == "pending" || it.status == "running" }
        if (!hasPending) return
        viewModelScope.launch {
            delay(3000)
            load()
            pollUntilDone()
        }
    }
}
