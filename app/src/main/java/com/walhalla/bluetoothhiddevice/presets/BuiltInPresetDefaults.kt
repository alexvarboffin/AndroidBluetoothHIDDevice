package com.walhalla.bluetoothhiddevice.presets

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Factory state of built-in presets, read from the same asset Room copies on first install. */
class BuiltInPresetDefaults(context: Context) {
    private val appContext = context.applicationContext
    private var cached: Snapshot? = null

    data class Snapshot(
        val categories: List<PresetCategoryEntity>,
        val presets: List<PresetEntity>,
        val actions: List<PresetActionEntity>
    ) {
        private val presetsById = presets.associateBy { it.id }
        private val actionsByPresetId = actions.groupBy { it.presetId }
            .mapValues { (_, items) -> items.sortedBy { it.sortOrder } }

        fun preset(presetId: Long): PresetEntity? = presetsById[presetId]

        fun actionsFor(presetId: Long): List<PresetActionEntity> = actionsByPresetId[presetId].orEmpty()
    }

    /** Blocking file and SQLite IO; call off the main thread. */
    @Synchronized
    fun load(): Snapshot = cached ?: read().also { cached = it }

    private fun read(): Snapshot {
        val file = File(appContext.cacheDir, "presets_seed_defaults.db")
        appContext.assets.open(PresetDatabase.SEED_ASSET).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).use { db ->
            Snapshot(
                categories = db.rawQuery("SELECT * FROM preset_categories", null).use { cursor ->
                    cursor.map {
                        PresetCategoryEntity(
                            id = long("id"),
                            title = string("title"),
                            sortOrder = int("sortOrder"),
                            isBuiltIn = bool("isBuiltIn"),
                            colorArgb = int("colorArgb"),
                            createdAt = long("createdAt")
                        )
                    }
                },
                presets = db.rawQuery("SELECT * FROM presets", null).use { cursor ->
                    cursor.map {
                        PresetEntity(
                            id = long("id"),
                            categoryId = long("categoryId"),
                            title = string("title"),
                            description = string("description"),
                            riskLevel = string("riskLevel"),
                            requiresConfirmation = bool("requiresConfirmation"),
                            isSensitive = bool("isSensitive"),
                            isBuiltIn = bool("isBuiltIn"),
                            sortOrder = int("sortOrder"),
                            createdAt = long("createdAt")
                        )
                    }
                },
                actions = db.rawQuery("SELECT * FROM preset_actions", null).use { cursor ->
                    cursor.map {
                        PresetActionEntity(
                            id = long("id"),
                            presetId = long("presetId"),
                            type = string("type"),
                            payloadJson = string("payloadJson"),
                            sortOrder = int("sortOrder")
                        )
                    }
                }
            )
        }
    }

    private fun <T> Cursor.map(row: Cursor.() -> T): List<T> {
        val result = ArrayList<T>(count)
        while (moveToNext()) result.add(row())
        return result
    }

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

    private fun Cursor.bool(column: String): Boolean = int(column) != 0
}
