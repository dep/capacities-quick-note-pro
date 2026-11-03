package com.dnnypck.capacitiesquicknotepro.data.oauth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.dnnypck.capacitiesquicknotepro.data.network.CapacitiesApi
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager
import com.dnnypck.targets.data.model.PendingOAuthSession
import com.dnnypck.targets.data.model.Space

class OAuthCoordinator(
    private val preferencesManager: PreferencesManager,
    private val oauthClient: OAuthClient = OAuthClient(),
    private val api: CapacitiesApi = CapacitiesApi()
) {
    suspend fun startAuthorization(context: Context): Result<Unit> {
        if (!OAuthConfig.isConfigured) {
            return Result.failure(
                Exception(
                    "OAuth isn't configured yet. Register this app with Capacities " +
                        "(team@capacities.io) and set CAPACITIES_CLIENT_ID in local.properties. " +
                        "See README.md for the exact fields to send."
                )
            )
        }

        return try {
            val endpoints = oauthClient.discoverEndpoints()
            val pkce = Pkce.generate()
            val state = oauthClient.generateState()
            preferencesManager.tokenStore.savePendingSession(
                PendingOAuthSession(state = state, codeVerifier = pkce.verifier)
            )
            val url = oauthClient.createAuthorizationUrl(endpoints, pkce, state)
            launchBrowser(context, url)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                Exception("Couldn't open Capacities sign-in: ${e.message ?: e.javaClass.simpleName}", e)
            )
        }
    }

    suspend fun completeAuthorization(uri: Uri?): Result<Space> {
        if (uri == null) {
            return Result.failure(Exception("Capacities sign-in didn't return a redirect."))
        }

        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val description = uri.getQueryParameter("error_description").orEmpty()
            preferencesManager.tokenStore.clearPendingSession()
            return Result.failure(
                Exception(
                    buildString {
                        append("Capacities denied sign-in ($error).")
                        if (description.isNotBlank()) {
                            append(" ")
                            append(description)
                        }
                    }
                )
            )
        }

        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val pending = preferencesManager.tokenStore.getPendingSession()
        if (pending == null) {
            return Result.failure(Exception("No sign-in is in progress. Tap Connect to Capacities and try again."))
        }
        if (System.currentTimeMillis() - pending.createdAtEpochMs > OAuthConfig.PENDING_SESSION_TTL_MS) {
            preferencesManager.tokenStore.clearPendingSession()
            return Result.failure(Exception("Sign-in timed out. Tap Connect to Capacities and try again."))
        }
        if (state.isNullOrBlank() || state != pending.state) {
            preferencesManager.tokenStore.clearPendingSession()
            return Result.failure(Exception("Sign-in could not be verified (state mismatch). Please try again."))
        }
        if (code.isNullOrBlank()) {
            preferencesManager.tokenStore.clearPendingSession()
            return Result.failure(Exception("Capacities didn't return an authorization code. Please try again."))
        }

        val endpoints = oauthClient.discoverEndpoints()
        val tokens = oauthClient.exchangeCode(endpoints, code, pending.codeVerifier)
            .getOrElse {
                preferencesManager.tokenStore.clearPendingSession()
                return Result.failure(it)
            }
        val spaceInfo = api.getSpace(tokens.accessToken).getOrElse {
            preferencesManager.tokenStore.clearPendingSession()
            return Result.failure(
                Exception("Signed in, but couldn't read the selected space. ${it.message ?: ""}".trim())
            )
        }

        val connection = tokens.copy(spaceId = spaceInfo.id)
        preferencesManager.tokenStore.save(connection)
        preferencesManager.tokenStore.clearPendingSession()
        preferencesManager.clearLegacyApiKey()
        val space = preferencesManager.upsertConnectedSpace(
            Space(id = spaceInfo.id, nickname = spaceInfo.title.ifBlank { null })
        )
        preferencesManager.saveSelectedSpaceId(space.id)
        return Result.success(space)
    }

    suspend fun disconnect(spaceId: String) {
        val connection = preferencesManager.tokenStore.get(spaceId)
        if (connection != null) {
            oauthClient.revoke(oauthClient.discoverEndpoints(), connection)
            preferencesManager.tokenStore.remove(spaceId)
        }
        preferencesManager.removeSpace(spaceId)
    }

    suspend fun disconnectAll() {
        preferencesManager.getSpaces().map { it.id }.forEach { disconnect(it) }
        preferencesManager.tokenStore.clear()
    }

    private fun launchBrowser(context: Context, url: String) {
        val uri = Uri.parse(url)
        val customTabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        if (context !is android.app.Activity) {
            customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            customTabs.launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_VIEW, uri)
            if (context !is android.app.Activity) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
