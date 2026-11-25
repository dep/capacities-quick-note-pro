package com.dnnypck.capacitiesquicknotepro.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteDeliveryStatus
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteOutbox
import com.dnnypck.capacitiesquicknotepro.data.notes.SavedNote
import com.dnnypck.capacitiesquicknotepro.data.notes.SendCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedNotesState(
    val notes: List<SavedNote> = emptyList(),
    val message: String? = null,
    val confirmDeleteId: String? = null,
    val confirmResendId: String? = null,
    val isBusy: Boolean = false
)

class SavedNotesViewModel(
    private val outbox: NoteOutbox,
    private val sendCoordinator: SendCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(SavedNotesState())
    val state: StateFlow<SavedNotesState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        outbox.purgeAcknowledged()
        _state.update { it.copy(notes = outbox.items()) }
    }

    fun requestDelete(id: String) {
        _state.update { it.copy(confirmDeleteId = id) }
    }

    fun cancelDelete() {
        _state.update { it.copy(confirmDeleteId = null) }
    }

    fun confirmDelete() {
        val id = _state.value.confirmDeleteId ?: return
        outbox.delete(id, allowUnresolved = true)
        _state.update { it.copy(confirmDeleteId = null, message = "Removed from this device") }
        reload()
    }

    fun requestResend(id: String) {
        val note = outbox.get(id) ?: return
        if (note.status == NoteDeliveryStatus.UNCERTAIN ||
            note.status == NoteDeliveryStatus.ACKNOWLEDGED ||
            note.status == NoteDeliveryStatus.QUEUED
        ) {
            _state.update { it.copy(confirmResendId = id) }
        } else {
            resend(id)
        }
    }

    fun cancelResend() {
        _state.update { it.copy(confirmResendId = null) }
    }

    fun confirmResend() {
        val id = _state.value.confirmResendId ?: return
        _state.update { it.copy(confirmResendId = null) }
        resend(id)
    }

    private fun resend(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val result = sendCoordinator.resend(id)
            _state.update {
                it.copy(isBusy = false, message = result.message)
            }
            reload()
        }
    }

    fun copied() {
        _state.update { it.copy(message = "Copied to clipboard") }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}

fun NoteDeliveryStatus.label(): String = when (this) {
    NoteDeliveryStatus.DRAFT -> "Saved on this device"
    NoteDeliveryStatus.QUEUED -> "Send interrupted"
    NoteDeliveryStatus.FAILED -> "Not sent"
    NoteDeliveryStatus.UNCERTAIN -> "Uncertain delivery"
    NoteDeliveryStatus.ACKNOWLEDGED -> "Accepted by Capacities (may still be syncing)"
}
