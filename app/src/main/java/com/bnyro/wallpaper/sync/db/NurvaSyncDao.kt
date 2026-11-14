package com.bnyro.wallpaper.sync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NurvaSyncDao {
    @Query("SELECT * FROM nurva_sync_groups ORDER BY updatedAt DESC")
    fun observeGroups(): Flow<List<SyncGroupEntity>>

    @Query("SELECT * FROM nurva_sync_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroup(groupId: String): SyncGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: SyncGroupEntity)

    @Query("SELECT * FROM nurva_group_members WHERE groupId = :groupId AND removedAt IS NULL ORDER BY joinedAt")
    fun observeMembers(groupId: String): Flow<List<SyncGroupMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<SyncGroupMemberEntity>)

    @Query("DELETE FROM nurva_group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: String)

    @Query("SELECT * FROM nurva_sync_devices WHERE groupId = :groupId ORDER BY displayName")
    fun observeDevices(groupId: String): Flow<List<SyncDeviceEntity>>

    @Query("SELECT * FROM nurva_sync_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDevice(deviceId: String): SyncDeviceEntity?

    @Query("SELECT * FROM nurva_sync_devices WHERE groupId = :groupId AND revokedAt IS NULL ORDER BY displayName, deviceId")
    suspend fun getActiveDevices(groupId: String): List<SyncDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(device: SyncDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevices(devices: List<SyncDeviceEntity>)

    @Query("DELETE FROM nurva_sync_devices WHERE groupId = :groupId")
    suspend fun clearDevices(groupId: String)

    @Transaction
    suspend fun replaceRemoteSnapshot(
        group: SyncGroupEntity,
        members: List<SyncGroupMemberEntity>,
        devices: List<SyncDeviceEntity>
    ) {
        upsertGroup(group)
        clearMembers(group.groupId)
        if (members.isNotEmpty()) upsertMembers(members)
        clearDevices(group.groupId)
        if (devices.isNotEmpty()) upsertDevices(devices)
    }

    @Query("UPDATE nurva_sync_devices SET revokedAt = :revokedAt WHERE deviceId = :deviceId")
    suspend fun markDeviceRevoked(deviceId: String, revokedAt: Long)

    @Query("UPDATE nurva_sync_devices SET lastAppliedRevision = :revision WHERE deviceId = :deviceId AND revokedAt IS NULL")
    suspend fun markRevisionApplied(deviceId: String, revision: Long)

    @Query("SELECT * FROM nurva_wallpaper_revisions WHERE groupId = :groupId ORDER BY revision DESC LIMIT 1")
    suspend fun getLatestRevision(groupId: String): SyncWallpaperRevisionEntity?

    @Query("SELECT * FROM nurva_wallpaper_revisions WHERE groupId = :groupId ORDER BY revision DESC")
    suspend fun getRevisions(groupId: String): List<SyncWallpaperRevisionEntity>

    @Query("SELECT * FROM nurva_wallpaper_revisions WHERE groupId = :groupId ORDER BY revision DESC")
    fun observeRevisions(groupId: String): Flow<List<SyncWallpaperRevisionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRevision(revision: SyncWallpaperRevisionEntity)

    @Query("UPDATE nurva_wallpaper_revisions SET localState = :state, localFilePath = :localFilePath WHERE revisionId = :revisionId")
    suspend fun updateRevisionLocalState(revisionId: String, state: String, localFilePath: String?)

    @Query("SELECT * FROM nurva_mosaic_tiles WHERE groupId = :groupId AND revision = :revision ORDER BY tileIndex")
    suspend fun getMosaicTiles(groupId: String, revision: Long): List<MosaicTileAssignmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMosaicTiles(tiles: List<MosaicTileAssignmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTheme(theme: SyncThemeEntity)

    @Query("SELECT * FROM nurva_themes WHERE groupId = :groupId AND themeId = :themeId LIMIT 1")
    suspend fun getTheme(groupId: String, themeId: String): SyncThemeEntity?

    @Query("SELECT * FROM nurva_themes WHERE groupId = :groupId ORDER BY updatedAt DESC")
    fun observeThemes(groupId: String): Flow<List<SyncThemeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThemeItems(items: List<SyncThemeItemEntity>)

    @Query("DELETE FROM nurva_theme_items WHERE themeId = :themeId")
    suspend fun clearThemeItems(themeId: String)

    @Transaction
    suspend fun replaceThemeItems(themeId: String, items: List<SyncThemeItemEntity>) {
        require(items.all { it.themeId == themeId }) { "Every item must belong to the theme being replaced" }
        clearThemeItems(themeId)
        if (items.isNotEmpty()) insertThemeItems(items)
    }

    @Query("SELECT * FROM nurva_theme_items WHERE themeId = :themeId ORDER BY position")
    suspend fun getThemeItems(themeId: String): List<SyncThemeItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: SyncScheduleEntity)

    @Query("SELECT * FROM nurva_sync_schedules WHERE groupId = :groupId ORDER BY localHour, localMinute")
    fun observeSchedules(groupId: String): Flow<List<SyncScheduleEntity>>

    @Query("SELECT * FROM nurva_sync_outbox WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun observeOperations(groupId: String): Flow<List<SyncOutboxEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: SyncOutboxEntity)

    @Query("SELECT * FROM nurva_sync_outbox WHERE operationId = :operationId LIMIT 1")
    suspend fun getOperation(operationId: String): SyncOutboxEntity?

    @Query("UPDATE nurva_sync_outbox SET nextAttemptAt = :nextAttemptAt, lastError = :lastError WHERE operationId = :operationId")
    suspend fun scheduleOperation(operationId: String, nextAttemptAt: Long, lastError: String?)

    @Query("SELECT * FROM nurva_sync_outbox WHERE nextAttemptAt <= :now ORDER BY createdAt LIMIT :limit")
    suspend fun getDueOperations(now: Long, limit: Int = 20): List<SyncOutboxEntity>

    @Query("UPDATE nurva_sync_outbox SET attempts = attempts + 1, nextAttemptAt = :nextAttemptAt, lastError = :lastError WHERE operationId = :operationId")
    suspend fun recordRetry(operationId: String, nextAttemptAt: Long, lastError: String?)

    @Query("DELETE FROM nurva_sync_outbox WHERE operationId = :operationId")
    suspend fun completeOperation(operationId: String)

    @Query("SELECT COUNT(*) FROM nurva_sync_outbox")
    fun observePendingOperationCount(): Flow<Int>
}
