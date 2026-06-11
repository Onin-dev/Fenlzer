package com.fenl.fenlzer.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

/**
 * Handwritten DTOs aligned with the current Fenlzer API OpenAPI schema and samples.
 *
 * Notes:
 * - Most "enum" API values are intentionally Strings to avoid crashes if the API adds values later.
 * - Flexible fields from OpenAPI additionalProperties are represented as JsonObject?.
 * - Date/time values are ISO-8601 strings; convert at repository/domain level if desired.
 */

@Serializable
data class ApiSuccess<T>(
    val success: Boolean,
    val requestId: String,
    val data: T
)

@Serializable
data class ApiErrorEnvelope(
    val success: Boolean = false,
    val requestId: String? = null,
    val error: ApiErrorBody
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val recommendedRetryAfterMs: Long? = null,
    val details: JsonObject? = null
)

object ApiJobState {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING_METADATA = "DOWNLOADING_METADATA"
    const val DOWNLOADING = "DOWNLOADING"
    const val POST_PROCESSING = "POST_PROCESSING"
    const val READY_FOR_TRANSFER = "READY_FOR_TRANSFER"
    const val TRANSFER_CONFIRMED = "TRANSFER_CONFIRMED"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val EXPIRED = "EXPIRED"
    const val NEEDS_ATTENTION = "NEEDS_ATTENTION"
    const val UNKNOWN = "UNKNOWN"

    fun isTerminalSuccess(state: String?): Boolean =
        state == TRANSFER_CONFIRMED || state == COMPLETED

    fun isTerminalFailure(state: String?): Boolean =
        state == FAILED || state == CANCELLED || state == EXPIRED

    fun isRunning(state: String?): Boolean =
        state == DOWNLOADING_METADATA || state == DOWNLOADING || state == POST_PROCESSING
}

object ApiPriorityClass {
    const val MANUAL = "MANUAL"
    const val AUTO = "AUTO"
}

object ApiJobReason {
    const val MANUAL_SINGLE = "MANUAL_SINGLE"
    const val MANUAL_BATCH = "MANUAL_BATCH"
    const val YOUTUBE_PLAYLIST = "YOUTUBE_PLAYLIST"
    const val DISCOVER_MANUAL = "DISCOVER_MANUAL"
    const val AUTO_FAVOURITE = "AUTO_FAVOURITE"
    const val AUTO_PLAYLIST_ADD = "AUTO_PLAYLIST_ADD"
}

object ApiPreferredFormat {
    const val M4A_AAC = "M4A_AAC"
}

object ApiStreamQuality {
    const val BEST = "BEST"
}

/* Requests */

@Serializable
data class SearchRequest(
    val query: String,
    val limit: Int = 5,
    val includeUnavailable: Boolean = false,
    val clientContext: JsonObject? = null
)

@Serializable
data class PlaylistPreviewRequest(
    val playlistUrl: String,
    val cacheTtlHours: Int = 24,
    val progressive: Boolean = true,
    val clientKnownVideoIds: List<String> = emptyList(),
    val includeUnavailable: Boolean = true
)

@Serializable
data class DownloadSource(
    val type: String = "YOUTUBE_VIDEO",
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val remoteItemId: String? = null
)

@Serializable
data class CreateDownloadRequest(
    val clientJobId: String? = null,
    val source: DownloadSource? = null,

    // The current API also accepts flattened source fields.
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val remoteItemId: String? = null,

    val jobType: String = "YOUTUBE_SEARCH",
    val priorityClass: String = ApiPriorityClass.MANUAL,
    val preferredFormat: String = ApiPreferredFormat.M4A_AAC,
    val fallbackToBestAvailable: Boolean = true,
    val reason: String? = ApiJobReason.MANUAL_SINGLE,
    val target: JsonObject? = null
)

@Serializable
data class BatchDownloadRequest(
    val batchId: String? = null,
    val priorityClass: String = ApiPriorityClass.MANUAL,
    val preferredFormat: String = ApiPreferredFormat.M4A_AAC,
    val fallbackToBestAvailable: Boolean = true,
    val reason: String? = null,
    val target: JsonObject? = null,
    val items: List<JsonObject> = emptyList()
)

@Serializable
data class JobStatusRequest(
    val apiJobIds: List<String>
)

