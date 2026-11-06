"""Locating a track's file by its tags when it isn't named the kobser way."""

from pathlib import Path

from services.navidrome_client import normalize
from services.tagger_service import find_file_by_tags


def make(tmp_path: Path, rel: str) -> Path:
    p = tmp_path / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(b"")
    return p


def fake_tags(table: dict[str, tuple[str, str]]):
    return lambda path: table.get(Path(path).name)


def test_finds_hand_named_file_by_title_and_artist(tmp_path):
    hit = make(tmp_path, "Joji/01 slow dancing.m4a")
    make(tmp_path, "Joji/02 other.m4a")
    tags = fake_tags({"01 slow dancing.m4a": ("SLOW DANCING IN THE DARK", "Joji"), "02 other.m4a": ("Other", "Joji")})
    assert find_file_by_tags([tmp_path / "Joji"], "Slow Dancing in the Dark", "Joji", normalize, tags) == hit


def test_searches_album_subfolders_but_not_deeper(tmp_path):
    hit = make(tmp_path, "Artist/Album/track.flac")
    make(tmp_path, "Artist/A/B/C/deep.flac")
    tags = fake_tags({"track.flac": ("Song", "Artist"), "deep.flac": ("Deep", "Artist")})
    assert find_file_by_tags([tmp_path / "Artist"], "Song", "Artist", normalize, tags) == hit
    assert find_file_by_tags([tmp_path / "Artist"], "Deep", "Artist", normalize, tags) is None


def test_artist_mismatch_is_not_a_match(tmp_path):
    make(tmp_path, "Mix/song.mp3")
    tags = fake_tags({"song.mp3": ("Song", "Someone Else")})
    assert find_file_by_tags([tmp_path / "Mix"], "Song", "Artist", normalize, tags) is None


def test_ignores_non_audio_and_missing_folders(tmp_path):
    make(tmp_path, "Artist/cover.jpg")
    tags = fake_tags({"cover.jpg": ("Song", "Artist")})
    assert find_file_by_tags([tmp_path / "Artist", tmp_path / "Nope"], "Song", "Artist", normalize, tags) is None
    assert find_file_by_tags([tmp_path / "Artist"], "", "Artist", normalize, tags) is None
