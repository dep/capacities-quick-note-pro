package com.dnnypck.capacitiesquicknotepro.data.network

import com.dnnypck.capacitiesquicknotepro.data.oauth.OAuthConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CapacitiesApi(
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    suspend fun getSpace(accessToken: String): Result<SpaceInfo> = withContext(Dispatchers.IO) {
        request("GET", "$BASE_URL/space", accessToken) { body ->
            json.decodeFromString<SpaceInfo>(body)
        }
    }

    suspend fun appendToDailyNote(accessToken: String, markdown: String): Result<Unit> {
        val payload = json.encodeToString(DailyNoteAppendRequest(markdown = markdown))
        return postJson("$BASE_URL/blocks/daily-note/append", accessToken, payload)
    }

    suspend fun createFromUrl(accessToken: String, url: String): Result<Unit> {
        val payload = json.encodeToString(CreateUrlObjectRequest(url = url))
        return postJson("$BASE_URL/object/url", accessToken, payload)
    }

    private suspend fun postJson(
        url: String,
        accessToken: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = KtorHttpClient.client.post(url) {
                header("Authorization", "Bearer $accessToken")
                header("X-Capacities-Api-Version", OAuthConfig.apiVersion)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(
                        ApiErrors.http(
                            response.status.value,
                            response.status.description,
                            response.bodyAsText()
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrors.network(e), e))
        }
    }

    private suspend fun <T> request(
        method: String,
        url: String,
        accessToken: String,
        parse: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val response = when (method) {
                "GET" -> KtorHttpClient.client.get(url) {
                    header("Authorization", "Bearer $accessToken")
                    header("X-Capacities-Api-Version", OAuthConfig.apiVersion)
                }
                else -> error("Unsupported method $method")
            }
            val body = response.bodyAsText()
            if (response.status.isSuccess()) {
                Result.success(parse(body.ifBlank { "{}" }))
            } else {
                Result.failure(
                    Exception(ApiErrors.http(response.status.value, response.status.description, body))
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrors.network(e), e))
        }
    }

    companion object {
        const val BASE_URL = "https://api.capacities.io"
    }
}

@Serializable
data class SpaceInfo(
    val id: String,
    val title: String = ""
)

@Serializable
data class DailyNoteAppendRequest(
    val markdown: String
)

@Serializable
data class CreateUrlObjectRequest(
    val url: String
)
