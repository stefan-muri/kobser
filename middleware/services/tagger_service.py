from mutagen import File as MutagenFile

DEFAULT_ALBUM = "Singles"


def tag(file_path: str, artist: str, title: str, album: str = DEFAULT_ALBUM) -> None:
    """Write artist/title plus albumartist/album tags.

    Navidrome's getArtists endpoint indexes by *album artist*, not track artist,
    so writing only `artist` leaves new artists invisible in the Library
    browse view (they're still findable via search3). We set albumartist = artist
    and album = "Singles" by default so every standalone download groups under
    the artist's own "Singles" album."""
    audio = MutagenFile(file_path, easy=True)
    if audio is None:
        return
    audio["artist"] = artist
    audio["title"] = title
    audio["album"] = album
    audio["albumartist"] = artist
    audio.save()
