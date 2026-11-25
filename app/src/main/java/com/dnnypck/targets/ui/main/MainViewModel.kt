package com.dnnypck.capacitiesquicknotepro.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dnnypck.capacitiesquicknotepro.data.network.CapacitiesApi
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
    val hasCredentials: Boolean = false,
    val isDemoMode: Boolean = false,
    val availableSpaces: List<Space> = emptyList(),
    val selectedSpaceId: String? = null,
    val triggerReviewRequest: Boolean = false
)

class MainViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState> = _state.asStateFlow()

    init {
        checkCredentials()
        loadSpaces()
        if (preferencesManager.isMigrationNoticePending()) {
            _state.update {
                it.copy(message = "Capacities updated their API — each space now needs its own API token. Open Settings to add one per space.")
            }
            preferencesManager.clearMigrationNotice()
        }
    }

    fun checkCredentials() {
        _state.update {
            it.copy(
                hasCredentials = preferencesManager.hasRequiredSettings(),
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
        // Provide a demo space so the UI has something to show
        if (preferencesManager.getSpaces().isEmpty()) {
            preferencesManager.addSpace(com.dnnypck.targets.data.model.Space(id = "demo-space", nickname = "Demo Space"))
            preferencesManager.saveSelectedSpaceId("demo-space")
        }
        _state.update { it.copy(isDemoMode = true) }
        loadSpaces()
    }

    private fun loadSpaces() {
        val spaces = preferencesManager.getSpaces()
        var selectedId = preferencesManager.getSelectedSpaceId()

        // If no selection or selected space doesn't exist, pick first space
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

    private fun isUrlOnly(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.matches(Regex("^https?://[^\\s]+$"))
    }

    fun sendContent() {
        val content = _state.value.content
        if (content.isBlank()) return

        val selectedSpaceId = _state.value.selectedSpaceId
        if (selectedSpaceId == null) {
            _state.update { it.copy(message = "Please select a space") }
            return
        }

        // Demo mode: simulate a successful send without hitting the API
        if (_state.value.isDemoMode) {
            viewModelScope.launch {
                _state.update { it.copy(isSending = true, message = null) }
                kotlinx.coroutines.delay(800)
                _state.update {
                    it.copy(
                        isSending = false,
                        message = "Demo: note sent successfully (not saved to Capacities)",
                        content = ""
                    )
                }
            }
            return
        }

        if (!preferencesManager.hasRequiredSettings()) {
            _state.update { it.copy(message = "Please configure API settings first") }
            return
        }

        val selectedSpace = _state.value.availableSpaces.find { it.id == selectedSpaceId }
        if (selectedSpace == null) {
            _state.update { it.copy(message = "Please select a space") }
            return
        }
        val apiToken = selectedSpace.apiToken
        if (apiToken.isNullOrBlank()) {
            _state.update { it.copy(message = "This space needs a new API token — open Settings to add one") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSending = true, message = null) }

            try {
                // Check if content is only a URL
                val isUrl = isUrlOnly(content)
                val endpoint = if (isUrl) CapacitiesApi.WEBLINK_ENDPOINT else CapacitiesApi.DAILY_NOTE_ENDPOINT
                val result = if (isUrl) {
                    // Use save-weblink endpoint
                    CapacitiesApi.saveWeblink(apiToken, content.trim())
                } else {
                    // Use save-to-daily-note endpoint
                    CapacitiesApi.appendToDailyNote(apiToken, content)
                }

                result.fold(
                    onSuccess = {
                        val saveCount = preferencesManager.incrementSuccessfulSaves()
                        _state.update {
                            it.copy(
                                isSending = false,
                                message = "Successfully saved to Capacities",
                                content = "",
                                triggerReviewRequest = saveCount == 5
                            )
                        }
                    },
                    onFailure = { error ->
                        val detailedError = buildString {
                            appendLine("Couldn't save your note to Capacities.")
                            appendLine()
                            appendLine(error.message ?: "Unknown error")
                            appendLine()
                            appendLine("Request details:")
                            appendLine("Endpoint: $endpoint")
                            appendLine("Space: ${selectedSpace.getDisplayName()}")
                            appendLine("API Token: ${if (apiToken.length >= 12) apiToken.take(12) + "..." else "(too short)"}")
                        }.trimEnd()
                        _state.update {
                            it.copy(
                                isSending = false,
                                message = detailedError
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSending = false,
                        message = "Couldn't save your note to Capacities: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearReviewRequest() {
        _state.update { it.copy(triggerReviewRequest = false) }
    }
}
