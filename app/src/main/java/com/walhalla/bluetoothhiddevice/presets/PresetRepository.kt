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
        renameEnglishBuiltIns()
        ensureBuiltInCategory("Home", sortOrder = 0) { homeId ->
            ensureBuiltInCommandPreset(homeId, "Calculator", "Windows Calculator", "calc", 0)
            ensureBuiltInCommandPreset(homeId, "Notepad", "Windows Notepad", "notepad", 1)
            ensureBuiltInShortcutPreset(homeId, "Ctrl+Alt+Del", "Security screen (Ctrl+Alt+Del)", "ctrl+alt+delete", 2)
            ensureBuiltInShortcutPreset(homeId, "Task Manager", "Task Manager (Ctrl+Shift+Esc)", "ctrl+shift+escape", 3)
            ensureBuiltInShortcutPreset(homeId, "Explorer", "File Explorer (Win+E)", "win+e", 4)
            ensureBuiltInShortcutPreset(homeId, "Settings", "Windows Settings (Win+I)", "win+i", 5)
            ensureBuiltInShortcutPreset(homeId, "Quick Link Menu", "Power user menu (Win+X)", "win+x", 6)
            ensureBuiltInShortcutPreset(homeId, "Show Desktop", "Minimize all / restore (Win+D)", "win+d", 7)
            ensureBuiltInShortcutPreset(homeId, "Lock PC", "Lock the workstation (Win+L)", "win+l", 8)
            ensureBuiltInShortcutPreset(homeId, "Snipping Tool", "Region screenshot (Win+Shift+S)", "win+shift+s", 9)
            ensureBuiltInShortcutPreset(homeId, "Clipboard History", "Clipboard history (Win+V)", "win+v", 10)
            ensureBuiltInShortcutPreset(homeId, "Emoji Panel", "Emoji panel (Win+.)", "win+.", 11)
            ensureBuiltInShortcutPreset(homeId, "Task View", "Task View (Win+Tab)", "win+tab", 12)
            ensureBuiltInShortcutPreset(homeId, "Next Desktop", "Next virtual desktop (Ctrl+Win+Right)", "ctrl+win+right", 13)
            ensureBuiltInShortcutPreset(homeId, "Project", "Display mode (Win+P)", "win+p", 14)
            ensureBuiltInShortcutPreset(homeId, "Search", "Windows Search (Win+S)", "win+s", 15)
            ensureBuiltInCommandPreset(homeId, "Network Adapters", "Network Connections (ncpa.cpl)", "ncpa.cpl", 16)
            ensureBuiltInCommandPreset(homeId, "Wi-Fi Settings", "Settings: Wi-Fi", "ms-settings:network-wifi", 17)
            ensureBuiltInCommandPreset(homeId, "Display Settings", "Settings: Display", "ms-settings:display", 18)
            ensureBuiltInCommandPreset(homeId, "Control Panel", "Classic Control Panel", "control", 19)
            ensureBuiltInCommandPreset(homeId, "Device Manager", "Device Manager (devmgmt.msc)", "devmgmt.msc", 20)
            ensureBuiltInCommandPreset(homeId, "System Properties", "System Properties (sysdm.cpl)", "sysdm.cpl", 21)
            ensureBuiltInCommandPreset(homeId, "Programs and Features", "Uninstall programs (appwiz.cpl)", "appwiz.cpl", 22)
            ensureBuiltInCommandPreset(homeId, "Services", "Windows Services (services.msc)", "services.msc", 23)
            ensureBuiltInCommandPreset(homeId, "Disk Management", "Disk Management (diskmgmt.msc)", "diskmgmt.msc", 24)
            ensureBuiltInCommandPreset(homeId, "Event Viewer", "Event Viewer (eventvwr.msc)", "eventvwr.msc", 25)
            ensureBuiltInCommandPreset(homeId, "Sound", "Sound devices (mmsys.cpl)", "mmsys.cpl", 26)
            ensureBuiltInCommandPreset(homeId, "Power Options", "Power Options (powercfg.cpl)", "powercfg.cpl", 27)
            ensureBuiltInCommandPreset(homeId, "System Information", "System Information (msinfo32)", "msinfo32", 28)
            ensureBuiltInCommandPreset(homeId, "DirectX Diagnostic", "DirectX Diagnostic Tool (dxdiag)", "dxdiag", 29)
            ensureBuiltInCommandPreset(homeId, "Command Prompt", "cmd", "cmd", 30)
            ensureBuiltInCommandPreset(homeId, "PowerShell", "Windows PowerShell", "powershell", 31)
            ensureBuiltInCommandPreset(homeId, "Registry Editor", "Registry Editor (UAC prompt)", "regedit", 32)
        }
        ensureBuiltInCategory("Work", sortOrder = 1) { workId ->
            ensureBuiltInCommandPreset(workId, "Firefox Profile Manager", "Open Firefox profile selector", "firefox -p", 0)
            ensureBuiltInCommandPreset(workId, "Task Manager", "Open Windows Task Manager", "taskmgr", 1)
        }
        ensureBuiltInCategory("Programming", sortOrder = 2) { devId ->
            ensureBuiltInCommandPreset(devId, "Android Studio", "Launch Android Studio from PATH/App Paths", "studio64", 0)
            ensureBuiltInCommandPreset(devId, "Visual Studio Code", "Launch VS Code", "code", 1)
        }
        ensureBuiltInCategory("Cursor IDE", sortOrder = 3) { cursorId ->
            ensureBuiltInShortcutPreset(cursorId, "AI Chat", "Open AI Chat (Ctrl+L)", "ctrl+l", 0)
            ensureBuiltInShortcutPreset(cursorId, "Inline Edit", "Inline AI edit (Ctrl+K)", "ctrl+k", 1)
            ensureBuiltInShortcutPreset(cursorId, "Agent/Composer", "Open Agent / Composer (Ctrl+I)", "ctrl+i", 2)
            ensureBuiltInShortcutPreset(cursorId, "Accept Change", "Accept suggestion or inline change (Tab)", "tab", 3)
            ensureBuiltInShortcutPreset(cursorId, "Reject Change", "Reject or dismiss (Escape)", "escape", 4)
            ensureBuiltInShortcutPreset(cursorId, "Accept All", "Accept all suggested changes (Ctrl+Enter)", "ctrl+enter", 5)
            ensureBuiltInShortcutPreset(cursorId, "Next Diff", "Next chat / change (Ctrl+])", "ctrl+]", 6)
            ensureBuiltInShortcutPreset(cursorId, "Prev Diff", "Previous chat / change (Ctrl+[)", "ctrl+[", 7)
            ensureBuiltInShortcutPreset(cursorId, "Terminal", "Toggle integrated terminal (Ctrl+`)", "ctrl+`", 8)
            ensureBuiltInShortcutPreset(cursorId, "Command Palette", "Command palette (Ctrl+Shift+P)", "ctrl+shift+p", 9)
            ensureBuiltInShortcutPreset(cursorId, "Quick Open", "Quick open file (Ctrl+P)", "ctrl+p", 10)
            ensureBuiltInShortcutPreset(cursorId, "New Chat", "New chat (Ctrl+N)", "ctrl+n", 11)
        }
        ensureBuiltInCategory("Streamer deck", sortOrder = 4) { deckId ->
            ensureBuiltInCommandPreset(
                deckId,
                "Open OBS",
                "Launch OBS. Default install path",
                "\"C:\\Program Files\\obs-studio\\bin\\64bit\\obs64.exe\"",
                0
            )
            ensureBuiltInShortcutPreset(deckId, "Game Bar", "Windows capture bar (Win+G)", "win+g", 1)
            ensureBuiltInShortcutPreset(deckId, "Record", "Start and stop Game Bar recording (Win+Alt+R)", "win+alt+r", 2)
            ensureBuiltInShortcutPreset(deckId, "Last 30s", "Last 30 seconds, if background recording is on (Win+Alt+G)", "win+alt+g", 3)
            ensureBuiltInShortcutPreset(deckId, "Mute mic", "Game Bar microphone (Win+Alt+M)", "win+alt+m", 4)
            ensureBuiltInShortcutPreset(deckId, "Screenshot", "Game Bar screenshot (Win+Alt+PrtScn)", "win+alt+prtsc", 5)
            ensureBuiltInShortcutPreset(deckId, "Scene 1", "OBS scene. Assign Ctrl+F1 once in OBS", "ctrl+f1", 6)
            ensureBuiltInShortcutPreset(deckId, "Scene 2", "OBS scene. Assign Ctrl+F2 once in OBS", "ctrl+f2", 7)
            ensureBuiltInShortcutPreset(deckId, "Scene 3", "OBS scene. Assign Ctrl+F3 once in OBS", "ctrl+f3", 8)
            ensureBuiltInShortcutPreset(deckId, "Go live", "OBS stream. Assign Start Streaming to Ctrl+F9 once", "ctrl+f9", 9)
        }
    }

    private suspend fun renameEnglishBuiltIns() {
        dao.renameBuiltInCategory("Дом", "Home")
        dao.renameBuiltInCategory("Работа", "Work")
        dao.renameBuiltInCategory("Программирование", "Programming")
        dao.renameBuiltInCategory("Стримерский дек", "Streamer deck")
        dao.replaceBuiltInPresetDescription(
            "Запуск OBS. Путь установки по умолчанию",
            "Launch OBS. Default install path"
        )
        dao.replaceBuiltInPresetDescription(
            "Панель записи Windows (Win+G)",
            "Windows capture bar (Win+G)"
        )
        dao.replaceBuiltInPresetDescription(
            "Старт и стоп записи Game Bar (Win+Alt+R)",
            "Start and stop Game Bar recording (Win+Alt+R)"
        )
        dao.replaceBuiltInPresetDescription(
            "Последние 30 секунд, если фоновая запись включена (Win+Alt+G)",
            "Last 30 seconds, if background recording is on (Win+Alt+G)"
        )
        dao.replaceBuiltInPresetDescription(
            "Микрофон Game Bar (Win+Alt+M)",
            "Game Bar microphone (Win+Alt+M)"
        )
        dao.replaceBuiltInPresetDescription(
            "Скриншот Game Bar (Win+Alt+PrtScn)",
            "Game Bar screenshot (Win+Alt+PrtScn)"
        )
        dao.replaceBuiltInPresetDescription(
            "Сцена OBS. Один раз назначь в OBS тот же Ctrl+F1",
            "OBS scene. Assign Ctrl+F1 once in OBS"
        )
        dao.replaceBuiltInPresetDescription(
            "Сцена OBS. Один раз назначь в OBS тот же Ctrl+F2",
            "OBS scene. Assign Ctrl+F2 once in OBS"
        )
        dao.replaceBuiltInPresetDescription(
            "Сцена OBS. Один раз назначь в OBS тот же Ctrl+F3",
            "OBS scene. Assign Ctrl+F3 once in OBS"
        )
        dao.replaceBuiltInPresetDescription(
            "Эфир OBS. Один раз назначь Start Streaming на Ctrl+F9",
            "OBS stream. Assign Start Streaming to Ctrl+F9 once"
        )
    }

    private suspend fun ensureBuiltInCategory(
        title: String,
        sortOrder: Int,
        block: suspend (Long) -> Unit
    ) {
        val categoryId = dao.getCategoryByTitle(title)?.id
            ?: dao.insertCategory(
                PresetCategoryEntity(
                    title = title,
                    sortOrder = sortOrder,
                    isBuiltIn = true
                )
            )
        block(categoryId)
    }

    private suspend fun ensureBuiltInCommandPreset(
        categoryId: Long,
        title: String,
        description: String,
        command: String,
        sortOrder: Int
    ) {
        if (dao.countPresetsInCategory(categoryId, title) > 0) return
        addSingleActionPreset(
            categoryId = categoryId,
            title = title,
            description = description,
            value = command,
            sortOrder = sortOrder,
            isBuiltIn = true
        )
    }

    private suspend fun ensureBuiltInShortcutPreset(
        categoryId: Long,
        title: String,
        description: String,
        shortcut: String,
        sortOrder: Int
    ) {
        if (dao.countPresetsInCategory(categoryId, title) > 0) return
        addKeyShortcutPreset(
            categoryId = categoryId,
            title = title,
            description = description,
            shortcut = shortcut,
            sortOrder = sortOrder,
            isBuiltIn = true
        )
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
                    .put("colorArgb", category.colorArgb)
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
                    colorArgb = source.optInt("colorArgb", 0),
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
            PresetActionCodec.TYPE_KEYBOARD_SHORTCUT -> PresetShortcutParser.parse(value)
            else -> PresetAction.RunWindowsCommand(value)
        }
    }
}
