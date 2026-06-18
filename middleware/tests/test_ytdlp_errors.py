"""Unit tests for the yt-dlp error taxonomy (describe_error / is_retryable).

These are the functions that turn raw yt-dlp exceptions into the short reasons
shown in the UI and decide whether a failed download is worth retrying. They're
pure string logic, so we can pin the behaviour without touching the network.
"""

import pytest

from services.ytdlp_service import describe_error, is_retryable


def err(msg: str) -> Exception:
    return Exception(msg)


class TestDescribeError:
    @pytest.mark.parametrize(
        "msg, expected",
        [
            ("Sign in to confirm your age", "age-restricted (needs an age-verified account cookie)"),
            ("This video may be inappropriate for some users", "age-restricted (needs an age-verified account cookie)"),
            ("Sign in to confirm you're not a bot", "bot check (needs cookies)"),
            ("HTTP Error 403: Forbidden", "blocked by YouTube (HTTP 403 — often transient, retry later)"),
            ("Private video", "private video"),
            ("Video unavailable", "video unavailable"),
            ("This video has been removed", "video unavailable"),
            ("Requested format is not available", "no downloadable audio format"),
            ("Please sign in", "requires sign-in (needs cookies)"),
            ("The read operation timed out", "network timeout"),
        ],
    )
    def test_known_reasons(self, msg, expected):
        assert describe_error(err(msg)) == expected

    def test_age_restriction_wins_over_signin(self):
        # A message can contain both "sign in" and "confirm your age"; the more
        # specific age-restriction reason must take precedence.
        assert describe_error(err("Sign in to confirm your age")).startswith("age-restricted")

    def test_unknown_falls_back_to_first_line_without_error_prefix(self):
        exc = err("ERROR: something weird happened\nsecond line of noise")
        assert describe_error(exc) == "something weird happened"

    def test_fallback_is_truncated(self):
        assert len(describe_error(err("x" * 500))) <= 200

    def test_empty_message_falls_back_to_class_name(self):
        assert describe_error(err("")) == "Exception"


class TestIsRetryable:
    @pytest.mark.parametrize(
        "msg",
        [
            "HTTP Error 403: Forbidden",
            "The read operation timed out",
            "Unable to extract player response",
            "nsig extraction failed",
            "HTTP Error 503: Service Unavailable",
            "Connection reset by peer",
        ],
    )
    def test_transient_is_retryable(self, msg):
        assert is_retryable(err(msg)) is True

    @pytest.mark.parametrize(
        "msg",
        [
            "Sign in to confirm your age",
            "Private video",
            "Video unavailable",
            "This is a members-only video",
            "Requested format is not available",
            "Sign in to confirm you're not a bot",
        ],
    )
    def test_permanent_is_not_retryable(self, msg):
        assert is_retryable(err(msg)) is False

    def test_unrecognised_is_not_retryable(self):
        # Default to not retrying things we don't understand.
        assert is_retryable(err("totally novel failure mode")) is False

    def test_permanent_wins_over_transient_tokens(self):
        # Contains "not a bot" (permanent) even though "sign in" appears too.
        assert is_retryable(err("Sign in to confirm you're not a bot")) is False
