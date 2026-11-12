package com.bnyro.wallpaper.sync.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSyncStatusTest {
    @Test
    fun noPublishedRevisionHasExplicitEmptyState() {
        assertEquals("No wallpaper published", deviceSyncStatusLabel(0L, 0L, "auto"))
    }

    @Test
    fun appliedCurrentRevisionIsUpToDate() {
        assertEquals("Up to date", deviceSyncStatusLabel(4L, 4L, "manual"))
    }

    @Test
    fun appliedNewerRevisionRemainsUpToDate() {
        assertEquals("Up to date", deviceSyncStatusLabel(4L, 5L, "auto"))
    }

    @Test
    fun manualDeviceWaitsForApproval() {
        assertEquals("Waiting for approval", deviceSyncStatusLabel(3L, 2L, "manual"))
    }

    @Test
    fun automaticDeviceShowsPendingSyncWithoutClaimingItIsOnline() {
        assertEquals("Waiting for automatic sync", deviceSyncStatusLabel(3L, 2L, "auto"))
    }

    @Test
    fun unknownApplyModeUsesNeutralPendingState() {
        assertEquals("Not up to date", deviceSyncStatusLabel(3L, 2L, "unknown"))
    }

    @Test
    fun missingCheckInHasExplicitEmptyState() {
        assertEquals("No check-in recorded yet", deviceLastSeenLabel(null, 1_000_000L))
        assertEquals("No check-in recorded yet", deviceLastSeenLabel(0L, 1_000_000L))
    }

    @Test
    fun recentCheckInIsJustNow() {
        assertEquals("Just now", deviceLastSeenLabel(1_000_000L - 30_000L, 1_000_000L))
    }

    @Test
    fun olderCheckInsUseCompactElapsedTime() {
        val now = 1_000_000_000L
        assertEquals("Seen 5m ago", deviceLastSeenLabel(now - 5 * 60_000L, now))
        assertEquals("Seen 3h ago", deviceLastSeenLabel(now - 3 * 60 * 60_000L, now))
        assertEquals("Seen 2d ago", deviceLastSeenLabel(now - 2 * 24 * 60 * 60_000L, now))
    }

    @Test
    fun futureClockSkewDoesNotProduceNegativeAge() {
        assertEquals("Just now", deviceLastSeenLabel(1_000_100L, 1_000_000L))
    }
}
