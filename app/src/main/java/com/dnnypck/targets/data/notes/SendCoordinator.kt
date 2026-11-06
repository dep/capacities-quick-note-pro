package com.dnnypck.capacitiesquicknotepro.data.notes

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface NoteSender {
    suspend fun send(spaceId: String, content: String): DeliveryOutcome
}

data class SubmitResult(
    val clearEditor: Boolean,
    val message: String,
    val isError: Boolean,
    val noteId: String? = null,
    val status: NoteDeliveryStatus? = null
)

class SendCoordinator(
    private val outbox: NoteOutbox,
    private val sender: NoteSender
) {
    private val mutex = Mutex()
    @Volatile private var inFlight = false

    suspend fun submit(
        content: String,
        spaceId: String,
        spaceName: String?,
        source: String
    ): SubmitResult = mutex.withLock {
        if (inFlight) {
            return SubmitResult(
                clearEditor = false,
                message = "Already sending a note. Wait for it to finish.",
                isError = true
            )
        }
        inFlight = true
        try {
            doSubmit(content, spaceId, spaceName, source)
        } finally {
            inFlight = false
        }
    }

    suspend fun resend(noteId: String): SubmitResult = mutex.withLock {
        val existing = outbox.get(noteId)
            ?: return SubmitResult(false, "That saved note is no longer on this device.", true)
        val warn = existing.status == NoteDeliveryStatus.UNCERTAIN ||
            existing.status == NoteDeliveryStatus.ACKNOWLEDGED ||
            existing.status == NoteDeliveryStatus.QUEUED
        if (!outbox.markQueued(noteId)) {
            return SubmitResult(
                clearEditor = false,
                message = "Couldn't update the local copy, so the note wasn't sent again. You can still copy it from Saved notes.",
                isError = true,
                noteId = noteId,
                status = existing.status
            )
        }
        val outcome = sendSafely(existing.spaceId, existing.content)
        val result = applyOutcome(noteId, outcome)
        if (warn) {
            return result.copy(
                message = buildString {
                    append(result.message)
                    append("\n\nIf Capacities already had this note, sending it again can create a duplicate.")
                }
            )
        }
        return result
    }

    private suspend fun doSubmit(
        content: String,
        spaceId: String,
        spaceName: String?,
        source: String
    ): SubmitResult {
        val queued = outbox.recordQueued(content, spaceId, spaceName, source)
            ?: return SubmitResult(
                clearEditor = false,
                message = "Couldn't save a local copy, so this note wasn't sent. Copy it before leaving this screen.",
                isError = true
            )
        val outcome = sendSafely(spaceId, content)
        return applyOutcome(queued.id, outcome)
    }

    private suspend fun sendSafely(spaceId: String, content: String): DeliveryOutcome {
        return try {
            sender.send(spaceId, content)
        } catch (error: Throwable) {
            DeliveryClassifier.fromThrowable(error)
        }
    }

    private fun applyOutcome(noteId: String, outcome: DeliveryOutcome): SubmitResult {
        return when (outcome) {
            DeliveryOutcome.Acknowledged -> {
                outbox.markAcknowledged(noteId)
                outbox.purgeAcknowledged()
                SubmitResult(
                    clearEditor = true,
                    message = "Capacities accepted this note. It can take a while to show in your daily note. A copy is in Saved notes.",
                    isError = false,
                    noteId = noteId,
                    status = NoteDeliveryStatus.ACKNOWLEDGED
                )
            }
            is DeliveryOutcome.Failed -> {
                outbox.markFailed(noteId, outcome.message)
                SubmitResult(
                    clearEditor = false,
                    message = "Couldn't send this note. A copy is in Saved notes.\n\n${outcome.message}",
                    isError = true,
                    noteId = noteId,
                    status = NoteDeliveryStatus.FAILED
                )
            }
            is DeliveryOutcome.Uncertain -> {
                outbox.markUncertain(noteId, outcome.message)
                SubmitResult(
                    clearEditor = false,
                    message = "Not sure whether Capacities received this note. A copy is in Saved notes. Resend only if it doesn't appear — that can create a duplicate.\n\n${outcome.message}",
                    isError = true,
                    noteId = noteId,
                    status = NoteDeliveryStatus.UNCERTAIN
                )
            }
        }
    }
}
