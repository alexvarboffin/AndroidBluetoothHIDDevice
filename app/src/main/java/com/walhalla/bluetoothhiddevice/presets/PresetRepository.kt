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

        addSingleActionPreset(homeId, "Calculator", "Windows Calculator", "calc", 0, isBuiltIn = true)
        addSingleActionPreset(homeId, "Notepad", "Windows Notepad", "notepad", 1, isBuiltIn = true)
        addSingleActionPreset(workId, "Firefox Profile Manager", "Open Firefox profile selector", "firefox -p", 0, isBuiltIn = true)
        addSingleActionPreset(workId, "Task Manager", "Open Windows Task Manager", "taskmgr", 1, isBuiltIn = true)
        addSingleActionPreset(devId, "Android Studio", "Launch Android Studio from PATH/App Paths", "studio64", 0, isBuiltIn = true)
        addSingleActionPreset(devId, "Visual Studio Code", "Launch VS Code", "code", 1, isBuiltIn = true)
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
        isSensitive: Boolean = false,
        isBuiltIn: Boolean = false
    ) {
        val action = actionFromValue(actionType, value)
        val preset = PresetEntity(
            categoryId = categoryId,
            title = title,
            description = description,
            riskLevel = if (isSensitive) "sensitive" else "normal",
            requiresConfirmation = isSensitive,
            isSensitive = isSensitive,
            isBuiltIn = isBuiltIn,
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

    suspend fun updateSingleActionPreset(
        presetId: Long,
        title: String,
        description: String,
        value: String,
        actionType: String,
        isSensitive: Boolean
    ): Boolean {
        val source = getPresetWithActions(presetId) ?: return false
        if (source.preset.isBuiltIn) return false

        val action = actionFromValue(actionType, value)
        dao.updatePresetWithActions(
            preset = source.preset.copy(
                title = title,
                description = description,
                riskLevel = if (isSensitive) "sensitive" else "normal",
                requiresConfirmation = isSensitive,
                isSensitive = isSensitive
            ),
            actions = listOf(PresetActionCodec.toEntity(presetId, action, 0))
        )
        return true
    }

    suspend fun duplicatePreset(presetId: Long): Boolean {
        val source = getPresetWithActions(presetId) ?: return false
        val nextSortOrder = dao.getMaxPresetSortOrder(source.preset.categoryId) + 1
        dao.insertPresetWithActions(
            preset = source.preset.copy(
                id = 0,
                title = "${source.preset.title} copy",
                isBuiltIn = false,
                sortOrder = nextSortOrder,
                createdAt = System.currentTimeMillis()
            ),
            actions = source.actions.map { action ->
                action.copy(id = 0, presetId = 0)
            }
        )
        return true
    }

    suspend fun deletePreset(presetId: Long): Boolean {
        return dao.deletePreset(presetId) > 0
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
                    .put("isBuiltIn", preset.isBuiltIn)
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
            val presetActions = actionsByPresetId[oldPresetId].orEmpty()
            val hasSensitiveAction = presetActions.any {
                it.type == PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT ||
                    it.type == PresetActionCodec.TYPE_CREDENTIAL
            }
            val isSensitive = source.optBoolean("isSensitive") || hasSensitiveAction
            dao.insertPresetWithActions(
                preset = PresetEntity(
                    categoryId = newCategoryId,
                    title = source.getString("title"),
                    description = source.optString("description"),
                    riskLevel = if (isSensitive) "sensitive" else source.optString("riskLevel", "normal"),
                    requiresConfirmation = source.optBoolean("requiresConfirmation") || isSensitive,
                    isSensitive = isSensitive,
                    isBuiltIn = false,
                    sortOrder = source.optInt("sortOrder", index),
                    createdAt = System.currentTimeMillis()
                ),
                actions = presetActions
            )
        }
    }

    private fun actionFromValue(actionType: String, value: String): PresetAction {
        return when (actionType) {
            PresetActionCodec.TYPE_TYPE_TEXT -> PresetAction.TypeText(value)
            PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT -> PresetAction.TypeSensitiveText(value)
            PresetActionCodec.TYPE_CREDENTIAL -> {
                val credential = JSONObject(value)
                PresetAction.Credential(
                    login = credential.getString("login"),
                    password = credential.getString("password")
                )
            }
            else -> PresetAction.RunWindowsCommand(value)
        }
    }
}
