package com.kobser.app.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleOrdersTest {
    @Test
    fun `starts at the current item and visits every other item once`() {
        val order = shuffledOrderStartingAt(10, 4, Random(1))
        assertEquals(4, order[0])
        assertEquals((0 until 10).toSet(), order.toSet())
        assertEquals(10, order.size)
    }

    @Test
    fun `is actually shuffled`() {
        val orders = (1..5).map { shuffledOrderStartingAt(20, 0, Random(it)).toList() }
        assertTrue(orders.distinct().size > 1)
        assertTrue(orders.any { it != (0 until 20).toList() })
    }

    @Test
    fun `handles edge cases`() {
        assertArrayEquals(IntArray(0), shuffledOrderStartingAt(0, 0))
        assertArrayEquals(intArrayOf(0), shuffledOrderStartingAt(1, 0))
        assertEquals(2, shuffledOrderStartingAt(3, 99, Random(0))[0])
        assertEquals(0, shuffledOrderStartingAt(3, -1, Random(0))[0])
    }
}
