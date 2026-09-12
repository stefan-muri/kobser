package com.kobser.app.playback

/**
 * Decides when a play counts. Follows the Last.fm rule Navidrome expects: a track
 * is scrobbled once after half its length or four minutes, whichever comes first,
 * and tracks under 30 seconds never are.
 */
class ScrobbleTracker {
    private var trackId: String? = null
    private var submitted = false

    /** Call on every track change. Returns the id to report as "now playing", if any. */
    fun onTrackStarted(id: String?): String? {
        trackId = id
        submitted = false
        return id
    }

    /** Call periodically while playing. Returns the id to scrobble the first time the threshold is crossed. */
    fun onProgress(positionMs: Long, durationMs: Long): String? {
        val id = trackId ?: return null
        if (submitted) return null
        if (durationMs in 1 until MIN_TRACK_MS) return null
        val threshold = if (durationMs > 0) minOf(durationMs / 2, MAX_THRESHOLD_MS) else MAX_THRESHOLD_MS
        if (positionMs < threshold) return null
        submitted = true
        return id
    }

    companion object {
        const val MIN_TRACK_MS = 30_000L
        const val MAX_THRESHOLD_MS = 4 * 60_000L
    }
}
