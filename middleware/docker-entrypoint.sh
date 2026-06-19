#!/bin/sh
# The image runs the app as the unprivileged 'app' user. When the container
# starts as root (the default), fix ownership of the writable bind-mounts so
# 'app' can use them, then drop to 'app'. This keeps existing host bind-mounts
# working without a manual chown. If already started as a non-root user (e.g.
# `docker run --user`), just exec the command as-is.
set -e

if [ "$(id -u)" = "0" ]; then
    # Data (SQLite DB) and secrets (cookies) are small — chown recursively.
    chown -R app:app /app/data /app/secrets 2>/dev/null || true
    # Music can be large or on a network share — only take the mount point so
    # new downloads can be written, without recursing over the whole library.
    chown app:app /music 2>/dev/null || true
    exec gosu app "$@"
fi

exec "$@"
