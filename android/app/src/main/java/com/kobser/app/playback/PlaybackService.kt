package com.kobser.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.kobser.app.MainActivity
import com.kobser.app.R
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.data.repository.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null

    @Inject
    lateinit var callback: LibrarySessionCallback

    @Inject
    lateinit var prefs: PreferencesRepository

    @Inject
    lateinit var streamCache: StreamCache

    @Inject
    lateinit var repository: LibraryRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // ExoPlayer must only be touched from the thread it was created on (main).
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        val notificationProvider = DefaultMediaNotificationProvider(this)
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

        val player = ExoPlayer.Builder(this)
            // Route every stream through the on-device cache (see StreamCache).
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(streamCache.dataSourceFactory())
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(CoilBitmapLoader(this))
            .build()

        Scrobbler(player, repository, playerScope).attach()

        player.addListener(object : Player.Listener {
            // Keep the shuffle/repeat buttons in sync when the state changes anywhere
            // (e.g. toggled from the phone UI), so Android Auto reflects it.
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled) reseedShuffle(player)
                mediaSession?.let { it.setMediaButtonPreferences(callback.buildMediaButtons(it.player)) }
            }

            // A freshly set queue gets a blind random order from ExoPlayer; re-seed so
            // shuffle starts from the item that was chosen and covers the whole queue.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && player.shuffleModeEnabled) {
                    reseedShuffle(player)
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                mediaSession?.let { it.setMediaButtonPreferences(callback.buildMediaButtons(it.player)) }
            }

            // Streams carry the session in the URL and bypass OkHttp, so a dead
            // session surfaces here as a 401 from the data source. Drop it the same
            // way the API interceptor does, so the phone UI returns to login.
            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 401) {
                    serviceScope.launch { prefs.clearSession() }
                }
            }
        })
    }

    /**
     * ExoPlayer's default shuffle order is a plain random permutation, which leaves
     * the current track at an arbitrary point in it — with repeat off, playback can
     * stop after a handful of songs. Use an order that starts at the current track.
     */
    private fun reseedShuffle(player: ExoPlayer) {
        val count = player.mediaItemCount
        if (count < 2) return
        val order = shuffledOrderStartingAt(count, player.currentMediaItemIndex)
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order, Random.nextLong()))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    // onTaskRemoved is left to Media3's default, which stops the service when the
    // player isn't set to play (and handles the Android 14+ foreground-service rules).

    override fun onDestroy() {
        serviceScope.cancel()
        playerScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
