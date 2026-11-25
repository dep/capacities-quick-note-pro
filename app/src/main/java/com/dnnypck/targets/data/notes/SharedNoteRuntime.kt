package com.dnnypck.capacitiesquicknotepro.data.notes

import java.io.File
import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide note services. Main, share, widget, and leftover widget-service
 * paths must share one outbox and one send gate so concurrent writers cannot
 * keep separate in-memory snapshots of the same file.
 */
object SharedNoteRuntime {
    private val lock = Any()
    private var filesDirPath: String? = null
    private var cachedOutbox: NoteOutbox? = null
    val sendGate = SendGate()

    fun outbox(filesDir: File): NoteOutbox = synchronized(lock) {
        val path = try {
            filesDir.canonicalPath
        } catch (_: Exception) {
            filesDir.absolutePath
        }
        val existing = cachedOutbox
        if (existing != null && filesDirPath == path) return existing
        filesDirPath = path
        NoteOutbox(FileNoteStorage.inAppFiles(filesDir)).also { cachedOutbox = it }
    }
}

class SendGate {
    val mutex = Mutex()
    @Volatile var inFlight = false
}
