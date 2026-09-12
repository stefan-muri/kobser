package com.kobser.app.offline

/** Mirror of Media3's Download.STATE_* values we care about, kept here so the maths stays JVM-testable. */
object DownloadStates {
    const val QUEUED = 0
    const val STOPPED = 1
    const val DOWNLOADING = 2
    const val COMPLETED = 3
    const val FAILED = 4
    const val REMOVING = 5
    const val RESTARTING = 7
}

/**
 * Progress of one collection from the download index states of its songs (by
 * cache key) plus the set of keys already fully in the cache (from before the
 * download framework, or served by another collection's download).
 */
fun collectionProgress(keys: List<String>, states: Map<String, Int>, cachedKeys: Set<String>): OfflineProgress {
    var done = 0
    var failed = 0
    var running = false
    for (key in keys) {
        when (states[key]) {
            DownloadStates.COMPLETED -> done++
            DownloadStates.FAILED -> failed++
            DownloadStates.QUEUED, DownloadStates.DOWNLOADING, DownloadStates.RESTARTING, DownloadStates.STOPPED -> running = true
            else -> if (key in cachedKeys) done++ else running = true
        }
    }
    return OfflineProgress(done = done, total = keys.size, failed = failed, running = running)
}
