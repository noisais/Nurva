package com.bnyro.wallpaper.sync.auth

import com.bnyro.wallpaper.sync.db.SyncOutboxEntity

/** Describes locally queued work without implying that a remote device is online. */
internal fun syncQueueStatusLabel(operation: SyncOutboxEntity): String {
    if (operation.operationType != APPLY_WALLPAPER) return "Pending"
    if (operation.nextAttemptAt == Long.MAX_VALUE || operation.lastError == WAITING_FOR_APPROVAL) {
        return "Waiting for approval"
    }
    if (!operation.lastError.isNullOrBlank() || operation.attempts > 0) return "Retry pending"
    return "Queued"
}

/** Hides the internal manual-approval sentinel and bounds displayed error text. */
internal fun syncQueueDetail(operation: SyncOutboxEntity): String? =
    operation.lastError
        ?.takeIf { it.isNotBlank() && it != WAITING_FOR_APPROVAL }
        ?.take(MAX_DETAIL_LENGTH)

/** Only automatic wallpaper operations can be retried by the background worker. */
internal fun syncQueueCanRetry(operation: SyncOutboxEntity): Boolean =
    operation.operationType == APPLY_WALLPAPER &&
        operation.nextAttemptAt != Long.MAX_VALUE &&
        operation.lastError != WAITING_FOR_APPROVAL

private const val APPLY_WALLPAPER = "apply_wallpaper"
private const val WAITING_FOR_APPROVAL = "Waiting for approval"
private const val MAX_DETAIL_LENGTH = 180
