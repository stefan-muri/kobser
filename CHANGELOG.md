# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/), and this project adheres to
[Semantic Versioning](https://semver.org/).

## [1.2.0] - 2026-09-12

### Added
- Offline music (Android): "Keep offline" on any album or playlist downloads it to the phone (in the background, with a progress notification, resuming after restarts) into storage that is never evicted; the new Offline music screen (More → Offline music) lists and plays what's kept, with no connection. The Library tab offers it when the server can't be reached; Settings shows the space used and can remove everything.
- Play counts (both clients): tracks are now reported to Navidrome as "now playing" and scrobbled once they've played halfway (or four minutes), so play counts, Recently played, and any Last.fm / ListenBrainz forwarding work.
- Android Auto: Albums, Recently added and Recently played shelves; libraries over 150 songs get A–Z folders instead of one giant list; the library is cached for a few minutes so browsing and voice search don't refetch it every time.
- Android: sleep timer (15/30/45/60 minutes or end of the current track) in the player's menu.
- Android: songs you stream are cached on the device (least-recently-used, 1 GB by default) so replaying them doesn't use data. The limit and a "Clear cache" button are in Settings.
- Android: playback failures are now shown as a message instead of failing silently.

### Fixed
- Sessions no longer expire after a fixed 30 days: any use renews them, so the phone and the car stay signed in.
- Android: the queue sheet shows the real play order while shuffle is on (dragging to reorder is for the linear queue only), and "Clear upcoming" clears what would actually play next.
- Android: login errors say what happened ("Wrong username or password", "Too many failed attempts", "can't reach the music server") instead of a status code.
- Song previews: YouTube's CDN throttles a single whole-file request to below playback speed, which made previews stall. The server now fetches previews in ranged chunks (the way yt-dlp itself does), supports seeking, and remembers resolved stream URLs for 30 minutes so seeks and replays don't re-run yt-dlp.
- Android: the Library "Shuffle" button now really shuffles. It used to hand the shuffled list to the play-or-jump helper, which found the first song in the existing queue and just jumped to it, keeping the old order.
- Android: turning shuffle on mid-queue now plays every remaining track. ExoPlayer's default random order left the current track at an arbitrary point in the permutation, so playback could stop after a few songs.
- Android: a session the server no longer accepts (expired, or dropped by the v1.1.0 backend upgrade) now returns the app to the login screen instead of leaving every tab and the Android Auto library empty.
- Android Auto: playback resumption from the car, Bluetooth or the system media chip now restores the last queue when the phone app isn't open.
- Android Auto: when signed out, the browse tree shows a "Sign in on your phone" entry and a sign-in action instead of an empty library.
- Android: opening the phone app while the car is already playing no longer replaces the car's queue with the previously saved one.

### Changed
- Android: moved to the current toolchain — AGP 9.4 with built-in Kotlin (Kotlin 2.3.21), Gradle 9.7, compileSdk 37, targetSdk 36 — and current libraries: Media3 1.11.1, Compose 1.12 (BOM 2026.09.00), Hilt 2.60.1, Navigation 2.10, OkHttp 5. Shuffle/repeat now use Media3's built-in media button icons, and Android Auto errors are surfaced properly. Building now needs a recent Android Studio.

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
