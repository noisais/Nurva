package com.bnyro.wallpaper.db

import android.content.Context
import androidx.room.Room
import com.bnyro.wallpaper.sync.db.NurvaMigrations

object DatabaseHolder {
    private const val DATABASE_NAME = "WallYouDb"
    lateinit var Database: AppDatabase

    fun create(applicationContext: Context) {
        Database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(NurvaMigrations.MIGRATION_3_4)
            .build()
    }
}
