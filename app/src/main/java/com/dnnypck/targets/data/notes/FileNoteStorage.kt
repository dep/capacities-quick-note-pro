package com.dnnypck.capacitiesquicknotepro.data.notes

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileNoteStorage(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : NoteStorage {
    @Volatile var isCorrupt: Boolean = false
        private set

    /** Test-only: write a complete temp file, then abort before replacing the dest. */
    @Volatile var simulateCrashBeforeReplace: Boolean = false

    override fun read(): StoredNotes = withLock { readLocked() }

    override fun write(data: StoredNotes): Boolean = mutate { data } != null

    override fun mutate(transform: (StoredNotes) -> StoredNotes): StoredNotes? = withLock {
        val current = readLocked()
        if (isCorrupt) return@withLock null
        val next = transform(current)
        if (!writeLocked(next)) return@withLock null
        next
    }

    private fun readLocked(): StoredNotes {
        if (file.exists() && file.length() > 0L) {
            parse(file)?.let { return it }
            isCorrupt = true
            return parse(bakFile()) ?: StoredNotes()
        }
        parse(bakFile())?.let { backup ->
            writeLocked(backup)
            return backup
        }
        return StoredNotes()
    }

    private fun writeLocked(data: StoredNotes): Boolean {
        if (isCorrupt) return false
        if (file.exists() && file.length() > 0L && parse(file) == null) {
            isCorrupt = true
            return false
        }
        val parent = file.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        return try {
            val bytes = json.encodeToString(data).toByteArray(Charsets.UTF_8)
            val tmp = File(parent, "${file.name}.tmp.${UUID.randomUUID()}")
            writeFully(tmp, bytes)
            if (simulateCrashBeforeReplace) {
                tmp.delete()
                return false
            }
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return false
            }
            copyToBak(file)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeFully(target: File, bytes: ByteArray) {
        FileOutputStream(target).use { fos ->
            fos.write(bytes)
            fos.flush()
            fos.fd.sync()
        }
    }

    private fun copyToBak(source: File) {
        val bak = bakFile()
        val bakTmp = File(source.parentFile, "${source.name}.bak.tmp")
        try {
            source.inputStream().use { input ->
                FileOutputStream(bakTmp).use { fos ->
                    input.copyTo(fos)
                    fos.flush()
                    fos.fd.sync()
                }
            }
            if (!bakTmp.renameTo(bak)) {
                bakTmp.delete()
            }
        } catch (_: Exception) {
            bakTmp.delete()
        }
    }

    private fun parse(target: File): StoredNotes? {
        if (!target.exists()) return null
        return try {
            json.decodeFromString<StoredNotes>(target.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun bakFile(): File = File(file.parentFile, "${file.name}.bak")

    private fun lockFile(): File = File(file.parentFile, "${file.name}.lock")

    private fun <T> withLock(block: () -> T): T {
        synchronized(processLock(file)) {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            return RandomAccessFile(lockFile(), "rw").use { raf ->
                var lock: FileLock? = null
                try {
                    lock = raf.channel.lock()
                    block()
                } finally {
                    try {
                        lock?.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "note_outbox.json"
        private val PROCESS_LOCKS = ConcurrentHashMap<String, Any>()

        fun inAppFiles(filesDir: File): FileNoteStorage {
            return FileNoteStorage(File(filesDir, FILE_NAME))
        }

        internal fun processLock(file: File): Any {
            val key = try {
                file.canonicalPath
            } catch (_: Exception) {
                file.absolutePath
            }
            return PROCESS_LOCKS.getOrPut(key) { Any() }
        }
    }
}
