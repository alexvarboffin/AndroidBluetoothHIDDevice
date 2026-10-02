package com.walhalla.bluetoothhiddevice.presets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PresetShortcutParserTest {

    @Test
    fun builtInScreenshotOpensWithAllChips() {
        val action = PresetShortcutParser.parse("win+alt+prtsc")
        assertEquals(PresetAction.KeyCombo("WIN+ALT", "PRTSC"), action)

        val draft = PresetShortcutDraft.fromAction(action)
        assertEquals(true, draft.win)
        assertEquals(true, draft.alt)
        assertEquals("prtsc", draft.key?.token)
        assertEquals("alt+win+prtsc", draft.toShortcutString())
    }

    @Test
    fun aliasesAndSpacesNormalize() {
        assertEquals(PresetAction.KeyCombo("CTRL", "PAGEUP"), PresetShortcutParser.parse("ctrl+page up"))
        assertEquals(PresetAction.KeyCombo("CTRL", "PAGEDOWN"), PresetShortcutParser.parse("control+pgdn"))
        assertEquals(PresetAction.KeyCombo("RALT", "E"), PresetShortcutParser.parse("altgr+e"))
        assertEquals(PresetAction.KeyPress("ESCAPE"), PresetShortcutParser.parse("esc"))
        assertEquals(PresetAction.KeyCombo("CTRL+ALT", "DELETE"), PresetShortcutParser.parse("ctrl+alt+delete"))
    }

    @Test
    fun plusAsKey() {
        assertEquals(PresetAction.KeyCombo("CTRL", "NUMPLUS"), PresetShortcutParser.parse("ctrl++"))
        assertEquals(PresetAction.KeyPress("NUMPLUS"), PresetShortcutParser.parse("+"))
    }

    @Test
    fun everyFormKeyRoundTrips() {
        ShortcutKeys.all.forEach { option ->
            val draft = ShortcutDraft(ctrl = true, rshift = true, key = option)
            val restored = PresetShortcutDraft.fromAction(PresetShortcutParser.parse(draft.toShortcutString()))
            assertNotNull(option.token, restored.key)
            assertEquals(option.token, draft, restored)
        }
    }

    @Test
    fun functionKeysUpToF12KeepTheirToken() {
        listOf("f10", "f11", "f12").forEach { token ->
            assertEquals(token, ShortcutKeys.findByToken(token)?.token)
        }
    }
}
