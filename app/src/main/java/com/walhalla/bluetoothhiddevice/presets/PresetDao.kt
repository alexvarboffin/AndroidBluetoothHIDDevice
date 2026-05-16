package com.walhalla.bluetoothhiddevice.presets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM preset_categories ORDER BY sortOrder, title")
    fun observeCategories(): Flow<List<PresetCategoryEntity>>

    @Query("SELECT * FROM presets ORDER BY categoryId, sortOrder, title")
    fun observeAllPresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM preset_actions ORDER BY presetId, sortOrder")
    fun observeAllActions(): Flow<List<PresetActionEntity>>

    @Query("SELECT * FROM presets WHERE categoryId = :categoryId ORDER BY sortOrder, title")
    fun observePresetsForCategory(categoryId: Long): Flow<List<PresetEntity>>

    @Query("SELECT * FROM preset_categories ORDER BY sortOrder, title")
    suspend fun getCategories(): List<PresetCategoryEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM preset_categories")
    suspend fun getMaxCategorySortOrder(): Int

    @Query("SELECT * FROM presets ORDER BY categoryId, sortOrder, title")
    suspend fun getAllPresets(): List<PresetEntity>

    @Query("SELECT * FROM preset_actions ORDER BY presetId, sortOrder")
    suspend fun getAllActions(): List<PresetActionEntity>

    @Query("SELECT COUNT(*) FROM preset_categories")
    suspend fun getCategoryCount(): Int

    @Query("SELECT * FROM presets WHERE id = :presetId LIMIT 1")
    suspend fun getPreset(presetId: Long): PresetEntity?

    @Query("SELECT * FROM preset_actions WHERE presetId = :presetId ORDER BY sortOrder")
    suspend fun getActionsForPreset(presetId: Long): List<PresetActionEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM presets WHERE categoryId = :categoryId")
    suspend fun getMaxPresetSortOrder(categoryId: Long): Int

    @Insert
    suspend fun insertCategory(category: PresetCategoryEntity): Long

    @Query("DELETE FROM preset_categories WHERE id = :categoryId AND isBuiltIn = 0")
    suspend fun deleteCustomCategory(categoryId: Long): Int

    @Insert
    suspend fun insertPreset(preset: PresetEntity): Long

    @Update
    suspend fun updatePreset(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :presetId AND isBuiltIn = 0")
    suspend fun deletePreset(presetId: Long): Int

    @Query("DELETE FROM preset_actions WHERE presetId = :presetId")
    suspend fun deleteActionsForPreset(presetId: Long)

    @Insert
    suspend fun insertActions(actions: List<PresetActionEntity>)

    @Transaction
    suspend fun insertPresetWithActions(
        preset: PresetEntity,
        actions: List<PresetActionEntity>
    ): Long {
        val presetId = insertPreset(preset)
        insertActions(actions.map { it.copy(presetId = presetId) })
        return presetId
    }

    @Transaction
    suspend fun updatePresetWithActions(
        preset: PresetEntity,
        actions: List<PresetActionEntity>
    ) {
        updatePreset(preset)
        deleteActionsForPreset(preset.id)
        insertActions(actions.map { it.copy(presetId = preset.id) })
    }
}
