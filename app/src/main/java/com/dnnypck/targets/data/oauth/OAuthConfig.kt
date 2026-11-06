package com.dnnypck.capacitiesquicknotepro.data.oauth

import com.dnnypck.capacitiesquicknotepro.BuildConfig

object OAuthConfig {
    const val RESOURCE = "https://api.capacities.io"
    const val SCOPES = "api:read api:write offline_access"
    const val DISCOVERY_URL = "https://api.capacities.io/.well-known/oauth-authorization-server"
    const val FALLBACK_AUTHORIZATION_ENDPOINT = "https://api.capacities.io/oauth/authorize"
    const val FALLBACK_TOKEN_ENDPOINT = "https://api.capacities.io/oauth/token"
    const val FALLBACK_REVOCATION_ENDPOINT = "https://api.capacities.io/oauth/revoke"
    const val PENDING_SESSION_TTL_MS = 15 * 60 * 1000L
    const val ACCESS_TOKEN_REFRESH_SKEW_SECONDS = 60L

    val clientId: String
        get() = BuildConfig.CAPACITIES_CLIENT_ID

    val redirectUri: String
        get() = BuildConfig.CAPACITIES_REDIRECT_URI

    val apiVersion: String
        get() = BuildConfig.CAPACITIES_API_VERSION

    val isConfigured: Boolean
        get() = clientId.isNotBlank()
}

data class OAuthEndpoints(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val revocationEndpoint: String
) {
    companion object {
        val FALLBACK = OAuthEndpoints(
            authorizationEndpoint = OAuthConfig.FALLBACK_AUTHORIZATION_ENDPOINT,
            tokenEndpoint = OAuthConfig.FALLBACK_TOKEN_ENDPOINT,
            revocationEndpoint = OAuthConfig.FALLBACK_REVOCATION_ENDPOINT
        )
    }
}
