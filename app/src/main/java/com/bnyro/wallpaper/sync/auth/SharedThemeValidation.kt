package com.bnyro.wallpaper.sync.auth

/** Normalizes and validates a user-supplied shared theme name. */
internal fun normalizedSharedThemeName(rawName: String): String {
    val name = rawName.trim()
    require(name.length in 1..80) { "Theme names must be 1–80 characters." }
    return name
}
