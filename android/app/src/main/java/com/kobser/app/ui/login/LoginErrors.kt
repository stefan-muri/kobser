package com.kobser.app.ui.login

/** Turns a login HTTP status into something a person can act on. */
fun loginErrorMessage(code: Int): String = when (code) {
    401 -> "Wrong username or password"
    429 -> "Too many failed attempts — wait a few minutes and try again"
    502, 503, 504 -> "Kobser can't reach the music server — check that Navidrome is running"
    404 -> "That URL isn't a Kobser server"
    else -> "Login failed (HTTP $code)"
}
