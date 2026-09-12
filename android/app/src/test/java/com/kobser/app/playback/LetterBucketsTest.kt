package com.kobser.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterBucketsTest {
    @Test
    fun `buckets by first letter, ignoring case, accents and leading junk`() {
        assertEquals("A", letterBucket("abba"))
        assertEquals("E", letterBucket("Élan"))
        assertEquals("T", letterBucket("  (the) thing"))
    }

    @Test
    fun `digits and non-latin go to hash`() {
        assertEquals("#", letterBucket("2001"))
        assertEquals("#", letterBucket("東京"))
        assertEquals("#", letterBucket(""))
    }

    @Test
    fun `groups sorted A to Z with hash last and titles sorted inside`() {
        val grouped = groupByLetter(listOf("zed", "Apple", "1999", "avocado", "Banana")) { it }
        assertEquals(listOf("A", "B", "Z", "#"), grouped.map { it.first })
        assertEquals(listOf("Apple", "avocado"), grouped[0].second)
    }
}
