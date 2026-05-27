package com.kobser.app.data.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Backwards-compatible JSON shape for /api/search:
 *  - new: {"songs": [...], "artists": [...]}
 *  - legacy: [SearchResult, SearchResult, ...]   ← treat as songs-only
 *
 * Without this, an old server hitting a new client throws
 * "expected BEGIN_OBJECT but was BEGIN_ARRAY".
 */
class SearchResponseDeserializer : JsonDeserializer<SearchResponse> {
    private val songsListType: Type = object : TypeToken<List<SearchResult>>() {}.type
    private val artistsListType: Type = object : TypeToken<List<ArtistResult>>() {}.type

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): SearchResponse {
        if (json.isJsonArray) {
            val songs: List<SearchResult> = context.deserialize(json, songsListType)
            return SearchResponse(songs = songs, artists = emptyList())
        }
        val obj = json.asJsonObject
        val songs: List<SearchResult> =
            obj.get("songs")?.takeIf { !it.isJsonNull }?.let { context.deserialize(it, songsListType) }
                ?: emptyList()
        val artists: List<ArtistResult> =
            obj.get("artists")?.takeIf { !it.isJsonNull }?.let { context.deserialize(it, artistsListType) }
                ?: emptyList()
        return SearchResponse(songs = songs, artists = artists)
    }
}
