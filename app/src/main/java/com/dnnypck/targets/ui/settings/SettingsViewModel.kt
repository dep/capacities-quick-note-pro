package com.dnnypck.capacitiesquicknotepro.ui.settings

import androidx.lifecycle.ViewModel
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager
import com.dnnypck.targets.data.model.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsScreenState(
    val apiKey: String = "",
    val spaceId: String = "",
    val spaces: List<Space> = emptyList(),
    val editingSpace: Space? = null,
    val showAddDialog: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsScreenState())
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                apiKey = preferencesManager.getApiKey(),
                spaceId = preferencesManager.getSpaceId(),
                spaces = preferencesManager.getSpaces()
            )
        }
    }

    fun updateApiKey(apiKey: String) {
        _state.update { it.copy(apiKey = apiKey) }
    }

    fun updateSpaceId(spaceId: String) {
        _state.update { it.copy(spaceId = spaceId) }
    }

    fun saveSettings() {
        val apiKey = _state.value.apiKey.trim()
        val spaceId = _state.value.spaceId.trim()

        if (apiKey.isBlank() || spaceId.isBlank()) {
            _state.update { it.copy(message = "Please fill in all fields") }
            return
        }

        preferencesManager.saveApiKey(apiKey)
        preferencesManager.saveSpaceId(spaceId)
        _state.update { it.copy(message = "Settings saved successfully") }
    }

    fun saveApiKey() {
        val apiKey = _state.value.apiKey.trim()
        if (apiKey.isBlank()) {
            _state.update { it.copy(message = "API Key cannot be empty") }
            return
        }
        preferencesManager.saveApiKey(apiKey)
        _state.update { it.copy(message = "API Key saved") }
    }

    fun showAddSpaceDialog() {
        _state.update { it.copy(showAddDialog = true, editingSpace = null) }
    }

    fun showEditSpaceDialog(space: Space) {
        _state.update { it.copy(showAddDialog = true, editingSpace = space) }
    }

    fun hideSpaceDialog() {
        _state.update { it.copy(showAddDialog = false, editingSpace = null) }
    }

    fun saveSpace(spaceId: String, nickname: String?) {
        val trimmedId = spaceId.trim()
        val trimmedNickname = nickname?.trim()

        if (trimmedId.isBlank()) {
            _state.update { it.copy(message = "Space ID cannot be empty") }
            return
        }

        val editingSpace = _state.value.editingSpace
        if (editingSpace != null) {
            // Update existing space
            val updatedSpace = editingSpace.copy(
                id = trimmedId,
                nickname = trimmedNickname
            )
            preferencesManager.updateSpace(updatedSpace)
            _state.update { it.copy(message = "Space updated") }
        } else {
            // Add new space
            val newSpace = Space(id = trimmedId, nickname = trimmedNickname)
            preferencesManager.addSpace(newSpace)
            _state.update { it.copy(message = "Space added") }
        }

        hideSpaceDialog()
        loadSettings()
    }

    fun deleteSpace(spaceId: String) {
        preferencesManager.removeSpace(spaceId)
        _state.update { it.copy(message = "Space deleted") }
        loadSettings()
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun getBackupJson(): String {
        return preferencesManager.createBackup()
    }

    fun restoreFromBackup(backupJson: String) {
        val result = preferencesManager.restoreFromBackup(backupJson)
        result.fold(
            onSuccess = {
                loadSettings()
                _state.update { it.copy(message = "Settings restored successfully") }
            },
            onFailure = { error ->
                _state.update { it.copy(message = "Restore failed: ${error.message}") }
            }
        )
    }
}
