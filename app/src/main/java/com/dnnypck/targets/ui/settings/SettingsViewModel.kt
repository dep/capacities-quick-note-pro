package com.dnnypck.capacitiesquicknotepro.ui.settings

import androidx.lifecycle.ViewModel
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager
import com.dnnypck.targets.data.model.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsScreenState(
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
                spaces = preferencesManager.getSpaces()
            )
        }
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

    fun saveSpace(apiToken: String, nickname: String?) {
        val trimmedToken = apiToken.trim()
        val trimmedNickname = nickname?.trim()

        if (trimmedToken.isBlank()) {
            _state.update { it.copy(message = "API Token can't be empty. Create one in Capacities → Settings → Capacities API.") }
            return
        }

        val editingSpace = _state.value.editingSpace
        if (editingSpace != null) {
            // Update existing space
            val updatedSpace = editingSpace.copy(
                apiToken = trimmedToken,
                nickname = trimmedNickname
            )
            preferencesManager.updateSpace(updatedSpace)
            _state.update { it.copy(message = "Space updated") }
        } else {
            // Add new space
            val newSpace = Space(
                id = java.util.UUID.randomUUID().toString(),
                nickname = trimmedNickname,
                apiToken = trimmedToken
            )
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
                val needsTokens = preferencesManager.getSpaces().any { it.apiToken.isNullOrBlank() }
                _state.update {
                    it.copy(
                        message = if (needsTokens) {
                            "Settings restored — the Capacities API changed, so add a new API token for each space"
                        } else {
                            "Settings restored successfully"
                        }
                    )
                }
            },
            onFailure = { error ->
                _state.update { it.copy(message = "Restore failed: ${error.message}") }
            }
        )
    }
}
