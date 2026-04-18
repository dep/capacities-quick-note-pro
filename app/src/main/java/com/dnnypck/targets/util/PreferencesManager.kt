package com.dnnypck.capacitiesquicknotepro.util

import android.content.Context
import android.content.SharedPreferences
import com.dnnypck.targets.data.model.BackupData
import com.dnnypck.targets.data.model.Space
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "capacities_preferences",
        Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun saveSpaceId(spaceId: String) {
        prefs.edit().putString(KEY_SPACE_ID, spaceId).apply()
    }

    fun getSpaceId(): String {
        return prefs.getString(KEY_SPACE_ID, "") ?: ""
    }

    fun hasRequiredSettings(): Boolean {
        return getApiKey().isNotBlank() && getSpaces().isNotEmpty()
    }

    // Multiple spaces support
    fun saveSpaces(spaces: List<Space>) {
        val jsonString = json.encodeToString(spaces)
        prefs.edit().putString(KEY_SPACES, jsonString).apply()
    }

    fun getSpaces(): List<Space> {
        val jsonString = prefs.getString(KEY_SPACES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Space>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSpace(space: Space) {
        val currentSpaces = getSpaces().toMutableList()
        currentSpaces.add(space)
        saveSpaces(currentSpaces)
    }

    fun removeSpace(spaceId: String) {
        val currentSpaces = getSpaces().filter { it.id != spaceId }
        saveSpaces(currentSpaces)
        // If deleted space was selected, clear selection
        if (getSelectedSpaceId() == spaceId) {
            clearSelectedSpaceId()
        }
    }

    fun updateSpace(updatedSpace: Space) {
        val currentSpaces = getSpaces().map {
            if (it.id == updatedSpace.id) updatedSpace else it
        }
        saveSpaces(currentSpaces)
    }

    fun saveSelectedSpaceId(spaceId: String) {
        prefs.edit().putString(KEY_SELECTED_SPACE_ID, spaceId).apply()
    }

    fun getSelectedSpaceId(): String? {
        return prefs.getString(KEY_SELECTED_SPACE_ID, null)
    }

    fun clearSelectedSpaceId() {
        prefs.edit().remove(KEY_SELECTED_SPACE_ID).apply()
    }

    fun migrateFromLegacySpaceId() {
        val legacySpaceId = prefs.getString(KEY_SPACE_ID, null)
        if (legacySpaceId != null && legacySpaceId.isNotBlank()) {
            val existingSpaces = getSpaces()
            // Only migrate if no spaces exist yet
            if (existingSpaces.isEmpty()) {
                val migratedSpace = Space(
                    id = legacySpaceId,
                    nickname = "Default Space"
                )
                addSpace(migratedSpace)
                saveSelectedSpaceId(legacySpaceId)
            }
            // Clear legacy key
            prefs.edit().remove(KEY_SPACE_ID).apply()
        }
    }

    // Backup and restore
    fun createBackup(): String {
        val backupData = BackupData(
            apiKey = getApiKey(),
            spaces = getSpaces(),
            selectedSpaceId = getSelectedSpaceId()
        )
        return json.encodeToString(backupData)
    }

    fun restoreFromBackup(backupJson: String): Result<Unit> {
        return try {
            val backupData = json.decodeFromString<BackupData>(backupJson)

            // Restore API key
            saveApiKey(backupData.apiKey)

            // Restore spaces
            saveSpaces(backupData.spaces)

            // Restore selected space if it exists in the restored spaces
            backupData.selectedSpaceId?.let { selectedId ->
                if (backupData.spaces.any { it.id == selectedId }) {
                    saveSelectedSpaceId(selectedId)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isDemoMode(): Boolean {
        return prefs.getBoolean(KEY_DEMO_MODE, false)
    }

    fun setDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SPACE_ID = "space_id"
        private const val KEY_SPACES = "spaces_json"
        private const val KEY_SELECTED_SPACE_ID = "selected_space_id"
        private const val KEY_DEMO_MODE = "demo_mode"
    }
}
