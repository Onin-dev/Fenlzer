package com.fenl.fenlzer.importing.youtube

data class YoutubeSearchResultItem(
    val remoteItemId: String,
    val youtubeVideoId: String?,
    val sourceUrl: String?,
    val title: String,
    val artistOrChannel: String?,
    val durationMs: Long?,
    val thumbnailUrl: String?,
    val canStream: Boolean,
    val canDownload: Boolean,
    val isLive: Boolean,
    val isUnavailable: Boolean
)

data class YoutubeImportProgress(
    val importJobId: String,
    val apiJobId: String? = null,
    val displayTitle: String,
    val status: String,
    val progressPercent: Int? = null,
    val message: String? = null
)

data class YoutubeImportItemResult(
    val importJobId: String,
    val trackId: String?,
    val displayTitle: String,
    val outcome: YoutubeImportOutcome,
    val message: String? = null,
    val duplicateTrackId: String? = null
)

enum class YoutubeImportOutcome {
    QUEUED,
    SUCCESS,
    DUPLICATE,
    FAILED
}

data class ActiveImportUiItem(
    val importJobId: String,
    val apiJobId: String?,
    val title: String,
    val sourceLabel: String,
    val status: String,
    val progressPercent: Int?,
    val etaSeconds: Long? = null,
    val queuePosition: Int?,
    val thumbnailUrl: String?,
    val errorMessage: String?,
    val retryable: Boolean,
    val cancellable: Boolean,
    val attemptCount: Int,
    val maxAttempts: Int,
    val dismissible: Boolean
)

data class ImportHistoryUiItem(
    val historyId: String,
    val importJobId: String?,
    val title: String,
    val result: String,
    val reason: String,
    val sourceLabel: String,
    val message: String?,
    val trackId: String?,
    val createdAt: Long
)

data class YoutubePlaylistPreview(
    val previewId: String,
    val status: String,
    val title: String?,
    val thumbnailUrl: String?,
    val totalExpectedItems: Int?,
    val loadedItemCount: Int?,
    val items: List<YoutubePlaylistPreviewItem>
)

data class YoutubePlaylistPreviewItem(
    val position: Int?,
    val remoteItemId: String,
    val youtubeVideoId: String?,
    val sourceUrl: String?,
    val title: String,
    val artistOrChannel: String?,
    val durationMs: Long?,
    val thumbnailUrl: String?,
    val canDownload: Boolean,
    val availability: String?,
    val alreadyKnownByClient: Boolean
)

enum class ImportHistoryFilter {
    ALL,
    SUCCESS,
    DUPLICATE,
    FAILED,
    CANCELLED
}