@Serializable
data class ConfirmFileRequest(
    val clientJobId: String? = null,
    val receivedSha256: String,
    val receivedSizeBytes: Long,
    val localTrackId: String? = null,
    val importedAt: String? = null
)

@Serializable
data class CancelRequest(
    val reason: String? = null
)

@Serializable
data class RetryRequest(
    val clientRetryId: String? = null,
    val preserveOriginalIntent: Boolean = true
)

@Serializable
data class ReorderRequest(
    val orderedApiJobIds: List<String>,
    val scope: String = "UPCOMING_ONLY"
)

@Serializable
data class StreamResolveRequest(
    val remoteItemId: String,
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val quality: String = ApiStreamQuality.BEST,
    val knownPlayableUrl: JsonObject? = null,
    val reason: String? = null
)

@Serializable
data class CreateHistoryUploadRequest(
    val clientUploadId: String? = null,
    val compression: String = "zstd",
    val estimatedEventCount: Int? = null,
    val estimatedCompressedChunkCount: Int = 1,
    val schemaVersion: Int = 1,
    val excludedPrivateModeEvents: Boolean = true
)

@Serializable
data class CompleteUploadRequest(
    val chunkCount: Int,
    val overallSha256: String? = null,
    val totalEventCount: Int? = null
)

@Serializable
data class DiscoverRefreshRequest(
    val historyUploadId: String,
    val targetDisplayCount: Int = 25,
    val maxCandidateCount: Int = 75,
    val strictlyExcludeImported: Boolean = true,
    val clientLibrary: JsonObject? = null,
    val previousRefreshDiagnostics: JsonObject? = null,
    val broadenReason: String? = null,
    val previousSnapshotId: String? = null
)

/* Response data */

@Serializable
data class LiveResponse(
    val status: String,
    val serverTime: String
)

@Serializable
data class HealthData(
    val status: String,
    val apiVersion: String,
    val serverTime: String,
    val apiReachable: Boolean,
    val authenticated: Boolean,
    val databaseOk: Boolean,
    val workerRunning: Boolean,
    val activeJobsSupported: Boolean,
    val streamSupported: Boolean,
    val features: Map<String, Boolean> = emptyMap(),
    val tools: JsonObject? = null,
    val worker: JsonObject? = null,
    val limits: JsonObject? = null
)

@Serializable
data class YoutubeSearchData(
    val query: String,
    val results: List<SearchResult>
)

@Serializable
data class SearchResult(
    val remoteItemId: String,
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val title: String,
    val artistOrChannel: String? = null,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
    val canStream: Boolean,
    val canDownload: Boolean,
    val isLive: Boolean = false,
    val isUnavailable: Boolean = false
)

@Serializable
data class PlaylistPreviewData(
    val previewId: String,
    val status: String,
    val playlistTitle: String? = null,
    val playlistThumbnailUrl: String? = null,
    val totalExpectedItems: Int? = null,
    val loadedItemCount: Int? = null,
    val expiresAt: String? = null,
    val items: List<PlaylistPreviewItem> = emptyList()
)

@Serializable
data class PlaylistPreviewItem(
    val position: Int? = null,
    val remoteItemId: String,
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val title: String,
    val artistOrChannel: String? = null,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
    val canStream: Boolean,
    val canDownload: Boolean,
    val availability: String? = null,
    val alreadyKnownByClient: Boolean = false
)

@Serializable
data class DownloadCreateData(
    val job: JobObject
)

@Serializable
data class BatchDownloadData(
    val batchId: String? = null,
    val createdJobs: List<BatchCreatedJob> = emptyList(),
    val rejectedItems: List<JsonObject> = emptyList()
)

@Serializable
data class BatchCreatedJob(
    val clientJobId: String? = null,
    val apiJobId: String,
    val status: String
)

@Serializable
data class JobResponseData(
    val job: JobObject
)

@Serializable
data class JobsStatusData(
    val jobs: List<CompactJobStatus>
)

