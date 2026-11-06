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
import kotlinx.coroutines.withContext
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

    // App-lifetime scope: work started here (e.g. a delete) survives the screen
    // that started it being closed.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Errors from background library work, for the app-wide snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        scope.launch { prefs.serverUrl.collect { cachedServerUrl = it ?: ""; invalidateCaches() } }
        scope.launch { prefs.sessionId.collect { cachedSessionId = it ?: ""; invalidateCaches() } }
    }

    /**
     * Deletes a track without making the caller wait: callers remove it from their
     * list first, the server delete runs in the app scope, and on failure [restore]
     * puts it back (on the main thread) and the reason goes to the snackbar.
     */
    fun deleteInBackground(song: Song, restore: () -> Unit = {}) {
        songsCache = songsCache?.let { (at, list) -> at to list.filter { it.id != song.id } }
        scope.launch {
            val result = deleteTrack(song.id)
            if (result.isSuccess) {
                // Keep it hidden for a while even if Navidrome is slow to drop it, so
                // the refresh below can't bring a deleted song back.
                recentlyDeleted[song.id] = System.currentTimeMillis()
                // The server only answers once Navidrome has dropped the track, so
                // this refresh comes back without it.
                notifyLibraryChanged()
            } else {
                invalidateCaches()
                withContext(Dispatchers.Main) { restore() }
                _messages.tryEmit("Couldn't delete “${song.title}”: ${result.exceptionOrNull()?.message ?: "unknown error"}")
            }
        }
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
        if (!forceRefresh) cachedSongs()?.let { return Result.success(it.withoutRecentlyDeleted { s -> s.id }) }
        return songsMutex.withLock {
            if (!forceRefresh) cachedSongs()?.let { return Result.success(it.withoutRecentlyDeleted { s -> s.id }) }
            fetchAllSongs(pageSize)
                .map { all -> all.withoutRecentlyDeleted { s -> s.id } }
                .onSuccess { songsCache = System.currentTimeMillis() to it }
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
    }.map { r ->
        r.starred?.let { st -> r.copy(starred = st.copy(song = st.song.withoutRecentlyDeleted { it.id })) } ?: r
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
            else Result.failure(Exception(serverMessage(response) ?: "HTTP error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** The middleware's `{"detail": "..."}` error text, when there is one. */
    private fun serverMessage(response: retrofit2.Response<*>): String? =
        runCatching {
            com.google.gson.JsonParser.parseString(response.errorBody()?.string().orEmpty())
                .asJsonObject.get("detail")?.asString
        }.getOrNull()

    // songId -> when it was deleted. Hidden from listings for RECENTLY_DELETED_MS.
    private val recentlyDeleted = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun <T> List<T>.withoutRecentlyDeleted(id: (T) -> String): List<T> {
        if (recentlyDeleted.isEmpty()) return this
        val now = System.currentTimeMillis()
        recentlyDeleted.entries.removeIf { now - it.value > RECENTLY_DELETED_MS }
        return filterNot { recentlyDeleted.containsKey(id(it)) }
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
        private const val RECENTLY_DELETED_MS = 2 * 60_000L
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
