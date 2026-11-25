package com.dnnypck.targets

import com.dnnypck.targets.data.model.BackupData
import com.dnnypck.targets.data.model.Space
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupDataTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `v1 backup decodes with token-less spaces`() {
        val v1Json = """
            {
                "apiKey": "legacy-key",
                "spaces": [
                    {"id": "abc123", "nickname": "Work", "createdAt": 1700000000000}
                ],
                "selectedSpaceId": "abc123",
                "backupVersion": 1,
                "backupTimestamp": 1700000000000
            }
        """.trimIndent()

        val backup = json.decodeFromString<BackupData>(v1Json)

        assertEquals(1, backup.spaces.size)
        assertEquals("abc123", backup.spaces[0].id)
        assertEquals("Work", backup.spaces[0].nickname)
        assertNull(backup.spaces[0].apiToken)
        assertEquals("abc123", backup.selectedSpaceId)
    }

    @Test
    fun `v2 backup round-trips per-space tokens`() {
        val original = BackupData(
            spaces = listOf(
                Space(id = "id-1", nickname = "Personal", apiToken = "cap-api-token1"),
                Space(id = "id-2", nickname = null, apiToken = null)
            ),
            selectedSpaceId = "id-1"
        )

        val restored = json.decodeFromString<BackupData>(json.encodeToString(original))

        assertEquals(2, restored.backupVersion)
        assertNull(restored.apiKey)
        assertEquals("cap-api-token1", restored.spaces[0].apiToken)
        assertNull(restored.spaces[1].apiToken)
        assertEquals("id-1", restored.selectedSpaceId)
    }
}
