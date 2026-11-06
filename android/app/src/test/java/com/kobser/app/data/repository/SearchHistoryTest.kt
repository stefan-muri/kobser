package com.kobser.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {
    @Test
    fun `newest first and duplicates move to the top`() {
        val h = addToSearchHistory(addToSearchHistory(addToSearchHistory(emptyList(), "joji"), "yeat"), "JOJI")
        assertEquals(listOf("JOJI", "yeat"), h)
    }

    @Test
    fun `blank queries are ignored and input is trimmed`() {
        assertEquals(listOf("a"), addToSearchHistory(addToSearchHistory(emptyList(), "  a  "), "   "))
    }

    @Test
    fun `capped at the limit`() {
        var h = emptyList<String>()
        (1..25).forEach { h = addToSearchHistory(h, "q$it") }
        assertEquals(20, h.size)
        assertEquals("q25", h.first())
        assertEquals("q6", h.last())
    }

    @Test
    fun `round-trips through storage`() {
        val h = listOf("tyler the creator", "back home")
        assertEquals(h, decodeSearchHistory(encodeSearchHistory(h)))
        assertEquals(emptyList<String>(), decodeSearchHistory(null))
        assertEquals(emptyList<String>(), decodeSearchHistory(""))
    }
}
