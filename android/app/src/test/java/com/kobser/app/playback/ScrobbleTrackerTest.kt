package com.kobser.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrobbleTrackerTest {
    @Test
    fun `scrobbles once at half the track`() {
        val t = ScrobbleTracker()
        assertEquals("a", t.onTrackStarted("a"))
        assertNull(t.onProgress(10_000, 200_000))
        assertNull(t.onProgress(99_000, 200_000))
        assertEquals("a", t.onProgress(100_000, 200_000))
        assertNull(t.onProgress(150_000, 200_000))
        assertNull(t.onProgress(199_000, 200_000))
    }

    @Test
    fun `caps the threshold at four minutes for long tracks`() {
        val t = ScrobbleTracker()
        t.onTrackStarted("long")
        assertNull(t.onProgress(239_000, 20 * 60_000))
        assertEquals("long", t.onProgress(240_000, 20 * 60_000))
    }

    @Test
    fun `never scrobbles very short tracks or previews without an id`() {
        val t = ScrobbleTracker()
        t.onTrackStarted("short")
        assertNull(t.onProgress(29_000, 29_000))
        assertNull(t.onTrackStarted(null))
        assertNull(t.onProgress(500_000, 200_000))
    }

    @Test
    fun `a new track resets the submission`() {
        val t = ScrobbleTracker()
        t.onTrackStarted("a")
        assertEquals("a", t.onProgress(100_000, 200_000))
        t.onTrackStarted("b")
        assertEquals("b", t.onProgress(100_000, 200_000))
    }

    @Test
    fun `unknown duration falls back to four minutes`() {
        val t = ScrobbleTracker()
        t.onTrackStarted("x")
        assertNull(t.onProgress(100_000, 0))
        assertEquals("x", t.onProgress(240_000, 0))
    }
}
