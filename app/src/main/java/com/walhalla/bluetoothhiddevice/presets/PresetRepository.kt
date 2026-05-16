package com.walhalla.bluetoothhiddevice.presets

import android.content.Context
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class PresetRepository(context: Context) {
    private val dao = PresetDatabase.getInstance(context).presetDao()

    val categories: Flow<List<PresetCategoryEntity>> = dao.observeCategories()
    val allPresets: Flow<List<PresetEntity>> = dao.observeAllPresets()
    val allActions: Flow<List<PresetActionEntity>> = dao.observeAllActions()

    fun presetsForCategory(categoryId: Long): Flow<List<PresetEntity>> {
        return dao.observePresetsForCategory(categoryId)
    }

    suspend fun ensureSeedData() {
        if (dao.getCategoryCount() > 0) return

        val homeId = dao.insertCategory(PresetCategoryEntity(title = "Дом", sortOrder = 0, isBuiltIn = true))
        val workId = dao.insertCategory(PresetCategoryEntity(title = "Работа", sortOrder = 1, isBuiltIn = true))
        val devId = dao.insertCategory(PresetCategoryEntity(title = "Программирование", sortOrder = 2, isBuiltIn = true))

        addSingleActionPreset(homeId, "Calculator", "Windows Calculator", "calc", 0)
        addSingleActionPreset(homeId, "Notepad", "Windows Notepad", "notepad", 1)
        addSingleActionPreset(workId, "Firefox Profile Manager", "Open Firefox profile selector", "firefox -p", 0)
        addSingleActionPreset(workId, "Task Manager", "Open Windows Task Manager", "taskmgr", 1)
        addSingleActionPreset(devId, "Android Studio", "Launch Android Studio from PATH/App Paths", "studio64", 0)
        addSingleActionPreset(devId, "Visual Studio Code", "Launch VS Code", "code", 1)
    }

    suspend fun addCategory(title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return

        dao.insertCategory(
            PresetCategoryEntity(
                title = cleanTitle,
                sortOrder = dao.getMaxCategorySortOrder() + 1,
                isBuiltIn = false
            )
        )
    }

    suspend fun deleteCustomCategory(categoryId: Long): Boolean {
        return dao.deleteCustomCategory(categoryId) > 0
    }

    suspend fun addSingleActionPreset(
        categoryId: Long,
        title: String,
        description: String,
        value: String,
        sortOrder: Int,
        actionType: String = PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND,
        isSensitive: Boolean = false
    ) {
        val action = when (actionType) {
            PresetActionCodec.TYPE_TYPE_TEXT -> PresetAction.TypeText(value)
            PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT -> PresetAction.TypeSensitiveText(value)
            else -> PresetAction.RunWindowsCommand(value)
        }
        val preset = PresetEntity(
            categoryId = categoryId,
            title = title,
            description = description,
            riskLevel = if (isSensitive) "sensitive" else "normal",
            requiresConfirmation = isSensitive,
            isSensitive = isSensitive,
            sortOrder = sortOrder
        )
        dao.insertPresetWithActions(
            preset = preset,
            actions = listOf(PresetActionCodec.toEntity(0, action, 0))
        )
    }

    suspend fun getPresetWithActions(presetId: Long): PresetWithActions? {
        val preset = dao.getPreset(presetId) ?: return null
        return PresetWithActions(
            preset = preset,
            actions = dao.getActionsForPreset(presetId)
        )
    }

    suspend fun exportToJson(includeSensitive: Boolean): String {
        val categories = dao.getCategories()
        val presets = dao.getAllPresets().filter { includeSensitive || !it.isSensitive }
        val presetIds = presets.map { it.id }.toSet()
        val actions = dao.getAllActions().filter { it.presetId in presetIds }

        return JSONObject()
            .put("version", 1)
            .put("categories", JSONArray(categories.map { category ->
                JSONObject()
                    .put("id", category.id)
                    .put("title", category.title)
                    .put("sortOrder", category.sortOrder)
                    .put("isBuiltIn", category.isBuiltIn)
                    .put("createdAt", category.createdAt)
            }))
            .put("presets", JSONArray(presets.map { preset ->
                JSONObject()
                    .put("id", preset.id)
                    .put("categoryId", preset.categoryId)
                    .put("title", preset.title)
                    .put("description", preset.description)
                    .put("riskLevel", preset.riskLevel)
                    .put("requiresConfirmation", preset.requiresConfirmation)
                    .put("isSensitive", preset.isSensitive)
                    .put("sortOrder", preset.sortOrder)
                    .put("createdAt", preset.createdAt)
            }))
            .put("actions", JSONArray(actions.map { action ->
                JSONObject()
                    .put("presetId", action.presetId)
                    .put("type", action.type)
                    .put("payloadJson", action.payloadJson)
                    .put("sortOrder", action.sortOrder)
            }))
            .toString(2)
    }

    suspend fun importFromJson(json: String) {
        val root = JSONObject(json)
        val categoryIdMap = mutableMapOf<Long, Long>()
        val categories = root.getJSONArray("categories")

        for (index in 0 until categories.length()) {
            val source = categories.getJSONObject(index)
            val oldId = source.getLong("id")
            val newId = dao.insertCategory(
                PresetCategoryEntity(
                    title = source.getString("title"),
                    sortOrder = source.optInt("sortOrder", index),
                    isBuiltIn = source.optBoolean("isBuiltIn", false),
                    createdAt = System.currentTimeMillis()
                )
            )
            categoryIdMap[oldId] = newId
        }

        val actionsByPresetId = mutableMapOf<Long, MutableList<PresetActionEntity>>()
        val actions = root.getJSONArray("actions")
        for (index in 0 until actions.length()) {
            val action = actions.getJSONObject(index)
            val oldPresetId = action.getLong("presetId")
            actionsByPresetId.getOrPut(oldPresetId) { mutableListOf() }.add(
                PresetActionEntity(
                    presetId = 0,
                    type = action.getString("type"),
                    payloadJson = action.getString("payloadJson"),
                    sortOrder = action.optInt("sortOrder", index)
                )
            )
        }

        val presets = root.getJSONArray("presets")
        for (index in 0 until presets.length()) {
            val source = presets.getJSONObject(index)
            val oldPresetId = source.getLong("id")
            val oldCategoryId = source.getLong("categoryId")
            val newCategoryId = categoryIdMap[oldCategoryId] ?: continue
            dao.insertPresetWithActions(
                preset = PresetEntity(
                    categoryId = newCategoryId,
                    title = source.getString("title"),
                    description = source.optString("description"),
                    riskLevel = source.optString("riskLevel", "normal"),
                    requiresConfirmation = source.optBoolean("requiresConfirmation"),
                    isSensitive = source.optBoolean("isSensitive"),
                    sortOrder = source.optInt("sortOrder", index),
                    createdAt = System.currentTimeMillis()
                ),
                actions = actionsByPresetId[oldPresetId].orEmpty()
            )
        }
    }
}
