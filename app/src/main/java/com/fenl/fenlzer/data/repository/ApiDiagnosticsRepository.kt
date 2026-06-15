package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.local.dao.ApiDiagnosticDao
import com.fenl.fenlzer.data.local.entity.ApiDiagnosticEntryEntity
import com.fenl.fenlzer.data.remote.ApiDiagnosticsSanitizer
import com.fenl.fenlzer.data.remote.RemoteDiagnosticsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

enum class ApiDiagnosticSource {
    LOCAL,
    SERVER
}

data class ApiDiagnosticItem(
    val diagnosticId: String,
    val source: ApiDiagnosticSource,
    val requestId: String?,
    val endpoint: String,
    val method: String,
    val startedAt: Long,
    val durationMs: Long,
    val statusCode: Int?,
    val success: Boolean,
    val errorCode: String?,
    val sanitizedMessage: String?
)

data class ServerDiagnosticsResult(
    val entries: List<ApiDiagnosticItem>,
    val errorMessage: String? = null
)

class ApiDiagnosticsRepository(
    private val localDao: ApiDiagnosticDao,
    private val remoteSource: RemoteDiagnosticsSource
) {
    fun observeLocal(limit: Int = 500): Flow<List<ApiDiagnosticItem>> =
        localDao.observeRecent(limit).map { entries -> entries.map(ApiDiagnosticEntryEntity::toItem) }

    suspend fun loadServer(limit: Int = 100): ServerDiagnosticsResult = runCatching {
        remoteSource.recentDiagnostics(limit = limit).entries.mapIndexed { index, entry ->
            ApiDiagnosticItem(
                diagnosticId = "server_${entry.requestId}_$index",
                source = ApiDiagnosticSource.SERVER,
                requestId = entry.requestId,
                endpoint = entry.endpoint,
                method = entry.method,
                startedAt = runCatching { Instant.parse(entry.createdAt).toEpochMilli() }
                    .getOrDefault(0L),
                durationMs = entry.durationMs.toLong(),
                statusCode = entry.statusCode,
                success = entry.success,
                errorCode = entry.errorCode,
                sanitizedMessage = ApiDiagnosticsSanitizer.sanitize(
                    entry.sanitizedMessage,
                    remoteSource.savedToken()
                )
            )
        }
    }.fold(
        onSuccess = { ServerDiagnosticsResult(entries = it) },
        onFailure = { throwable ->
            ServerDiagnosticsResult(
                entries = emptyList(),
                errorMessage = ApiDiagnosticsSanitizer.sanitize(
                    throwable.localizedMessage ?: "Server diagnostics are unavailable.",
                    remoteSource.savedToken()
                )
            )
        }
    )

    suspend fun clearLocal() = localDao.clearAll()
}

private fun ApiDiagnosticEntryEntity.toItem() = ApiDiagnosticItem(
    diagnosticId = diagnosticId,
    source = ApiDiagnosticSource.LOCAL,
    requestId = requestId,
    endpoint = endpoint,
    method = method,
    startedAt = startedAt,
    durationMs = durationMs,
    statusCode = statusCode,
    success = success,
    errorCode = errorCode,
    sanitizedMessage = sanitizedMessage
)
