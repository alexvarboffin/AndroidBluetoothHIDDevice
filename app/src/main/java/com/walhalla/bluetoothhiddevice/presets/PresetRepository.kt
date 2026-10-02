package com.walhalla.bluetoothhiddevice.presets

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PresetRepository(
    context: Context,
    private val database: PresetDatabase = PresetDatabase.getInstance(context)
) {
    private val dao = database.presetDao()
    private val defaults = BuiltInPresetDefaults(context)

    val categories: Flow<List<PresetCategoryEntity>> = dao.observeCategories()
    val allPresets: Flow<List<PresetEntity>> = dao.observeAllPresets()
    val allActions: Flow<List<PresetActionEntity>> = dao.observeAllActions()

    fun presetsForCategory(categoryId: Long): Flow<List<PresetEntity>> {
        return dao.observePresetsForCategory(categoryId)
    }

    /** Adds built-ins shipped by a newer asset than the one this install was created from. */
    suspend fun syncBuiltIns() {
        val seed = withContext(Dispatchers.IO) { defaults.load() }
        dao.insertMissingBuiltIns(
            categories = seed.categories,
            presets = seed.presets,
            actionsByPresetId = seed.actions.groupBy { it.presetId }
        )
    }

    suspend fun resetBuiltInPreset(presetId: Long): Boolean {
        val seed = withContext(Dispatchers.IO) { defaults.load() }
        val defaultPreset = seed.preset(presetId) ?: return false
        dao.updatePresetWithActions(
            preset = defaultPreset,
            actions = seed.actionsFor(presetId).map { it.copy(id = 0) }
        )
        return true
    }

    /** IDs of built-in presets whose content differs from the factory asset. */
    suspend fun findModifiedBuiltIns(
        presets: List<PresetEntity>,
        actions: List<PresetActionEntity>
    ): Set<Long> {
        val seed = withContext(Dispatchers.IO) { defaults.load() }
        val actionsByPresetId = actions.groupBy { it.presetId }
        return presets
            .filter { it.isBuiltIn }
            .filterNot { preset ->
                val defaultPreset = seed.preset(preset.id) ?: return@filterNot true
                sameContent(preset, defaultPreset) &&
                    comparableActions(actionsByPresetId[preset.id].orEmpty()) ==
                    comparableActions(seed.actionsFor(preset.id))
            }
            .map { it.id }
            .toSet()
    }

    private fun sameContent(current: PresetEntity, default: PresetEntity): Boolean {
        return current.categoryId == default.categoryId &&
            current.title == default.title &&
            current.description == default.description &&
            current.riskLevel == default.riskLevel &&
            current.requiresConfirmation == default.requiresConfirmation &&
            current.isSensitive == default.isSensitive
    }

    /** Payload JSON text and modifier order differ after an edit round trip, so compare meaning. */
    private fun comparableActions(actions: List<PresetActionEntity>): List<Any> {
        return actions.sortedBy { it.sortOrder }.map { entity ->
            runCatching {
                when (val action = PresetActionCodec.fromEntity(entity)) {
                    is PresetAction.KeyCombo -> PresetShortcutParser.normalizeKey(action.key) to action.modifier
                        .split('+')
                        .filter { it.isNotBlank() }
                        .map { PresetShortcutParser.normalizeModifier(it) }
                        .toSet()
                    is PresetAction.KeyPress -> PresetAction.KeyPress(PresetShortcutParser.normalizeKey(action.key))
                    else -> action
                }
            }.getOrElse { entity.type to entity.payloadJson }
        }
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

    suspend fun setCategoryColor(categoryId: Long, colorArgb: Int) {
        dao.updateCategoryColor(categoryId, colorArgb)
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
        insertPresetWithAction(
            categoryId = categoryId,
            title = title,
            description = description,
            action = action,
            sortOrder = sortOrder,
            isSensitive = isSensitive,
            isBuiltIn = isBuiltIn
        )
    }

    suspend fun addKeyShortcutPreset(
        categoryId: Long,
        title: String,
        description: String,
        shortcut: String,
        sortOrder: Int,
        isBuiltIn: Boolean = false
    ) {
        val action = PresetShortcutParser.parse(shortcut)
        insertPresetWithAction(
            categoryId = categoryId,
            title = title,
            description = description,
            action = action,
            sortOrder = sortOrder,
            isBuiltIn = isBuiltIn
        )
    }

    private suspend fun insertPresetWithAction(
        categoryId: Long,
        title: String,
        description: String,
        action: PresetAction,
        sortOrder: Int,
        isSensitive: Boolean = false,
        isBuiltIn: Boolean = false
    ) {
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
        // if (source.preset.isBuiltIn) return false

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

    suspend fun addPresetWithActions(
        categoryId: Long,
        title: String,
        description: String,
        actions: List<PresetAction>
    ): Long {
        val sortOrder = dao.getMaxPresetSortOrder(categoryId) + 1
        return dao.insertPresetWithActions(
            preset = PresetEntity(
                categoryId = categoryId,
                title = title,
                description = description,
                sortOrder = sortOrder
            ),
            actions = actions.mapIndexed { index, action -> PresetActionCodec.toEntity(0, action, index) }
        )
    }

    suspend fun replacePresetActions(
        presetId: Long,
        title: String,
        description: String,
        actions: List<PresetAction>
    ): Boolean {
        val source = getPresetWithActions(presetId) ?: return false
        dao.updatePresetWithActions(
            preset = source.preset.copy(title = title, description = description),
            actions = actions.mapIndexed { index, action -> PresetActionCodec.toEntity(presetId, action, index) }
        )
        return true
    }

    /** Presets listed in [orderedIds] come first in that order, the rest keep their relative order. */
    suspend fun reorderPresets(categoryId: Long, orderedIds: List<Long>) {
        database.withTransaction {
            val inCategory = dao.getPresetsInCategory(categoryId)
            val byId = inCategory.associateBy { it.id }
            val ordered = orderedIds.distinct().mapNotNull { byId[it] }
            val rest = inCategory.filter { it.id !in orderedIds }.sortedBy { it.sortOrder }
            (ordered + rest).forEachIndexed { index, preset ->
                if (preset.sortOrder != index) dao.updatePreset(preset.copy(sortOrder = index))
            }
        }
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

    /**
     * Version 2: each category holds its presets, each preset holds its actions, and an action
     * value is a JSON object. Built-in IDs are the fixed asset IDs, so they match on any device.
     */
    suspend fun exportToJson(includeSensitive: Boolean): String {
        val categories = dao.getCategories()
        val presetsByCategoryId = dao.getAllPresets()
            .filter { includeSensitive || !it.isSensitive }
            .groupBy { it.categoryId }
        val actionsByPresetId = dao.getAllActions().groupBy { it.presetId }

        return JSONObject()
            .put("version", EXPORT_VERSION)
            .put("categories", JSONArray(categories.map { category ->
                JSONObject()
                    .put("id", category.id)
                    .put("title", category.title)
                    .put("sortOrder", category.sortOrder)
                    .put("isBuiltIn", category.isBuiltIn)
                    .put("colorArgb", category.colorArgb)
                    .put("presets", JSONArray(presetsByCategoryId[category.id].orEmpty().map { preset ->
                        JSONObject()
                            .put("id", preset.id)
                            .put("title", preset.title)
                            .put("description", preset.description)
                            .put("riskLevel", preset.riskLevel)
                            .put("requiresConfirmation", preset.requiresConfirmation)
                            .put("isSensitive", preset.isSensitive)
                            .put("isBuiltIn", preset.isBuiltIn)
                            .put("sortOrder", preset.sortOrder)
                            .put("actions", JSONArray(
                                actionsByPresetId[preset.id].orEmpty().sortedBy { it.sortOrder }.map { action ->
                                    JSONObject()
                                        .put("type", action.type)
                                        .put("value", JSONObject(action.payloadJson))
                                        .put("sortOrder", action.sortOrder)
                                }
                            ))
                    }))
            }))
            .toString(2)
    }

    /**
     * Built-in rows are matched by fixed ID and get the file's values. Custom groups are matched
     * by title, custom presets are skipped when the group already has one with the same title and
     * actions, so importing the same file twice changes nothing. All or nothing: one transaction.
     */
    suspend fun importFromJson(json: String) {
        val root = JSONObject(json)
        database.withTransaction {
            if (root.optInt("version", 1) >= EXPORT_VERSION) {
                importNested(root)
            } else {
                importLegacy(root)
            }
        }
    }

    private suspend fun importNested(root: JSONObject) {
        val categories = root.getJSONArray("categories")
        for (categoryIndex in 0 until categories.length()) {
            val category = categories.getJSONObject(categoryIndex)
            val categoryId = resolveImportCategory(category, trustBuiltInIds = true)
            val presets = category.optJSONArray("presets") ?: JSONArray()
            for (presetIndex in 0 until presets.length()) {
                val preset = presets.getJSONObject(presetIndex)
                val actionsJson = preset.optJSONArray("actions") ?: JSONArray()
                val actions = (0 until actionsJson.length()).map { actionIndex ->
                    val action = actionsJson.getJSONObject(actionIndex)
                    PresetActionEntity(
                        presetId = 0,
                        type = action.getString("type"),
                        payloadJson = action.getJSONObject("value").toString(),
                        sortOrder = action.optInt("sortOrder", actionIndex)
                    )
                }
                importPreset(categoryId, preset, actions, trustBuiltInIds = true)
            }
        }
    }

    /** Version 1 files: flat arrays and IDs from the old autoincrement database, not asset IDs. */
    private suspend fun importLegacy(root: JSONObject) {
        val categoryIdMap = mutableMapOf<Long, Long>()
        val categories = root.getJSONArray("categories")
        for (index in 0 until categories.length()) {
            val category = categories.getJSONObject(index)
            categoryIdMap[category.getLong("id")] = resolveImportCategory(category, trustBuiltInIds = false)
        }

        val actionsByPresetId = mutableMapOf<Long, MutableList<PresetActionEntity>>()
        val actions = root.getJSONArray("actions")
        for (index in 0 until actions.length()) {
            val action = actions.getJSONObject(index)
            actionsByPresetId.getOrPut(action.getLong("presetId")) { mutableListOf() }.add(
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
            val preset = presets.getJSONObject(index)
            val categoryId = categoryIdMap[preset.getLong("categoryId")] ?: continue
            importPreset(
                categoryId = categoryId,
                source = preset,
                actions = actionsByPresetId[preset.getLong("id")].orEmpty().sortedBy { it.sortOrder },
                trustBuiltInIds = false
            )
        }
    }

    private suspend fun resolveImportCategory(source: JSONObject, trustBuiltInIds: Boolean): Long {
        val title = source.getString("title")
        val colorArgb = source.optInt("colorArgb", 0)
        val existing = if (source.optBoolean("isBuiltIn", false)) {
            val id = source.optLong("id", 0)
            (if (trustBuiltInIds && id in 1 until BUILT_IN_ID_LIMIT) dao.getCategory(id) else null)
                ?.takeIf { it.isBuiltIn }
                ?: dao.findCategoryByTitle(title, isBuiltIn = true)
                ?: dao.findCategoryByTitle(title, isBuiltIn = false)
        } else {
            dao.findCategoryByTitle(title, isBuiltIn = false)
        }

        if (existing != null) {
            if (existing.colorArgb != colorArgb) dao.updateCategoryColor(existing.id, colorArgb)
            return existing.id
        }
        return dao.insertCategory(
            PresetCategoryEntity(
                title = title,
                sortOrder = dao.getMaxCategorySortOrder() + 1,
                isBuiltIn = false,
                colorArgb = colorArgb
            )
        )
    }

    private suspend fun importPreset(
        categoryId: Long,
        source: JSONObject,
        actions: List<PresetActionEntity>,
        trustBuiltInIds: Boolean
    ) {
        actions.forEach { PresetActionCodec.fromEntity(it) }

        val title = source.getString("title")
        val description = source.optString("description")
        val hasSensitiveAction = actions.any {
            it.type == PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT ||
                it.type == PresetActionCodec.TYPE_CREDENTIAL
        }
        val isSensitive = source.optBoolean("isSensitive") || hasSensitiveAction
        val riskLevel = if (isSensitive) "sensitive" else source.optString("riskLevel", "normal")
        val requiresConfirmation = source.optBoolean("requiresConfirmation") || isSensitive

        val id = source.optLong("id", 0)
        if (trustBuiltInIds && source.optBoolean("isBuiltIn", false) && id in 1 until BUILT_IN_ID_LIMIT) {
            val builtIn = dao.getPreset(id)?.takeIf { it.isBuiltIn }
            if (builtIn != null) {
                dao.updatePresetWithActions(
                    preset = builtIn.copy(
                        title = title,
                        description = description,
                        riskLevel = riskLevel,
                        requiresConfirmation = requiresConfirmation,
                        isSensitive = isSensitive
                    ),
                    actions = actions
                )
                return
            }
        }

        val incomingActions = comparableActions(actions)
        val alreadyThere = dao.getPresetsInCategory(categoryId).any { existing ->
            existing.title == title &&
                comparableActions(dao.getActionsForPreset(existing.id)) == incomingActions
        }
        if (alreadyThere) return

        dao.insertPresetWithActions(
            preset = PresetEntity(
                categoryId = categoryId,
                title = title,
                description = description,
                riskLevel = riskLevel,
                requiresConfirmation = requiresConfirmation,
                isSensitive = isSensitive,
                isBuiltIn = false,
                sortOrder = dao.getMaxPresetSortOrder(categoryId) + 1
            ),
            actions = actions
        )
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
            PresetActionCodec.TYPE_KEYBOARD_SHORTCUT -> PresetShortcutParser.parse(value)
            else -> PresetAction.RunWindowsCommand(value)
        }
    }

    companion object {
        const val EXPORT_VERSION = 2

        /** Built-in rows in presets_seed.db use IDs below this; user rows start here. */
        const val BUILT_IN_ID_LIMIT = 100_000L
    }
}
