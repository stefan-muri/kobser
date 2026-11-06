package com.kobser.app.auto

/** One time another app connected to Kobser's media service. */
data class ConnectionEntry(val timeMillis: Long, val packageName: String, val event: String)

/** Human name for the apps that typically connect to a media service. */
fun controllerLabel(packageName: String): String = when {
    packageName == "com.google.android.projection.gearhead" -> "Android Auto"
    packageName.startsWith("com.google.android.apps.automotive") || packageName.startsWith("com.android.car") -> "Car system"
    packageName == "com.android.systemui" -> "System media controls"
    packageName == "com.android.bluetooth" -> "Bluetooth"
    packageName.startsWith("com.google.android.wearable") -> "Wear OS"
    packageName == "com.google.android.googlequicksearchbox" -> "Google Assistant"
    else -> packageName
}

/** Newest first, capped at [max]. */
fun appendCapped(entries: List<ConnectionEntry>, entry: ConnectionEntry, max: Int = 30): List<ConnectionEntry> =
    (listOf(entry) + entries).take(max)
