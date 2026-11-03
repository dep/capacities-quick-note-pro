package com.dnnypck.targets.data.model

import kotlinx.serialization.Serializable

@Serializable
data class OAuthConnection(
    val spaceId: String,
    val accessToken: String,
    val refreshToken: String = "",
    val expiresAtEpochSeconds: Long,
    val scope: String = ""
) {
    fun needsRefresh(
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
        skewSeconds: Long = 60
    ): Boolean {
        return nowEpochSeconds + skewSeconds >= expiresAtEpochSeconds
    }

    fun canRefresh(): Boolean = refreshToken.isNotBlank()
}

@Serializable
data class PendingOAuthSession(
    val state: String,
    val codeVerifier: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
