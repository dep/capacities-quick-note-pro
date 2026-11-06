package com.dnnypck.capacitiesquicknotepro.data.network

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiErrors {
    fun http(status: Int, description: String, responseBody: String): String {
        val explanation = when (status) {
            400 -> "Bad request (400). Capacities rejected the note. Try sending it again, or reconnect this space in Settings."
            401 -> "Unauthorized (401). Your Capacities sign-in expired or was revoked. Open Settings and tap Connect to Capacities."
            403 -> "Forbidden (403). This connection doesn't have permission to write to the space. Reconnect in Settings and grant write access."
            404 -> "Not found (404). The connected space may no longer exist. Reconnect it in Settings."
            413 -> "Note too large (413). Try sending a shorter note."
            429 -> "Rate limited (429). You've made too many requests. Wait a moment and try again."
            in 500..599 -> "Capacities server error ($status). This is a problem on Capacities' side, not your settings. Please try again in a little while."
            else -> "Request failed ($status ${description.ifBlank { "" }}).".trim()
        }

        return buildString {
            append(explanation)
            val trimmedBody = responseBody.trim()
            if (trimmedBody.isNotEmpty()) {
                append("\n\nServer response: ")
                append(trimmedBody)
            }
        }
    }

    fun network(e: Throwable): String {
        return when (e) {
            is UnknownHostException ->
                "Can't reach Capacities. Check your internet connection and try again."
            is ConnectException ->
                "Couldn't connect to Capacities. Check your internet connection and try again."
            is SocketTimeoutException ->
                "The request timed out. Your connection may be slow — please try again."
            else ->
                "Something went wrong while talking to Capacities: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun isUnauthorized(error: Throwable?): Boolean {
        val message = error?.message ?: return false
        return message.contains("Unauthorized (401)") || message.contains("(401)")
    }
}
