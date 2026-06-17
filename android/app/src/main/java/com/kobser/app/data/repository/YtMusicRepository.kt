package com.kobser.app.data.repository

import com.kobser.app.data.api.DownloadRequest
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.api.YtAlbum
import com.kobser.app.data.api.YtArtist
import com.kobser.app.data.api.YtSong
import com.kobser.app.ui.ytmusic.DuplicateException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the YouTube Music exploration endpoints (/api/artist, /api/album) and
 * routes downloads through /api/download with source = "youtube_music".
 *
 * Kept separate from LibraryRepository, which deals with the local Subsonic library.
 */
@Singleton
class YtMusicRepository @Inject constructor(
    private val api: KobserApi,
) {
    suspend fun getArtist(channelId: String): Result<YtArtist> = try {
        val resp = api.getYtArtist(channelId)
        if (resp.isSuccessful && resp.body() != null) Result.success(resp.body()!!)
        else Result.failure(Exception("Failed to load artist (${resp.code()})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getArtistSongs(channelId: String): Result<List<YtSong>> = try {
        val resp = api.getYtArtistSongs(channelId)
        if (resp.isSuccessful && resp.body() != null) Result.success(resp.body()!!)
        else Result.failure(Exception("Failed to load songs (${resp.code()})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getAlbum(browseId: String): Result<YtAlbum> = try {
        val resp = api.getYtAlbum(browseId)
        if (resp.isSuccessful && resp.body() != null) Result.success(resp.body()!!)
        else Result.failure(Exception("Failed to load album (${resp.code()})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun download(
        videoId: String,
        artist: String,
        title: String,
        album: String?,
    ): Result<Unit> = try {
        val resp = api.download(
            DownloadRequest(
                videoId = videoId,
                title = title,
                artist = artist,
                source = "youtube_music",
                album = album?.takeIf { it.isNotBlank() },
            )
        )
        when {
            resp.isSuccessful -> Result.success(Unit)
            resp.code() == 409 -> Result.failure(DuplicateException())
            else -> Result.failure(Exception("Download failed (${resp.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
