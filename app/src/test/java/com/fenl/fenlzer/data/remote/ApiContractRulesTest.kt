package com.fenl.fenlzer.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiContractRulesTest {
    @Test
    fun terminalSuccessIncludesTransferConfirmedAndCompleted() {
        assertTrue(ApiJobState.isTerminalSuccess(ApiJobState.TRANSFER_CONFIRMED))
        assertTrue(ApiJobState.isTerminalSuccess(ApiJobState.COMPLETED))
        assertFalse(ApiJobState.isTerminalSuccess(ApiJobState.READY_FOR_TRANSFER))
    }

    @Test
    fun runningStatesIncludePreciseAndGenericApiStates() {
        assertTrue(ApiJobState.isRunning(ApiJobState.DOWNLOADING_METADATA))
        assertTrue(ApiJobState.isRunning(ApiJobState.DOWNLOADING))
        assertTrue(ApiJobState.isRunning(ApiJobState.POST_PROCESSING))
        assertTrue(ApiJobState.isRunning(ApiJobState.PROCESSING))
        assertTrue(ApiJobState.isRunning(ApiJobState.RUNNING))
        assertFalse(ApiJobState.isRunning(ApiJobState.READY_FOR_TRANSFER))
    }

    @Test
    fun idempotencyKeyUsesFenlzerPrefix() {
        assertTrue(IdempotencyKeyFactory.create().startsWith("fenlzer_"))
    }

    @Test
    fun baseUrlNormalizationAcceptsVersionedAndUnversionedInputs() {
        assertEquals(
            "https://api.example.com/",
            FenlzerApiFactory.normalizeBaseUrl("https://api.example.com/v1")
        )
        assertEquals(
            "https://api.example.com/",
            FenlzerApiFactory.normalizeBaseUrl("https://api.example.com/")
        )
    }

    @Test
    fun sanitizerRedactsToken() {
        val sanitized = ApiDiagnosticsSanitizer.sanitize(
            message = "Authorization failed for token secret-token",
            token = "secret-token"
        )

        assertFalse(sanitized!!.contains("secret-token"))
        assertTrue(sanitized.contains("[redacted]"))
    }
}
