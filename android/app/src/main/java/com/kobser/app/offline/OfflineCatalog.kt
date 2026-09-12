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
    val sourceId: String get() = key.substringAfter(':')

    companion object {
        fun albumKey(id: String) = "album:$id"
        fun playlistKey(id: String) = "playlist:$id"
    }
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
