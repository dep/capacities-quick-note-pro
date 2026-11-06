package com.dnnypck.capacitiesquicknotepro.data.notes

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class DeliveryOutcome {
    data object Acknowledged : DeliveryOutcome()
    data class Failed(val message: String) : DeliveryOutcome()
    data class Uncertain(val message: String) : DeliveryOutcome()
}

object DeliveryClassifier {
    fun fromResult(result: Result<Unit>): DeliveryOutcome {
        result.getOrNull()?.let { return DeliveryOutcome.Acknowledged }
        val error = result.exceptionOrNull() ?: return DeliveryOutcome.Uncertain("Unknown send result")
        return fromThrowable(error)
    }

    fun fromThrowable(error: Throwable): DeliveryOutcome {
        val chain = generateSequence(error) { it.cause }.toList()
        if (chain.any { it is SocketTimeoutException }) {
            return DeliveryOutcome.Uncertain(messageOf(error))
        }
        if (chain.any { it is UnknownHostException || it is ConnectException }) {
            return DeliveryOutcome.Failed(messageOf(error))
        }
        val message = messageOf(error)
        val status = httpStatus(message)
        return when (status) {
            401, 403, 400, 404, 413, 429 -> DeliveryOutcome.Failed(message)
            in 500..599, 408 -> DeliveryOutcome.Uncertain(message)
            null -> {
                if (message.contains("timed out", ignoreCase = true)) {
                    DeliveryOutcome.Uncertain(message)
                } else {
                    DeliveryOutcome.Uncertain(message)
                }
            }
            else -> DeliveryOutcome.Uncertain(message)
        }
    }

    private fun messageOf(error: Throwable): String {
        return error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }

    private fun httpStatus(message: String): Int? {
        val match = Regex("\\((\\d{3})\\)").find(message)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}
