package com.dnnypck.capacitiesquicknotepro.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dnnypck.capacitiesquicknotepro.data.oauth.OAuthConfig
import com.dnnypck.capacitiesquicknotepro.data.oauth.OAuthCoordinator
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager
import com.dnnypck.targets.data.model.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsScreenState(
    val spaces: List<Space> = emptyList(),
    val editingSpace: Space? = null,
    val showEditDialog: Boolean = false,
    val showDisconnectConfirm: Space? = null,
    val showSignOutAllConfirm: Boolean = false,
    val isBusy: Boolean = false,
    val oauthConfigured: Boolean = OAuthConfig.isConfigured,
    val needsOAuthMigration: Boolean = false,
    val redirectUri: String = OAuthConfig.redirectUri,
    val message: String? = null
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val oauthCoordinator: OAuthCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsScreenState())
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        _state.update {
            it.copy(
                spaces = preferencesManager.getSpaces(),
                oauthConfigured = OAuthConfig.isConfigured,
                needsOAuthMigration = preferencesManager.needsOAuthMigration(),
                redirectUri = OAuthConfig.redirectUri
            )
        }
    }

    fun connect(context: Context) {
        if (!OAuthConfig.isConfigured) {
            _state.update {
                it.copy(
                    message = "OAuth client ID is empty. Remove any blank CAPACITIES_CLIENT_ID override so the build uses capacities_api_quick_note_pro."
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val result = oauthCoordinator.startAuthorization(context)
            _state.update {
                it.copy(
                    isBusy = false,
                    message = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun showEditSpaceDialog(space: Space) {
        _state.update { it.copy(showEditDialog = true, editingSpace = space) }
    }

    fun hideSpaceDialog() {
        _state.update { it.copy(showEditDialog = false, editingSpace = null) }
    }

    fun saveNickname(nickname: String?) {
        val editingSpace = _state.value.editingSpace ?: return
        val updated = editingSpace.copy(nickname = nickname?.trim()?.ifBlank { null })
        preferencesManager.updateSpace(updated)
        hideSpaceDialog()
        loadSettings()
        _state.update { it.copy(message = "Space nickname updated") }
    }

    fun requestDisconnect(space: Space) {
        _state.update { it.copy(showDisconnectConfirm = space) }
    }

    fun cancelDisconnect() {
        _state.update { it.copy(showDisconnectConfirm = null) }
    }

    fun confirmDisconnect() {
        val space = _state.value.showDisconnectConfirm ?: return
        _state.update { it.copy(showDisconnectConfirm = null, isBusy = true) }
        viewModelScope.launch {
            oauthCoordinator.disconnect(space.id)
            loadSettings()
            _state.update { it.copy(isBusy = false, message = "Disconnected ${space.getDisplayName()}") }
        }
    }

    fun requestSignOutAll() {
        _state.update { it.copy(showSignOutAllConfirm = true) }
    }

    fun cancelSignOutAll() {
        _state.update { it.copy(showSignOutAllConfirm = false) }
    }

    fun confirmSignOutAll() {
        _state.update { it.copy(showSignOutAllConfirm = false, isBusy = true) }
        viewModelScope.launch {
            oauthCoordinator.disconnectAll()
            loadSettings()
            _state.update { it.copy(isBusy = false, message = "Signed out of Capacities") }
        }
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
            onSuccess = { restoreResult ->
                loadSettings()
                val message = when (restoreResult) {
                    PreferencesManager.RestoreResult.SignedInSpacesKept ->
                        "Settings restored. You are still signed in for spaces that were already connected."
                    PreferencesManager.RestoreResult.NeedsSignIn ->
                        "Settings restored. Sign in with Capacities to connect each space."
                }
                _state.update { it.copy(message = message) }
            },
            onFailure = { error ->
                _state.update { it.copy(message = "Restore failed: ${error.message}") }
            }
        )
    }
}
