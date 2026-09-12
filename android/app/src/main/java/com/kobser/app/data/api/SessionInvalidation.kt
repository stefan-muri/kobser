package com.kobser.app.data.api

/**
 * True when an HTTP response means the session we hold is dead — expired, or dropped
 * server-side (e.g. the v1.1.0 backend migration re-created the sessions table). A
 * 401 from the login endpoint is a wrong password, not a dead session, so it's excluded.
 */
fun isSessionInvalidResponse(path: String, code: Int): Boolean =
    code == 401 && path.trimEnd('/') != "/api/login"
