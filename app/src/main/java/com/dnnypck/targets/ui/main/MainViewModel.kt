package com.dnnypck.capacitiesquicknotepro.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteOutbox
import com.dnnypck.capacitiesquicknotepro.data.notes.SavedNote
import com.dnnypck.capacitiesquicknotepro.data.notes.SendCoordinator
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
    val selectedSpaceId: String? = null,
    val unresolvedCount: Int = 0
)

class MainViewModel(
    private val preferencesManager: PreferencesManager,
    private val outbox: NoteOutbox,
    private val sendCoordinator: SendCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState> = _state.asStateFlow()

    init {
        checkCredentials()
        loadSpaces()
        restoreDraft()
        refreshOutboxCounts()
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
        persistDraft(_state.value.content, spaceId)
    }

    fun refreshSpaces() {
        loadSpaces()
        checkCredentials()
        restoreDraft()
        refreshOutboxCounts()
    }

    fun updateContent(content: String) {
        _state.update { it.copy(content = content) }
        persistDraft(content, _state.value.selectedSpaceId)
    }

    fun applyIncomingText(text: String) {
        val current = _state.value.content
        if (current.isNotBlank() && current != text) {
            persistDraft(current, _state.value.selectedSpaceId)
            outbox.snapshotActiveDraftIfNeeded(text)
        }
        updateContent(text)
    }

    fun persistDraftNow() {
        persistDraft(_state.value.content, _state.value.selectedSpaceId)
    }

    fun restoreToEditor(noteId: String) {
        val note = outbox.get(noteId) ?: return
        if (_state.value.content.isNotBlank() && _state.value.content != note.content) {
            persistDraft(_state.value.content, _state.value.selectedSpaceId)
            outbox.snapshotActiveDraftIfNeeded(note.content)
        }
        _state.update { it.copy(content = note.content) }
        persistDraft(note.content, note.spaceId.ifBlank { _state.value.selectedSpaceId })
        if (note.spaceId.isNotBlank()) {
            selectSpace(note.spaceId)
        }
    }

    fun sendContent() {
        val content = _state.value.content
        if (content.isBlank()) return
        if (_state.value.isSending) return

        val selectedSpaceId = _state.value.selectedSpaceId
        if (selectedSpaceId == null) {
            _state.update { it.copy(message = "Please select a space", isError = true) }
            return
        }

        if (_state.value.isDemoMode) {
            viewModelScope.launch {
                _state.update { it.copy(isSending = true, message = null, isError = false) }
                persistDraft(content, selectedSpaceId)
                kotlinx.coroutines.delay(800)
                _state.update {
                    it.copy(
                        isSending = false,
                        message = "Demo: note sent successfully (not saved to Capacities). A local copy stays in Saved notes only if you keep the draft.",
                        isError = false,
                        content = content
                    )
                }
            }
            return
        }

        if (!preferencesManager.hasRequiredSettings()) {
            persistDraft(content, selectedSpaceId)
            _state.update {
                it.copy(
                    message = "Please connect a Capacities space in Settings first. Your note is still in the editor.",
                    isError = true
                )
            }
            return
        }

        val spaceName = _state.value.availableSpaces.find { it.id == selectedSpaceId }?.getDisplayName()

        viewModelScope.launch {
            _state.update { it.copy(isSending = true, message = null, isError = false) }
            val result = sendCoordinator.submit(
                content = content,
                spaceId = selectedSpaceId,
                spaceName = spaceName,
                source = SavedNote.SOURCE_MAIN
            )
            if (result.clearEditor) {
                outbox.clearActiveDraft()
            }
            refreshOutboxCounts()
            _state.update {
                it.copy(
                    isSending = false,
                    message = result.message,
                    isError = result.isError,
                    content = if (result.clearEditor) "" else content
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, isError = false) }
    }

    private fun restoreDraft() {
        val draft = outbox.activeDraft() ?: return
        if (draft.content.isBlank()) return
        if (_state.value.content.isNotBlank()) return
        _state.update { it.copy(content = draft.content) }
        draft.spaceId?.let { id ->
            if (_state.value.availableSpaces.any { it.id == id }) {
                selectSpace(id)
            }
        }
    }

    private fun persistDraft(content: String, spaceId: String?) {
        outbox.saveActiveDraft(content, spaceId)
    }

    private fun refreshOutboxCounts() {
        outbox.purgeAcknowledged()
        _state.update { it.copy(unresolvedCount = outbox.unresolved().size) }
    }
}
