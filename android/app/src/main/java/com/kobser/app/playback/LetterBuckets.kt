package com.kobser.app.playback

import java.text.Normalizer

/** Folder key for a title in an A–Z browse tree: its first letter, or "#" for anything else. */
fun letterBucket(title: String): String {
    val stripped = Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    val first = stripped.firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (first.isLetter() && first.code < 128) first.uppercaseChar().toString() else "#"
}

/** Groups titles into A–Z buckets, "#" last, each bucket sorted by title. */
fun <T> groupByLetter(items: List<T>, title: (T) -> String): List<Pair<String, List<T>>> {
    val groups = items.groupBy { letterBucket(title(it)) }
    val keys = groups.keys.sortedWith(compareBy({ it == "#" }, { it }))
    return keys.map { k -> k to groups.getValue(k).sortedBy { title(it).lowercase() } }
}
