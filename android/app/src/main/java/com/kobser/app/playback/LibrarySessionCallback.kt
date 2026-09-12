package com.kobser.app.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.kobser.app.MainActivity
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

@UnstableApi
@Singleton
class LibrarySessionCallback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val api: KobserApi,
    private val prefs: PreferencesRepository,
    private val gson: Gson,
) : MediaLibraryService.MediaLibrarySession.Callback {

    private val imageLoader = ImageLoader(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CMD_SHUFFLE = "com.kobser.app.SHUFFLE"
        private const val CMD_REPEAT = "com.kobser.app.REPEAT"
        /** Above this many songs the "Songs" folder splits into A–Z sub-folders. */
        private const val FLAT_SONG_LIST_MAX = 150
    }

    // ── Shuffle / repeat as custom Android Auto buttons ──────────────────────
    // Android Auto's now-playing screen doesn't surface these reliably from the
    // player commands, so we publish them as custom session commands, using
    // Media3's built-in icon constants so every surface (Auto, notification,
    // Wear) renders them consistently.

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        // Do NOT build on super.onConnect(): since Media3 1.9 it returns a placeholder
        // with EMPTY command sets (a "callback not implemented" marker), so deriving
        // from it silently strips every controller of play/prepare/set-queue.
        // Grant the full default set to every controller, as the app always has —
        // the phone UI, Android Auto, Bluetooth and system UI all need it.
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .add(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            .setMediaButtonPreferences(buildMediaButtons(session.player))
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
        session.setMediaButtonPreferences(buildMediaButtons(player))
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /** Builds the shuffle + repeat command buttons reflecting the player's current state. */
    fun buildMediaButtons(player: Player): ImmutableList<CommandButton> {
        val shuffleOn = player.shuffleModeEnabled
        val shuffle = CommandButton.Builder(
            if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .setDisplayName(if (shuffleOn) "Shuffle on" else "Shuffle off")
            .build()
        val (repeatIcon, repeatName) = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE to "Repeat one"
            Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL to "Repeat all"
            else -> CommandButton.ICON_REPEAT_OFF to "Repeat off"
        }
        val repeat = CommandButton.Builder(repeatIcon)
            .setSessionCommand(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
            .setDisplayName(repeatName)
            .build()
        return ImmutableList.of(shuffle, repeat)
    }

    // ── Signed-out handling ──────────────────────────────────────────────────
    // Without a session every library call 401s and the browse tree would just be
    // empty. A root-level error would make Android Auto refuse the connection
    // outright, so instead we keep the tree and show a placeholder entry, and send
    // the session an authentication error carrying a "Sign in" action that opens
    // the phone app (which shows the login screen).

    private fun isSignedIn(): Boolean =
        prefs.cachedSessionId.isNotBlank() && prefs.cachedServerUrl.isNotBlank()

    private fun signInRequiredError(): SessionError {
        val signIn = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val extras = Bundle().apply {
            putString(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT, "Sign in")
            putParcelable(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT, signIn)
        }
        return SessionError(
            SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
            "Sign in to Kobser on your phone",
            extras,
        )
    }

    /** Resumes the queue saved by the phone UI when Auto/Bluetooth/system UI asks us to play. */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return scope.future {
            val saved = SavedQueue.parse(prefs.lastTrackJson.first(), gson)
                ?: throw IllegalStateException("nothing to resume")
            val items = resolveUris(saved.songs.map { createPlayableItem(it, contextType = null, contextId = "") })
            MediaSession.MediaItemsWithStartPosition(
                ImmutableList.copyOf(items),
                saved.currentIndex,
                saved.positionMs,
            )
        }
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
            if (!isSignedIn()) {
                session.sendError(browser, signInRequiredError())
                val placeholder = if (parentId == "root") listOf(signInPlaceholderItem()) else emptyList()
                return@future LibraryResult.ofItemList(ImmutableList.copyOf(placeholder), params)
            }
            val items = when (parentId) {
                "root" -> {
                    listOf(
                        createBrowsableItem("recent_played", "Recently played"),
                        createBrowsableItem("recent_added", "Recently added"),
                        createBrowsableItem("favorites", "Favorites"),
                        createBrowsableItem("playlists", "Playlists"),
                        createBrowsableItem("albums", "Albums"),
                        createBrowsableItem("artists", "Artists"),
                        createBrowsableItem("songs", "Songs"),
                    )
                }
                "songs" -> {
                    val songs = repository.getSongs().getOrNull() ?: emptyList()
                    if (songs.size <= FLAT_SONG_LIST_MAX) {
                        songs.map { createPlayableItem(it, contextType = "songs", contextId = "") }
                    } else {
                        // Big library: A–Z folders instead of one enormous list (which the
                        // car UI truncates and takes ages to render).
                        groupByLetter(songs) { it.title }.map { (letter, group) ->
                            createBrowsableItem("songs_letter_$letter", letter, "${group.size} songs")
                        }
                    }
                }
                "albums" -> albumItems(repository.getAlbumList("alphabeticalByName", size = 500).getOrNull())
                "recent_added" -> albumItems(repository.getAlbumList("newest", size = 100).getOrNull())
                "recent_played" -> albumItems(repository.getAlbumList("recent", size = 100).getOrNull())
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
                    if (parentId.startsWith("songs_letter_")) {
                        val letter = parentId.removePrefix("songs_letter_")
                        loadContextSongs("songs_letter", letter).map { song ->
                            createPlayableItem(song, contextType = "songs_letter", contextId = letter)
                        }
                    } else if (parentId.startsWith("artist_id_")) {
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
            // A 401 mid-browse makes the auth interceptor drop the session; say so
            // rather than returning an empty folder.
            if (!isSignedIn()) {
                session.sendError(browser, signInRequiredError())
                val placeholder = if (parentId == "root") listOf(signInPlaceholderItem()) else emptyList()
                return@future LibraryResult.ofItemList(ImmutableList.copyOf(placeholder), params)
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(attachArtwork(items)), params)
        }
    }

    private fun signInPlaceholderItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("signin")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle("Sign in on your phone")
                    .setSubtitle("Open Kobser to log in, then come back")
                    .build()
            )
            .build()

    private fun createBrowsableItem(id: String, title: String, subtitle: String? = null): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .build()
            )
            .build()
    }

    /** Browsable album entries (getAlbumList2 results) that open into the album's tracks. */
    private fun albumItems(response: com.kobser.app.data.api.SubsonicResponse?): List<MediaItem> =
        response?.albumList2?.album?.map { album ->
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

    /** Loads the full song list backing a context, for queue expansion. */
    private suspend fun loadContextSongs(contextType: String, contextId: String): List<Song> =
        when (contextType) {
            "songs" -> repository.getSongs().getOrNull() ?: emptyList()
            "songs_letter" -> (repository.getSongs().getOrNull() ?: emptyList())
                .filter { letterBucket(it.title) == contextId }
                .sortedBy { it.title.lowercase() }
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
