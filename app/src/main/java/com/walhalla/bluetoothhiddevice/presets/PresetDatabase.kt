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
    version = 4,
    exportSchema = false
)
abstract class PresetDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    companion object {
        /** Built by tools/build_presets_seed_db.py. Built-in rows keep the same IDs on every device. */
        const val SEED_ASSET = "databases/presets_seed.db"
        private const val DATABASE_NAME = "presets.db"
        private const val LEGACY_DATABASE_NAME = "preset_database"

        @Volatile
        private var INSTANCE: PresetDatabase? = null

        fun getInstance(context: Context): PresetDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    context.applicationContext.deleteDatabase(LEGACY_DATABASE_NAME)
                    Room.databaseBuilder(
                        context.applicationContext,
                        PresetDatabase::class.java,
                        DATABASE_NAME
                    )
                        .createFromAsset(SEED_ASSET)
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                        .build()
                }
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE presets ADD COLUMN isBuiltIn INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE presets
                    SET isBuiltIn = 1
                    WHERE title IN (
                        'Calculator',
                        'Notepad',
                        'Firefox Profile Manager',
                        'Task Manager',
                        'Android Studio',
                        'Visual Studio Code'
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE preset_categories ADD COLUMN colorArgb INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
