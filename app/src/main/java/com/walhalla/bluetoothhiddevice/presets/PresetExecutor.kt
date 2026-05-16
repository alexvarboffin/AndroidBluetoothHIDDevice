package com.walhalla.bluetoothhiddevice.presets

import com.walhalla.bluetoothhiddevice.HidDeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class PresetExecutor(
    private val hidDeviceManager: HidDeviceManager
) {
    suspend fun execute(actions: List<PresetAction>): Boolean = withContext(Dispatchers.IO) {
        for (action in actions) {
            val success = when (action) {
                is PresetAction.RunWindowsCommand -> hidDeviceManager.sendWindowsRunCommandBlocking(action.command)
                is PresetAction.TypeText -> hidDeviceManager.sendTextBlocking(action.text)
                is PresetAction.TypeSensitiveText -> hidDeviceManager.sendTextBlocking(action.text)
                is PresetAction.Credential -> {
                    hidDeviceManager.sendTextBlocking(action.login) &&
                        hidDeviceManager.sendKeyPressBlocking("TAB") &&
                        hidDeviceManager.sendTextBlocking(action.password) &&
                        hidDeviceManager.sendKeyPressBlocking("ENTER")
                }
                is PresetAction.KeyCombo -> hidDeviceManager.sendKeyComboBlocking(action.modifier, action.key)
                is PresetAction.KeyPress -> hidDeviceManager.sendKeyPressBlocking(action.key)
                is PresetAction.Delay -> {
                    delay(action.millis)
                    true
                }
            }
            if (!success) return@withContext false
        }
        true
    }
}
