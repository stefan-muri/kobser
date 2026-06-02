# Security

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Instead, contact
the maintainer by email.
You'll get a response as soon as possible.

## Threat model & known considerations

kobser is built for **trusted, self-hosted use** (a home lab / personal server),
not for hostile multi-tenant exposure on the public internet. Keep this in mind:

- **Navidrome passwords are stored in the session database.** The Subsonic API
  authenticates every request with the user's credentials, so the middleware
  keeps them in its SQLite session store (`./data/kobser/kobser.db`). That file
  lives on your server and is gitignored — but treat the host as you would any
  box holding credentials. Do not expose the database.

- **Run it behind HTTPS.** The API and streaming endpoints pass a session token
  (in a header or query string). Put kobser behind a reverse proxy with TLS
  (Caddy, nginx, Traefik) rather than exposing plain HTTP to the internet.

- **Accounts are Navidrome accounts.** Authorization is whatever Navidrome
  grants the user. Authenticated users can download into and delete from their
  own library.

- **Downloads are sandboxed to the library path** and filenames are sanitized to
  prevent path traversal, but the feature inherently writes files to disk — only
  give accounts to people you trust.

## Supported versions

This is a personal/community project; security fixes land on `master`. Pull the
latest and rebuild to stay current.
