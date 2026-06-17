package com.kobser.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// ── Auth ─────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val sessionId: String
)

data class UserResponse(
    val username: String
)

// ── Search & download ────────────────────────────────────────────────────────

data class SearchRequest(
    val query: String,
    val limit: Int = 10,
    val source: String = "youtube_music",
)

data class SearchResult(
    val videoId: String,
    val title: String,
    val channel: String,
    val duration: Int,
    val thumbnail: String,
    val album: String? = null,
)

data class SearchResponse(
    val songs: List<SearchResult>,
    val artists: List<ArtistResult>,
)

data class ArtistResult(
    val channelId: String,
    val name: String,
    val thumbnail: String?,
    val subscribers: String,
)

// ── YT Music exploration (artist / album pages) ──────────────────────────────

data class YtSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val thumbnail: String?,
)

data class YtRelease(
    val browseId: String,
    val title: String,
    val year: String,
    val type: String,
    val thumbnail: String?,
)

data class YtArtist(
    val channelId: String,
    val name: String,
    val description: String,
    val thumbnail: String?,
    val topSongs: List<YtSong>,
    val albums: List<YtRelease>,
    val singles: List<YtRelease>,
)

data class YtAlbumTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val trackNumber: Int,
    val duration: Int,
)

data class YtAlbum(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: String,
    val type: String,
    val thumbnail: String?,
    val tracks: List<YtAlbumTrack>,
)

data class DownloadRequest(
    val videoId: String,
    val title: String,
    val artist: String,
    val source: String = "youtube_music",
    val album: String? = null,
    val force: Boolean = false,
)

data class DownloadStartResponse(
    val jobId: String,
    val status: String
)

data class JobStatusResponse(
    val jobId: String,
    val status: String,
    val error: String?,
    val file: String?
)

data class DownloadsListResponse(
    val downloads: List<DownloadRecord>
)

data class DownloadRecord(
    val id: String,
    @SerializedName("video_id") val videoId: String,
    val artist: String,
    val title: String,
    val status: String,
    val error: String?,
    @SerializedName("file_path") val filePath: String?,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long?,
    @SerializedName("started_at") val startedAt: Long,
    @SerializedName("completed_at") val completedAt: Long?
)

// ── Playlist import ───────────────────────────────────────────────────────────

data class ImportRequest(val url: String)

data class ImportStartResponse(
    val importId: String,
    val name: String,
    val total: Int,
)

data class ImportStatusResponse(
    val importId: String,
    val name: String,
    val status: String,
    val total: Int,
    val current: Int,
    val downloaded: Int,
    val existing: Int,
    val failed: Int,
    val playlistId: String?,
    val error: String?,
    val failures: List<String> = emptyList(),
)

// ── Stats ────────────────────────────────────────────────────────────────────

data class StatsResponse(
    val disk: DiskUsage,
    val ram: MemoryUsage,
    @SerializedName("cpu_percent") val cpuPercent: Double
)

data class DiskUsage(val used: Long, val total: Long)
data class MemoryUsage(val used: Long, val total: Long)

// ── Generic ──────────────────────────────────────────────────────────────────

data class OkResponse(val ok: Boolean)

// ── Retrofit interface ───────────────────────────────────────────────────────

interface KobserApi {
    // Auth
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/api/logout")
    suspend fun logout(): Response<Unit>

    @GET("/api/me")
    suspend fun me(): Response<UserResponse>

    // YouTube search & download
    @POST("/api/search")
    suspend fun search(@Body request: SearchRequest): Response<SearchResponse>

    @POST("/api/download")
    suspend fun download(@Body request: DownloadRequest): Response<DownloadStartResponse>

    // YT Music exploration
    @GET("/api/artist/{channelId}")
    suspend fun getYtArtist(@Path("channelId") channelId: String): Response<YtArtist>

    @GET("/api/artist/{channelId}/songs")
    suspend fun getYtArtistSongs(@Path("channelId") channelId: String): Response<List<YtSong>>

    @GET("/api/album/{browseId}")
    suspend fun getYtAlbum(@Path("browseId") browseId: String): Response<YtAlbum>

    @GET("/api/status/{jobId}")
    suspend fun jobStatus(@Path("jobId") jobId: String): Response<JobStatusResponse>

    @POST("/api/jobs/{jobId}/cancel")
    suspend fun cancelJob(@Path("jobId") jobId: String): Response<OkResponse>

    @GET("/api/downloads")
    suspend fun listDownloads(): Response<DownloadsListResponse>

    @DELETE("/api/downloads/{jobId}")
    suspend fun deleteDownload(@Path("jobId") jobId: String): Response<OkResponse>

    @POST("/api/downloads/{jobId}/retry")
    suspend fun retryDownload(@Path("jobId") jobId: String): Response<DownloadStartResponse>

    // Library (Subsonic proxy)
    @GET("/api/library/{subpath}")
    suspend fun getLibrary(
        @Path("subpath", encoded = true) subpath: String,
        @QueryMap params: Map<String, String> = emptyMap()
    ): Response<SubsonicResponseWrapper>

    // Playlists — these go through the same /api/library proxy but need typed
    // signatures because Subsonic's updatePlaylist takes repeating song-id query
    // params, which `getLibrary`'s Map<String, String> can't express.
    @GET("/api/library/createPlaylist")
    suspend fun createPlaylist(
        @Query("name") name: String,
    ): Response<SubsonicResponseWrapper>

    @GET("/api/library/deletePlaylist")
    suspend fun deletePlaylist(
        @Query("id") id: String,
    ): Response<SubsonicResponseWrapper>

    @GET("/api/library/updatePlaylist")
    suspend fun updatePlaylist(
        @Query("playlistId") playlistId: String,
        @Query("name") name: String? = null,
        @Query("songIdToAdd") songIdsToAdd: List<String>? = null,
        @Query("songIndexToRemove") songIndexToRemove: Int? = null,
    ): Response<SubsonicResponseWrapper>

    // Track management
    @DELETE("/api/track/{trackId}")
    suspend fun deleteTrack(@Path("trackId") trackId: String): Response<OkResponse>

    // Playlist import
    @POST("/api/import/spotify")
    suspend fun importSpotify(@Body request: ImportRequest): Response<ImportStartResponse>

    @GET("/api/import/{importId}")
    suspend fun importStatus(@Path("importId") importId: String): Response<ImportStatusResponse>

    // Server ops
    @POST("/api/rescan")
    suspend fun rescan(): Response<OkResponse>

    @GET("/api/stats")
    suspend fun getStats(): Response<StatsResponse>
}
