package com.dnnypck.capacitiesquicknotepro.notes

import com.dnnypck.capacitiesquicknotepro.data.notes.MemoryNoteStorage
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteDeliveryStatus
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteOutbox
import com.dnnypck.capacitiesquicknotepro.data.notes.SavedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteOutboxTest {
    private val storage = MemoryNoteStorage()
    private val outbox = NoteOutbox(storage, idFactory = { "note-1" }, clock = { 1_000L })

    @Test
    fun activeDraftSurvivesReload() {
        assertTrue(outbox.saveActiveDraft("long essay", "space-1"))
        val reloaded = NoteOutbox(storage, idFactory = { "x" }, clock = { 2_000L })
        assertEquals("long essay", reloaded.activeDraft()?.content)
        assertEquals("space-1", reloaded.activeDraft()?.spaceId)
    }

    @Test
    fun failedDraftWriteDoesNotClaimSuccess() {
        storage.failNextWrite = true
        assertFalse(outbox.saveActiveDraft("keep me", "space-1"))
        assertNull(outbox.activeDraft())
    }

    @Test
    fun queuedNoteIsRetainedAcrossRestart() {
        val saved = outbox.recordQueued("hello", "space-1", "Work", SavedNote.SOURCE_MAIN)
        assertNotNull(saved)
        assertEquals(NoteDeliveryStatus.QUEUED, saved!!.status)
        val reloaded = NoteOutbox(storage, idFactory = { "x" }, clock = { 3_000L })
        val item = reloaded.items().single()
        assertEquals("hello", item.content)
        assertEquals("space-1", item.spaceId)
        assertEquals(NoteDeliveryStatus.QUEUED, item.status)
    }

    @Test
    fun failedAndUncertainNotesAreNeverAutoPurged() {
        var now = 0L
        var nextId = 0
        val box = NoteOutbox(storage, idFactory = { "id-${nextId++}" }, clock = { now })
        val failed = box.recordQueued("failed note", "s", null, SavedNote.SOURCE_MAIN)!!
        box.markFailed(failed.id, "offline")
        now = 1_000
        val queued = box.recordQueued("uncertain note", "s", null, SavedNote.SOURCE_WIDGET)!!
        box.markUncertain(queued.id, "timeout")

        now = 100L * 24 * 60 * 60 * 1000
        box.purgeAcknowledged()
        val statuses = box.items().map { it.status }.toSet()
        assertTrue(statuses.contains(NoteDeliveryStatus.FAILED))
        assertTrue(statuses.contains(NoteDeliveryStatus.UNCERTAIN))
        assertEquals(2, box.items().size)
    }

    @Test
    fun acknowledgedNotesAreKeptTemporarilyThenPurged() {
        var now = 0L
        val box = NoteOutbox(storage, idFactory = { "ack" }, clock = { now })
        val queued = box.recordQueued("accepted", "s", "Work", SavedNote.SOURCE_MAIN)!!
        box.markAcknowledged(queued.id)
        now = 13L * 24 * 60 * 60 * 1000
        box.purgeAcknowledged()
        assertEquals(1, box.items().size)
        now = 15L * 24 * 60 * 60 * 1000
        box.purgeAcknowledged()
        assertTrue(box.items().none { it.status == NoteDeliveryStatus.ACKNOWLEDGED })
    }

    @Test
    fun userCanInspectCopyAndDeleteUnresolved() {
        val queued = outbox.recordQueued("recover me", "s", "Work", SavedNote.SOURCE_SHARE)!!
        outbox.markFailed(queued.id, "401")
        val found = outbox.get(queued.id)
        assertEquals("recover me", found?.content)
        assertTrue(outbox.delete(queued.id, allowUnresolved = true))
        assertNull(outbox.get(queued.id))
    }

    @Test
    fun snapshotPreservesReplacedEditorContent() {
        outbox.saveActiveDraft("first note", "s1")
        assertTrue(outbox.snapshotActiveDraftIfNeeded("shared text"))
        val snapshot = outbox.items().single { it.status == NoteDeliveryStatus.DRAFT }
        assertEquals("first note", snapshot.content)
        assertEquals("shared text", outbox.activeDraft()?.content)
    }
}
