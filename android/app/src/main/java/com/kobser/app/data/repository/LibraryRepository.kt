package com.kobser.app.data.repository

import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.api.Song
import com.kobser.app.data.api.SubsonicResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val api: KobserApi,
    private val prefs: PreferencesRepository
) {
    @Volatile private var cachedServerUrl: String = ""
    @Volatile private var cachedSessionId: String = ""

    private val _libraryChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val libraryChanged: SharedFlow<Unit> = _libraryChanged.asSharedFlow()

    fun notifyLibraryChanged() {
        invalidateCaches()
        _libraryChanged.tryEmit(Unit)
    }

    // The full song list is what Android Auto browses and searches against and
    // what the Library tab shows. Fetching it is one paginated pass over the whole
    // library, so keep the result briefly instead of repeating that per request.
    private val songsMutex = Mutex()
    @Volatile private var songsCache: Pair<Long, List<Song>>? = null

    fun invalidateCaches() { songsCache = null }

    init {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { prefs.serverUrl.collect { cachedServerUrl = it ?: ""; invalidateCaches() } }
        scope.launch { prefs.sessionId.collect { cachedSessionId = it ?: ""; invalidateCaches() } }
    }

    private suspend fun <T> handleResponse(call: suspend () -> retrofit2.Response<com.kobser.app.data.api.SubsonicResponseWrapper>): Result<SubsonicResponse> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val wrapper = response.body()
                if (wrapper?.response?.status == "ok") {
                    Result.success(wrapper.response)
                } else {
                    Result.failure(Exception(wrapper?.response?.error?.message ?: "Unknown Subsonic error"))
                }
            } else {
                Result.failure(Exception("HTTP error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtists(): Result<SubsonicResponse> = handleResponse<SubsonicResponse> { api.getLibrary("getArtists") }

    suspend fun getArtist(id: String): Result<SubsonicResponse> = handleResponse<SubsonicResponse> {
        api.getLibrary("getArtist", mapOf("id" to id))
    }

    suspend fun getAlbum(id: String): Result<SubsonicResponse> = handleResponse<SubsonicResponse> {
        api.getLibrary("getAlbum", mapOf("id" to id))
    }

    /**
     * Loads the entire song library by paginating Subsonic's `search3` endpoint.
     * Mirrors the web app's Library.svelte fetchAll() implementation.
     */
    suspend fun getSongs(pageSize: Int = 500, forceRefresh: Boolean = false): Result<List<Song>> {
        if (!forceRefresh) cachedSongs()?.let { return Result.success(it) }
        return songsMutex.withLock {
            if (!forceRefresh) cachedSongs()?.let { return Result.success(it) }
            fetchAllSongs(pageSize).onSuccess { songsCache = System.currentTimeMillis() to it }
        }
    }

    private fun cachedSongs(): List<Song>? =
        songsCache?.let { (at, list) -> list.takeIf { System.currentTimeMillis() - at < SONGS_CACHE_TTL_MS } }

    private suspend fun fetchAllSongs(pageSize: Int): Result<List<Song>> {
        return try {
            val all = mutableListOf<Song>()
            var offset = 0
            while (true) {
                val response = api.getLibrary(
                    "search3",
                    mapOf(
                        "query" to " ",
                        "songCount" to pageSize.toString(),
                        "songOffset" to offset.toString(),
                        "artistCount" to "0",
                        "albumCount" to "0",
                    )
                )
                if (!response.isSuccessful) {
                    return Result.failure(Exception("HTTP error: ${response.code()}"))
                }
                val sr = response.body()?.response
                if (sr?.status != "ok") {
                    return Result.failure(
                        Exception(sr?.error?.message ?: "Unknown Subsonic error")
                    )
                }
                val batch = sr.searchResult3?.song ?: emptyList()
                all.addAll(batch)
                if (batch.size < pageSize) break
                offset += pageSize
            }
            Result.success(all)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFavorites(): Result<SubsonicResponse> = handleResponse<SubsonicResponse> {
        api.getLibrary("getStarred")
    }

    /**
     * Subsonic getAlbumList2. [type] is e.g. "newest", "recent", "frequent",
     * "alphabeticalByName"; the server caps [size] at 500 per call.
     */
    suspend fun getAlbumList(type: String, size: Int = 100, offset: Int = 0): Result<SubsonicResponse> =
        handleResponse<SubsonicResponse> {
            api.getLibrary(
                "getAlbumList2",
                mapOf("type" to type, "size" to size.toString(), "offset" to offset.toString()),
            )
        }

    suspend fun star(id: String): Result<SubsonicResponse> = handleResponse<SubsonicResponse> {
        api.getLibrary("star", mapOf("id" to id))
    }

    suspend fun unstar(id: String): Result<SubsonicResponse> = handleResponse<SubsonicResponse> {
        api.getLibrary("unstar", mapOf("id" to id))
    }

    /** Subsonic scrobble: submission=false is "now playing", true records the play. */
    suspend fun scrobble(id: String, submission: Boolean): Result<SubsonicResponse> = handleResponse<SubsonicResponse> {
        api.getLibrary("scrobble", mapOf("id" to id, "submission" to submission.toString()))
    }

    suspend fun deleteTrack(id: String): Result<Unit> {
        return try {
            val response = api.deleteTrack(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Playlists ────────────────────────────────────────────────────────────

    suspend fun getPlaylists(): Result<SubsonicResponse> = handleResponse<SubsonicResponse> {
        api.getLibrary("getPlaylists")
    }

    suspend fun getPlaylist(id: String): Result<SubsonicResponse> = handleResponse<SubsonicResponse> {
        api.getLibrary("getPlaylist", mapOf("id" to id))
    }

    suspend fun createPlaylist(name: String): Result<Unit> {
        return try {
            val response = api.createPlaylist(name)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renamePlaylist(id: String, name: String): Result<Unit> {
        return try {
            val response = api.updatePlaylist(playlistId = id, name = name)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(id: String): Result<Unit> {
        return try {
            val response = api.deletePlaylist(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Result<Unit> {
        return try {
            val response = api.updatePlaylist(playlistId = playlistId, songIdsToAdd = songIds)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songIndex: Int): Result<Unit> {
        return try {
            val response = api.updatePlaylist(playlistId = playlistId, songIndexToRemove = songIndex)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val SONGS_CACHE_TTL_MS = 3 * 60_000L
    }

    fun getCoverArtUrl(id: String, size: Int = 300): String =
        // Pass full URLs through unchanged (e.g. a YouTube thumbnail on a preview
        // track); only wrap bare Subsonic cover-art ids.
        if (id.startsWith("http")) id
        else "${cachedServerUrl.trimEnd('/')}/api/library/getCoverArt?id=$id&size=$size&session=$cachedSessionId"

    fun getStreamUrl(trackId: String): String =
        "${cachedServerUrl.trimEnd('/')}/api/stream/$trackId?session=$cachedSessionId"

    /** Synchronous preview-stream URL for a YouTube videoId (mirrors getStreamUrl). */
    fun previewUrl(videoId: String): String =
        "${cachedServerUrl.trimEnd('/')}/api/preview/$videoId?session=$cachedSessionId"

    suspend fun getPreviewUrl(videoId: String): String {
        val baseUrl = prefs.serverUrl.first() ?: ""
        val sessionId = prefs.sessionId.first() ?: ""
        return "${baseUrl.trimEnd('/')}/api/preview/$videoId?session=$sessionId"
    }
}
