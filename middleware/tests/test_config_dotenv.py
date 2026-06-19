"""Tests for the stdlib .env loader used by local (non-Docker) runs.

It must fill in missing environment variables from a repo-root .env without
ever overriding values already present in the environment (so Docker / explicit
`VAR=... uvicorn` launches win).
"""

import os

import pytest

from config import _load_dotenv


@pytest.fixture(autouse=True)
def _restore_environ():
    saved = dict(os.environ)
    yield
    os.environ.clear()
    os.environ.update(saved)


def _write(tmp_path, text):
    p = tmp_path / ".env"
    p.write_text(text)
    return p


def test_loads_simple_pairs(tmp_path):
    os.environ.pop("KOBSER_TEST_A", None)
    _load_dotenv(_write(tmp_path, "KOBSER_TEST_A=hello\n"))
    assert os.environ["KOBSER_TEST_A"] == "hello"


def test_existing_env_wins(tmp_path):
    os.environ["KOBSER_TEST_B"] = "from-env"
    _load_dotenv(_write(tmp_path, "KOBSER_TEST_B=from-file\n"))
    assert os.environ["KOBSER_TEST_B"] == "from-env"


def test_ignores_comments_and_blanks(tmp_path):
    os.environ.pop("KOBSER_TEST_C", None)
    _load_dotenv(_write(tmp_path, "# a comment\n\n   \nKOBSER_TEST_C=ok\n"))
    assert os.environ["KOBSER_TEST_C"] == "ok"


def test_strips_surrounding_quotes(tmp_path):
    os.environ.pop("KOBSER_TEST_D", None)
    os.environ.pop("KOBSER_TEST_E", None)
    _load_dotenv(_write(tmp_path, 'KOBSER_TEST_D="quoted"\nKOBSER_TEST_E=\'single\'\n'))
    assert os.environ["KOBSER_TEST_D"] == "quoted"
    assert os.environ["KOBSER_TEST_E"] == "single"


def test_value_may_contain_equals(tmp_path):
    os.environ.pop("KOBSER_TEST_F", None)
    _load_dotenv(_write(tmp_path, "KOBSER_TEST_F=http://host:4533/path?a=b\n"))
    assert os.environ["KOBSER_TEST_F"] == "http://host:4533/path?a=b"


def test_missing_file_is_noop(tmp_path):
    # Should not raise.
    _load_dotenv(tmp_path / "does-not-exist.env")
