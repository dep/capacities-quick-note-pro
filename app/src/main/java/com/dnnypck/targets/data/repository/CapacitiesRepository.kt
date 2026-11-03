package com.dnnypck.capacitiesquicknotepro.data.repository

import com.dnnypck.capacitiesquicknotepro.data.network.ApiErrors
import com.dnnypck.capacitiesquicknotepro.data.network.CapacitiesApi
import com.dnnypck.capacitiesquicknotepro.data.network.NoteContent
import com.dnnypck.capacitiesquicknotepro.data.oauth.OAuthClient
import com.dnnypck.capacitiesquicknotepro.data.oauth.OAuthConfig
import com.dnnypck.capacitiesquicknotepro.data.oauth.OAuthEndpoints
import com.dnnypck.capacitiesquicknotepro.data.oauth.TokenStore
import com.dnnypck.targets.data.model.OAuthConnection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CapacitiesRepository(
    private val tokenStore: TokenStore,
    private val api: CapacitiesApi = CapacitiesApi(),
    private val oauthClient: OAuthClient = OAuthClient()
) {
    private val refreshMutex = Mutex()
    @Volatile private var cachedEndpoints: OAuthEndpoints? = null

    suspend fun sendNote(spaceId: String, content: String): Result<Unit> {
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            return Result.failure(Exception("Please enter a note"))
        }
        return if (NoteContent.isUrlOnly(trimmed)) {
            authorized(spaceId) { token -> api.createFromUrl(token, trimmed) }
        } else {
            authorized(spaceId) { token -> api.appendToDailyNote(token, content) }
        }
    }

    suspend fun fetchSpace(accessToken: String) = api.getSpace(accessToken)

    private suspend fun authorized(
        spaceId: String,
        block: suspend (accessToken: String) -> Result<Unit>
    ): Result<Unit> {
        val tokens = ensureFresh(spaceId).getOrElse { return Result.failure(it) }
        val first = block(tokens.accessToken)
        if (first.isSuccess || !ApiErrors.isUnauthorized(first.exceptionOrNull())) {
            return first
        }
        val refreshed = refreshNow(spaceId).getOrElse { return Result.failure(it) }
        return block(refreshed.accessToken)
    }

    private suspend fun ensureFresh(spaceId: String): Result<OAuthConnection> {
        val current = tokenStore.get(spaceId)
            ?: return Result.failure(
                Exception("This space isn't connected. Open Settings and tap Connect to Capacities.")
            )
        if (!current.needsRefresh(skewSeconds = OAuthConfig.ACCESS_TOKEN_REFRESH_SKEW_SECONDS)) {
            return Result.success(current)
        }
        return refreshNow(spaceId)
    }

    private suspend fun refreshNow(spaceId: String): Result<OAuthConnection> {
        return refreshMutex.withLock {
            val current = tokenStore.get(spaceId)
                ?: return@withLock Result.failure(
                    Exception("This space isn't connected. Open Settings and tap Connect to Capacities.")
                )
            if (!current.needsRefresh(skewSeconds = OAuthConfig.ACCESS_TOKEN_REFRESH_SKEW_SECONDS)) {
                return@withLock Result.success(current)
            }
            val endpoints = endpoints()
            val refreshed = oauthClient.refresh(endpoints, current)
            refreshed.fold(
                onSuccess = { tokens ->
                    val stored = tokens.copy(spaceId = spaceId)
                    tokenStore.save(stored)
                    Result.success(stored)
                },
                onFailure = { error ->
                    Result.failure(
                        Exception(
                            "Couldn't refresh Capacities sign-in. Open Settings and reconnect this space.\n\n${error.message ?: ""}".trim()
                        )
                    )
                }
            )
        }
    }

    private suspend fun endpoints(): OAuthEndpoints {
        cachedEndpoints?.let { return it }
        val discovered = oauthClient.discoverEndpoints()
        cachedEndpoints = discovered
        return discovered
    }
}
