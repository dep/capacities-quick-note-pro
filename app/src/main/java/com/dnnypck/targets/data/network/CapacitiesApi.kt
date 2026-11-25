package com.dnnypck.capacitiesquicknotepro.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DailyNoteAppendRequest(
    val markdown: String
)

@Serializable
data class CreateObjectFromUrlRequest(
    val url: String
)

object CapacitiesApi {
    const val DAILY_NOTE_ENDPOINT = "https://api.capacities.io/blocks/daily-note/append"
    const val WEBLINK_ENDPOINT = "https://api.capacities.io/object/url"

    private val json = Json

    suspend fun appendToDailyNote(apiToken: String, markdown: String): Result<String> =
        post(DAILY_NOTE_ENDPOINT, apiToken, json.encodeToString(DailyNoteAppendRequest(markdown)))

    suspend fun saveWeblink(apiToken: String, url: String): Result<String> =
        post(WEBLINK_ENDPOINT, apiToken, json.encodeToString(CreateObjectFromUrlRequest(url)))

    private suspend fun post(url: String, apiToken: String, body: String): Result<String> {
        val result = postToTarget(
            url = url,
            headers = mapOf(
                "Authorization" to "Bearer $apiToken",
                "Content-Type" to "application/json"
            ),
            body = body
        )
        return result.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                val message = error.message ?: ""
                if (message.contains("(401)") || message.contains("(403)") ||
                    message.contains("HTTP 401") || message.contains("HTTP 403")
                ) {
                    Result.failure(
                        Exception(
                            "$message\n\nHint: The Capacities API changed — old API keys no longer work. " +
                                "Create a per-space token (cap-api-…) in Capacities under Settings → Capacities API."
                        )
                    )
                } else {
                    Result.failure(error)
                }
            }
        )
    }
}
