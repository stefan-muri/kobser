package com.kobser.app.data.repository

import com.kobser.app.data.api.KobserApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** A server download the app is following, keyed by YouTube video id. */
data class TrackedDownload(val jobId: String, val title: String, val status: String)

sealed interface DownloadEvent {
    val title: String
    data class Added(override val title: String) : DownloadEvent
    data class Failed(override val title: String, val reason: String) : DownloadEvent
}

/**
 * Follows every server download to completion, wherever it was started (search,
 * an artist or album page, the player), and refreshes the library the moment the
 * song lands. Previously screens marked a download "done" as soon as the server
 * *accepted* it and never refreshed, so new songs only appeared after a manual
 * refresh or once the library cache expired.
 */
@Singleton
class DownloadTracker @Inject constructor(
    private val api: KobserApi,
    private val library: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<Map<String, TrackedDownload>>(emptyMap())
    /** videoId -> latest known state; screens derive their row icons from this. */
    val downloads: StateFlow<Map<String, TrackedDownload>> = _downloads.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    /** One-off "added" / "failed" notices for the app-wide snackbar. */
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    fun track(videoId: String, jobId: String, title: String) {
        if (_downloads.value[videoId]?.jobId == jobId) return
        _downloads.update { it + (videoId to TrackedDownload(jobId, title, "pending")) }
        scope.launch {
            val outcome = followJob(
                fetch = {
                    val resp = api.jobStatus(jobId)
                    when {
                        resp.isSuccessful -> resp.body()?.let { it.status to it.error }
                        resp.code() == 404 -> "error" to "The server lost track of this download"
                        else -> null
                    }
                },
                onStatus = { status ->
                    _downloads.update { m -> m[videoId]?.let { m + (videoId to it.copy(status = status)) } ?: m }
                },
                sleep = { delay(it) },
            )
            _downloads.update { m -> m[videoId]?.let { m + (videoId to it.copy(status = outcome.status)) } ?: m }
            if (outcome.succeeded) {
                library.notifyLibraryChanged()
                _events.tryEmit(DownloadEvent.Added(title))
            } else if (outcome.status != "cancelled") {
                _events.tryEmit(DownloadEvent.Failed(title, outcome.error ?: "Download failed"))
            }
        }
    }
}
