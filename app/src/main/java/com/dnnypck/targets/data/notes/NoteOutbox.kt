package com.dnnypck.capacitiesquicknotepro.data.notes

class NoteOutbox(
    private val storage: NoteStorage,
    private val idFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun items(): List<SavedNote> = snapshot().items.sortedByDescending { it.updatedAt }

    fun get(id: String): SavedNote? = snapshot().items.firstOrNull { it.id == id }

    fun activeDraft(): ActiveDraft? = snapshot().activeDraft

    fun unresolved(): List<SavedNote> = items().filter { it.isUnresolved }

    fun saveActiveDraft(content: String, spaceId: String?): Boolean {
        val draft = if (content.isBlank()) {
            null
        } else {
            ActiveDraft(content = content, spaceId = spaceId, updatedAt = clock())
        }
        return mutate { current -> current.copy(activeDraft = draft) }
    }

    fun clearActiveDraft(): Boolean = mutate { current -> current.copy(activeDraft = null) }

    fun snapshotActiveDraftIfNeeded(incomingContent: String): Boolean {
        return mutate { current ->
            val existing = current.activeDraft?.content?.takeIf { it.isNotBlank() }
                ?: return@mutate current
            if (existing == incomingContent) return@mutate current
            val snapshot = SavedNote(
                id = idFactory(),
                content = existing,
                spaceId = current.activeDraft?.spaceId.orEmpty(),
                status = NoteDeliveryStatus.DRAFT,
                source = SavedNote.SOURCE_MAIN,
                createdAt = clock(),
                updatedAt = clock()
            )
            val incoming = if (incomingContent.isBlank()) {
                null
            } else {
                ActiveDraft(
                    content = incomingContent,
                    spaceId = current.activeDraft?.spaceId,
                    updatedAt = clock()
                )
            }
            current.copy(items = current.items + snapshot, activeDraft = incoming)
        }
    }

    fun recordDraft(
        content: String,
        spaceId: String,
        spaceName: String?,
        source: String
    ): SavedNote? = append(content, spaceId, spaceName, source, NoteDeliveryStatus.DRAFT)

    fun recordQueued(
        content: String,
        spaceId: String,
        spaceName: String?,
        source: String
    ): SavedNote? = append(content, spaceId, spaceName, source, NoteDeliveryStatus.QUEUED)

    fun markFailed(id: String, error: String): Boolean = update(id) { note ->
        note.copy(
            status = NoteDeliveryStatus.FAILED,
            lastError = sanitizeError(error),
            updatedAt = clock()
        )
    }

    fun markUncertain(id: String, error: String): Boolean = update(id) { note ->
        note.copy(
            status = NoteDeliveryStatus.UNCERTAIN,
            lastError = sanitizeError(error),
            updatedAt = clock()
        )
    }

    fun markAcknowledged(id: String): Boolean = update(id) { note ->
        val now = clock()
        note.copy(
            status = NoteDeliveryStatus.ACKNOWLEDGED,
            lastError = null,
            acknowledgedAt = now,
            updatedAt = now
        )
    }

    fun markQueued(id: String): Boolean = update(id) { note ->
        note.copy(status = NoteDeliveryStatus.QUEUED, updatedAt = clock())
    }

    fun delete(id: String, allowUnresolved: Boolean): Boolean {
        var removed = false
        val ok = mutate { current ->
            val note = current.items.firstOrNull { it.id == id } ?: return@mutate current
            if (note.isUnresolved && !allowUnresolved) return@mutate current
            removed = true
            current.copy(items = current.items.filterNot { it.id == id })
        }
        return ok && removed
    }

    fun purgeAcknowledged(
        maxAgeMs: Long = ACKNOWLEDGED_MAX_AGE_MS,
        maxCount: Int = ACKNOWLEDGED_MAX_COUNT
    ) {
        mutate { current ->
            val now = clock()
            val acknowledged = current.items
                .filter { it.status == NoteDeliveryStatus.ACKNOWLEDGED }
                .sortedBy { it.acknowledgedAt ?: it.updatedAt }
            val keepAck = acknowledged
                .filter { now - (it.acknowledgedAt ?: it.updatedAt) <= maxAgeMs }
                .takeLast(maxCount)
                .toSet()
            current.copy(
                items = current.items.filter { note ->
                    note.status != NoteDeliveryStatus.ACKNOWLEDGED || note in keepAck
                }
            )
        }
    }

    private fun append(
        content: String,
        spaceId: String,
        spaceName: String?,
        source: String,
        status: NoteDeliveryStatus
    ): SavedNote? {
        val now = clock()
        val note = SavedNote(
            id = idFactory(),
            content = content,
            spaceId = spaceId,
            spaceName = spaceName,
            source = source,
            status = status,
            createdAt = now,
            updatedAt = now
        )
        return if (mutate { current -> current.copy(items = current.items + note) }) note else null
    }

    private fun update(id: String, transform: (SavedNote) -> SavedNote): Boolean {
        var found = false
        val ok = mutate { current ->
            if (current.items.none { it.id == id }) return@mutate current
            found = true
            current.copy(
                items = current.items.map { note ->
                    if (note.id == id) transform(note) else note
                }
            )
        }
        return ok && found
    }

    private fun snapshot(): StoredNotes = storage.read()

    private fun mutate(transform: (StoredNotes) -> StoredNotes): Boolean {
        return storage.mutate(transform) != null
    }

    companion object {
        const val ACKNOWLEDGED_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
        const val ACKNOWLEDGED_MAX_COUNT = 100

        fun sanitizeError(error: String): String {
            return error
                .replace(Regex("(?i)Bearer\\s+\\S+"), "Bearer [redacted]")
                .replace(Regex("(?i)cap-api-\\S+"), "[redacted-token]")
        }
    }
}
