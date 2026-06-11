package com.fenl.fenlzer.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface FenlzerApiService {
    @GET("live")
    suspend fun live(): LiveResponse

    @GET("v1/health")
    suspend fun health(): ApiSuccess<HealthData>

    @POST("v1/youtube/search")
    suspend fun searchYoutube(
        @Body request: SearchRequest
    ): ApiSuccess<YoutubeSearchData>

    @POST("v1/youtube/playlists/preview")
    suspend fun createPlaylistPreview(
        @Body request: PlaylistPreviewRequest
    ): ApiSuccess<PlaylistPreviewData>

    @GET("v1/youtube/playlists/preview/{previewId}")
    suspend fun getPlaylistPreview(
        @Path("previewId") previewId: String
    ): ApiSuccess<PlaylistPreviewData>

    @POST("v1/downloads")
    suspend fun createDownload(
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Body request: CreateDownloadRequest
    ): ApiSuccess<DownloadCreateData>

    @POST("v1/downloads/batch")
    suspend fun createDownloadBatch(
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Body request: BatchDownloadRequest
    ): ApiSuccess<BatchDownloadData>

    @GET("v1/jobs/{jobId}")
    suspend fun getJob(
        @Path("jobId") jobId: String
    ): ApiSuccess<JobResponseData>

    @POST("v1/jobs/status")
    suspend fun getManyJobStatuses(
        @Body request: JobStatusRequest
    ): ApiSuccess<JobsStatusData>

    @Streaming
    @GET("v1/jobs/{jobId}/file")
    suspend fun getJobFile(
        @Path("jobId") jobId: String,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    @POST("v1/jobs/{jobId}/file/confirm")
    suspend fun confirmJobFile(
        @Path("jobId") jobId: String,
        @Body request: ConfirmFileRequest
    ): ApiSuccess<ConfirmFileData>

    @POST("v1/jobs/{jobId}/cancel")
    suspend fun cancelJob(
        @Path("jobId") jobId: String,
        @Body request: CancelRequest = CancelRequest()
    ): ApiSuccess<CancelJobData>

    @POST("v1/jobs/{jobId}/retry")
    suspend fun retryJob(
        @Path("jobId") jobId: String,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Body request: RetryRequest = RetryRequest()
    ): ApiSuccess<RetryJobData>

    @POST("v1/jobs/reorder")
    suspend fun reorderJobs(
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Body request: ReorderRequest
    ): ApiSuccess<ReorderJobsData>

    @POST("v1/stream/resolve")
    suspend fun resolveStream(
        @Body request: StreamResolveRequest
    ): ApiSuccess<StreamResolveData>

    @POST("v1/discover/history/uploads")
    suspend fun createHistoryUpload(
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Body request: CreateHistoryUploadRequest
    ): ApiSuccess<HistoryUploadCreateData>

    @POST("v1/discover/history/uploads/{uploadId}/chunks")
    suspend fun uploadHistoryChunk(
        @Path("uploadId") uploadId: String,
        @Header("Content-Encoding") contentEncoding: String,
        @Header("X-Fenlzer-Upload-Id") uploadIdHeader: String,
        @Header("X-Fenlzer-Chunk-Index") chunkIndex: Int,
        @Header("X-Fenlzer-Chunk-Count") chunkCount: Int,
        @Body compressedBody: RequestBody
    ): ApiSuccess<HistoryChunkData>

    @POST("v1/discover/history/uploads/{uploadId}/complete")
    suspend fun completeHistoryUpload(
        @Path("uploadId") uploadId: String,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Body request: CompleteUploadRequest
    ): ApiSuccess<HistoryCompleteData>

    @POST("v1/discover/refresh")
    suspend fun refreshDiscover(
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Body request: DiscoverRefreshRequest
    ): ApiSuccess<DiscoverRefreshData>

    @POST("v1/discover/refresh-broader")
    suspend fun refreshDiscoverBroader(
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Body request: DiscoverRefreshRequest
    ): ApiSuccess<DiscoverRefreshData>

    @GET("v1/diagnostics/recent")
    suspend fun recentDiagnostics(
        @Query("limit") limit: Int = 100,
        @Query("since") since: String? = null
    ): ApiSuccess<DiagnosticsRecentData>
}
