package com.kobser.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCacheKeysTest {
    @Test
    fun `library streams key on the track id, not the session`() {
        assertEquals(
            "stream:abc123",
            StreamCacheKeys.keyFor("http://10.0.0.5:8000/api/stream/abc123?session=one"),
        )
        assertEquals(
            StreamCacheKeys.keyFor("http://10.0.0.5:8000/api/stream/abc123?session=one"),
            StreamCacheKeys.keyFor("https://music.example.com/api/stream/abc123?session=two"),
        )
    }

    @Test
    fun `previews key on the video id`() {
        assertEquals("preview:dQw4w9WgXcQ", StreamCacheKeys.keyFor("http://h/api/preview/dQw4w9WgXcQ?session=s"))
    }

    @Test
    fun `different tracks never share a key`() {
        assertEquals(false, StreamCacheKeys.keyFor("http://h/api/stream/a?session=s") == StreamCacheKeys.keyFor("http://h/api/stream/b?session=s"))
    }

    @Test
    fun `unknown urls fall back to the url without its query`() {
        assertEquals("http://h/other/thing", StreamCacheKeys.keyFor("http://h/other/thing?x=1"))
    }
}
