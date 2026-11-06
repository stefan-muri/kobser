package com.kobser.app.offline

import com.google.gson.Gson
import com.kobser.app.data.api.Song

/** An album or playlist the user asked to keep on the phone. */
data class OfflineCollection(
    val key: String,            // "album:<id>" or "playlist:<id>"
    val title: String,
    val subtitle: String,
    val coverArt: String?,
    val songs: List<Song>,
    val pinnedAt: Long,
) {
    val isAlbum: Boolean get() = key.startsWith("album:")
    /** A single song kept on its own ("Download to phone" on a song). */
    val isSong: Boolean get() = key.startsWith("song:")
    val sourceId: String get() = key.substringAfter(':')

    companion object {
        fun albumKey(id: String) = "album:$id"
        fun playlistKey(id: String) = "playlist:$id"
        fun songKey(id: String) = "song:$id"

        fun forSong(song: Song, now: Long = System.currentTimeMillis()) = OfflineCollection(
            key = songKey(song.id),
            title = song.title,
            subtitle = song.artist,
            coverArt = song.coverArt,
            songs = listOf(song),
            pinnedAt = now,
        )
    }
}

/** How a song is kept on the phone, for its row's menu and indicator. */
enum class OfflineSongState {
    /** Not on the phone. */
    NONE,
    /** Kept on its own and still downloading. */
    DOWNLOADING,
    /** Kept on its own and fully downloaded. */
    DOWNLOADED,
    /** On the phone because a kept album or playlist contains it. */
    IN_COLLECTION,
}

/**
 * Offline state of every song the catalog mentions. A song kept on its own wins
 * over one that's only covered by an album or playlist, since only the former
 * can be removed from the song's own menu.
 */
fun offlineSongStates(catalog: OfflineCatalog, progress: Map<String, OfflineProgress>): Map<String, OfflineSongState> {
    val states = HashMap<String, OfflineSongState>()
    for (c in catalog.collections) {
        if (c.isSong) continue
        c.songs.forEach { states[it.id] = OfflineSongState.IN_COLLECTION }
    }
    for (c in catalog.collections) {
        if (!c.isSong) continue
        val p = progress[c.key]
        states[c.sourceId] = if (p == null || p.running || !p.complete) OfflineSongState.DOWNLOADING else OfflineSongState.DOWNLOADED
    }
    return states
}

/** The persisted list of pinned collections, plus the set logic around it. */
data class OfflineCatalog(val collections: List<OfflineCollection> = emptyList()) {

    fun find(key: String): OfflineCollection? = collections.firstOrNull { it.key == key }

    fun upsert(collection: OfflineCollection): OfflineCatalog =
        OfflineCatalog(collections.filterNot { it.key == collection.key } + collection)

    fun remove(key: String): OfflineCatalog = OfflineCatalog(collections.filterNot { it.key == key })

    /** Every distinct song across all pinned collections, most recently pinned first. */
    fun allSongs(): List<Song> =
        collections.sortedByDescending { it.pinnedAt }.flatMap { it.songs }.distinctBy { it.id }

    /** Song ids that only [key] references — safe to evict when it is unpinned. */
    fun songsOnlyIn(key: String): Set<String> {
        val target = find(key) ?: return emptySet()
        val stillNeeded = collections.filterNot { it.key == key }.flatMap { it.songs }.map { it.id }.toSet()
        return target.songs.map { it.id }.filterNot { it in stillNeeded }.toSet()
    }

    fun toJson(gson: Gson): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String?, gson: Gson): OfflineCatalog =
            if (json.isNullOrBlank()) OfflineCatalog()
            else try { gson.fromJson(json, OfflineCatalog::class.java) ?: OfflineCatalog() } catch (_: Exception) { OfflineCatalog() }
    }
}
