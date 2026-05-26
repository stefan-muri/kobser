package com.kobser.app.data.api

import com.google.gson.annotations.SerializedName
import androidx.compose.runtime.Immutable

@Immutable
data class SubsonicResponseWrapper(
    @SerializedName("subsonic-response")
    val response: SubsonicResponse
)

data class SubsonicResponse(
    val status: String,
    val version: String,
    val error: SubsonicError?,
    val artists: ArtistsList?,
    val artist: ArtistDetail?,
    val album: AlbumDetail?,
    val albumList2: AlbumList?,
    val starred: Starred?,
    val randomSongs: SongList?,
    val searchResult3: SearchResult3?,
    val playlists: PlaylistList?,
    val playlist: PlaylistDetail?,
)

data class SubsonicError(
    val code: Int,
    val message: String
)

// ── Songs & lists ────────────────────────────────────────────────────────────

@Immutable
data class SongList(
    val song: List<Song> = emptyList()
)

@Immutable
data class Song(
    val id: String,
    val parent: String?,
    val title: String,
    val album: String?,
    val artist: String,
    val track: Int?,
    val year: Int?,
    val genre: String?,
    val coverArt: String?,
    val duration: Int,
    val bitRate: Int?,
    val contentType: String?,
    val suffix: String?,
    val size: Long?,
    val albumId: String?,
    val artistId: String?,
    val type: String?,
    val created: String?,
    val starred: String?,
)

// ── Artists ──────────────────────────────────────────────────────────────────

data class ArtistsList(
    val index: List<ArtistIndex> = emptyList()
)

data class ArtistIndex(
    val name: String,
    val artist: List<Artist> = emptyList()
)

data class Artist(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int = 0,
    val starred: String?,
)

data class ArtistDetail(
    val id: String,
    val name: String,
    val album: List<Album> = emptyList()
)

// ── Albums ───────────────────────────────────────────────────────────────────

data class AlbumList(
    val album: List<Album> = emptyList()
)

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int = 0,
    val year: Int?,
    val starred: String?,
)

data class AlbumDetail(
    val id: String,
    val name: String,
    val artist: String,
    val song: List<Song> = emptyList()
)

// ── Starred / favorites ──────────────────────────────────────────────────────

data class Starred(
    val song: List<Song> = emptyList(),
    val album: List<Album> = emptyList(),
    val artist: List<Artist> = emptyList(),
)

// ── search3 (paginated library load) ─────────────────────────────────────────

data class SearchResult3(
    val song: List<Song> = emptyList(),
    val album: List<Album> = emptyList(),
    val artist: List<Artist> = emptyList(),
)

// ── Playlists ────────────────────────────────────────────────────────────────

data class PlaylistList(
    val playlist: List<Playlist> = emptyList()
)

data class Playlist(
    val id: String,
    val name: String,
    val comment: String?,
    val owner: String?,
    val public: Boolean?,
    val songCount: Int = 0,
    val duration: Int = 0,
    val created: String?,
    val changed: String?,
    val coverArt: String?,
)

data class PlaylistDetail(
    val id: String,
    val name: String,
    val comment: String?,
    val owner: String?,
    val public: Boolean?,
    val songCount: Int = 0,
    val duration: Int = 0,
    val created: String?,
    val changed: String?,
    val coverArt: String?,
    val entry: List<Song>? = null,
)
