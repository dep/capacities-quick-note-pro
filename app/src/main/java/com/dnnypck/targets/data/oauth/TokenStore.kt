package com.dnnypck.capacitiesquicknotepro.data.oauth

import android.content.SharedPreferences
import com.dnnypck.targets.data.model.OAuthConnection
import com.dnnypck.targets.data.model.PendingOAuthSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TokenStore(
    private val prefs: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun get(spaceId: String): OAuthConnection? {
        return getAll().firstOrNull { it.spaceId == spaceId }
    }

    fun getAll(): List<OAuthConnection> {
        val raw = prefs.getString(KEY_CONNECTIONS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<OAuthConnection>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(connection: OAuthConnection) {
        val updated = getAll().filterNot { it.spaceId == connection.spaceId } + connection
        persist(updated)
    }

    fun replaceAll(connections: List<OAuthConnection>) {
        persist(connections)
    }

    fun remove(spaceId: String) {
        persist(getAll().filterNot { it.spaceId == spaceId })
    }

    fun clear() {
        persist(emptyList())
        clearPendingSession()
    }

    fun hasAnyConnection(): Boolean = getAll().isNotEmpty()

    fun savePendingSession(session: PendingOAuthSession) {
        prefs.edit().putString(KEY_PENDING_SESSION, json.encodeToString(session)).apply()
    }

    fun getPendingSession(): PendingOAuthSession? {
        val raw = prefs.getString(KEY_PENDING_SESSION, null) ?: return null
        return try {
            json.decodeFromString<PendingOAuthSession>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun clearPendingSession() {
        prefs.edit().remove(KEY_PENDING_SESSION).apply()
    }

    private fun persist(connections: List<OAuthConnection>) {
        prefs.edit().putString(KEY_CONNECTIONS, json.encodeToString(connections)).apply()
    }

    companion object {
        private const val KEY_CONNECTIONS = "oauth_connections_json"
        private const val KEY_PENDING_SESSION = "oauth_pending_session_json"
    }
}
