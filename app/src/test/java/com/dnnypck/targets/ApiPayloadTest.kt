package com.dnnypck.capacitiesquicknotepro

import com.dnnypck.capacitiesquicknotepro.data.network.CreateUrlObjectRequest
import com.dnnypck.capacitiesquicknotepro.data.network.DailyNoteAppendRequest
import com.dnnypck.capacitiesquicknotepro.data.network.ApiErrors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiPayloadTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun dailyNoteRequestEscapesQuotesAndNewlines() {
        val body = json.encodeToString(
            DailyNoteAppendRequest(markdown = "Hello \"world\"\nNext line")
        )
        assertEquals("""{"markdown":"Hello \"world\"\nNext line"}""", body)
    }

    @Test
    fun weblinkRequestUsesUrlField() {
        val body = json.encodeToString(CreateUrlObjectRequest(url = "https://example.com/a?b=1"))
        assertEquals("""{"url":"https://example.com/a?b=1"}""", body)
    }

    @Test
    fun unauthorizedDetection() {
        assertTrue(ApiErrors.isUnauthorized(Exception("Unauthorized (401). Please reconnect.")))
        assertTrue(ApiErrors.http(401, "Unauthorized", "").contains("Connect to Capacities"))
        assertTrue(ApiErrors.http(429, "Too Many Requests", "slow down").contains("Server response"))
    }
}
