package com.dnnypck.capacitiesquicknotepro

import com.dnnypck.targets.data.model.BackupData
import com.dnnypck.targets.data.model.BackupSupport
import com.dnnypck.targets.data.model.OAuthConnection
import com.dnnypck.targets.data.model.Space
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun connectionNeedsRefreshUsesSkew() {
        val connection = OAuthConnection(
            spaceId = "space-1",
            accessToken = "access",
            refreshToken = "refresh",
            expiresAtEpochSeconds = 1_000
        )
        assertFalse(connection.needsRefresh(nowEpochSeconds = 900, skewSeconds = 60))
        assertTrue(connection.needsRefresh(nowEpochSeconds = 940, skewSeconds = 60))
        assertTrue(connection.needsRefresh(nowEpochSeconds = 1_000, skewSeconds = 60))
    }

    @Test
    fun canRefreshRequiresRefreshToken() {
        assertTrue(
            OAuthConnection("id", "a", "r", 1).canRefresh()
        )
        assertFalse(
            OAuthConnection("id", "a", "", 1).canRefresh()
        )
    }

    @Test
    fun legacyBackupStillDecodesApiKeyButRestoreMustSanitize() {
        val legacy = """
            {"apiKey":"cap-api-old","spaces":[{"id":"space-1","nickname":"Notes"}],"selectedSpaceId":"space-1","backupVersion":1,"backupTimestamp":1}
        """.trimIndent()
        val backup = json.decodeFromString<BackupData>(legacy)
        assertEquals("cap-api-old", backup.apiKey)
        assertEquals(1, backup.spaces.size)
        assertTrue(backup.connections.isEmpty())
        val sanitized = BackupSupport.sanitize(backup)
        assertEquals("", sanitized.apiKey)
    }

    @Test
    fun spaceSettingsRoundTripWithoutCredentials() {
        val original = BackupData(
            spaces = listOf(Space(id = "abc", nickname = "Work")),
            selectedSpaceId = "abc"
        )
        val decoded = json.decodeFromString<BackupData>(json.encodeToString(original))
        assertEquals("abc", decoded.spaces.single().id)
        assertEquals("Work", decoded.spaces.single().nickname)
        assertEquals("abc", decoded.selectedSpaceId)
        assertEquals("", decoded.apiKey)
        assertTrue(decoded.connections.isEmpty())
    }
}
