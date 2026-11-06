package com.dnnypck.capacitiesquicknotepro.util

import android.content.Context
import android.content.SharedPreferences
import com.dnnypck.capacitiesquicknotepro.data.oauth.SecurePreferences
import com.dnnypck.capacitiesquicknotepro.data.oauth.TokenStore
import com.dnnypck.targets.data.model.BackupData
import com.dnnypck.targets.data.model.BackupSupport
import com.dnnypck.targets.data.model.Space
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "capacities_preferences",
        Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true }

    val tokenStore: TokenStore = TokenStore(SecurePreferences.create(context), json)

    /**
     * Removes all whitespace (spaces, tabs, and new lines) from a credential.
     * API keys and Space IDs never contain whitespace, so this strips stray
     * characters that sneak in when pasting from another app.
     */
    private fun sanitizeCredential(value: String): String {
        return value.filterNot { it.isWhitespace() }
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun clearLegacyApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    fun getSpaceId(): String {
        return prefs.getString(KEY_SPACE_ID, "") ?: ""
    }

    fun hasRequiredSettings(): Boolean {
        if (tokenStore.getAll().isEmpty()) return false
        return getSpaces().any { tokenStore.get(it.id) != null }
    }

    fun needsOAuthMigration(): Boolean {
        return getApiKey().isNotBlank() && !hasRequiredSettings()
    }

    fun saveSpaces(spaces: List<Space>) {
        val sanitizedSpaces = spaces.map { it.copy(id = sanitizeCredential(it.id)) }
        val jsonString = json.encodeToString(sanitizedSpaces)
        prefs.edit().putString(KEY_SPACES, jsonString).apply()
    }

    fun getSpaces(): List<Space> {
        val jsonString = prefs.getString(KEY_SPACES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Space>>(jsonString)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSpace(space: Space) {
        val currentSpaces = getSpaces().toMutableList()
        currentSpaces.add(space)
        saveSpaces(currentSpaces)
    }

    fun upsertConnectedSpace(space: Space): Space {
        val current = getSpaces()
        val existing = current.firstOrNull { it.id == space.id }
        return if (existing != null) {
            val merged = existing.copy(
                nickname = existing.nickname?.takeIf { it.isNotBlank() } ?: space.nickname
            )
            saveSpaces(current.map { if (it.id == space.id) merged else it })
            merged
        } else {
            addSpace(space)
            space
        }
    }

    fun removeSpace(spaceId: String) {
        val currentSpaces = getSpaces().filter { it.id != spaceId }
        saveSpaces(currentSpaces)
        if (getSelectedSpaceId() == spaceId) {
            clearSelectedSpaceId()
            currentSpaces.firstOrNull()?.id?.let { saveSelectedSpaceId(it) }
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
            if (existingSpaces.isEmpty()) {
                val migratedSpace = Space(
                    id = legacySpaceId,
                    nickname = "Default Space"
                )
                addSpace(migratedSpace)
                saveSelectedSpaceId(legacySpaceId)
            }
            prefs.edit().remove(KEY_SPACE_ID).apply()
        }
    }

    fun createBackup(): String {
        val backupData = BackupSupport.createExport(
            spaces = getSpaces(),
            selectedSpaceId = getSelectedSpaceId()
        )
        return json.encodeToString(backupData)
    }

    fun restoreFromBackup(backupJson: String): Result<RestoreResult> {
        return try {
            val backupData = json.decodeFromString<BackupData>(backupJson)
            val plan = BackupSupport.planRestore(
                backup = backupData,
                existingTokenSpaceIds = tokenStore.getAll().map { it.spaceId }.toSet(),
                currentSelectedSpaceId = getSelectedSpaceId()
            )

            saveSpaces(plan.spaces)
            if (plan.selectedSpaceId != null) {
                saveSelectedSpaceId(plan.selectedSpaceId)
            } else {
                clearSelectedSpaceId()
            }

            // Never import credentials from the file. Export does not sign out;
            // restore keeps tokens for spaces that still exist and drops the rest.
            clearLegacyApiKey()
            plan.tokenSpaceIdsToDrop.forEach { tokenStore.remove(it) }

            Result.success(
                if (plan.needsSignIn) {
                    RestoreResult.NeedsSignIn
                } else {
                    RestoreResult.SignedInSpacesKept
                }
            )
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

    enum class RestoreResult {
        SignedInSpacesKept,
        NeedsSignIn
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SPACE_ID = "space_id"
        private const val KEY_SPACES = "spaces_json"
        private const val KEY_SELECTED_SPACE_ID = "selected_space_id"
        private const val KEY_DEMO_MODE = "demo_mode"
    }
}
