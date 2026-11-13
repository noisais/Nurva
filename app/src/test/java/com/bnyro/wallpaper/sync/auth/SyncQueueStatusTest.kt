package com.bnyro.wallpaper.sync.auth

import com.bnyro.wallpaper.sync.db.SyncOutboxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncQueueStatusTest {
    private fun operation(
        operationType: String = "apply_wallpaper",
        attempts: Int = 0,
        nextAttemptAt: Long = 1_000L,
        lastError: String? = null
    ) = SyncOutboxEntity(
        operationId = "wallpaper_apply:group:3",
        groupId = "group",
        operationType = operationType,
        payloadJson = "{}",
        createdAt = 1L,
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
        lastError = lastError
    )

    @Test
    fun freshAutomaticOperationIsQueuedAndRetryable() {
        val item = operation()
        assertEquals("Queued", syncQueueStatusLabel(item))
        assertTrue(syncQueueCanRetry(item))
        assertTrue(syncQueueDetail(item) == null)
    }

    @Test
    fun manualApprovalWaitDoesNotOfferBackgroundRetry() {
        val item = operation(nextAttemptAt = Long.MAX_VALUE, lastError = "Waiting for approval")
        assertEquals("Waiting for approval", syncQueueStatusLabel(item))
        assertFalse(syncQueueCanRetry(item))
        assertTrue(syncQueueDetail(item) == null)
    }

    @Test
    fun failedAutomaticOperationShowsBoundedErrorAndCanRetry() {
        val item = operation(attempts = 2, lastError = "Network request failed. Check your connection and try again.")
        assertEquals("Retry pending", syncQueueStatusLabel(item))
        assertTrue(syncQueueCanRetry(item))
        assertEquals("Network request failed. Check your connection and try again.", syncQueueDetail(item))
    }

    @Test
    fun longErrorDetailIsBounded() {
        val item = operation(lastError = "x".repeat(240))
        assertEquals(180, syncQueueDetail(item)?.length)
    }

    @Test
    fun unknownOperationIsNotRetriedAsWallpaperWork() {
        val item = operation(operationType = "unknown")
        assertEquals("Pending", syncQueueStatusLabel(item))
        assertFalse(syncQueueCanRetry(item))
    }
}
