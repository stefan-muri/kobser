package com.kobser.app.playback

import com.google.gson.Gson
import com.kobser.app.data.api.Song

/**
 * The play queue persisted across app runs. Written by [MusicPlayer] whenever the
 * current track changes, read back on the next launch and by
 * [LibrarySessionCallback.onPlaybackResumption] when Android Auto, Bluetooth or the
 * system media-resumption UI asks us to resume without the phone UI being open.
 */
data class SavedQueue(
    val songs: List<Song>,
    val currentIndex: Int,
    val positionMs: Long,
) {
    fun toJson(gson: Gson): String =
        gson.toJson(Payload(songs = songs, currentIndex = currentIndex, positionMs = positionMs))

    // Serialised shape. Older builds saved a single `song`; keep reading that.
    private data class Payload(
        val song: Song? = null,
        val songs: List<Song>? = null,
        val currentIndex: Int = 0,
        val positionMs: Long = 0,
    )

    companion object {
        /** Returns null when there's nothing usable to restore. */
        fun parse(json: String?, gson: Gson): SavedQueue? {
            if (json.isNullOrBlank()) return null
            val payload = try {
                gson.fromJson(json, Payload::class.java)
            } catch (_: Exception) {
                null
            } ?: return null
            val songs = payload.songs?.takeIf { it.isNotEmpty() }
                ?: payload.song?.let { listOf(it) }
                ?: return null
            return SavedQueue(
                songs = songs,
                currentIndex = payload.currentIndex.coerceIn(0, songs.size - 1),
                positionMs = payload.positionMs.coerceAtLeast(0L),
            )
        }
    }
}
