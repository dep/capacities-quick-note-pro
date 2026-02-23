package com.dnnypck.capacitiesquicknotepro.data.network

import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun postToTarget(
    url: String,
    headers: Map<String, String>,
    body: String
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val response = KtorHttpClient.client.post(url) {
            headers {
                headers.forEach { (key, value) ->
                    append(key, value)
                }
            }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        if (response.status.value in 200..299) {
            Result.success(response.bodyAsText())
        } else {
            val errorBody = response.bodyAsText()
            Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}\n\nResponse: $errorBody"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
