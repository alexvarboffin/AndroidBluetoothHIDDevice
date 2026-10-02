package com.walhalla.bluetoothhiddevice.presets

enum class ShortcutKeyGroup(val title: String) {
    COMMON("Common"),
    LETTERS("A-Z"),
    DIGITS("0-9"),
    FUNCTION("F1-F12"),
    NAVIGATION("Nav"),
    SYSTEM("System"),
    NUMPAD("Numpad")
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
    val rctrl: Boolean = false,
    val rshift: Boolean = false,
    val ralt: Boolean = false,
    val rwin: Boolean = false,
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
            if (rctrl) add("rctrl")
            if (rshift) add("rshift")
            if (ralt) add("ralt")
            if (rwin) add("rwin")
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
            if (rctrl) add("RCtrl")
            if (rshift) add("RShift")
            if (ralt) add("AltGr")
            if (rwin) add("RWin")
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
        ShortcutKeyOption("\\", "\\", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption(";", ";", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("'", "'", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption(",", ",", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption(".", ".", ShortcutKeyGroup.COMMON),
        ShortcutKeyOption("/", "/", ShortcutKeyGroup.COMMON),
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

    private val navigation = listOf(
        ShortcutKeyOption("Home", "home", ShortcutKeyGroup.NAVIGATION),
        ShortcutKeyOption("End", "end", ShortcutKeyGroup.NAVIGATION),
        ShortcutKeyOption("PgUp", "pageup", ShortcutKeyGroup.NAVIGATION),
        ShortcutKeyOption("PgDn", "pagedown", ShortcutKeyGroup.NAVIGATION),
        ShortcutKeyOption("Insert", "insert", ShortcutKeyGroup.NAVIGATION)
    )

    private val system = listOf(
        ShortcutKeyOption("PrtScn", "prtsc", ShortcutKeyGroup.SYSTEM),
        ShortcutKeyOption("Pause", "pause", ShortcutKeyGroup.SYSTEM),
        ShortcutKeyOption("Menu", "application", ShortcutKeyGroup.SYSTEM),
        ShortcutKeyOption("Caps Lock", "capslock", ShortcutKeyGroup.SYSTEM),
        ShortcutKeyOption("Num Lock", "numlock", ShortcutKeyGroup.SYSTEM),
        ShortcutKeyOption("Scroll Lock", "scrolllock", ShortcutKeyGroup.SYSTEM)
    )

    private val numpad = ('0'..'9').map { digit ->
        ShortcutKeyOption("Num $digit", "num$digit", ShortcutKeyGroup.NUMPAD)
    } + listOf(
        ShortcutKeyOption("Num +", "numplus", ShortcutKeyGroup.NUMPAD),
        ShortcutKeyOption("Num -", "numminus", ShortcutKeyGroup.NUMPAD),
        ShortcutKeyOption("Num *", "nummultiply", ShortcutKeyGroup.NUMPAD),
        ShortcutKeyOption("Num /", "numdivide", ShortcutKeyGroup.NUMPAD),
        ShortcutKeyOption("Num .", "numdecimal", ShortcutKeyGroup.NUMPAD),
        ShortcutKeyOption("Num Enter", "numenter", ShortcutKeyGroup.NUMPAD)
    )

    val all: List<ShortcutKeyOption> = common + letters + digits + function + navigation + system + numpad

    fun forGroup(group: ShortcutKeyGroup): List<ShortcutKeyOption> {
        return all.filter { it.group == group }
    }

    fun findByToken(token: String): ShortcutKeyOption? {
        val normalized = PresetShortcutParser.normalizeKey(token).lowercase()
        return all.firstOrNull { it.token == normalized }
    }
}

object PresetShortcutDraft {
    fun fromShortcut(shortcut: String): ShortcutDraft {
        val parts = PresetShortcutParser.splitShortcut(shortcut)
        if (parts.isEmpty()) return ShortcutDraft()
        return withModifiers(parts.dropLast(1), ShortcutKeys.findByToken(parts.last()))
    }

    fun fromAction(action: PresetAction): ShortcutDraft {
        return when (action) {
            is PresetAction.KeyPress -> ShortcutDraft(key = ShortcutKeys.findByToken(action.key))
            is PresetAction.KeyCombo -> withModifiers(
                action.modifier.split('+').filter { it.isNotBlank() },
                ShortcutKeys.findByToken(action.key)
            )
            else -> ShortcutDraft()
        }
    }

    private fun withModifiers(modifiers: List<String>, key: ShortcutKeyOption?): ShortcutDraft {
        val normalized = modifiers.map { PresetShortcutParser.normalizeModifier(it) }.toSet()
        return ShortcutDraft(
            ctrl = "CTRL" in normalized,
            shift = "SHIFT" in normalized,
            alt = "ALT" in normalized,
            win = "WIN" in normalized,
            rctrl = "RCTRL" in normalized,
            rshift = "RSHIFT" in normalized,
            ralt = "RALT" in normalized,
            rwin = "RWIN" in normalized,
            key = key
        )
    }
}
