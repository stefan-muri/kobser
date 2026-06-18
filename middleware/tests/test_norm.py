"""Unit tests for _norm — the loose matcher behind duplicate detection and
Navidrome song lookup. It strips accents, case, and punctuation so that
"Beyoncé" and "beyonce" compare equal.
"""

from routes.download import _norm as download_norm
from services.navidrome_client import _norm


def test_lowercases_and_strips_punctuation():
    assert _norm("A.C.E!") == "ace"


def test_strips_accents():
    assert _norm("Beyoncé") == "beyonce"
    assert _norm("Sigur Rós") == "sigurros"


def test_ignores_whitespace_and_symbols():
    assert _norm("Daft Punk") == _norm("daft-punk") == _norm("DAFT  PUNK")


def test_accented_equals_unaccented():
    assert _norm("Café del Mar") == _norm("cafe del mar")


def test_empty_and_symbol_only():
    assert _norm("") == ""
    assert _norm("!!!") == ""


def test_download_and_client_norm_agree():
    # The two copies of _norm must stay in lockstep or dedup breaks.
    for s in ["Beyoncé", "A.C.E!", "Daft-Punk", ""]:
        assert download_norm(s) == _norm(s)
