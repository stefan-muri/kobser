package com.kobser.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kobser.app.data.api.Song
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
                        createBrowsableItem("songs", "Songs"),
                        createBrowsableItem("artists", "Artists"),
                        createBrowsableItem("playlists", "Playlists"),
                        createBrowsableItem("favorites", "Favorites")
                    )
                }
                "songs" -> {
                    repository.getSongs().getOrNull()?.map { song ->
                        createPlayableItem(song, contextType = "songs", contextId = "")
                    } ?: emptyList()
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
                        createPlayableItem(song, contextType = "favorites", contextId = "")
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
                            createPlayableItem(song, contextType = "album", contextId = albumId)
                        } ?: emptyList()
                    } else if (parentId.startsWith("playlist_id_")) {
                        val playlistId = parentId.removePrefix("playlist_id_")
                        repository.getPlaylist(playlistId).getOrNull()?.playlist?.entry?.map { song ->
                            createPlayableItem(song, contextType = "playlist", contextId = playlistId)
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

    /**
     * Builds a playable item. When [contextType] is non-null the mediaId encodes the
     * surrounding list (e.g. "track|album|<albumId>|<trackId>") so that tapping it in
     * Android Auto can be expanded into the whole list — see [onSetMediaItems].
     * A null context yields a bare trackId mediaId, which plays standalone.
     */
    private fun createPlayableItem(
        song: Song,
        contextType: String?,
        contextId: String,
    ): MediaItem {
        val mediaId = if (contextType != null) "track|$contextType|$contextId|${song.id}" else song.id
        // Carry the fields the phone UI needs so it can rebuild a Song when playback
        // is driven externally (Android Auto). See MusicPlayer.songFromMediaItem.
        val extras = Bundle().apply {
            putString("coverArt", song.coverArt)
            putInt("duration", song.duration)
            putString("starred", song.starred)
            putString("albumId", song.albumId)
            putString("artistId", song.artistId)
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album ?: "")
                    .setArtworkUri(Uri.parse(repository.getCoverArtUrl(song.coverArt ?: "")))
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    /** Extracts the real Subsonic track id from a (possibly context-encoded) mediaId. */
    private fun realTrackId(mediaId: String): String =
        if (mediaId.startsWith("track|")) mediaId.substringAfterLast("|") else mediaId

    /** Loads the full song list backing a context, for queue expansion. */
    private suspend fun loadContextSongs(contextType: String, contextId: String): List<Song> =
        when (contextType) {
            "songs" -> repository.getSongs().getOrNull() ?: emptyList()
            "favorites" -> repository.getFavorites().getOrNull()?.starred?.song ?: emptyList()
            "album" -> repository.getAlbum(contextId).getOrNull()?.album?.song ?: emptyList()
            "playlist" -> repository.getPlaylist(contextId).getOrNull()?.playlist?.entry ?: emptyList()
            else -> emptyList()
        }

    /** Attaches resolved stream URIs to a list of playable items. */
    private fun resolveUris(items: List<MediaItem>): List<MediaItem> =
        items.map { item ->
            item.buildUpon()
                .setUri(repository.getStreamUrl(realTrackId(item.mediaId)))
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
                // Search results play standalone (no surrounding list to expand into).
                createPlayableItem(song, contextType = null, contextId = "")
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
            resolveUris(mediaItems).toMutableList()
        }
    }

    /**
     * When Android Auto plays a browsed song, it sets a single media item. We expand
     * that into the full list it belongs to (album / playlist / favorites / all songs)
     * and start playback at the tapped track, so the rest of the list queues up.
     *
     * Multi-item sets (the phone app already supplies a full queue) and standalone
     * items (search results) fall through to the default behaviour.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val single = mediaItems.singleOrNull()
        if (single != null && single.mediaId.startsWith("track|")) {
            val parts = single.mediaId.removePrefix("track|").split("|")
            val contextType = parts.getOrElse(0) { "" }
            val contextId = parts.getOrElse(1) { "" }
            val tappedTrackId = parts.last()
            return scope.future {
                val songs = loadContextSongs(contextType, contextId)
                if (songs.isEmpty()) {
                    val resolved = resolveUris(mediaItems)
                    MediaSession.MediaItemsWithStartPosition(ImmutableList.copyOf(resolved), 0, startPositionMs)
                } else {
                    val items = songs.map { createPlayableItem(it, contextType, contextId) }
                    val index = songs.indexOfFirst { it.id == tappedTrackId }.coerceAtLeast(0)
                    val resolved = resolveUris(items)
                    MediaSession.MediaItemsWithStartPosition(ImmutableList.copyOf(resolved), index, C.TIME_UNSET)
                }
            }
        }
        return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
    }
}
