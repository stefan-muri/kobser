"""Tests for the security-hardening batch: login rate-limiting (H2) and the
library proxy's Subsonic-verb allowlist (M3)."""

import time

import pytest

from routes import auth as auth_routes
from routes.library import _SUBSONIC_VERB_RE


@pytest.fixture(autouse=True)
def _clear_login_state():
    auth_routes._login_failures.clear()
    yield
    auth_routes._login_failures.clear()


class TestLoginRateLimit:
    def test_not_limited_below_threshold(self):
        for _ in range(auth_routes._LOGIN_MAX_FAILURES - 1):
            auth_routes._record_failure("1.2.3.4")
        assert auth_routes._is_rate_limited("1.2.3.4") is False

    def test_limited_at_threshold(self):
        for _ in range(auth_routes._LOGIN_MAX_FAILURES):
            auth_routes._record_failure("1.2.3.4")
        assert auth_routes._is_rate_limited("1.2.3.4") is True

    def test_success_clears_failures(self):
        for _ in range(auth_routes._LOGIN_MAX_FAILURES):
            auth_routes._record_failure("1.2.3.4")
        auth_routes._clear_failures("1.2.3.4")
        assert auth_routes._is_rate_limited("1.2.3.4") is False

    def test_old_failures_expire_out_of_window(self, monkeypatch):
        # Stuff the deque with timestamps older than the window.
        old = time.time() - auth_routes._LOGIN_WINDOW_S - 1
        from collections import deque
        auth_routes._login_failures["1.2.3.4"] = deque([old] * auth_routes._LOGIN_MAX_FAILURES)
        assert auth_routes._is_rate_limited("1.2.3.4") is False

    def test_keys_are_isolated_per_client(self):
        for _ in range(auth_routes._LOGIN_MAX_FAILURES):
            auth_routes._record_failure("1.1.1.1")
        assert auth_routes._is_rate_limited("1.1.1.1") is True
        assert auth_routes._is_rate_limited("2.2.2.2") is False


class TestSubsonicVerbGuard:
    @pytest.mark.parametrize("good", ["getArtists", "getCoverArt.view", "search3", "getAlbumList2"])
    def test_allows_flat_verbs(self, good):
        assert _SUBSONIC_VERB_RE.match(good)

    @pytest.mark.parametrize(
        "bad",
        ["../admin", "..", "foo/bar", "/etc/passwd", "get Artists", "a/../b", ""],
    )
    def test_rejects_traversal_and_slashes(self, bad):
        assert _SUBSONIC_VERB_RE.match(bad) is None
