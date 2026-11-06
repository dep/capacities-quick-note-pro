package com.dnnypck.capacitiesquicknotepro.notes

import com.dnnypck.capacitiesquicknotepro.data.notes.DeliveryClassifier
import com.dnnypck.capacitiesquicknotepro.data.notes.DeliveryOutcome
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DeliveryClassifierTest {
    @Test
    fun successIsAcknowledgedNotVisibleGuarantee() {
        val outcome = DeliveryClassifier.fromResult(Result.success(Unit))
        assertTrue(outcome is DeliveryOutcome.Acknowledged)
    }

    @Test
    fun offlineBeforeReachIsFailedAndSafeToResend() {
        val outcome = DeliveryClassifier.fromResult(
            Result.failure(Exception("Can't reach Capacities", UnknownHostException("api.capacities.io")))
        )
        assertTrue(outcome is DeliveryOutcome.Failed)
    }

    @Test
    fun connectExceptionIsFailed() {
        val outcome = DeliveryClassifier.fromThrowable(ConnectException("refused"))
        assertTrue(outcome is DeliveryOutcome.Failed)
    }

    @Test
    fun timeoutIsUncertain() {
        val outcome = DeliveryClassifier.fromResult(
            Result.failure(Exception("The request timed out.", SocketTimeoutException("timeout")))
        )
        assertTrue(outcome is DeliveryOutcome.Uncertain)
    }

    @Test
    fun authExpiredIsFailed() {
        val outcome = DeliveryClassifier.fromResult(
            Result.failure(Exception("Unauthorized (401). Your Capacities sign-in expired."))
        )
        assertTrue(outcome is DeliveryOutcome.Failed)
    }

    @Test
    fun serverErrorIsUncertain() {
        val outcome = DeliveryClassifier.fromResult(
            Result.failure(Exception("Capacities server error (500)."))
        )
        assertTrue(outcome is DeliveryOutcome.Uncertain)
    }

    @Test
    fun validationErrorIsFailed() {
        val outcome = DeliveryClassifier.fromResult(
            Result.failure(Exception("Note too large (413)."))
        )
        assertTrue(outcome is DeliveryOutcome.Failed)
    }
}
