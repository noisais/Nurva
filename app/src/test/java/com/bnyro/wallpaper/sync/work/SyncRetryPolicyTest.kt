package com.bnyro.wallpaper.sync.work

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRetryPolicyTest {
    @Test fun exponentialBackoffStopsAtCap() {
        assertEquals(15_000L, SyncRetryPolicy.delayMillis(0))
        assertEquals(30_000L, SyncRetryPolicy.delayMillis(1))
        assertEquals(60_000L, SyncRetryPolicy.delayMillis(2))
        assertEquals(SyncRetryPolicy.DEFAULT_MAX_DELAY_MS, SyncRetryPolicy.delayMillis(30))
    }
}
