package com.kobser.app.offline

import com.kobser.app.data.api.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineSongStatesTest {
    private fun song(id: String) = Song(
        id = id, parent = null, title = "t$id", album = null, artist = "a", track = null, year = null,
        genre = null, coverArt = null, duration = 100, bitRate = null, contentType = null, suffix = null,
        size = null, albumId = null, artistId = null, type = null, created = null, starred = null,
    )

    private val album = OfflineCollection("album:1", "A", "x", null, listOf(song("a"), song("b")), 1)

    @Test
    fun `songs in a kept album are IN_COLLECTION, others absent`() {
        val s = offlineSongStates(OfflineCatalog(listOf(album)), emptyMap())
        assertEquals(OfflineSongState.IN_COLLECTION, s["a"])
        assertNull(s["z"])
    }

    @Test
    fun `a song kept on its own is DOWNLOADING until its download completes`() {
        val pinned = OfflineCollection.forSong(song("z"), now = 2)
        val catalog = OfflineCatalog(listOf(pinned))
        assertEquals(OfflineSongState.DOWNLOADING, offlineSongStates(catalog, emptyMap())["z"])
        assertEquals(OfflineSongState.DOWNLOADING,
            offlineSongStates(catalog, mapOf("song:z" to OfflineProgress(0, 1, 0, running = true)))["z"])
        assertEquals(OfflineSongState.DOWNLOADED,
            offlineSongStates(catalog, mapOf("song:z" to OfflineProgress(1, 1, 0, running = false)))["z"])
    }

    @Test
    fun `own pin wins over album membership so it stays removable`() {
        val catalog = OfflineCatalog(listOf(album, OfflineCollection.forSong(song("a"), now = 3)))
        val s = offlineSongStates(catalog, mapOf("song:a" to OfflineProgress(1, 1, 0, running = false)))
        assertEquals(OfflineSongState.DOWNLOADED, s["a"])
        assertEquals(OfflineSongState.IN_COLLECTION, s["b"])
    }

    @Test
    fun `song keys and helpers`() {
        val c = OfflineCollection.forSong(song("q"), now = 9)
        assertEquals("song:q", c.key)
        assertEquals(true, c.isSong)
        assertEquals("q", c.sourceId)
        assertEquals(false, album.isSong)
    }
}
