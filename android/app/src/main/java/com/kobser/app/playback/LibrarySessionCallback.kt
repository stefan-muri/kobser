package com.kobser.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kobser.app.data.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySessionCallback @Inject constructor(
    private val repository: LibraryRepository
) : MediaLibraryService.MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItem.Builder()
            .setMediaId("root")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle("Kobser Library")
                    .build()
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return scope.future {
            val items = when (parentId) {
                "root" -> {
                    listOf(
                        createBrowsableItem("artists", "Artists"),
                        createBrowsableItem("playlists", "Playlists"),
                        createBrowsableItem("favorites", "Favorites")
                    )
                }
                "artists" -> {
                    repository.getArtists().getOrNull()?.artists?.index?.flatMap { it.artist }?.map { artist ->
                        MediaItem.Builder()
                            .setMediaId("artist_id_${artist.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setTitle(artist.name)
                                    .setSubtitle("${artist.albumCount} albums")
                                    .setArtworkUri(Uri.parse(repository.getCoverArtUrl(artist.coverArt ?: "")))
                                    .build()
                            )
                            .build()
                    } ?: emptyList()
                }
                "playlists" -> {
                    repository.getPlaylists().getOrNull()?.playlists?.playlist?.map { playlist ->
                        MediaItem.Builder()
                            .setMediaId("playlist_id_${playlist.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setTitle(playlist.name)
                                    .setSubtitle("${playlist.songCount} songs")
                                    .build()
                            )
                            .build()
                    } ?: emptyList()
                }
                "favorites" -> {
                    repository.getFavorites().getOrNull()?.starred?.song?.map { song ->
                        createPlayableItem(
                            id = song.id,
                            title = song.title,
                            artist = song.artist,
                            album = song.album ?: "",
                            coverArt = song.coverArt ?: ""
                        )
                    } ?: emptyList()
                }
                else -> {
                    if (parentId.startsWith("artist_id_")) {
                        val artistId = parentId.removePrefix("artist_id_")
                        repository.getArtist(artistId).getOrNull()?.artist?.album?.map { album ->
                            MediaItem.Builder()
                                .setMediaId("album_id_${album.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setTitle(album.name)
                                        .setSubtitle(album.artist)
                                        .setArtworkUri(Uri.parse(repository.getCoverArtUrl(album.coverArt ?: "")))
                                        .build()
                                )
                                .build()
                        } ?: emptyList()
                    } else if (parentId.startsWith("album_id_")) {
                        val albumId = parentId.removePrefix("album_id_")
                        repository.getAlbum(albumId).getOrNull()?.album?.song?.map { song ->
                            createPlayableItem(
                                id = song.id,
                                title = song.title,
                                artist = song.artist,
                                album = song.album ?: "",
                                coverArt = song.coverArt ?: ""
                            )
                        } ?: emptyList()
                    } else if (parentId.startsWith("playlist_id_")) {
                        val playlistId = parentId.removePrefix("playlist_id_")
                        repository.getPlaylist(playlistId).getOrNull()?.playlist?.entry?.map { song ->
                            createPlayableItem(
                                id = song.id,
                                title = song.title,
                                artist = song.artist,
                                album = song.album ?: "",
                                coverArt = song.coverArt ?: ""
                            )
                        } ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }
    }

    private fun createBrowsableItem(id: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle(title)
                    .build()
            )
            .build()
    }

    private fun createPlayableItem(id: String, title: String, artist: String, album: String, coverArt: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(Uri.parse(repository.getCoverArtUrl(coverArt)))
                    .build()
            )
            .build()
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return scope.future {
            val songs = repository.getSongs().getOrNull() ?: emptyList()
            val q = query.trim()
            val filtered = songs.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    (it.album?.contains(q, ignoreCase = true) == true)
            }.map { song ->
                createPlayableItem(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album ?: "",
                    coverArt = song.coverArt ?: "",
                )
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(filtered), params)
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        return scope.future {
            mediaItems.map { item ->
                val trackId = item.mediaId
                val streamUrl = repository.getStreamUrl(trackId)
                item.buildUpon()
                    .setUri(streamUrl)
                    .build()
            }.toMutableList()
        }
    }
}
