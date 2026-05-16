package com.walhalla.bluetoothhiddevice.presets

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PresetCategoryEntity::class,
        PresetEntity::class,
        PresetActionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PresetDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    companion object {
        @Volatile
        private var INSTANCE: PresetDatabase? = null

        fun getInstance(context: Context): PresetDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PresetDatabase::class.java,
                    "preset_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preset_categories ADD COLUMN isBuiltIn INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE preset_categories
                    SET isBuiltIn = 1
                    WHERE title IN ('Дом', 'Работа', 'Программирование')
                    """.trimIndent()
                )
            }
        }
    }
}
