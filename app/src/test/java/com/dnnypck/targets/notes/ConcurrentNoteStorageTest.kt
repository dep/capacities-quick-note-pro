package com.dnnypck.capacitiesquicknotepro.notes

import com.dnnypck.capacitiesquicknotepro.data.notes.FileNoteStorage
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteDeliveryStatus
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteOutbox
import com.dnnypck.capacitiesquicknotepro.data.notes.SavedNote
import com.dnnypck.capacitiesquicknotepro.data.notes.StoredNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ConcurrentNoteStorageTest {

    @Test
    fun independentOutboxesCannotClobberParallelSaves() {
        val file = tempOutbox()
        val main = NoteOutbox(FileNoteStorage(file), idFactory = { "main-${counter()}" })
        val widget = NoteOutbox(FileNoteStorage(file), idFactory = { "widget-${counter()}" })
        val perWriter = 40
        val barrier = CyclicBarrier(2)
        val errors = mutableListOf<Throwable>()

        val mainThread = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                repeat(perWriter) { i ->
                    assertNotNull(main.recordQueued("main-$i", "space", "Work", SavedNote.SOURCE_MAIN))
                }
            } catch (error: Throwable) {
                synchronized(errors) { errors.add(error) }
            }
        }
        val widgetThread = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                repeat(perWriter) { i ->
                    assertNotNull(widget.recordQueued("widget-$i", "space", "Work", SavedNote.SOURCE_WIDGET))
                }
            } catch (error: Throwable) {
                synchronized(errors) { errors.add(error) }
            }
        }
        mainThread.start()
        widgetThread.start()
        mainThread.join(20_000)
        widgetThread.join(20_000)
        assertTrue("writers hung", !mainThread.isAlive && !widgetThread.isAlive)
        assertTrue(errors.joinToString(), errors.isEmpty())

        val reloaded = NoteOutbox(FileNoteStorage(file))
        val contents = reloaded.items().map { it.content }.toSet()
        val expected = (0 until perWriter).flatMap { listOf("main-$it", "widget-$it") }.toSet()
        assertEquals(expected, contents)
        assertEquals(perWriter * 2, reloaded.items().size)
    }

    @Test
    fun parallelStatusUpdateAndInsertKeepBothNotes() {
        val file = tempOutbox()
        val seed = NoteOutbox(FileNoteStorage(file), idFactory = { "seed" })
        val queued = seed.recordQueued("already queued", "space", "Work", SavedNote.SOURCE_MAIN)
        assertNotNull(queued)

        val main = NoteOutbox(FileNoteStorage(file))
        val widget = NoteOutbox(FileNoteStorage(file), idFactory = { "new-note" })
        val barrier = CyclicBarrier(2)
        val errors = mutableListOf<Throwable>()

        val statusThread = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                assertTrue(main.markUncertain(queued!!.id, "timeout"))
            } catch (error: Throwable) {
                synchronized(errors) { errors.add(error) }
            }
        }
        val insertThread = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                assertNotNull(widget.recordQueued("widget draft", "space", "Work", SavedNote.SOURCE_WIDGET))
            } catch (error: Throwable) {
                synchronized(errors) { errors.add(error) }
            }
        }
        statusThread.start()
        insertThread.start()
        statusThread.join(10_000)
        insertThread.join(10_000)
        assertTrue(errors.joinToString(), errors.isEmpty())

        val items = NoteOutbox(FileNoteStorage(file)).items()
        assertEquals(2, items.size)
        val byId = items.associateBy { it.id }
        assertEquals(NoteDeliveryStatus.UNCERTAIN, byId.getValue("seed").status)
        assertEquals("already queued", byId.getValue("seed").content)
        assertEquals("widget draft", byId.getValue("new-note").content)
        assertEquals(NoteDeliveryStatus.QUEUED, byId.getValue("new-note").status)
    }

    @Test
    fun interruptedReplaceLeavesLastValidFileIntact() {
        val file = tempOutbox()
        val storage = FileNoteStorage(file)
        val original = SavedNote(
            id = "keep",
            content = "do not destroy",
            spaceId = "s",
            status = NoteDeliveryStatus.FAILED,
            createdAt = 1,
            updatedAt = 1
        )
        assertTrue(storage.write(StoredNotes(items = listOf(original))))
        val before = file.readText()

        storage.simulateCrashBeforeReplace = true
        assertFalse(
            storage.write(
                StoredNotes(
                    items = listOf(
                        original,
                        original.copy(id = "lost-if-clobber", content = "newer")
                    )
                )
            )
        )
        assertEquals(before, file.readText())
        assertEquals("do not destroy", FileNoteStorage(file).read().items.single().content)
        assertTrue(file.readText().isNotBlank())
        assertFalse(file.readText().startsWith("{partial"))
    }

    @Test
    fun leftoverPartialTmpDoesNotReplaceValidDest() {
        val file = tempOutbox()
        val storage = FileNoteStorage(file)
        val note = SavedNote(
            id = "a",
            content = "valid dest",
            spaceId = "s",
            status = NoteDeliveryStatus.DRAFT,
            createdAt = 1,
            updatedAt = 1
        )
        assertTrue(storage.write(StoredNotes(items = listOf(note))))
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText("{partial")
        assertEquals("valid dest", FileNoteStorage(file).read().items.single().content)

        val extra = note.copy(id = "b", content = "second")
        assertTrue(FileNoteStorage(file).write(StoredNotes(items = listOf(note, extra))))
        val reloaded = FileNoteStorage(file).read().items.map { it.content }.toSet()
        assertEquals(setOf("valid dest", "second"), reloaded)
        assertTrue(file.readText().startsWith("{"))
        assertFalse(file.readText().contains("{partial"))
    }

    @Test
    fun corruptDestKeepsBytesAndStillShowsBackupCopy() {
        val file = tempOutbox()
        val storage = FileNoteStorage(file)
        val note = SavedNote(
            id = "visible",
            content = "still recoverable",
            spaceId = "s",
            status = NoteDeliveryStatus.FAILED,
            createdAt = 1,
            updatedAt = 1
        )
        assertTrue(storage.write(StoredNotes(items = listOf(note))))
        file.writeText("{not-json")
        val reader = FileNoteStorage(file)
        assertEquals("still recoverable", reader.read().items.single().content)
        assertTrue(reader.isCorrupt)
        assertFalse(reader.write(StoredNotes()))
        assertEquals("{not-json", file.readText())
    }

    @Test
    fun writeWithoutPriorReadDoesNotOverwriteCorruptDest() {
        val file = tempOutbox()
        file.writeText("{not-json")
        val storage = FileNoteStorage(file)
        assertFalse(storage.write(StoredNotes()))
        assertEquals("{not-json", file.readText())
        assertTrue(storage.isCorrupt)
    }

    @Test
    fun missingDestRecoversBackupInsteadOfEmptyOverwrite() {
        val file = tempOutbox()
        val storage = FileNoteStorage(file)
        val note = SavedNote(
            id = "recovered",
            content = "from backup",
            spaceId = "s",
            status = NoteDeliveryStatus.UNCERTAIN,
            createdAt = 1,
            updatedAt = 1
        )
        assertTrue(storage.write(StoredNotes(items = listOf(note))))
        val bak = File(file.parentFile, "${file.name}.bak")
        assertTrue("expected last-good backup", bak.exists())
        file.delete()
        val recovered = FileNoteStorage(file).read()
        assertEquals("from backup", recovered.items.single().content)

        val second = NoteOutbox(FileNoteStorage(file), idFactory = { "extra" })
        assertNotNull(second.recordQueued("another", "s", null, SavedNote.SOURCE_MAIN))
        val items = NoteOutbox(FileNoteStorage(file)).items().map { it.content }.toSet()
        assertEquals(setOf("from backup", "another"), items)
    }

    @Test
    fun manyThreadsMutatingSharedFileRetainEveryNote() {
        val file = tempOutbox()
        val threads = 8
        val perThread = 20
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failures = AtomicInteger(0)
        repeat(threads) { t ->
            pool.execute {
                try {
                    start.await()
                    val outbox = NoteOutbox(
                        FileNoteStorage(file),
                        idFactory = { "t$t-${counter()}" }
                    )
                    repeat(perThread) { i ->
                        val saved = outbox.recordQueued("t$t-$i", "s", null, SavedNote.SOURCE_WIDGET)
                        if (saved == null) failures.incrementAndGet()
                    }
                } catch (_: Throwable) {
                    failures.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals(0, failures.get())
        assertEquals(threads * perThread, NoteOutbox(FileNoteStorage(file)).items().size)
    }

    private fun tempOutbox(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "outbox-${java.util.UUID.randomUUID()}")
        assertTrue(dir.mkdirs())
        val file = File(dir, FileNoteStorage.FILE_NAME)
        file.delete()
        File(dir, "${FileNoteStorage.FILE_NAME}.bak").delete()
        dir.deleteOnExit()
        file.deleteOnExit()
        return file
    }

    companion object {
        private val ids = AtomicInteger(0)
        fun counter(): Int = ids.incrementAndGet()
    }
}
