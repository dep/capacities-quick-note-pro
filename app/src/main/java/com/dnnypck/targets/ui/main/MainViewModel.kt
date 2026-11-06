package com.dnnypck.capacitiesquicknotepro.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dnnypck.capacitiesquicknotepro.data.repository.CapacitiesRepository
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager
import com.dnnypck.targets.data.model.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainScreenState(
    val content: String = "",
    val isSending: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val hasCredentials: Boolean = false,
    val needsOAuthMigration: Boolean = false,
    val isDemoMode: Boolean = false,
    val availableSpaces: List<Space> = emptyList(),
    val selectedSpaceId: String? = null
)

class MainViewModel(
    private val preferencesManager: PreferencesManager,
    private val repository: CapacitiesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState> = _state.asStateFlow()

    init {
        checkCredentials()
        loadSpaces()
    }

    fun checkCredentials() {
        _state.update {
            it.copy(
                hasCredentials = preferencesManager.hasRequiredSettings(),
                needsOAuthMigration = preferencesManager.needsOAuthMigration(),
                isDemoMode = preferencesManager.isDemoMode()
            )
        }
    }

    fun disableDemoMode() {
        preferencesManager.setDemoMode(false)
        preferencesManager.removeSpace("demo-space")
        _state.update { it.copy(isDemoMode = false) }
        loadSpaces()
        checkCredentials()
    }

    fun enableDemoMode() {
        preferencesManager.setDemoMode(true)
        if (preferencesManager.getSpaces().none { it.id == "demo-space" }) {
            preferencesManager.addSpace(Space(id = "demo-space", nickname = "Demo Space"))
            preferencesManager.saveSelectedSpaceId("demo-space")
        }
        _state.update { it.copy(isDemoMode = true) }
        loadSpaces()
    }

    private fun loadSpaces() {
        val spaces = preferencesManager.getSpaces()
        var selectedId = preferencesManager.getSelectedSpaceId()

        if (selectedId == null || spaces.none { it.id == selectedId }) {
            selectedId = spaces.firstOrNull()?.id
            selectedId?.let { preferencesManager.saveSelectedSpaceId(it) }
        }

        _state.update {
            it.copy(
                availableSpaces = spaces,
                selectedSpaceId = selectedId
            )
        }
    }

    fun selectSpace(spaceId: String) {
        preferencesManager.saveSelectedSpaceId(spaceId)
        _state.update { it.copy(selectedSpaceId = spaceId) }
    }

    fun refreshSpaces() {
        loadSpaces()
        checkCredentials()
    }

    fun updateContent(content: String) {
        _state.update { it.copy(content = content) }
    }

    fun sendContent() {
        val content = _state.value.content
        if (content.isBlank()) return

        val selectedSpaceId = _state.value.selectedSpaceId
        if (selectedSpaceId == null) {
            _state.update { it.copy(message = "Please select a space", isError = true) }
            return
        }

        if (_state.value.isDemoMode) {
            viewModelScope.launch {
                _state.update { it.copy(isSending = true, message = null, isError = false) }
                kotlinx.coroutines.delay(800)
                _state.update {
                    it.copy(
                        isSending = false,
                        message = "Demo: note sent successfully (not saved to Capacities)",
                        isError = false,
                        content = ""
                    )
                }
            }
            return
        }

        if (!preferencesManager.hasRequiredSettings()) {
            _state.update {
                it.copy(
                    message = "Please connect a Capacities space in Settings first",
                    isError = true
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSending = true, message = null, isError = false) }

            val result = repository.sendNote(selectedSpaceId, content)
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isSending = false,
                            message = "Successfully saved to Capacities",
                            isError = false,
                            content = ""
                        )
                    }
                },
                onFailure = { error ->
                    val detailedError = buildString {
                        appendLine("Couldn't save your note to Capacities.")
                        appendLine()
                        appendLine(error.message ?: "Unknown error")
                    }.trimEnd()
                    _state.update {
                        it.copy(
                            isSending = false,
                            message = detailedError,
                            isError = true
                        )
                    }
                }
            )
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, isError = false) }
    }
}
