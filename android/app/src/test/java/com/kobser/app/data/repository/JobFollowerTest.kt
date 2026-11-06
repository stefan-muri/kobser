package com.kobser.app.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobFollowerTest {
    private fun script(vararg steps: Pair<String, String?>?): suspend () -> Pair<String, String?>? {
        val it = steps.iterator()
        return { if (it.hasNext()) it.next() else error("polled past the end") }
    }

    @Test
    fun `follows a job to done, reporting each status once`() = runBlocking {
        val seen = mutableListOf<String>()
        val outcome = followJob(
            fetch = script("pending" to null, "downloading" to null, "downloading" to null, "scanning" to null, "done" to null),
            onStatus = { seen += it }, sleep = {},
        )
        assertTrue(outcome.succeeded)
        assertEquals(listOf("pending", "downloading", "scanning", "done"), seen)
    }

    @Test
    fun `transient failures are retried, not treated as finished`() = runBlocking {
        val outcome = followJob(
            fetch = script(null, "downloading" to null, null, "done" to null),
            onStatus = {}, sleep = {},
        )
        assertTrue(outcome.succeeded)
    }

    @Test
    fun `fetch exceptions are retried too`() = runBlocking {
        var calls = 0
        val outcome = followJob(
            fetch = { calls++; if (calls < 3) throw RuntimeException("timeout") else "done" to null },
            onStatus = {}, sleep = {},
        )
        assertTrue(outcome.succeeded)
        assertEquals(3, calls)
    }

    @Test
    fun `errors carry the server's reason`() = runBlocking {
        val outcome = followJob(fetch = script("downloading" to null, "error" to "bot check (needs cookies)"), onStatus = {}, sleep = {})
        assertFalse(outcome.succeeded)
        assertEquals("error", outcome.status)
        assertEquals("bot check (needs cookies)", outcome.error)
    }

    @Test
    fun `gives up after the timeout`() = runBlocking {
        var slept = 0L
        val outcome = followJob(
            fetch = { "downloading" to null }, onStatus = {},
            pollMs = 1_000, timeoutMs = 5_000, sleep = { slept += it },
        )
        assertEquals("timeout", outcome.status)
        assertEquals(5_000L, slept)
    }
}
