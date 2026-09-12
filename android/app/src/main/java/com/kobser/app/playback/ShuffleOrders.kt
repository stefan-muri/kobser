package com.kobser.app.playback

import kotlin.random.Random

/**
 * A random play order over [count] items that starts at [current], so enabling shuffle
 * mid-queue plays every other track exactly once instead of stopping wherever the
 * current track happened to land in a blind permutation.
 */
fun shuffledOrderStartingAt(count: Int, current: Int, random: Random = Random): IntArray {
    if (count <= 0) return IntArray(0)
    val start = current.coerceIn(0, count - 1)
    val rest = (0 until count).filter { it != start }.shuffled(random)
    return (listOf(start) + rest).toIntArray()
}
