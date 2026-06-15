package com.fenl.fenlzer.importing

sealed interface ImportExecutionResult {
    data object Completed : ImportExecutionResult
    data object Cancelled : ImportExecutionResult

    data class RetryableFailure(
        val message: String?
    ) : ImportExecutionResult

    data class TerminalFailure(
        val message: String?
    ) : ImportExecutionResult
}

object ImportRetryPolicy {
    fun canRetry(attemptCount: Int, maxAttempts: Int): Boolean =
        attemptCount < maxAttempts
}
