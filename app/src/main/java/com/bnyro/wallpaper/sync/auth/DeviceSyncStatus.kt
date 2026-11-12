package com.bnyro.wallpaper.sync.auth

/** Projects server revision and device apply preferences into a clear, non-speculative UI state. */
internal fun deviceSyncStatusLabel(currentRevision: Long, lastAppliedRevision: Long, applyMode: String): String {
    if (currentRevision <= 0L) return "No wallpaper published"
    if (lastAppliedRevision >= currentRevision) return "Up to date"
    return when (applyMode) {
        "manual" -> "Waiting for approval"
        "auto" -> "Waiting for automatic sync"
        else -> "Not up to date"
    }
}

/** Returns a compact age label; future timestamps are treated as a recent check-in. */
internal fun deviceLastSeenLabel(lastSeenAtMillis: Long?, nowMillis: Long): String {
    if (lastSeenAtMillis == null || lastSeenAtMillis <= 0L) return "No check-in recorded yet"
    val elapsed = (nowMillis - lastSeenAtMillis).coerceAtLeast(0L)
    return when {
        elapsed < MINUTE -> "Just now"
        elapsed < HOUR -> "Seen ${elapsed / MINUTE}m ago"
        elapsed < DAY -> "Seen ${elapsed / HOUR}h ago"
        else -> "Seen ${elapsed / DAY}d ago"
    }
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
