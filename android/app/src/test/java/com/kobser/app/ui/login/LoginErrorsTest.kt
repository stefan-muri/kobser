package com.kobser.app.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginErrorsTest {
    @Test
    fun `maps the statuses the backend actually returns`() {
        assertEquals("Wrong username or password", loginErrorMessage(401))
        assertTrue(loginErrorMessage(429).contains("Too many"))
        assertTrue(loginErrorMessage(502).contains("music server"))
    }

    @Test
    fun `unknown codes keep the number visible`() {
        assertEquals("Login failed (HTTP 500)", loginErrorMessage(500))
    }
}
