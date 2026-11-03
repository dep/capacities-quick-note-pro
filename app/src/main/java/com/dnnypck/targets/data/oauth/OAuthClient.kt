package com.dnnypck.capacitiesquicknotepro.data.oauth

import android.net.Uri
import com.dnnypck.capacitiesquicknotepro.data.network.ApiErrors
import com.dnnypck.capacitiesquicknotepro.data.network.KtorHttpClient
import com.dnnypck.targets.data.model.OAuthConnection
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom

class OAuthClient(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    suspend fun discoverEndpoints(): OAuthEndpoints = withContext(Dispatchers.IO) {
        try {
            val response = KtorHttpClient.client.get(OAuthConfig.DISCOVERY_URL)
            if (!response.status.isSuccess()) return@withContext OAuthEndpoints.FALLBACK
            val metadata = json.decodeFromString<AuthorizationServerMetadata>(response.bodyAsText())
            OAuthEndpoints(
                authorizationEndpoint = metadata.authorizationEndpoint
                    ?: OAuthConfig.FALLBACK_AUTHORIZATION_ENDPOINT,
                tokenEndpoint = metadata.tokenEndpoint
                    ?: OAuthConfig.FALLBACK_TOKEN_ENDPOINT,
                revocationEndpoint = metadata.revocationEndpoint
                    ?: OAuthConfig.FALLBACK_REVOCATION_ENDPOINT
            )
        } catch (_: Exception) {
            OAuthEndpoints.FALLBACK
        }
    }

    fun createAuthorizationUrl(
        endpoints: OAuthEndpoints,
        pkce: PkceCodes,
        state: String
    ): String {
        val uri = Uri.parse(endpoints.authorizationEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", OAuthConfig.clientId)
            .appendQueryParameter("redirect_uri", OAuthConfig.redirectUri)
            .appendQueryParameter("scope", OAuthConfig.SCOPES)
            .appendQueryParameter("resource", OAuthConfig.RESOURCE)
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", pkce.method)
            .appendQueryParameter("state", state)
            .build()
        return uri.toString()
    }

    fun generateState(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64Url.encode(bytes)
    }

    suspend fun exchangeCode(
        endpoints: OAuthEndpoints,
        code: String,
        codeVerifier: String
    ): Result<OAuthConnection> = withContext(Dispatchers.IO) {
        requestTokens(
            endpoints = endpoints,
            parameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", OAuthConfig.redirectUri)
                append("client_id", OAuthConfig.clientId)
                append("code_verifier", codeVerifier)
            },
            placeholderSpaceId = ""
        )
    }

    suspend fun refresh(
        endpoints: OAuthEndpoints,
        connection: OAuthConnection
    ): Result<OAuthConnection> = withContext(Dispatchers.IO) {
        if (!connection.canRefresh()) {
            return@withContext Result.failure(
                Exception("This space needs to be reconnected. Open Settings and tap Connect to Capacities.")
            )
        }
        requestTokens(
            endpoints = endpoints,
            parameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", connection.refreshToken)
                append("client_id", OAuthConfig.clientId)
            },
            placeholderSpaceId = connection.spaceId
        )
    }

    suspend fun revoke(
        endpoints: OAuthEndpoints,
        connection: OAuthConnection
    ) = withContext(Dispatchers.IO) {
        val token = connection.refreshToken.ifBlank { connection.accessToken }
        if (token.isBlank()) return@withContext
        try {
            KtorHttpClient.client.submitForm(
                url = endpoints.revocationEndpoint,
                formParameters = Parameters.build {
                    append("token", token)
                    append(
                        "token_type_hint",
                        if (connection.refreshToken.isNotBlank()) "refresh_token" else "access_token"
                    )
                    append("client_id", OAuthConfig.clientId)
                }
            )
        } catch (_: Exception) {
            // RFC 7009: local sign-out should still succeed if revoke fails.
        }
    }

    private suspend fun requestTokens(
        endpoints: OAuthEndpoints,
        parameters: Parameters,
        placeholderSpaceId: String
    ): Result<OAuthConnection> {
        return try {
            val response = KtorHttpClient.client.submitForm(
                url = endpoints.tokenEndpoint,
                formParameters = parameters
            )
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return Result.failure(
                    Exception(parseOAuthError(response.status.value, body))
                )
            }
            val token = json.decodeFromString<TokenResponse>(body)
            val expiresAt = (System.currentTimeMillis() / 1000) + token.expiresIn
            Result.success(
                OAuthConnection(
                    spaceId = placeholderSpaceId,
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken.orEmpty(),
                    expiresAtEpochSeconds = expiresAt,
                    scope = token.scope.orEmpty()
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrors.network(e), e))
        }
    }

    private fun parseOAuthError(status: Int, body: String): String {
        val parsed = try {
            json.decodeFromString<OAuthErrorResponse>(body)
        } catch (_: Exception) {
            null
        }
        val error = parsed?.error
        val description = parsed?.errorDescription
        val meaning = when (error) {
            "invalid_client" -> "This app isn't registered with Capacities yet. The developer needs to set CAPACITIES_CLIENT_ID after Capacities issues one."
            "invalid_grant" -> "Sign-in expired or was revoked. Open Settings and connect to Capacities again."
            "unauthorized_client" -> "The redirect URI isn't on the Capacities allow-list. Register ${OAuthConfig.redirectUri} with the Capacities team."
            "invalid_scope" -> "Capacities rejected the requested permissions. Ask the Capacities team to allow api:read, api:write, and offline_access."
            "invalid_target" -> "Capacities rejected the OAuth resource parameter."
            else -> null
        }
        return buildString {
            append(meaning ?: "Couldn't complete Capacities sign-in ($status).")
            if (!description.isNullOrBlank()) {
                append("\n\n")
                append(description)
            } else if (meaning == null && body.isNotBlank()) {
                append("\n\n")
                append(body)
            }
        }
    }
}

@Serializable
private data class AuthorizationServerMetadata(
    @SerialName("authorization_endpoint") val authorizationEndpoint: String? = null,
    @SerialName("token_endpoint") val tokenEndpoint: String? = null,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null
)

@Serializable
private data class OAuthErrorResponse(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)
