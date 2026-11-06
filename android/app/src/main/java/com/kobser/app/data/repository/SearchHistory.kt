package com.kobser.app.data.repository

/** Most recent first, case-insensitively de-duplicated, capped at [max]. */
fun addToSearchHistory(history: List<String>, query: String, max: Int = 20): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return history
    return (listOf(q) + history.filterNot { it.equals(q, ignoreCase = true) }).take(max)
}

/** Stored as one query per line (queries come from a single-line field). */
fun encodeSearchHistory(history: List<String>): String = history.joinToString("\n")

fun decodeSearchHistory(raw: String?): List<String> =
    raw?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
