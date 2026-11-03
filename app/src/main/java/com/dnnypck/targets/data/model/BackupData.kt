package com.dnnypck.targets.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val apiKey: String = "",
    val spaces: List<Space> = emptyList(),
    val selectedSpaceId: String? = null,
    val backupVersion: Int = CURRENT_VERSION,
    val backupTimestamp: Long = System.currentTimeMillis(),
    val connections: List<OAuthConnection> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 2
        const val LEGACY_API_TOKEN_VERSION = 1
    }

    val isLegacyApiTokenBackup: Boolean
        get() = connections.isEmpty() && apiKey.isNotBlank()
}
