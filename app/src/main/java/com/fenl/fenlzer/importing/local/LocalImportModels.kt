package com.fenl.fenlzer.importing.local

import android.net.Uri

data class LocalImportBatchResult(
    val items: List<LocalImportItemResult>,
    val startedAt: Long,
    val completedAt: Long
) {
    val successes: List<LocalImportItemResult>
        get() = items.filter { it.outcome == LocalImportOutcome.SUCCESS }

    val duplicates: List<LocalImportItemResult>
        get() = items.filter { it.outcome == LocalImportOutcome.DUPLICATE }

    val failures: List<LocalImportItemResult>
        get() = items.filter { it.outcome == LocalImportOutcome.FAILED }
}

data class LocalImportItemResult(
    val filename: String,
    val displayTitle: String,
    val outcome: LocalImportOutcome,
    val sourceUri: Uri? = null,
    val trackId: String? = null,
    val duplicateTrackId: String? = null,
    val message: String? = null,
    val metadataWarning: Boolean = false
)

enum class LocalImportOutcome {
    SUCCESS,
    DUPLICATE,
    FAILED
}

data class LocalImportProgress(
    val currentIndex: Int,
    val total: Int,
    val filename: String,
    val stage: LocalImportStage,
    val percent: Int? = null
)

enum class LocalImportStage {
    COPYING,
    EXTRACTING_METADATA,
    SAVING,
    COMPLETE
}
