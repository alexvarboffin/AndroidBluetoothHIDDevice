package com.walhalla.bluetoothhiddevice.presets

enum class ShortcutKeyGroup(val title: String) {
    COMMON("Common"),
    LETTERS("A-Z"),
    DIGITS("0-9"),
    FUNCTION("F1-F12")
}

data class ShortcutKeyOption(
    val label: String,
    val token: String,
    val group: ShortcutKeyGroup
)

data class ShortcutDraft(
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val win: Boolean = false,
    val key: ShortcutKeyOption? = null
) {
    val isValid: Boolean get() = key != null

    fun toShortcutString(): String {
        val selectedKey = key ?: return ""
        val modifiers = buildList {
            if (ctrl) add("ctrl")
            if (shift) add("shift")
            if (alt) add("alt")
            if (win) add("win")
        }
        return (modifiers + selectedKey.token).joinToString("+")
    }

    fun displayLabel(): String {
        if (key == null) return "Select a key"
        val parts = buildList {
            if (ctrl) add("Ctrl")
            if (shift) add("Shift")
            if (alt) add("Alt")
            if (win) add("Win")
            add(key.label)
        }
        return parts.joinToString(" + ")
    }
}

object ShortcutKeys {
    private val common = listOf(
        ShortcutKeyOption("Tab", "tab", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("Esc", "escape", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("Enter", "enter", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("Space", "space", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("Backspace", "backspace", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("Delete", "delete", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("`", "`", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("[", "[", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("]", "]", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("-", "-", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("=", "=", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("Up", "up", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("Down", "down", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("Left", "left", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("Right", "right", ShortcutKeyGroup.COMMON)
    )

    private val letters = ('A'..'Z').map { letter ->
        ShortcutKeyOption(letter.toString(), letter.lowercaseChar().toString(), ShortcutKeyGroup.LETTERS)
    }

    private val digits = ('0'..'9').map { digit ->
        ShortcutKeyOption(digit.toString(), digit.toString(), ShortcutKeyGroup.DIGITS)
    }

    private val function = (1..12).map { index ->
        ShortcutKeyOption("F$index", "f$index", ShortcutKeyGroup.FUNCTION)
    }

    val all: List<ShortcutKeyOption> = common + letters + digits + function

    fun forGroup(group: ShortcutKeyGroup): List<ShortcutKeyOption> {
        return all.filter { it.group == group }
    }

    fun findByToken(token: String): ShortcutKeyOption? {
        val normalized = token.lowercase()
        return all.firstOrNull { it.token == normalized }
    }
}

object PresetShortcutDraft {
    private val modifierTokens = setOf("ctrl", "control", "shift", "alt", "win", "gui", "meta")

    fun fromShortcut(shortcut: String): ShortcutDraft {
        val parts = shortcut.split('+').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ShortcutDraft()

        val keyToken = parts.last()
        if (parts.size == 1 && keyToken !in modifierTokens) {
            return ShortcutDraft(key = ShortcutKeys.findByToken(keyToken))
        }

        var ctrl = false
        var shift = false
        var alt = false
        var win = false
        parts.dropLast(1).forEach { part ->
            when (part) {
                "ctrl", "control" -> ctrl = true
                "shift" -> shift = true
                "alt" -> alt = true
                "win", "gui", "meta" -> win = true
            }
        }
        return ShortcutDraft(
            ctrl = ctrl,
            shift = shift,
            alt = alt,
            win = win,
            key = ShortcutKeys.findByToken(keyToken)
        )
    }

    fun fromAction(action: PresetAction): ShortcutDraft {
        return when (action) {
            is PresetAction.KeyPress -> ShortcutDraft(key = ShortcutKeys.findByToken(action.key))
            is PresetAction.KeyCombo -> {
                var ctrl = false
                var shift = false
                var alt = false
                var win = false
                action.modifier.split('+').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach { part ->
                    when (part) {
                        "ctrl", "control" -> ctrl = true
                        "shift" -> shift = true
                        "alt" -> alt = true
                        "win", "gui", "meta" -> win = true
                    }
                }
                ShortcutDraft(
                    ctrl = ctrl,
                    shift = shift,
                    alt = alt,
                    win = win,
                    key = ShortcutKeys.findByToken(action.key)
                )
            }
            else -> ShortcutDraft()
        }
    }
}
