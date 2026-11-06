package com.dnnypck.capacitiesquicknotepro.notes

import com.dnnypck.capacitiesquicknotepro.data.notes.FileNoteStorage
import com.dnnypck.capacitiesquicknotepro.data.notes.SavedNote
import com.dnnypck.capacitiesquicknotepro.data.notes.StoredNotes
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteDeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileNoteStorageTest {
    @Test
    fun roundTripPersistsNoteBody() {
        val file = File.createTempFile("outbox", ".json")
        file.deleteOnExit()
        val storage = FileNoteStorage(file)
        val note = SavedNote(
            id = "1",
            content = "keep this",
            spaceId = "s",
            status = NoteDeliveryStatus.FAILED,
            createdAt = 1,
            updatedAt = 1
        )
        assertTrue(storage.write(StoredNotes(items = listOf(note))))
        assertEquals("keep this", FileNoteStorage(file).read().items.single().content)
    }

    @Test
    fun corruptFileIsNotOverwritten() {
        val file = File.createTempFile("outbox-corrupt", ".json")
        file.deleteOnExit()
        file.writeText("{not-json")
        val storage = FileNoteStorage(file)
        storage.read()
        assertTrue(storage.isCorrupt)
        assertFalse(storage.write(StoredNotes()))
        assertEquals("{not-json", file.readText())
    }
}
