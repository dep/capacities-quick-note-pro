package com.dnnypck.targets.data.model

/**
 * Manual backups are settings-only. They never include Capacities sign-in
 * (OAuth access/refresh tokens or legacy API keys). Export does not read or
 * clear the token store. Restore never imports credentials.
 */
object BackupSupport {
    fun createExport(
        spaces: List<Space>,
        selectedSpaceId: String?,
        timestamp: Long = System.currentTimeMillis()
    ): BackupData {
        return BackupData(
            spaces = spaces,
            selectedSpaceId = selectedSpaceId,
            backupVersion = BackupData.CURRENT_VERSION,
            backupTimestamp = timestamp
        )
    }

    fun sanitize(backup: BackupData): BackupData {
        return backup.copy(
            apiKey = "",
            connections = emptyList()
        )
    }

    fun planRestore(
        backup: BackupData,
        existingTokenSpaceIds: Set<String>,
        currentSelectedSpaceId: String?
    ): RestorePlan {
        val sanitized = sanitize(backup)
        val restoredIds = sanitized.spaces.map { it.id }.toSet()
        val keep = existingTokenSpaceIds.intersect(restoredIds)
        val drop = existingTokenSpaceIds - restoredIds
        val selected = sanitized.selectedSpaceId?.takeIf { it in restoredIds }
            ?: currentSelectedSpaceId?.takeIf { it in restoredIds }
            ?: sanitized.spaces.firstOrNull()?.id
        return RestorePlan(
            spaces = sanitized.spaces,
            selectedSpaceId = selected,
            tokenSpaceIdsToKeep = keep,
            tokenSpaceIdsToDrop = drop,
            needsSignIn = keep.isEmpty()
        )
    }

    fun jsonContainsSignInSecrets(json: String): Boolean {
        val lowered = json.lowercase()
        return listOf(
            "\"apikey\"",
            "\"connections\"",
            "\"accesstoken\"",
            "\"refreshtoken\"",
            "\"access_token\"",
            "\"refresh_token\""
        ).any { lowered.contains(it) }
    }
}

data class RestorePlan(
    val spaces: List<Space>,
    val selectedSpaceId: String?,
    val tokenSpaceIdsToKeep: Set<String>,
    val tokenSpaceIdsToDrop: Set<String>,
    val needsSignIn: Boolean
)
