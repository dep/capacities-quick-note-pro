package com.dnnypck.capacitiesquicknotepro.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dnnypck.capacitiesquicknotepro.data.network.postToTarget
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
    val selectedSpaceId: String? = null
)

class MainViewModel(
    private val preferencesManager: PreferencesManager
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

        val apiKey = preferencesManager.getApiKey()
        val spaceId = selectedSpaceId

        viewModelScope.launch {
            _state.update { it.copy(isSending = true, message = null) }

            try {
                val headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                )

                // Check if content is only a URL
                val isUrl = isUrlOnly(content)
                val (endpoint, body) = if (isUrl) {
                    // Use save-weblink endpoint
                    "https://api.capacities.io/save-weblink" to """
                        {
                            "spaceId": "$spaceId",
                            "url": "${content.trim().replace("\"", "\\\"")}"
                        }
                    """.trimIndent()
                } else {
                    // Use save-to-daily-note endpoint
                    "https://api.capacities.io/save-to-daily-note" to """
                        {
                            "spaceId": "$spaceId",
                            "mdText": "${content.replace("\"", "\\\"").replace("\n", "\\n")}",
                            "origin": "commandPalette"
                        }
                    """.trimIndent()
                }

                val result = postToTarget(
                    url = endpoint,
                    headers = headers,
                    body = body
                )

                result.fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isSending = false,
                                message = "Successfully saved to Capacities",
                                content = ""
                            )
                        }
                    },
                    onFailure = { error ->
                        val detailedError = buildString {
                            appendLine("Failed to send to Capacities")
                            appendLine()
                            appendLine("Error: ${error.message}")
                            appendLine()
                            appendLine("Request details:")
                            appendLine("Endpoint: $endpoint")
                            appendLine("Space ID: $spaceId")
                            appendLine("API Key: ${apiKey.take(8)}...")
                            appendLine()
                            appendLine("Request body:")
                            appendLine(body)
                        }
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
                        message = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
