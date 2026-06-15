package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.local.dao.ApiDiagnosticDao
import com.fenl.fenlzer.data.local.entity.ApiDiagnosticEntryEntity
import com.fenl.fenlzer.data.remote.DiagnosticsEntry
import com.fenl.fenlzer.data.remote.DiagnosticsRecentData
import com.fenl.fenlzer.data.remote.RemoteDiagnosticsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiDiagnosticsRepositoryTest {
    @Test
    fun combinesObservableLocalEntriesWithSanitizedServerEntries() = runTest {
        val localDao = FakeDiagnosticDao(
            ApiDiagnosticEntryEntity(
                diagnosticId = "local-1",
                requestId = "request-local",
                endpoint = "/v1/health",
                method = "GET",
                startedAt = 100L,
                durationMs = 12L,
                statusCode = 200,
                success = true
            )
        )
        val remote = FakeRemoteDiagnosticsSource(
            token = "secret-token",
            response = {
                DiagnosticsRecentData(
                    entries = listOf(
                        DiagnosticsEntry(
                            requestId = "request-server",
                            endpoint = "/v1/youtube/search",
                            method = "POST",
                            statusCode = 401,
                            durationMs = 25,
                            success = false,
                            errorCode = "UNAUTHORIZED",
                            sanitizedMessage = "Token secret-token was rejected.",
                            createdAt = "2026-06-15T10:15:30Z"
                        )
                    )
                )
            }
        )
        val repository = ApiDiagnosticsRepository(localDao, remote)

        val local = repository.observeLocal().first()
        val server = repository.loadServer()

        assertEquals(1, local.size)
        assertEquals(ApiDiagnosticSource.SERVER, server.entries.single().source)
        assertFalse(server.entries.single().sanitizedMessage!!.contains("secret-token"))
        assertEquals(null, server.errorMessage)
    }

    @Test
    fun offlineServerFailureIsReturnedWithoutDiscardingLocalDiagnostics() = runTest {
        val localDao = FakeDiagnosticDao()
        val repository = ApiDiagnosticsRepository(
            localDao = localDao,
            remoteSource = FakeRemoteDiagnosticsSource(
                token = "secret-token",
                response = { error("Network failed for secret-token") }
            )
        )

        val result = repository.loadServer()

        assertTrue(result.entries.isEmpty())
        assertTrue(result.errorMessage!!.contains("[redacted]"))
        assertFalse(result.errorMessage.contains("secret-token"))
    }

    private class FakeDiagnosticDao(vararg initial: ApiDiagnosticEntryEntity) : ApiDiagnosticDao {
        val entries = MutableStateFlow(initial.toList())

        override suspend fun insert(entry: ApiDiagnosticEntryEntity) {
            entries.value = entries.value + entry
        }

        override fun observeRecent(limit: Int): Flow<List<ApiDiagnosticEntryEntity>> = entries

        override suspend fun trimToLatest(limit: Int) {
            entries.value = entries.value.takeLast(limit)
        }

        override suspend fun clearAll() {
            entries.value = emptyList()
        }
    }

    private class FakeRemoteDiagnosticsSource(
        private val token: String,
        private val response: suspend () -> DiagnosticsRecentData
    ) : RemoteDiagnosticsSource {
        override fun savedToken(): String = token

        override suspend fun recentDiagnostics(limit: Int, since: String?): DiagnosticsRecentData =
            response()
    }
}
