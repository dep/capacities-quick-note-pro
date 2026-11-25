package com.dnnypck.capacitiesquicknotepro.notes

import com.dnnypck.capacitiesquicknotepro.data.notes.DeliveryOutcome
import com.dnnypck.capacitiesquicknotepro.data.notes.MemoryNoteStorage
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteDeliveryStatus
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteOutbox
import com.dnnypck.capacitiesquicknotepro.data.notes.SavedNote
import com.dnnypck.capacitiesquicknotepro.data.notes.SendCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SendCoordinatorTest {
    private val storage = MemoryNoteStorage()
    private val outbox = NoteOutbox(storage, idFactory = { "n1" }, clock = { 10L })

    @Test
    fun persistFailureDoesNotSendAndKeepsEditor() = runBlocking {
        storage.failNextWrite = true
        var sent = false
        val coordinator = SendCoordinator(outbox) { _, _ ->
            sent = true
            DeliveryOutcome.Acknowledged
        }
        val result = coordinator.submit("precious note", "space", "Work", SavedNote.SOURCE_MAIN)
        assertFalse(sent)
        assertFalse(result.clearEditor)
        assertTrue(result.isError)
        assertTrue(result.message.contains("local copy", ignoreCase = true))
        assertTrue(outbox.items().isEmpty())
    }

    @Test
    fun failedSendKeepsEditorAndDurableCopy() = runBlocking {
        val coordinator = SendCoordinator(outbox) { _, _ ->
            DeliveryOutcome.Failed("Unauthorized (401).")
        }
        val result = coordinator.submit("keep me", "space", "Work", SavedNote.SOURCE_MAIN)
        assertFalse(result.clearEditor)
        assertTrue(result.isError)
        assertEquals(NoteDeliveryStatus.FAILED, outbox.items().single().status)
        assertEquals("keep me", outbox.items().single().content)
        assertTrue(result.message.contains("Saved notes"))
    }

    @Test
    fun timeoutIsUncertainAndDoesNotAutoRetry() = runBlocking {
        var calls = 0
        val coordinator = SendCoordinator(outbox) { _, _ ->
            calls++
            DeliveryOutcome.Uncertain("timed out")
        }
        val result = coordinator.submit("maybe sent", "space", null, SavedNote.SOURCE_WIDGET)
        assertEquals(1, calls)
        assertFalse(result.clearEditor)
        assertEquals(NoteDeliveryStatus.UNCERTAIN, outbox.items().single().status)
        assertTrue(result.message.contains("duplicate", ignoreCase = true))
    }

    @Test
    fun acknowledgedClearsEditorButKeepsHistory() = runBlocking {
        val coordinator = SendCoordinator(outbox) { _, _ -> DeliveryOutcome.Acknowledged }
        val result = coordinator.submit("accepted text", "space", "Work", SavedNote.SOURCE_MAIN)
        assertTrue(result.clearEditor)
        assertFalse(result.isError)
        assertEquals(NoteDeliveryStatus.ACKNOWLEDGED, outbox.items().single().status)
        assertEquals("accepted text", outbox.items().single().content)
        assertTrue(result.message.contains("accepted", ignoreCase = true))
        assertFalse(result.message.contains("Successfully saved to Capacities"))
    }

    @Test
    fun explicitResendWarnsAboutDuplicatesForUncertainNotes() = runBlocking {
        val first = SendCoordinator(outbox) { _, _ -> DeliveryOutcome.Uncertain("timeout") }
        first.submit("maybe", "space", null, SavedNote.SOURCE_MAIN)
        val noteId = outbox.items().single().id
        val resend = SendCoordinator(outbox) { _, _ -> DeliveryOutcome.Acknowledged }.resend(noteId)
        assertEquals(NoteDeliveryStatus.ACKNOWLEDGED, outbox.get(noteId)?.status)
        assertTrue(resend.message.contains("duplicate", ignoreCase = true))
    }

    @Test
    fun thrownSenderIsUncertainAndKeepsCopy() = runBlocking {
        val coordinator = SendCoordinator(outbox) { _, _ ->
            throw SocketTimeoutException("read timed out")
        }
        val result = coordinator.submit("maybe", "space", null, SavedNote.SOURCE_MAIN)
        assertFalse(result.clearEditor)
        assertEquals(NoteDeliveryStatus.UNCERTAIN, outbox.items().single().status)
        assertEquals("maybe", outbox.items().single().content)
        assertEquals(1, outbox.items().size)
    }

    @Test
    fun classifiesOfflineAsFailedKeepEditor() = runBlocking {
        val coordinator = SendCoordinator(outbox) { _, _ ->
            com.dnnypck.capacitiesquicknotepro.data.notes.DeliveryClassifier.fromThrowable(
                Exception("Can't reach Capacities", UnknownHostException("dns"))
            )
        }
        val offline = coordinator.submit("offline", "s", null, SavedNote.SOURCE_MAIN)
        assertEquals(NoteDeliveryStatus.FAILED, outbox.items().last().status)
        assertFalse(offline.clearEditor)
    }
}