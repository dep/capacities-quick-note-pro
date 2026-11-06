package com.dnnypck.capacitiesquicknotepro.data.notes

class NoteOutbox(
    private val storage: NoteStorage,
    private val idFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    @Volatile private var cached: StoredNotes = storage.read()

    fun items(): List<SavedNote> = synchronized(this) { cached.items.sortedByDescending { it.updatedAt } }

    fun get(id: String): SavedNote? = synchronized(this) { cached.items.firstOrNull { it.id == id } }

    fun activeDraft(): ActiveDraft? = synchronized(this) { cached.activeDraft }

    fun unresolved(): List<SavedNote> = items().filter { it.isUnresolved }

    fun saveActiveDraft(content: String, spaceId: String?): Boolean {
        val trimmed = content
        val draft = if (trimmed.isBlank()) {
            null
        } else {
            ActiveDraft(content = trimmed, spaceId = spaceId, updatedAt = clock())
        }
        return persist(cached.copy(activeDraft = draft))
    }

    fun clearActiveDraft(): Boolean = persist(cached.copy(activeDraft = null))

    fun snapshotActiveDraftIfNeeded(incomingContent: String): Boolean {
        val current = activeDraft()?.content?.takeIf { it.isNotBlank() } ?: return true
        if (current == incomingContent) return true
        val snapshot = SavedNote(
            id = idFactory(),
            content = current,
            spaceId = activeDraft()?.spaceId.orEmpty(),
            status = NoteDeliveryStatus.DRAFT,
            source = SavedNote.SOURCE_MAIN,
            createdAt = clock(),
            updatedAt = clock()
        )
        val incoming = if (incomingContent.isBlank()) {
            null
        } else {
            ActiveDraft(content = incomingContent, spaceId = activeDraft()?.spaceId, updatedAt = clock())
        }
        return persist(cached.copy(items = cached.items + snapshot, activeDraft = incoming))
    }

    fun recordDraft(
        content: String,
        spaceId: String,
        spaceName: String?,
        source: String
    ): SavedNote? {
        val now = clock()
        val note = SavedNote(
            id = idFactory(),
            content = content,
            spaceId = spaceId,
            spaceName = spaceName,
            source = source,
            status = NoteDeliveryStatus.DRAFT,
            createdAt = now,
            updatedAt = now
        )
        return if (persist(cached.copy(items = cached.items + note))) note else null
    }

    fun recordQueued(
        content: String,
        spaceId: String,
        spaceName: String?,
        source: String
    ): SavedNote? {
        val now = clock()
        val note = SavedNote(
            id = idFactory(),
            content = content,
            spaceId = spaceId,
            spaceName = spaceName,
            source = source,
            status = NoteDeliveryStatus.QUEUED,
            createdAt = now,
            updatedAt = now
        )
        return if (persist(cached.copy(items = cached.items + note))) note else null
    }

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
        val note = get(id) ?: return false
        if (note.isUnresolved && !allowUnresolved) return false
        return persist(cached.copy(items = cached.items.filterNot { it.id == id }))
    }

    fun purgeAcknowledged(
        maxAgeMs: Long = ACKNOWLEDGED_MAX_AGE_MS,
        maxCount: Int = ACKNOWLEDGED_MAX_COUNT
    ) {
        val now = clock()
        val acknowledged = cached.items
            .filter { it.status == NoteDeliveryStatus.ACKNOWLEDGED }
            .sortedBy { it.acknowledgedAt ?: it.updatedAt }
        val keepAck = acknowledged
            .filter { now - (it.acknowledgedAt ?: it.updatedAt) <= maxAgeMs }
            .takeLast(maxCount)
            .toSet()
        val kept = cached.items.filter { note ->
            note.status != NoteDeliveryStatus.ACKNOWLEDGED || note in keepAck
        }
        persist(cached.copy(items = kept))
    }

    private fun update(id: String, transform: (SavedNote) -> SavedNote): Boolean {
        val items = cached.items.map { if (it.id == id) transform(it) else it }
        return persist(cached.copy(items = items))
    }

    private fun persist(next: StoredNotes): Boolean {
        synchronized(this) {
            if (!storage.write(next)) return false
            cached = next
            return true
        }
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
