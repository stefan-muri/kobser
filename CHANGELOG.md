# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/), and this project adheres to
[Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-06-19

### Added
- Downloads are now per-user: each user sees and manages only their own downloads, while admins see everyone's. (Existing download history is cleared on upgrade.)
- Import Spotify playlists into the library, on both web and Android.
- Duplicate detection: warn before re-downloading a song already in the library, with a confirm-to-proceed flow instead of a hard block.
- Retry failed or cancelled downloads without restarting them.
- Android: "Add to playlist" and "View artist" actions in the expanded player.

### Changed
- Login stores a derived Subsonic token instead of the cleartext password.
- Playlist imports download tracks in parallel with retry-once and per-track error handling.
- Download concurrency is capped and the in-memory job table is pruned to bound memory use.
- yt-dlp failures are reported as clear, classified reasons instead of raw tracebacks.
- Login returns 502 "music server unreachable" when Navidrome is down, rather than a misleading 401.

### Security
- Added login rate-limiting, a Content-Security-Policy and related security headers, Subsonic proxy path hardening, and disabled Android backup of app data.
- The container now runs the application as a non-root user; the entrypoint fixes bind-mount ownership on startup, so no manual `chown` is required.
- yt-dlp is given a throwaway copy of the cookies file so the mounted secret is not modified.
- Raised the `python-multipart` floor to 0.0.18 to pick up the upstream CVE fix.

### Fixed
- Track deletion no longer fails for files in a user library or when Navidrome returns a virtual path.
- HTTPS login on Android when the server URL has no explicit port.
- Orphaned downloads are reconciled on restart instead of remaining stuck in progress.
- Stalled connections no longer hang, via socket timeout and retries.
- Fall back to `MUSIC_DIR` when the reported library path is unavailable.
- Refreshed yt-dlp cookies now persist.

### Developer
- Added backend unit tests and ruff linting in CI.
- Android and Docker builds run only when their files change.

## [1.0.0] - 2026-06-02

- Initial release.

[1.1.0]: https://github.com/stefan-muri/kobser/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/stefan-muri/kobser/releases/tag/v1.0.0
