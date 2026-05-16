package com.walhalla.bluetoothhiddevice.presets

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "preset_categories")
data class PresetCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sortOrder: Int,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "presets",
    foreignKeys = [
        ForeignKey(
            entity = PresetCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val title: String,
    val description: String = "",
    val riskLevel: String = "normal",
    val requiresConfirmation: Boolean = false,
    val isSensitive: Boolean = false,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "preset_actions",
    foreignKeys = [
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("presetId")]
)
data class PresetActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val type: String,
    val payloadJson: String,
    val sortOrder: Int
)

data class PresetWithActions(
    val preset: PresetEntity,
    val actions: List<PresetActionEntity>
)
