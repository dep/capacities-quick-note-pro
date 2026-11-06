package com.dnnypck.capacitiesquicknotepro.notes

import com.dnnypck.capacitiesquicknotepro.data.notes.NoteOutbox
import com.dnnypck.targets.data.model.BackupSupport
import com.dnnypck.targets.data.model.Space
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSanitizeTest {
    @Test
    fun errorTextRedactsTokens() {
        val raw = "Authorization: Bearer eyJabc.def  cap-api-secretkey"
        val cleaned = NoteOutbox.sanitizeError(raw)
        assertFalse(cleaned.contains("eyJabc"))
        assertFalse(cleaned.contains("secretkey"))
        assertTrue(cleaned.contains("[redacted]"))
    }

    @Test
    fun settingsBackupStillExcludesNoteBodiesAndCredentials() {
        val encoded = Json { ignoreUnknownKeys = true }.encodeToString(
            BackupSupport.createExport(
                spaces = listOf(Space(id = "s", nickname = "Work")),
                selectedSpaceId = "s",
                timestamp = 1
            )
        )
        assertFalse(BackupSupport.jsonContainsSignInSecrets(encoded))
        assertFalse(encoded.contains("markdown"))
        assertFalse(encoded.contains("accessToken"))
    }
}
