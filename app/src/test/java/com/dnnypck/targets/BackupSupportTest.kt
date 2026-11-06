package com.dnnypck.capacitiesquicknotepro

import com.dnnypck.targets.data.model.BackupData
import com.dnnypck.targets.data.model.BackupSupport
import com.dnnypck.targets.data.model.OAuthConnection
import com.dnnypck.targets.data.model.Space
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSupportTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val work = Space(id = "space-work", nickname = "Work", createdAt = 1)
    private val personal = Space(id = "space-home", nickname = "Home", createdAt = 2)

    @Test
    fun exportOmitsSignInCredentials() {
        val exported = BackupSupport.createExport(
            spaces = listOf(work, personal),
            selectedSpaceId = "space-work",
            timestamp = 42
        )
        assertEquals("", exported.apiKey)
        assertTrue(exported.connections.isEmpty())
        assertEquals(BackupData.CURRENT_VERSION, exported.backupVersion)
        assertEquals(listOf(work, personal), exported.spaces)
        assertEquals("space-work", exported.selectedSpaceId)

        val encoded = json.encodeToString(exported)
        assertFalse(BackupSupport.jsonContainsSignInSecrets(encoded))
        assertTrue(encoded.contains("space-work"))
        assertTrue(encoded.contains("Work"))
        assertFalse(encoded.contains("eyJ"))
        assertFalse(encoded.contains("cap-api-"))
    }

    @Test
    fun sanitizeStripsLegacyApiKeyAndOauthTokens() {
        val dirty = BackupData(
            apiKey = "cap-api-old",
            spaces = listOf(work),
            selectedSpaceId = "space-work",
            backupVersion = BackupData.LEGACY_OAUTH_TOKEN_VERSION,
            connections = listOf(
                OAuthConnection(
                    spaceId = "space-work",
                    accessToken = "access-secret",
                    refreshToken = "refresh-secret",
                    expiresAtEpochSeconds = 99
                )
            )
        )
        val clean = BackupSupport.sanitize(dirty)
        assertEquals("", clean.apiKey)
        assertTrue(clean.connections.isEmpty())
        assertEquals(listOf(work), clean.spaces)
        assertFalse(json.encodeToString(clean).contains("access-secret"))
        assertFalse(json.encodeToString(clean).contains("refresh-secret"))
        assertFalse(json.encodeToString(clean).contains("cap-api-old"))
    }

    @Test
    fun restoreOnFreshInstallRequiresSignInAndKeepsNoTokens() {
        val backup = BackupSupport.createExport(listOf(work), "space-work")
        val plan = BackupSupport.planRestore(
            backup = backup,
            existingTokenSpaceIds = emptySet(),
            currentSelectedSpaceId = null
        )
        assertTrue(plan.needsSignIn)
        assertTrue(plan.tokenSpaceIdsToKeep.isEmpty())
        assertTrue(plan.tokenSpaceIdsToDrop.isEmpty())
        assertEquals("space-work", plan.selectedSpaceId)
        assertEquals(listOf(work), plan.spaces)
    }

    @Test
    fun restoreIntoConnectedInstallKeepsOverlappingSignIn() {
        val backup = BackupSupport.createExport(listOf(work, personal), "space-home")
        val plan = BackupSupport.planRestore(
            backup = backup,
            existingTokenSpaceIds = setOf("space-work", "space-old"),
            currentSelectedSpaceId = "space-work"
        )
        assertFalse(plan.needsSignIn)
        assertEquals(setOf("space-work"), plan.tokenSpaceIdsToKeep)
        assertEquals(setOf("space-old"), plan.tokenSpaceIdsToDrop)
        assertEquals("space-home", plan.selectedSpaceId)
    }

    @Test
    fun restoreDisjointSpacesRequiresReconnectWithoutImportingBackupTokens() {
        val backup = BackupData(
            apiKey = "cap-api-old",
            spaces = listOf(personal),
            selectedSpaceId = "space-home",
            connections = listOf(
                OAuthConnection(
                    spaceId = "space-home",
                    accessToken = "must-not-import",
                    refreshToken = "must-not-import-refresh",
                    expiresAtEpochSeconds = 1
                )
            )
        )
        val plan = BackupSupport.planRestore(
            backup = backup,
            existingTokenSpaceIds = setOf("space-work"),
            currentSelectedSpaceId = "space-work"
        )
        assertTrue(plan.needsSignIn)
        assertTrue(plan.tokenSpaceIdsToKeep.isEmpty())
        assertEquals(setOf("space-work"), plan.tokenSpaceIdsToDrop)
        assertEquals(listOf(personal), plan.spaces)
        assertEquals("space-home", plan.selectedSpaceId)
    }

    @Test
    fun legacyV1BackupDoesNotSurfaceApiKeyAfterSanitize() {
        val legacy = """
            {"apiKey":"cap-api-old","spaces":[{"id":"space-1","nickname":"Notes","createdAt":1}],"selectedSpaceId":"space-1","backupVersion":1,"backupTimestamp":1}
        """.trimIndent()
        val parsed = json.decodeFromString<BackupData>(legacy)
        assertEquals("cap-api-old", parsed.apiKey)
        val clean = BackupSupport.sanitize(parsed)
        assertEquals("", clean.apiKey)
        assertTrue(clean.connections.isEmpty())
        val encoded = json.encodeToString(BackupSupport.createExport(clean.spaces, clean.selectedSpaceId, 1))
        assertFalse(BackupSupport.jsonContainsSignInSecrets(encoded))
    }

    @Test
    fun selectedSpaceFallsBackToCurrentThenFirst() {
        val backup = BackupSupport.createExport(listOf(work, personal), selectedSpaceId = "missing")
        val plan = BackupSupport.planRestore(
            backup = backup,
            existingTokenSpaceIds = setOf("space-home"),
            currentSelectedSpaceId = "space-home"
        )
        assertEquals("space-home", plan.selectedSpaceId)

        val noCurrent = BackupSupport.planRestore(
            backup = backup,
            existingTokenSpaceIds = emptySet(),
            currentSelectedSpaceId = "also-missing"
        )
        assertEquals("space-work", noCurrent.selectedSpaceId)
    }

    @Test
    fun emptyBackupClearsSelection() {
        val plan = BackupSupport.planRestore(
            backup = BackupSupport.createExport(emptyList(), null),
            existingTokenSpaceIds = emptySet(),
            currentSelectedSpaceId = "space-work"
        )
        assertNull(plan.selectedSpaceId)
        assertTrue(plan.needsSignIn)
    }
}
