package com.dnnypck.targets.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val apiKey: String? = null,
    val spaces: List<Space>,
    val selectedSpaceId: String?,
    val backupVersion: Int = 2,
    val backupTimestamp: Long = System.currentTimeMillis()
)
