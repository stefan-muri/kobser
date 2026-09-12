package com.kobser.app.playback

import java.net.URI

/**
 * Cache keys for streamed audio. The stream URLs carry the login session as a query
 * parameter, so keying on the raw URL would miss the cache after every re-login;
 * key on what identifies the audio instead.
 */
object StreamCacheKeys {
    fun keyFor(url: String): String {
        val path = try { URI(url).path.orEmpty() } catch (_: Exception) { "" }
        return when {
            path.startsWith("/api/stream/") -> "stream:" + path.removePrefix("/api/stream/")
            path.startsWith("/api/preview/") -> "preview:" + path.removePrefix("/api/preview/")
            else -> url.substringBefore('?')
        }
    }
}
