package com.bnyro.wallpaper.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.bnyro.wallpaper.db.dao.FavoritesDao
import com.bnyro.wallpaper.db.obj.Wallpaper
import com.bnyro.wallpaper.sync.db.*

@Database(
    version = 4,
    autoMigrations = [
        AutoMigration(1, 2),
        AutoMigration(2, 3)
    ],
    entities = [
        Wallpaper::class,
        SyncGroupEntity::class,
        SyncGroupMemberEntity::class,
        SyncDeviceEntity::class,
        SyncWallpaperRevisionEntity::class,
        MosaicTileAssignmentEntity::class,
        SyncThemeEntity::class,
        SyncThemeItemEntity::class,
        SyncScheduleEntity::class,
        SyncOutboxEntity::class,
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun nurvaSyncDao(): NurvaSyncDao
}
