package com.kobser.app.offline

import com.google.gson.Gson
import com.kobser.app.data.api.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineCatalogTest {
    private val gson = Gson()

    private fun song(id: String) = Song(
        id = id, parent = null, title = "t$id", album = null, artist = "a", track = null, year = null,
        genre = null, coverArt = null, duration = 100, bitRate = null, contentType = null, suffix = null,
        size = null, albumId = null, artistId = null, type = null, created = null, starred = null,
    )

    private fun coll(key: String, ids: List<String>, at: Long = 0) =
        OfflineCollection(key, "T", "S", null, ids.map(::song), at)

    @Test
    fun `upsert replaces by key and remove drops it`() {
        val c = OfflineCatalog().upsert(coll("album:1", listOf("a"))).upsert(coll("album:1", listOf("a", "b")))
        assertEquals(1, c.collections.size)
        assertEquals(listOf("a", "b"), c.find("album:1")!!.songs.map { it.id })
        assertNull(c.remove("album:1").find("album:1"))
    }

    @Test
    fun `songs shared with another collection are kept on unpin`() {
        val c = OfflineCatalog()
            .upsert(coll("album:1", listOf("a", "b", "c")))
            .upsert(coll("playlist:9", listOf("b", "z")))
        assertEquals(setOf("a", "c"), c.songsOnlyIn("album:1"))
        assertEquals(setOf("z"), c.songsOnlyIn("playlist:9"))
        assertEquals(emptySet<String>(), c.songsOnlyIn("missing"))
    }

    @Test
    fun `allSongs is deduplicated, newest collection first`() {
        val c = OfflineCatalog()
            .upsert(coll("album:1", listOf("a", "b"), at = 1))
            .upsert(coll("playlist:9", listOf("b", "z"), at = 2))
        assertEquals(listOf("b", "z", "a"), c.allSongs().map { it.id })
    }

    @Test
    fun `round-trips through json and survives garbage`() {
        val c = OfflineCatalog().upsert(coll("album:1", listOf("a"), at = 5))
        val back = OfflineCatalog.fromJson(c.toJson(gson), gson)
        assertEquals(c, back)
        assertEquals(OfflineCatalog(), OfflineCatalog.fromJson("nope", gson))
        assertEquals(OfflineCatalog(), OfflineCatalog.fromJson(null, gson))
    }
}
