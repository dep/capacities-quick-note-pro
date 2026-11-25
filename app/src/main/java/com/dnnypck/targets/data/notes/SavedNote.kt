package com.dnnypck.capacitiesquicknotepro.data.notes

import kotlinx.serialization.Serializable

@Serializable
enum class NoteDeliveryStatus {
    DRAFT,
    QUEUED,
    FAILED,
    UNCERTAIN,
    ACKNOWLEDGED
}

@Serializable
data class SavedNote(
    val id: String,
    val content: String,
    val spaceId: String,
    val spaceName: String? = null,
    val source: String = SOURCE_MAIN,
    val status: NoteDeliveryStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val lastError: String? = null,
    val acknowledgedAt: Long? = null
) {
    val isUnresolved: Boolean
        get() = status == NoteDeliveryStatus.QUEUED ||
            status == NoteDeliveryStatus.FAILED ||
            status == NoteDeliveryStatus.UNCERTAIN ||
            status == NoteDeliveryStatus.DRAFT

    companion object {
        const val SOURCE_MAIN = "main"
        const val SOURCE_SHARE = "share"
        const val SOURCE_WIDGET = "widget"
    }
}

@Serializable
data class ActiveDraft(
    val content: String,
    val spaceId: String? = null,
    val updatedAt: Long
)

@Serializable
data class StoredNotes(
    val items: List<SavedNote> = emptyList(),
    val activeDraft: ActiveDraft? = null
)

interface NoteStorage {
    fun read(): StoredNotes
    fun write(data: StoredNotes): Boolean
    fun mutate(transform: (StoredNotes) -> StoredNotes): StoredNotes?
}

class MemoryNoteStorage(
    initial: StoredNotes = StoredNotes()
) : NoteStorage {
    var data: StoredNotes = initial
    var failNextWrite: Boolean = false

    override fun read(): StoredNotes = synchronized(this) { data }

    override fun write(data: StoredNotes): Boolean = mutate { data } != null

    override fun mutate(transform: (StoredNotes) -> StoredNotes): StoredNotes? {
        synchronized(this) {
            if (failNextWrite) {
                failNextWrite = false
                return null
            }
            data = transform(data)
            return data
        }
    }
}
