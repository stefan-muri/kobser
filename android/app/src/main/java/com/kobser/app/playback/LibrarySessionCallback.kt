package com.kobser.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kobser.app.R
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.api.SearchRequest
import com.kobser.app.data.api.SearchResult
import com.kobser.app.data.api.Song
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.data.repository.PreferencesRepository
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySessionCallback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val api: KobserApi,
    private val prefs: PreferencesRepository,
) : MediaLibraryService.MediaLibrarySession.Callback {

    private val imageLoader = ImageLoader(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CMD_SHUFFLE = "com.kobser.app.SHUFFLE"
        private const val CMD_REPEAT = "com.kobser.app.REPEAT"
    }

    // ── Shuffle / repeat as custom Android Auto buttons ──────────────────────
    // Android Auto's now-playing screen doesn't surface these reliably from the
    // player commands, so we publish them as custom session commands.

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val base = super.onConnect(session, controller)
        val sessionCommands = base.availableSessionCommands.buildUpon()
            .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .add(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(base.availablePlayerCommands)
            .setCustomLayout(buildCustomLayout(session.player))
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val player = session.player
        when (customCommand.customAction) {
            CMD_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
            CMD_REPEAT -> player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
        session.setCustomLayout(buildCustomLayout(player))
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /** Builds the shuffle + repeat command buttons reflecting the player's current state. */
    fun buildCustomLayout(player: Player): ImmutableList<CommandButton> {
        val shuffle = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .setDisplayName(if (player.shuffleModeEnabled) "Shuffle on" else "Shuffle")
            .setIconResId(R.drawable.ic_shuffle)
            .build()
        val repeat = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
            .setDisplayName("Repeat")
            .setIconResId(
                when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                    else -> R.drawable.ic_repeat
                }
            )
            .build()
        return ImmutableList.of(shuffle, repeat)
    }

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
                                    .setExtras(Bundle().apply { putString("coverArt", artist.coverArt) })
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
                                        .setExtras(Bundle().apply { putString("coverArt", album.coverArt) })
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
            LibraryResult.ofItemList(ImmutableList.copyOf(attachArtwork(items)), params)
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

    // Android Auto's host process loads browse-item artwork from the URI itself and
    // can't fetch our cleartext-http cover-art URLs (in-app loading works because
    // it's the app's own process). So we load the bytes here — in the app process,
    // via the same Coil pipeline — and embed them as artworkData, which AA renders
    // directly. Capped + concurrency-limited to keep browse responsive.

    private suspend fun artworkBytes(coverId: String): ByteArray? = try {
        withTimeoutOrNull(2500) {
            val url = repository.getCoverArtUrl(coverId, 192)
            val result = imageLoader.execute(
                ImageRequest.Builder(context).data(url).allowHardware(false).build()
            )
            (result as? SuccessResult)?.drawable?.toBitmap()?.let { bmp ->
                ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    out.toByteArray()
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Embeds cover-art bytes (read from each item's "coverArt" extra) so AA can show them. */
    private suspend fun attachArtwork(items: List<MediaItem>): List<MediaItem> =
        withTimeoutOrNull(6000) {
            coroutineScope {
                val semaphore = Semaphore(6)
                items.mapIndexed { index, item ->
                    async {
                        val coverId = item.mediaMetadata.extras?.getString("coverArt")
                        if (index < 60 && !coverId.isNullOrBlank()) {
                            val bytes = semaphore.withPermit { artworkBytes(coverId) }
                            if (bytes != null) {
                                return@async item.buildUpon().setMediaMetadata(
                                    item.mediaMetadata.buildUpon()
                                        .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                        .build()
                                ).build()
                            }
                        }
                        item
                    }
                }.awaitAll()
            }
        } ?: items

    /** Attaches resolved stream URIs to a list of playable items. */
    private fun resolveUris(items: List<MediaItem>): List<MediaItem> =
        items.map { item ->
            val mediaId = item.mediaId
            val uri = if (mediaId.startsWith("preview|")) {
                repository.previewUrl(mediaId.removePrefix("preview|"))
            } else {
                repository.getStreamUrl(realTrackId(mediaId))
            }
            item.buildUpon().setUri(uri).build()
        }

    // Cache the last search so onGetSearchResult doesn't re-query the server.
    private var lastSearchQuery: String? = null
    private var lastSearchResults: List<MediaItem> = emptyList()

    private suspend fun runSearch(query: String): List<MediaItem> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        // Local library matches first…
        val songs = repository.getSongs().getOrNull() ?: emptyList()
        val local = songs.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                (it.album?.contains(q, ignoreCase = true) == true)
        }.map { song ->
            // Search results play standalone (no surrounding list to expand into).
            createPlayableItem(song, contextType = null, contextId = "")
        }

        // …then YouTube / YT Music results, which stream as previews when tapped.
        val online = try {
            val source = prefs.searchSource.first()
            val resp = api.search(SearchRequest(query = q, source = source))
            if (resp.isSuccessful) {
                resp.body()?.songs?.map { createPreviewItem(it) } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return attachArtwork(local + online)
    }

    /** A YouTube/YT Music search hit that streams a preview (mediaId resolved by resolveUris). */
    private fun createPreviewItem(result: SearchResult): MediaItem {
        return MediaItem.Builder()
            .setMediaId("preview|${result.videoId}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setTitle(result.title)
                    .setArtist(result.channel)
                    .setArtworkUri(if (result.thumbnail.isNotBlank()) Uri.parse(result.thumbnail) else null)
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
        return scope.future {
            val results = runSearch(query)
            lastSearchQuery = query
            lastSearchResults = results
            // Tell the browser (Android Auto) results are ready, otherwise it
            // spins forever waiting and never calls onGetSearchResult.
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }
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
            val results = if (query == lastSearchQuery) lastSearchResults else runSearch(query)
            val pageItems = results.drop(page * pageSize).take(pageSize)
            LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params)
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
