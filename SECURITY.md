# Security

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Instead, contact
the maintainer by email.
You'll get a response as soon as possible.

## Threat model & known considerations

kobser is built for **trusted, self-hosted use** (a home lab / personal server),
not for hostile multi-tenant exposure on the public internet. Keep this in mind:

- **Sessions store a derived token, not your password.** At login the
  middleware derives a reusable Subsonic `(salt, token)` pair from the password
  and persists only that (plus the resolved library path) in its SQLite session
  store (`./data/kobser/kobser.db`); the cleartext password is never written to
  disk. The token is replayable against the Subsonic API but, unlike the
  password, can't be used for Navidrome's web login or reused elsewhere — still,
  treat the database as sensitive and don't expose it.

- **Run it behind HTTPS.** The API and streaming endpoints pass a session token
  (in a header or query string). Put kobser behind a reverse proxy with TLS
  (Caddy, nginx, Traefik) rather than exposing plain HTTP to the internet.

- **Accounts are Navidrome accounts.** Authorization is whatever Navidrome
  grants the user. Authenticated users can download into and delete from their
  own library.

- **Downloads are sandboxed to the library path** and filenames are sanitized to
  prevent path traversal, but the feature inherently writes files to disk — only
  give accounts to people you trust.

- **The container runs as a non-root user.** The entrypoint fixes ownership of
  the mounted `data`, `secrets`, and `music` volumes on startup and then drops
  to an unprivileged user, so the application never runs as root. No manual
  `chown` is required on the host.

## Supported versions

This is a personal/community project; security fixes land on `master`. Pull the
latest and rebuild to stay current.
