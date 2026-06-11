package com.fenl.fenlzer.data.remote

import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.entity.ApiDiagnosticEntryEntity
import com.fenl.fenlzer.data.settings.ApiTokenStore
import com.fenl.fenlzer.data.settings.AppSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class ApiRepository(
    private val settingsRepository: AppSettingsRepository,
    private val tokenStore: ApiTokenStore,
    private val diagnosticRecorder: ApiDiagnosticRecorder,
    private val dispatchers: FenlzerDispatchers,
    private val serviceFactory: (String, () -> String) -> FenlzerApiService = FenlzerApiFactory::create,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun savedToken(): String = tokenStore.getToken().orEmpty()

    fun saveApiSettings(baseUrl: String, token: String) {
        settingsRepository.setApiBaseUrl(baseUrl.trim())
        if (token.isBlank()) {
            tokenStore.clearToken()
        } else {
            tokenStore.saveToken(token.trim())
        }
    }

    suspend fun searchYoutube(query: String, limit: Int = 5): YoutubeSearchData =
        callConfiguredApi(
            endpoint = "/v1/youtube/search",
            method = "POST"
        ) { service ->
            service.searchYoutube(SearchRequest(query = query, limit = limit))
        }

    suspend fun createYoutubeDownload(
        request: CreateDownloadRequest,
        idempotencyKey: String = IdempotencyKeyFactory.create()
    ): DownloadCreateData =
        callConfiguredApi(
            endpoint = "/v1/downloads",
            method = "POST"
        ) { service ->
            service.createDownload(
                idempotencyKey = idempotencyKey,
                request = request
            )
        }

    suspend fun createPlaylistPreview(playlistUrl: String): PlaylistPreviewData =
        callConfiguredApi(
            endpoint = "/v1/youtube/playlists/preview",
            method = "POST"
        ) { service ->
            service.createPlaylistPreview(PlaylistPreviewRequest(playlistUrl = playlistUrl))
        }

    suspend fun getPlaylistPreview(previewId: String): PlaylistPreviewData =
        callConfiguredApi(
            endpoint = "/v1/youtube/playlists/preview/$previewId",
            method = "GET"
        ) { service ->
            service.getPlaylistPreview(previewId)
        }

    suspend fun createDownloadBatch(
        request: BatchDownloadRequest,
        idempotencyKey: String = IdempotencyKeyFactory.create()
    ): BatchDownloadData =
        callConfiguredApi(
            endpoint = "/v1/downloads/batch",
            method = "POST"
        ) { service ->
            service.createDownloadBatch(
                idempotencyKey = idempotencyKey,
                request = request
            )
        }

    suspend fun getJob(jobId: String): JobResponseData =
        callConfiguredApi(
            endpoint = "/v1/jobs/$jobId",
            method = "GET"
        ) { service ->
            service.getJob(jobId)
        }

    suspend fun getManyJobStatuses(apiJobIds: List<String>): JobsStatusData =
        callConfiguredApi(
            endpoint = "/v1/jobs/status",
            method = "POST"
        ) { service ->
            service.getManyJobStatuses(JobStatusRequest(apiJobIds = apiJobIds))
        }

    suspend fun confirmJobFile(
        jobId: String,
        request: ConfirmFileRequest
    ): ConfirmFileData =
        callConfiguredApi(
            endpoint = "/v1/jobs/$jobId/file/confirm",
            method = "POST"
        ) { service ->
            service.confirmJobFile(jobId, request)
        }

    suspend fun cancelJob(jobId: String, reason: String): CancelJobData =
        callConfiguredApi(
            endpoint = "/v1/jobs/$jobId/cancel",
            method = "POST"
        ) { service ->
            service.cancelJob(jobId, CancelRequest(reason = reason))
        }

    suspend fun retryJob(
        jobId: String,
        clientRetryId: String = IdempotencyKeyFactory.create()
    ): RetryJobData =
        callConfiguredApi(
            endpoint = "/v1/jobs/$jobId/retry",
            method = "POST"
        ) { service ->
            service.retryJob(
                jobId = jobId,
                idempotencyKey = clientRetryId,
                request = RetryRequest(clientRetryId = clientRetryId)
            )
        }

    suspend fun reorderJobs(orderedApiJobIds: List<String>): ReorderJobsData =
        callConfiguredApi(
            endpoint = "/v1/jobs/reorder",
            method = "POST"
        ) { service ->
            service.reorderJobs(
                idempotencyKey = IdempotencyKeyFactory.create(),
                request = ReorderRequest(orderedApiJobIds = orderedApiJobIds)
            )
        }

    suspend fun resolveStream(request: StreamResolveRequest): StreamResolveData =
        callConfiguredApi(
            endpoint = "/v1/stream/resolve",
            method = "POST"
        ) { service ->
            service.resolveStream(request)
        }

    suspend fun createHistoryUpload(
        request: CreateHistoryUploadRequest,
        idempotencyKey: String = IdempotencyKeyFactory.create()
    ): HistoryUploadCreateData =
        callConfiguredApi(
            endpoint = "/v1/discover/history/uploads",
            method = "POST"
        ) { service ->
            service.createHistoryUpload(
                idempotencyKey = idempotencyKey,
                request = request
            )
        }

    suspend fun uploadHistoryChunk(
        uploadId: String,
        chunkIndex: Int,
        chunkCount: Int,
        compressedBody: ByteArray
    ): HistoryChunkData =
        callConfiguredApi(
            endpoint = "/v1/discover/history/uploads/$uploadId/chunks",
            method = "POST"
        ) { service ->
            service.uploadHistoryChunk(
                uploadId = uploadId,
                contentEncoding = "zstd",
                uploadIdHeader = uploadId,
                chunkIndex = chunkIndex,
                chunkCount = chunkCount,
                compressedBody = compressedBody.toRequestBody()
            )
        }

    suspend fun completeHistoryUpload(
        uploadId: String,
        request: CompleteUploadRequest,
        idempotencyKey: String = IdempotencyKeyFactory.create()
    ): HistoryCompleteData =
        callConfiguredApi(
            endpoint = "/v1/discover/history/uploads/$uploadId/complete",
            method = "POST"
        ) { service ->
            service.completeHistoryUpload(
                uploadId = uploadId,
                idempotencyKey = idempotencyKey,
                request = request
            )
        }

    suspend fun refreshDiscover(
        request: DiscoverRefreshRequest,
        idempotencyKey: String = IdempotencyKeyFactory.create()
    ): DiscoverRefreshData =
        callConfiguredApi(
            endpoint = "/v1/discover/refresh",
            method = "POST"
        ) { service ->
            service.refreshDiscover(
                idempotencyKey = idempotencyKey,
                request = request
            )
        }

    suspend fun refreshDiscoverBroader(
        request: DiscoverRefreshRequest,
        idempotencyKey: String = IdempotencyKeyFactory.create()
    ): DiscoverRefreshData =
        callConfiguredApi(
            endpoint = "/v1/discover/refresh-broader",
            method = "POST"
        ) { service ->
            service.refreshDiscoverBroader(
                idempotencyKey = idempotencyKey,
                request = request
            )
        }

    suspend fun getJobFile(
        jobId: String,
        range: String? = null
    ): Response<ResponseBody> = withContext(dispatchers.io) {
        val connection = configuredApi()
        val startedAt = nowMillis()

        try {
            val response = connection.service.getJobFile(jobId = jobId, range = range)
            val durationMs = nowMillis() - startedAt
            val requestId = response.headers()["X-Request-Id"]

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = requestId,
                    endpoint = "/v1/jobs/$jobId/file",
                    method = "GET",
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = response.code(),
                    success = response.isSuccessful,
                    errorCode = if (response.isSuccessful) null else "HTTP_${response.code()}",
                    sanitizedMessage = if (response.isSuccessful) {
                        null
                    } else {
                        "The API returned HTTP ${response.code()} while transferring the file."
                    },
                    metadataJson = null
                )
            )

            if (!response.isSuccessful) {
                throw ApiOperationException(
                    message = "The API could not transfer the completed file.",
                    errorCode = "HTTP_${response.code()}",
                    retryable = response.code() in 500..599,
                    statusCode = response.code(),
                    requestId = requestId
                )
            }

            response
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: ApiOperationException) {
            throw exception
        } catch (ioException: IOException) {
            val durationMs = nowMillis() - startedAt
            val message = ApiDiagnosticsSanitizer.sanitize(
                ioException.message ?: "Fenlzer could not reach the API.",
                connection.token
            )

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = null,
                    endpoint = "/v1/jobs/$jobId/file",
                    method = "GET",
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = null,
                    success = false,
                    errorCode = "NETWORK_ERROR",
                    sanitizedMessage = message,
                    metadataJson = null
                )
            )

            throw ApiOperationException(
                message = message ?: "Fenlzer could not reach the API.",
                errorCode = "NETWORK_ERROR",
                retryable = true
            )
        } catch (throwable: Throwable) {
            val durationMs = nowMillis() - startedAt
            val message = ApiDiagnosticsSanitizer.sanitize(
                throwable.message ?: "Fenlzer could not transfer the completed file.",
                connection.token
            )

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = null,
                    endpoint = "/v1/jobs/$jobId/file",
                    method = "GET",
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = null,
                    success = false,
                    errorCode = "UNKNOWN_ERROR",
                    sanitizedMessage = message,
                    metadataJson = null
                )
            )

            throw ApiOperationException(
                message = message ?: "Fenlzer could not transfer the completed file.",
                errorCode = "UNKNOWN_ERROR",
                retryable = false
            )
        }
    }

    suspend fun testHealth(
        baseUrl: String,
        token: String
    ): ApiHealthCheckResult = withContext(dispatchers.io) {
        val normalizedBaseUrl = baseUrl.trim()
        val normalizedToken = token.trim()

        if (normalizedBaseUrl.isBlank()) {
            return@withContext ApiHealthCheckResult.Failure(
                message = "Enter an API base URL first.",
                errorCode = "MISSING_BASE_URL",
                retryable = false
            )
        }

        if (normalizedToken.isBlank()) {
            return@withContext ApiHealthCheckResult.Failure(
                message = "Enter an API token first.",
                errorCode = "MISSING_TOKEN",
                retryable = false
            )
        }

        val startedAt = nowMillis()
        try {
            val service = serviceFactory(normalizedBaseUrl) { normalizedToken }
            val response = service.health()
            val durationMs = nowMillis() - startedAt

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = response.requestId,
                    endpoint = "/v1/health",
                    method = "GET",
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = 200,
                    success = true,
                    errorCode = null,
                    sanitizedMessage = null,
                    metadataJson = null
                )
            )

            ApiHealthCheckResult.Success(
                requestId = response.requestId,
                durationMs = durationMs,
                health = response.data
            )
        } catch (httpException: HttpException) {
            val durationMs = nowMillis() - startedAt
            val parsedError = parseHttpError(httpException)
            val message = ApiDiagnosticsSanitizer.sanitize(
                parsedError?.error?.message ?: httpException.message(),
                normalizedToken
            )

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = parsedError?.requestId,
                    endpoint = "/v1/health",
                    method = "GET",
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = httpException.code(),
                    success = false,
                    errorCode = parsedError?.error?.code ?: "HTTP_${httpException.code()}",
                    sanitizedMessage = message,
                    metadataJson = null
                )
            )

            ApiHealthCheckResult.Failure(
                message = message ?: "The API returned HTTP ${httpException.code()}.",
                errorCode = parsedError?.error?.code ?: "HTTP_${httpException.code()}",
                retryable = parsedError?.error?.retryable ?: false,
                statusCode = httpException.code(),
                requestId = parsedError?.requestId
            )
        } catch (ioException: IOException) {
            val durationMs = nowMillis() - startedAt
            val message = ApiDiagnosticsSanitizer.sanitize(
                ioException.message ?: "Fenlzer could not reach the API.",
                normalizedToken
            )

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = null,
                    endpoint = "/v1/health",
                    method = "GET",
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = null,
                    success = false,
                    errorCode = "NETWORK_ERROR",
                    sanitizedMessage = message,
                    metadataJson = null
                )
            )

            ApiHealthCheckResult.Failure(
                message = message ?: "Fenlzer could not reach the API.",
                errorCode = "NETWORK_ERROR",
                retryable = true
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            val durationMs = nowMillis() - startedAt
            val message = ApiDiagnosticsSanitizer.sanitize(
                throwable.message ?: "Fenlzer could not test the API connection.",
                normalizedToken
            )

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = null,
                    endpoint = "/v1/health",
                    method = "GET",
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = null,
                    success = false,
                    errorCode = "UNKNOWN_ERROR",
                    sanitizedMessage = message,
                    metadataJson = null
                )
            )

            ApiHealthCheckResult.Failure(
                message = message ?: "Fenlzer could not test the API connection.",
                errorCode = "UNKNOWN_ERROR",
                retryable = false
            )
        }
    }

    private suspend fun <T> callConfiguredApi(
        endpoint: String,
        method: String,
        call: suspend (FenlzerApiService) -> ApiSuccess<T>
    ): T = withContext(dispatchers.io) {
        val connection = configuredApi()
        val startedAt = nowMillis()

        try {
            val response = call(connection.service)
            val durationMs = nowMillis() - startedAt

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = response.requestId,
                    endpoint = endpoint,
                    method = method,
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = 200,
                    success = true,
                    errorCode = null,
                    sanitizedMessage = null,
                    metadataJson = null
                )
            )

            response.data
        } catch (httpException: HttpException) {
            val durationMs = nowMillis() - startedAt
            val parsedError = parseHttpError(httpException)
            val message = ApiDiagnosticsSanitizer.sanitize(
                parsedError?.error?.message ?: httpException.message(),
                connection.token
            )

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = parsedError?.requestId,
                    endpoint = endpoint,
                    method = method,
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = httpException.code(),
                    success = false,
                    errorCode = parsedError?.error?.code ?: "HTTP_${httpException.code()}",
                    sanitizedMessage = message,
                    metadataJson = null
                )
            )

            throw ApiOperationException(
                message = message ?: "The API returned HTTP ${httpException.code()}.",
                errorCode = parsedError?.error?.code ?: "HTTP_${httpException.code()}",
                retryable = parsedError?.error?.retryable ?: false,
                statusCode = httpException.code(),
                requestId = parsedError?.requestId
            )
        } catch (ioException: IOException) {
            val durationMs = nowMillis() - startedAt
            val message = ApiDiagnosticsSanitizer.sanitize(
                ioException.message ?: "Fenlzer could not reach the API.",
                connection.token
            )

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = null,
                    endpoint = endpoint,
                    method = method,
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = null,
                    success = false,
                    errorCode = "NETWORK_ERROR",
                    sanitizedMessage = message,
                    metadataJson = null
                )
            )

            throw ApiOperationException(
                message = message ?: "Fenlzer could not reach the API.",
                errorCode = "NETWORK_ERROR",
                retryable = true
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            val durationMs = nowMillis() - startedAt
            val message = ApiDiagnosticsSanitizer.sanitize(
                throwable.message ?: "Fenlzer could not complete the API request.",
                connection.token
            )

            diagnosticRecorder.record(
                ApiDiagnosticEntryEntity(
                    diagnosticId = UUID.randomUUID().toString(),
                    requestId = null,
                    endpoint = endpoint,
                    method = method,
                    startedAt = startedAt,
                    durationMs = durationMs,
                    statusCode = null,
                    success = false,
                    errorCode = "UNKNOWN_ERROR",
                    sanitizedMessage = message,
                    metadataJson = null
                )
            )

            throw ApiOperationException(
                message = message ?: "Fenlzer could not complete the API request.",
                errorCode = "UNKNOWN_ERROR",
                retryable = false
            )
        }
    }

    private fun configuredApi(): ConfiguredApi {
        val normalizedBaseUrl = settingsRepository.settings.value.apiBaseUrl.trim()
        val normalizedToken = tokenStore.getToken().orEmpty().trim()

        if (normalizedBaseUrl.isBlank()) {
            throw ApiOperationException(
                message = "Configure the API base URL in Settings before searching YouTube.",
                errorCode = "MISSING_BASE_URL",
                retryable = false
            )
        }

        if (normalizedToken.isBlank()) {
            throw ApiOperationException(
                message = "Configure the API token in Settings before searching YouTube.",
                errorCode = "MISSING_TOKEN",
                retryable = false
            )
        }

        return ConfiguredApi(
            service = serviceFactory(normalizedBaseUrl) { normalizedToken },
            token = normalizedToken
        )
    }

    private fun parseHttpError(httpException: HttpException): ApiErrorEnvelope? {
        val body = httpException.response()?.errorBody()?.string() ?: return null
        return runCatching {
            FenlzerApiFactory.json.decodeFromString<ApiErrorEnvelope>(body)
        }.getOrNull()
    }

    private data class ConfiguredApi(
        val service: FenlzerApiService,
        val token: String
    )
}

class ApiOperationException(
    override val message: String,
    val errorCode: String,
    val retryable: Boolean,
    val statusCode: Int? = null,
    val requestId: String? = null
) : Exception(message)

sealed interface ApiHealthCheckResult {
    data class Success(
        val requestId: String,
        val durationMs: Long,
        val health: HealthData
    ) : ApiHealthCheckResult

    data class Failure(
        val message: String,
        val errorCode: String,
        val retryable: Boolean,
        val statusCode: Int? = null,
        val requestId: String? = null
    ) : ApiHealthCheckResult
}
