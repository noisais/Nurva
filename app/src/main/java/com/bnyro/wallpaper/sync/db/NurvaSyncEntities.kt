package com.bnyro.wallpaper.sync.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "nurva_sync_groups")
data class SyncGroupEntity(
    @PrimaryKey val groupId: String,
    val displayName: String,
    val ownerUserId: String,
    val currentRevision: Long,
    val currentThemeId: String?,
    val currentAssetPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "nurva_group_members", primaryKeys = ["groupId", "userId"], indices = [Index(value = ["userId"])])
data class SyncGroupMemberEntity(
    val groupId: String,
    val userId: String,
    val role: String,
    val joinedAt: Long,
    val removedAt: Long?,
)

@Entity(tableName = "nurva_sync_devices", indices = [Index(value = ["groupId"])])
data class SyncDeviceEntity(
    @PrimaryKey val deviceId: String,
    val groupId: String,
    val userId: String,
    val displayName: String,
    val wallpaperTarget: String,
    val applyMode: String,
    val lastAppliedRevision: Long,
    val lastSeenAt: Long?,
    val revokedAt: Long?,
)

@Entity(tableName = "nurva_wallpaper_revisions", indices = [Index(value = ["groupId", "revision"], unique = true)])
data class SyncWallpaperRevisionEntity(
    @PrimaryKey val revisionId: String,
    val groupId: String,
    val revision: Long,
    val createdByUserId: String,
    val storagePath: String,
    val localFilePath: String?,
    val sha256: String,
    val mimeType: String,
    val byteSize: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val createdAt: Long,
    val localState: String,
)

@Entity(tableName = "nurva_mosaic_tiles", primaryKeys = ["groupId", "revision", "deviceId"], indices = [Index(value = ["groupId", "revision"])])
data class MosaicTileAssignmentEntity(
    val groupId: String,
    val revision: Long,
    val deviceId: String,
    val tileIndex: Int,
    val rowIndex: Int,
    val columnIndex: Int,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float,
    val outputWidth: Int,
    val outputHeight: Int,
)

@Entity(tableName = "nurva_themes", indices = [Index(value = ["groupId"])])
data class SyncThemeEntity(
    @PrimaryKey val themeId: String,
    val groupId: String,
    val name: String,
    val layoutMode: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "nurva_theme_items", primaryKeys = ["themeId", "position"], indices = [Index(value = ["themeId"])])
data class SyncThemeItemEntity(
    val themeId: String,
    val position: Int,
    val revisionId: String,
    val deviceId: String?,
)

@Entity(tableName = "nurva_sync_schedules", indices = [Index(value = ["groupId"])])
data class SyncScheduleEntity(
    @PrimaryKey val scheduleId: String,
    val groupId: String,
    val themeId: String?,
    val daysOfWeekMask: Int,
    val localHour: Int,
    val localMinute: Int,
    val timeZoneId: String,
    val enabled: Boolean,
    val requireApproval: Boolean,
    val nextRunAt: Long?,
    val updatedAt: Long,
)

@Entity(tableName = "nurva_sync_outbox", indices = [Index(value = ["groupId", "nextAttemptAt"])])
data class SyncOutboxEntity(
    @PrimaryKey val operationId: String,
    val groupId: String,
    val operationType: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
)
