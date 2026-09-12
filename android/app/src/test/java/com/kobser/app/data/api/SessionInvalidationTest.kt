package com.kobser.app.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionInvalidationTest {

    @Test
    fun `401 on an authenticated endpoint invalidates the session`() {
        assertTrue(isSessionInvalidResponse("/api/library/getArtists", 401))
        assertTrue(isSessionInvalidResponse("/api/me", 401))
        assertTrue(isSessionInvalidResponse("/api/stream/abc123", 401))
    }

    @Test
    fun `401 on login is a bad password, not a dead session`() {
        assertFalse(isSessionInvalidResponse("/api/login", 401))
    }

    @Test
    fun `other status codes never invalidate the session`() {
        assertFalse(isSessionInvalidResponse("/api/library/getArtists", 200))
        assertFalse(isSessionInvalidResponse("/api/library/getArtists", 403))
        assertFalse(isSessionInvalidResponse("/api/library/getArtists", 502))
        assertFalse(isSessionInvalidResponse("/api/login", 429))
    }
}
