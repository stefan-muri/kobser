package com.kobser.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import com.kobser.app.data.api.Song
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.data.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class RepeatMode { OFF, ONE, ALL }

/** Active sleep timer: pause at [endsAtMs] (epoch), or when the current track ends. */
data class SleepTimer(val endsAtMs: Long? = null, val atTrackEnd: Boolean = false) {
    val isActive: Boolean get() = endsAtMs != null || atTrackEnd
}

data class PlaybackProgress(val positionMs: Long, val durationMs: Long)

@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepo: LibraryRepository,
    private val prefs: PreferencesRepository,
    private val gson: Gson,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var pendingPlay: (() -> Unit)? = null

    // ── State flows ──────────────────────────────────────────────────────────

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    val currentSong: StateFlow<Song?> = combine(_queue, _currentIndex) { q, i ->
        q.getOrNull(i)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val isCurrentLiked: StateFlow<Boolean> = currentSong
        .map { song -> !song?.starred.isNullOrEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _shuffleOn = MutableStateFlow(false)
    val shuffleOn: StateFlow<Boolean> = _shuffleOn.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress(0L, 0L))
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    /**
     * Queue indices in the order they will actually play — the linear queue, or the
     * player's shuffle order when shuffle is on. The queue sheet renders this so
     * "up next" is truthful.
     */
    private val _playOrder = MutableStateFlow<List<Int>>(emptyList())
    val playOrder: StateFlow<List<Int>> = _playOrder.asStateFlow()

    private fun updatePlayOrder() {
        val c = controller ?: return
        val timeline = c.currentTimeline
        if (timeline.isEmpty) {
            _playOrder.value = emptyList()
            return
        }
        val shuffle = c.shuffleModeEnabled
        val order = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET && order.size <= timeline.windowCount) {
            order.add(i)
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        _playOrder.value = order
    }

    // ── Sleep timer ─────────────────────────────────────────────────────────

    private val _sleepTimer = MutableStateFlow(SleepTimer())
    val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimer.value = SleepTimer(endsAtMs = endsAt)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _sleepTimer.value = SleepTimer()
        }
    }

    fun setSleepAtTrackEnd() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepTimer.value = SleepTimer(atTrackEnd = true)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepTimer.value = SleepTimer()
    }

    /** Human-readable reason the last playback attempt failed; null once dismissed. */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    fun dismissPlaybackError() { _playbackError.value = null }

    // ── Init ─────────────────────────────────────────────────────────────────

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get()
            wireListeners()
            val pending = pendingPlay
            if (pending != null) {
                pendingPlay = null
                pending.invoke()
            } else {
                restoreLastTrack()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun wireListeners() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startProgressLoop() else stopProgressLoop()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncQueueFromController()
                updatePlayOrder()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (_sleepTimer.value.atTrackEnd && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    controller?.pause()
                    _sleepTimer.value = SleepTimer()
                }
                syncQueueFromController()
                updatePlayOrder()
                saveLastTrack()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleOn.value = shuffleModeEnabled
                updatePlayOrder()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackError.value = describePlaybackError(error)
            }
        })
    }

    private fun describePlaybackError(error: PlaybackException): String {
        val song = currentSong.value
        val http = error.cause as? HttpDataSource.InvalidResponseCodeException
        return when {
            http != null && song?.isPreview() == true && http.responseCode == 502 ->
                "The server couldn't fetch this preview from YouTube (check its logs)"
            http != null && http.responseCode == 401 -> "Session expired — please sign in again"
            http != null && http.responseCode == 404 -> "This track is no longer on the server"
            http != null -> "Playback failed (HTTP ${http.responseCode})"
            error.cause is HttpDataSource.HttpDataSourceException -> "Network error — check your connection"
            else -> "Playback failed (${error.errorCodeName})"
        }
    }

    /**
     * Mirrors the controller's timeline into [_queue] / [_currentIndex]. When playback is
     * driven externally — e.g. Android Auto sets a fresh queue directly on the session —
     * our Song list would otherwise be stale, leaving the phone's mini/expanded player
     * blank or wrong. We detect divergence by comparing real track ids and rebuild Songs
     * from each item's metadata + extras only when needed (so phone-initiated playback,
     * which already populated [_queue], is a no-op).
     */
    private fun syncQueueFromController() {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (count == 0) {
            if (_queue.value.isNotEmpty()) _queue.value = emptyList()
            _currentIndex.value = -1
            return
        }
        val controllerIds = (0 until count).map { realTrackId(c.getMediaItemAt(it).mediaId) }
        if (controllerIds != _queue.value.map { it.id }) {
            _queue.value = (0 until count).map { songFromMediaItem(c.getMediaItemAt(it)) }
        }
        _currentIndex.value = c.currentMediaItemIndex
    }

    private fun realTrackId(mediaId: String): String =
        if (mediaId.startsWith("track|")) mediaId.substringAfterLast("|") else mediaId

    /** Rebuilds a Song from a session MediaItem (metadata + the extras we attach when browsing). */
    private fun songFromMediaItem(item: MediaItem): Song {
        val md = item.mediaMetadata
        val ex = md.extras
        return Song(
            id = realTrackId(item.mediaId),
            parent = null,
            title = md.title?.toString() ?: "",
            album = md.albumTitle?.toString(),
            artist = md.artist?.toString() ?: "",
            track = null,
            year = null,
            genre = null,
            coverArt = ex?.getString("coverArt"),
            duration = ex?.getInt("duration") ?: 0,
            bitRate = null,
            contentType = null,
            suffix = null,
            size = null,
            albumId = ex?.getString("albumId"),
            artistId = ex?.getString("artistId"),
            type = null,
            created = null,
            starred = ex?.getString("starred"),
        )
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                snapshotProgress()
                delay(500)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
        snapshotProgress()
        saveLastTrack()
    }

    private fun snapshotProgress() {
        val c = controller ?: return
        _progress.value = PlaybackProgress(
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
        )
    }

    // ── Queue operations ─────────────────────────────────────────────────────

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val c = controller
        if (c == null) {
            pendingPlay = { playQueue(songs, startIndex) }
            return
        }
        if (songs.isEmpty()) return
        _queue.value = songs
        _currentIndex.value = startIndex.coerceIn(0, songs.size - 1)
        c.setMediaItems(songs.map { it.toMediaItem() }, _currentIndex.value, 0L)
        c.prepare()
        c.play()
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (_queue.value.isEmpty() || _currentIndex.value < 0) {
            playQueue(listOf(song), 0)
            return
        }
        // Store a distinct copy so queue entries always have unique object identity
        // (used as the stable reorder key, even when the same song is queued twice).
        _queue.value = _queue.value + song.copy()
        c.addMediaItem(song.toMediaItem())
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        val list = _queue.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _queue.value = list
        // Removing the current item makes ExoPlayer advance to the next; syncQueueFromController
        // then reconciles _currentIndex from the controller.
        c.removeMediaItem(index)
        _currentIndex.value = when {
            list.isEmpty() -> -1
            index < _currentIndex.value -> _currentIndex.value - 1
            index == _currentIndex.value -> _currentIndex.value.coerceAtMost(list.size - 1)
            else -> _currentIndex.value
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        val list = _queue.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        _queue.value = list
        c.moveMediaItem(from, to)
    }

    /** Removes everything that would still play after the current track, in play order. */
    fun clearUpcoming() {
        val c = controller ?: return
        val cur = _currentIndex.value
        if (cur < 0) return
        val order = _playOrder.value.ifEmpty { _queue.value.indices.toList() }
        val pos = order.indexOf(cur)
        if (pos < 0) return
        val upcoming = order.drop(pos + 1).sortedDescending()
        if (upcoming.isEmpty()) return
        val remaining = _queue.value.filterIndexed { i, _ -> i !in upcoming }
        _queue.value = remaining
        upcoming.forEach { c.removeMediaItem(it) }
    }

    // ── Transport controls ──────────────────────────────────────────────────

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            // After a playback error the player sits idle; play() alone does nothing.
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
        } else if (_repeatMode.value == RepeatMode.ALL && _queue.value.isNotEmpty()) {
            c.seekTo(0, 0L)
        }
    }

    fun prev() {
        val c = controller ?: return
        if (c.currentPosition > 3000) {
            c.seekTo(0L)
        } else if (c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
        } else {
            c.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /**
     * Jumps to a specific item in the current queue without rebuilding it.
     * Used by the queue panel when the user taps an upcoming/past track.
     */
    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index !in _queue.value.indices) return
        c.seekTo(index, 0L)
        c.play()
    }

    fun seekFraction(fraction: Float) {
        val c = controller ?: return
        val dur = c.duration
        if (dur <= 0) return
        c.seekTo((fraction.coerceIn(0f, 1f) * dur).toLong())
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun toggleRepeat() {
        val c = controller ?: return
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        }
        c.repeatMode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    /**
     * Toggles the like state of the current song. Optimistically updates the local
     * queue and reverts if the network call fails.
     */
    fun toggleLike() {
        val idx = _currentIndex.value
        val list = _queue.value
        val song = list.getOrNull(idx) ?: return
        if (song.isPreview()) return  // previews aren't in the library
        val wasLiked = !song.starred.isNullOrEmpty()
        val newStarred = if (wasLiked) null else java.time.Instant.now().toString()

        val updated = list.toMutableList()
        updated[idx] = updated[idx].copy(starred = newStarred)
        _queue.value = updated

        scope.launch {
            val result = if (wasLiked) libraryRepo.unstar(song.id) else libraryRepo.star(song.id)
            if (result.isFailure) {
                // Revert
                val reverted = _queue.value.toMutableList()
                if (idx in reverted.indices) {
                    reverted[idx] = reverted[idx].copy(starred = song.starred)
                    _queue.value = reverted
                }
            } else {
                // Keep the library/favorites screens in sync.
                libraryRepo.notifyLibraryChanged()
            }
        }
    }

    fun closePlayer() {
        val c = controller ?: return
        c.pause()
        c.clearMediaItems()
        _queue.value = emptyList()
        _currentIndex.value = -1
        _progress.value = PlaybackProgress(0L, 0L)
        scope.launch { prefs.clearLastTrack() }
    }

    // ── Last-track persistence ──────────────────────────────────────────────

    private fun saveLastTrack() {
        val c = controller
        val songs = _queue.value
        if (songs.isEmpty() || c == null) {
            scope.launch { prefs.clearLastTrack() }
            return
        }
        val saved = SavedQueue(
            songs = songs,
            currentIndex = _currentIndex.value.coerceAtLeast(0),
            positionMs = c.currentPosition.coerceAtLeast(0L),
        )
        scope.launch { prefs.saveLastTrack(saved.toJson(gson)) }
    }

    private fun restoreLastTrack() {
        scope.launch {
            val c = controller ?: return@launch
            // The service may already hold a queue — e.g. Android Auto browsed and
            // started playback before the phone UI was opened. Mirror it instead of
            // replacing it with whatever we saved last time.
            if (c.mediaItemCount > 0) {
                syncQueueFromController()
                return@launch
            }
            val saved = SavedQueue.parse(prefs.lastTrackJson.first(), gson)
            if (saved == null) {
                prefs.clearLastTrack()
                return@launch
            }
            _queue.value = saved.songs
            _currentIndex.value = saved.currentIndex
            c.setMediaItems(
                saved.songs.map { it.toMediaItem() },
                saved.currentIndex,
                saved.positionMs,
            )
            c.prepare()
            _progress.value = PlaybackProgress(
                positionMs = saved.positionMs,
                durationMs = saved.songs.getOrNull(saved.currentIndex)?.duration?.times(1000L) ?: 0L,
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Song.toMediaItem(): MediaItem {
        // The stream URI is resolved by LibrarySessionCallback.onAddMediaItems
        // via the mediaId (which we set to the Subsonic track id).
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
    }

    /**
     * Plays [songs] starting at [index], but if the song at [index] is already
     * in the current queue, just jumps to it instead of replacing the queue.
     */
    fun playOrJumpTo(songs: List<Song>, index: Int) {
        val songId = songs.getOrNull(index)?.id ?: return
        val queueIndex = _queue.value.indexOfFirst { it.id == songId }
        if (queueIndex >= 0) {
            jumpTo(queueIndex)
        } else {
            playQueue(songs, index)
        }
    }

    // ── Legacy shim ──────────────────────────────────────────────────────────
    // Existing call site: LibraryViewModel.play(song) → keep working as a
    // single-track queue while the UI migrates to playQueue.
    fun play(song: Song) = playQueue(listOf(song), 0)

    /**
     * Streams a YouTube/YT Music track without downloading it. Represented as a
     * synthetic Song whose id (`preview|<videoId>`) resolves to /api/preview, and
     * whose coverArt is the YT thumbnail URL (passed through by getCoverArtUrl).
     */
    fun playPreview(
        videoId: String,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        durationSeconds: Int,
    ) {
        val preview = Song(
            id = "preview|$videoId",
            parent = null,
            title = title,
            album = null,
            artist = artist,
            track = null,
            year = null,
            genre = null,
            coverArt = thumbnailUrl,
            duration = durationSeconds,
            bitRate = null,
            contentType = null,
            suffix = null,
            size = null,
            albumId = null,
            artistId = null,
            type = null,
            created = null,
            starred = null,
        )
        playQueue(listOf(preview), 0)
    }
}

/** A preview (un-downloaded) track isn't in the library, so it can't be liked or deleted. */
fun Song.isPreview(): Boolean = id.startsWith("preview|")
