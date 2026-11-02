package com.bnyro.wallpaper.sync.work

object SyncRetryPolicy {
    const val DEFAULT_BASE_DELAY_MS = 15_000L
    const val DEFAULT_MAX_DELAY_MS = 6 * 60 * 60 * 1_000L

    fun delayMillis(attemptsAlreadyMade: Int, baseDelayMs: Long = DEFAULT_BASE_DELAY_MS, maxDelayMs: Long = DEFAULT_MAX_DELAY_MS): Long {
        require(attemptsAlreadyMade >= 0) { "attempt count cannot be negative" }
        require(baseDelayMs > 0) { "base delay must be positive" }
        require(maxDelayMs >= baseDelayMs) { "maximum delay must be at least the base delay" }
        val multiplier = 1L shl attemptsAlreadyMade.coerceAtMost(30)
        val delay = if (baseDelayMs > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else baseDelayMs * multiplier
        return delay.coerceAtMost(maxDelayMs)
    }
}
