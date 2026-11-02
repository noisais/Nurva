package com.bnyro.wallpaper.sync.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive v3→v4 migration: existing Wall You favorites/history data is retained. */
object NurvaMigrations {
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `nurva_sync_groups` (`groupId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `ownerUserId` TEXT NOT NULL, `currentRevision` INTEGER NOT NULL, `currentThemeId` TEXT, `currentAssetPath` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`groupId`))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `nurva_group_members` (`groupId` TEXT NOT NULL, `userId` TEXT NOT NULL, `role` TEXT NOT NULL, `joinedAt` INTEGER NOT NULL, `removedAt` INTEGER, PRIMARY KEY(`groupId`, `userId`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nurva_group_members_userId` ON `nurva_group_members` (`userId`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `nurva_sync_devices` (`deviceId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `userId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `wallpaperTarget` TEXT NOT NULL, `applyMode` TEXT NOT NULL, `lastAppliedRevision` INTEGER NOT NULL, `lastSeenAt` INTEGER, `revokedAt` INTEGER, PRIMARY KEY(`deviceId`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nurva_sync_devices_groupId` ON `nurva_sync_devices` (`groupId`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `nurva_wallpaper_revisions` (`revisionId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `createdByUserId` TEXT NOT NULL, `storagePath` TEXT NOT NULL, `localFilePath` TEXT, `sha256` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `byteSize` INTEGER NOT NULL, `imageWidth` INTEGER NOT NULL, `imageHeight` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `localState` TEXT NOT NULL, PRIMARY KEY(`revisionId`))""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_nurva_wallpaper_revisions_groupId_revision` ON `nurva_wallpaper_revisions` (`groupId`, `revision`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `nurva_mosaic_tiles` (`groupId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, `tileIndex` INTEGER NOT NULL, `rowIndex` INTEGER NOT NULL, `columnIndex` INTEGER NOT NULL, `cropLeft` REAL NOT NULL, `cropTop` REAL NOT NULL, `cropRight` REAL NOT NULL, `cropBottom` REAL NOT NULL, `outputWidth` INTEGER NOT NULL, `outputHeight` INTEGER NOT NULL, PRIMARY KEY(`groupId`, `revision`, `deviceId`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nurva_mosaic_tiles_groupId_revision` ON `nurva_mosaic_tiles` (`groupId`, `revision`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `nurva_themes` (`themeId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `name` TEXT NOT NULL, `layoutMode` TEXT NOT NULL, `description` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`themeId`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nurva_themes_groupId` ON `nurva_themes` (`groupId`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `nurva_theme_items` (`themeId` TEXT NOT NULL, `position` INTEGER NOT NULL, `revisionId` TEXT NOT NULL, `deviceId` TEXT, PRIMARY KEY(`themeId`, `position`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nurva_theme_items_themeId` ON `nurva_theme_items` (`themeId`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `nurva_sync_schedules` (`scheduleId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `themeId` TEXT, `daysOfWeekMask` INTEGER NOT NULL, `localHour` INTEGER NOT NULL, `localMinute` INTEGER NOT NULL, `timeZoneId` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `requireApproval` INTEGER NOT NULL, `nextRunAt` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`scheduleId`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nurva_sync_schedules_groupId` ON `nurva_sync_schedules` (`groupId`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `nurva_sync_outbox` (`operationId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `operationType` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `nextAttemptAt` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`operationId`))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nurva_sync_outbox_groupId_nextAttemptAt` ON `nurva_sync_outbox` (`groupId`, `nextAttemptAt`)")
        }
    }
}
