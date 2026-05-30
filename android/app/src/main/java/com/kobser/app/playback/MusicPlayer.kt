package com.kobser.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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

data class PlaybackProgress(val positionMs: Long, val durationMs: Long)

private data class LastTrackPayload(val song: Song, val positionMs: Long)

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
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncQueueFromController()
                saveLastTrack()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleOn.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
            }
        })
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
        _queue.value = _queue.value + song
        c.addMediaItem(song.toMediaItem())
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index == _currentIndex.value) return
        val list = _queue.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _queue.value = list
        c.removeMediaItem(index)
        if (index < _currentIndex.value) _currentIndex.value -= 1
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

    fun clearUpcoming() {
        val c = controller ?: return
        val cur = _currentIndex.value
        if (cur < 0) return
        val total = _queue.value.size
        if (cur + 1 >= total) return
        _queue.value = _queue.value.take(cur + 1)
        for (i in (total - 1) downTo (cur + 1)) c.removeMediaItem(i)
    }

    // ── Transport controls ──────────────────────────────────────────────────

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
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
        val song = currentSong.value
        val c = controller
        if (song == null || c == null) {
            scope.launch { prefs.clearLastTrack() }
            return
        }
        val payload = LastTrackPayload(
            song = song,
            positionMs = c.currentPosition.coerceAtLeast(0L),
        )
        scope.launch { prefs.saveLastTrack(gson.toJson(payload)) }
    }

    private fun restoreLastTrack() {
        scope.launch {
            val json = prefs.lastTrackJson.first() ?: return@launch
            val payload = try {
                gson.fromJson(json, LastTrackPayload::class.java)
            } catch (_: Exception) {
                prefs.clearLastTrack()
                return@launch
            }
            val c = controller ?: return@launch
            _queue.value = listOf(payload.song)
            _currentIndex.value = 0
            c.setMediaItems(
                listOf(payload.song.toMediaItem()),
                0,
                payload.positionMs.coerceAtLeast(0L),
            )
            c.prepare()
            // Don't autoplay — mirror the web behavior of restoring but staying paused.
            _progress.value = PlaybackProgress(
                positionMs = payload.positionMs,
                durationMs = payload.song.duration * 1000L,
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

    // ── Legacy shim ──────────────────────────────────────────────────────────
    // Existing call site: LibraryViewModel.play(song) → keep working as a
    // single-track queue while the UI migrates to playQueue.
    fun play(song: Song) = playQueue(listOf(song), 0)
}
