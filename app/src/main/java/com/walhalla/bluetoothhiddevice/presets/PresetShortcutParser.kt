package com.walhalla.bluetoothhiddevice.presets

object PresetShortcutParser {
    val MODIFIERS = setOf(
        "ctrl", "control", "shift", "alt", "win", "gui", "meta",
        "rctrl", "rcontrol", "rshift", "ralt", "altgr", "rwin", "rgui", "rmeta"
    )

    fun splitShortcut(shortcut: String): List<String> {
        val trimmed = shortcut.trim()
        val endsWithPlusKey = trimmed == "+" || trimmed.endsWith("++")
        val body = if (endsWithPlusKey) trimmed.dropLast(1) else trimmed
        val parts = body.split('+').map { it.trim() }.filter { it.isNotEmpty() }
        return if (endsWithPlusKey) parts + "+" else parts
    }

    fun parse(shortcut: String): PresetAction {
        val parts = splitShortcut(shortcut)
        require(parts.isNotEmpty()) { "Shortcut cannot be empty" }

        val modifierParts = parts.dropLast(1)
        val keyPart = parts.last()

        if (modifierParts.isEmpty()) {
            return PresetAction.KeyPress(normalizeKey(keyPart))
        }

        require(modifierParts.all { compact(it) in MODIFIERS }) {
            "Unsupported shortcut: $shortcut"
        }

        val modifier = modifierParts.joinToString("+") { normalizeModifier(it) }
        return PresetAction.KeyCombo(modifier, normalizeKey(keyPart))
    }

    fun normalizeModifier(modifier: String): String {
        return when (compact(modifier)) {
            "control" -> "CTRL"
            "rcontrol" -> "RCTRL"
            "altgr" -> "RALT"
            "gui", "meta" -> "WIN"
            "rgui", "rmeta" -> "RWIN"
            else -> compact(modifier).uppercase()
        }
    }

    fun normalizeKey(key: String): String {
        return when (val compactKey = compact(key)) {
            "esc" -> "ESCAPE"
            "return" -> "ENTER"
            "del" -> "DELETE"
            "ins" -> "INSERT"
            "pgup" -> "PAGEUP"
            "pgdn" -> "PAGEDOWN"
            "printscreen", "prtscn" -> "PRTSC"
            "break" -> "PAUSE"
            "menu", "apps" -> "APPLICATION"
            "+", "plus" -> "NUMPLUS"
            "" -> key.uppercase()
            else -> compactKey.uppercase()
        }
    }

    private fun compact(value: String): String {
        return value.lowercase().filterNot { it.isWhitespace() || it == '_' }
    }
}
