package com.walhalla.bluetoothhiddevice.presets

import org.json.JSONObject

sealed interface PresetAction {
    data class RunWindowsCommand(val command: String) : PresetAction
    data class TypeText(val text: String) : PresetAction
    data class TypeSensitiveText(val text: String) : PresetAction
    data class Credential(val login: String, val password: String) : PresetAction
    data class KeyCombo(val modifier: String, val key: String) : PresetAction
    data class KeyPress(val key: String) : PresetAction
    data class Delay(val millis: Long) : PresetAction
}

object PresetActionCodec {
    const val TYPE_RUN_WINDOWS_COMMAND = "RunWindowsCommand"
    const val TYPE_TYPE_TEXT = "TypeText"
    const val TYPE_TYPE_SENSITIVE_TEXT = "TypeSensitiveText"
    const val TYPE_CREDENTIAL = "Credential"
    const val TYPE_KEY_COMBO = "KeyCombo"
    const val TYPE_KEY_PRESS = "KeyPress"
    const val TYPE_DELAY = "Delay"

    fun toEntity(
        presetId: Long,
        action: PresetAction,
        sortOrder: Int
    ): PresetActionEntity {
        val (type, payload) = when (action) {
            is PresetAction.RunWindowsCommand -> TYPE_RUN_WINDOWS_COMMAND to JSONObject()
                .put("command", action.command)
            is PresetAction.TypeText -> TYPE_TYPE_TEXT to JSONObject()
                .put("text", action.text)
            is PresetAction.TypeSensitiveText -> TYPE_TYPE_SENSITIVE_TEXT to JSONObject()
                .put("text", action.text)
            is PresetAction.Credential -> TYPE_CREDENTIAL to JSONObject()
                .put("login", action.login)
                .put("password", action.password)
            is PresetAction.KeyCombo -> TYPE_KEY_COMBO to JSONObject()
                .put("modifier", action.modifier)
                .put("key", action.key)
            is PresetAction.KeyPress -> TYPE_KEY_PRESS to JSONObject()
                .put("key", action.key)
            is PresetAction.Delay -> TYPE_DELAY to JSONObject()
                .put("millis", action.millis)
        }

        return PresetActionEntity(
            presetId = presetId,
            type = type,
            payloadJson = payload.toString(),
            sortOrder = sortOrder
        )
    }

    fun fromEntity(entity: PresetActionEntity): PresetAction {
        val payload = JSONObject(entity.payloadJson)
        return when (entity.type) {
            TYPE_RUN_WINDOWS_COMMAND -> PresetAction.RunWindowsCommand(payload.getString("command"))
            TYPE_TYPE_TEXT -> PresetAction.TypeText(payload.getString("text"))
            TYPE_TYPE_SENSITIVE_TEXT -> PresetAction.TypeSensitiveText(payload.getString("text"))
            TYPE_CREDENTIAL -> PresetAction.Credential(
                login = payload.getString("login"),
                password = payload.getString("password")
            )
            TYPE_KEY_COMBO -> PresetAction.KeyCombo(
                modifier = payload.getString("modifier"),
                key = payload.getString("key")
            )
            TYPE_KEY_PRESS -> PresetAction.KeyPress(payload.getString("key"))
            TYPE_DELAY -> PresetAction.Delay(payload.getLong("millis"))
            else -> error("Unsupported preset action type: ${entity.type}")
        }
    }
}
