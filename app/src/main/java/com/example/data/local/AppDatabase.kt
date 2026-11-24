package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PresetEntity::class, ExportRecordEntity::class, ProjectEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
    abstract fun exportDao(): ExportDao
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `projects` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `aspectPresetName` TEXT NOT NULL,
                        `resolutionLabel` TEXT NOT NULL,
                        `fps` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `activePresetName` TEXT NOT NULL,
                        `layerCount` INTEGER NOT NULL,
                        `videoDisplayName` TEXT,
                        `thumbnailPath` TEXT,
                        `playheadMs` INTEGER NOT NULL,
                        `canvasFitModeName` TEXT NOT NULL,
                        `timelineZoom` REAL NOT NULL,
                        `snapshotJson` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "luna_studio.db",
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
