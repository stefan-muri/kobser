package com.kobser.app.data.repository

/** Server-side states of a download job that mean it's finished, one way or the other. */
val FINISHED_JOB_STATUSES = setOf("done", "error", "cancelled")

/** Where a followed job ended up. `status` is the last server status seen. */
data class JobOutcome(val status: String, val error: String?) {
    val succeeded: Boolean get() = status == "done"
}

/**
 * Polls a download job until it finishes, reporting every status change.
 *
 * Kept free of Android and Retrofit types so it can be unit-tested: [fetch]
 * returns the job's (status, error), or null on a transient failure (network
 * blip, server restarting), which is simply retried. Gives up after
 * [timeoutMs] with status "timeout".
 */
suspend fun followJob(
    fetch: suspend () -> Pair<String, String?>?,
    onStatus: (String) -> Unit,
    pollMs: Long = 1_200,
    timeoutMs: Long = 15 * 60_000,
    sleep: suspend (Long) -> Unit,
): JobOutcome {
    var waited = 0L
    var last: String? = null
    while (waited < timeoutMs) {
        val result = runCatching { fetch() }.getOrNull()
        if (result != null) {
            val (status, error) = result
            if (status != last) {
                last = status
                onStatus(status)
            }
            if (status in FINISHED_JOB_STATUSES) return JobOutcome(status, error)
        }
        sleep(pollMs)
        waited += pollMs
    }
    return JobOutcome("timeout", "Timed out waiting for the server")
}
