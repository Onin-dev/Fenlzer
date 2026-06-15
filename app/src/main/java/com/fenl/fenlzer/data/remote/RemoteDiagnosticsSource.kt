package com.fenl.fenlzer.data.remote

interface RemoteDiagnosticsSource {
    fun savedToken(): String

    suspend fun recentDiagnostics(
        limit: Int = 100,
        since: String? = null
    ): DiagnosticsRecentData
}
