package com.dnnypck.capacitiesquicknotepro.data.notes

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileNoteStorage(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : NoteStorage {
    @Volatile var isCorrupt: Boolean = false
        private set

    override fun read(): StoredNotes {
        if (!file.exists()) return StoredNotes()
        return try {
            json.decodeFromString<StoredNotes>(file.readText())
        } catch (_: Exception) {
            // Leave the file in place. Overwriting a corrupt file would destroy
            // the only remaining copy of the user's notes.
            isCorrupt = true
            StoredNotes()
        }
    }

    override fun write(data: StoredNotes): Boolean {
        if (isCorrupt) return false
        return try {
            val parent = file.parentFile ?: return false
            if (!parent.exists() && !parent.mkdirs()) return false
            val tmp = File(parent, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(data))
            if (tmp.renameTo(file)) return true
            if (file.exists() && !file.delete()) return false
            tmp.renameTo(file)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val FILE_NAME = "note_outbox.json"

        fun inAppFiles(filesDir: File): FileNoteStorage {
            return FileNoteStorage(File(filesDir, FILE_NAME))
        }
    }
}