@Serializable
data class JobObject(
    val apiJobId: String,
    val clientJobId: String? = null,
    val jobType: String? = null,
    val status: String,
    val state: String,
    val source: JobSource,
    val priorityClass: String? = null,
    val priorityLabel: String? = null,
    val priority: JsonObject? = null,
    val reason: String? = null,
    val queuePosition: Int? = null,
    val progressPercent: Int? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Long? = null,
    val etaSeconds: Long? = null,
    val currentStep: String? = null,
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val displayTitle: String? = null,
    val preferredFormat: String? = null,
    val actualFormat: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val completedAt: String? = null,
    val expiresAt: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val retryable: Boolean = false,
    val file: JobFile? = null,
    val metadata: JobMetadata? = null,
    val error: JobError? = null
)

@Serializable
data class CompactJobStatus(
    val apiJobId: String,
    val status: String,
    val state: String? = null,
    val source: JobSource? = null,
    val reason: String? = null,
    val priorityClass: String? = null,
    val priorityLabel: String? = null,
    val priority: JsonObject? = null,
    val progressPercent: Int? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Long? = null,
    val etaSeconds: Long? = null,
    val currentStep: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val retryable: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val expiresAt: String? = null
)

@Serializable
data class JobSource(
    val type: String? = null,
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val remoteItemId: String? = null
)

@Serializable
data class JobFile(
    val available: Boolean,
    val expiresAt: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val contentType: String? = null,
    val filename: String? = null
)

@Serializable
data class JobMetadata(
    val title: String? = null,
    val artistOrChannel: String? = null,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null
)

@Serializable
data class JobError(
    val code: String? = null,
    val message: String? = null,
    val safeTechnicalMessage: String? = null
)

@Serializable
data class ConfirmFileData(
    val apiJobId: String,
    val fileConfirmed: Boolean,
    val serverTemporaryFileDeleted: Boolean
)

@Serializable
data class CancelJobData(
    val apiJobId: String,
    val status: String
)

@Serializable
data class RetryJobData(
    val oldJobId: String,
    val newJob: RetryNewJob
)

@Serializable
data class RetryNewJob(
    val apiJobId: String,
    val status: String,
    val priorityClass: String? = null
)

@Serializable
data class ReorderJobsData(
    val updated: Boolean,
    val orderedApiJobIds: List<String>
)

@Serializable
data class StreamResolveData(
    val remoteItemId: String,
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val playableUrl: String,
    val expiresAt: String,
    val durationMs: Long? = null,
    val title: String? = null,
    val artistOrChannel: String? = null,
    val thumbnailUrl: String? = null,
    val canStream: Boolean,
    val canDownload: Boolean,
    val urlMode: String,
    val reusable: Boolean,
    val reusableUntil: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val requiresHeaders: Boolean,
    val httpHeaders: Map<String, String> = emptyMap(),
    val isUrlExpired: Boolean,
    val quality: String
)

@Serializable
data class HistoryUploadCreateData(
    val uploadId: String,
    val acceptedCompression: String,
    val targetChunkSizeBytes: Int,
    val expiresAt: String
)

@Serializable
data class HistoryChunkData(
    val uploadId: String,
    val chunkIndex: Int,
    val accepted: Boolean,
    val duplicate: Boolean,
    val receivedCompressedBytes: Int,
    val chunkSha256: String
)

@Serializable
data class HistoryCompleteData(
    val uploadId: String,
    val status: String,
    val usableForDiscover: Boolean
)

@Serializable
data class DiscoverRefreshData(
    val snapshotId: String,
    val generatedAt: String,
    val refreshType: String,
    val candidateRequestTarget: Int,
    val finalDisplayedCount: Int,
    val refreshBroaderAvailable: Boolean,
    val items: List<DiscoverItem>,
    val diagnostics: JsonObject
)

@Serializable
data class DiscoverItem(
    val remoteItemId: String,
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val title: String,
    val artistOrChannel: String? = null,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
    val canStream: Boolean,
    val canDownload: Boolean,
    val isLive: Boolean = false,
    val isUnavailable: Boolean = false,
    val recommendationReason: String? = null,
    val alreadyImported: Boolean = false,
    val position: Int? = null
)

@Serializable
data class DiagnosticsRecentData(
    val entries: List<DiagnosticsEntry>
)

@Serializable
data class DiagnosticsEntry(
    val requestId: String,
    val endpoint: String,
    val method: String,
    val statusCode: Int,
    val durationMs: Int,
    val success: Boolean,
    val errorCode: String? = null,
    val sanitizedMessage: String? = null,
    val createdAt: String
)
