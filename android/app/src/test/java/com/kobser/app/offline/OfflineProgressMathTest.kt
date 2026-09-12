package com.kobser.app.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineProgressMathTest {
    private val keys = listOf("a", "b", "c", "d")

    @Test
    fun `counts completed, failed and still-running`() {
        val p = collectionProgress(
            keys,
            mapOf("a" to DownloadStates.COMPLETED, "b" to DownloadStates.FAILED, "c" to DownloadStates.DOWNLOADING),
            cachedKeys = setOf("d"),
        )
        assertEquals(OfflineProgress(done = 2, total = 4, failed = 1, running = true), p)
        assertEquals(false, p.complete)
    }

    @Test
    fun `everything cached with no records is complete`() {
        val p = collectionProgress(keys, emptyMap(), keys.toSet())
        assertEquals(OfflineProgress(4, 4, 0, running = false), p)
        assertEquals(true, p.complete)
    }

    @Test
    fun `a song with no record and no cache counts as pending`() {
        val p = collectionProgress(keys, mapOf("a" to DownloadStates.COMPLETED), setOf("b"))
        assertEquals(OfflineProgress(2, 4, 0, running = true), p)
    }
}
