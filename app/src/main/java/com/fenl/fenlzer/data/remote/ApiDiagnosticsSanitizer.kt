package com.fenl.fenlzer.data.remote

object ApiDiagnosticsSanitizer {
    private const val REDACTED = "[redacted]"

    fun sanitize(message: String?, token: String?): String? {
        if (message == null) return null
        val trimmedToken = token?.trim().orEmpty()
        return if (trimmedToken.isBlank()) {
            message
        } else {
            message.replace(trimmedToken, REDACTED)
        }
    }
}
