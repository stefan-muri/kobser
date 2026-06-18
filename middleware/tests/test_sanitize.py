"""Unit tests for _sanitize — the filename guard used to build on-disk paths.

The security-critical property is that no user-supplied artist/title can produce
a path separator or a ".."/"." component that escapes the target directory.
"""

import pytest

from services.ytdlp_service import _sanitize


@pytest.mark.parametrize(
    "raw, expected",
    [
        ("AC/DC", "AC DC"),
        ("normal name", "normal name"),
        ("  padded  ", "padded"),
        ("mult  spaced", "mult spaced"),
        ('a<b>c:"d', "a b c d"),
    ],
)
def test_basic_sanitisation(raw, expected):
    assert _sanitize(raw) == expected


@pytest.mark.parametrize("raw", ["", "   ", "..", ".", "...", "/", "///"])
def test_degenerate_inputs_become_unknown(raw):
    # Anything that would otherwise collapse to empty or a bare dot-component
    # falls back to a safe literal.
    assert _sanitize(raw) == "Unknown"


@pytest.mark.parametrize(
    "raw",
    [
        "../../etc/passwd",
        "..\\..\\windows",
        "/absolute/path",
        "name/with/slashes",
        "trailing/",
        "nul\x00byte",
    ],
)
def test_no_path_traversal(raw):
    out = _sanitize(raw)
    assert "/" not in out
    assert "\\" not in out
    assert out not in (".", "..")
    assert "\x00" not in out
