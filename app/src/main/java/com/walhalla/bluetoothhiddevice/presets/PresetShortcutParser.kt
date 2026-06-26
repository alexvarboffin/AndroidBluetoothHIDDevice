package com.walhalla.bluetoothhiddevice.presets

object PresetShortcutParser {
    private val MODIFIERS = setOf("ctrl", "control", "shift", "alt", "win", "gui", "meta")

    fun parse(shortcut: String): PresetAction {
        val parts = shortcut.split('+').map { it.trim() }.filter { it.isNotEmpty() }
        require(parts.isNotEmpty()) { "Shortcut cannot be empty" }

        val modifierParts = parts.dropLast(1)
        val keyPart = parts.last()

        if (modifierParts.isEmpty()) {
            return PresetAction.KeyPress(normalizeKey(keyPart))
        }

        require(modifierParts.all { it.lowercase() in MODIFIERS }) {
            "Unsupported shortcut: $shortcut"
        }

        val modifier = modifierParts.joinToString("+") { normalizeModifier(it) }
        return PresetAction.KeyCombo(modifier, normalizeKey(keyPart))
    }

    private fun normalizeModifier(modifier: String): String {
        return when (modifier.lowercase()) {
            "control" -> "CTRL"
            else -> modifier.uppercase()
        }
    }

    private fun normalizeKey(key: String): String {
        return when (key.lowercase()) {
            "esc" -> "ESCAPE"
            else -> key.uppercase()
        }
    }
}
