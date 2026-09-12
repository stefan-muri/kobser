package com.kobser.app.playback

/** Extracts the real Subsonic track id from a (possibly context-encoded) mediaId. */
fun realTrackId(mediaId: String): String =
    if (mediaId.startsWith("track|")) mediaId.substringAfterLast("|") else mediaId

/** Previews (`preview|<videoId>`) aren't library tracks. */
fun isPreviewMediaId(mediaId: String): Boolean = mediaId.startsWith("preview|")
