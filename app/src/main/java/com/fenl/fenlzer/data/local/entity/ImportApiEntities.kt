package com.fenl.fenlzer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "import_jobs",
    indices = [
        Index(value = ["status", "priority"]),
        Index("apiJobId"),
        Index("remoteItemId"),
        Index("targetPlaylistId"),
        Index("createdAt")
    ],
    foreignKeys = [
        ForeignKey(
            entity = RemoteItemEntity::class,
            parentColumns = ["remoteItemId"],
            childColumns = ["remoteItemId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["targetPlaylistId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class ImportJobEntity(
    @PrimaryKey val importJobId: String,
    val apiJobId: String? = null,
    val jobType: String,
    val sourceType: String? = null,
    val reason: String? = null,
    val priorityClass: String? = null,
    val pendingActionType: String? = null,
    val priority: Int,
    val status: String,
    val sourceUrl: String? = null,
    val youtubeVideoId: String? = null,
    val remoteItemId: String? = null,
    val targetPlaylistId: String? = null,
    val targetFavourite: Boolean,
    val preferredFormat: String,
    val actualFormat: String? = null,
    val progressPercent: Int? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val technicalDetailsJson: String? = null,
    @ColumnInfo(defaultValue = "0") val attemptCount: Int = 0,
    @ColumnInfo(defaultValue = "3") val maxAttempts: Int = 3,
    @ColumnInfo(defaultValue = "0") val isVisibleInActiveImports: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
)

@Entity(
    tableName = "import_history_entries",
    indices = [
        Index(value = ["result", "createdAt"]),
        Index("trackId"),
        Index("youtubeVideoId"),
        Index("importJobId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ImportJobEntity::class,
            parentColumns = ["importJobId"],
            childColumns = ["importJobId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class ImportHistoryEntryEntity(
    @PrimaryKey val historyId: String,
    val importJobId: String? = null,
    val result: String,
    val reason: String,
    val sourceType: String? = null,
    val jobType: String? = null,
    val requestedFormat: String? = null,
    val finalFormat: String? = null,
    val pendingActionType: String? = null,
    val trackId: String? = null,
    val sourceUrl: String? = null,
    val youtubeVideoId: String? = null,
    val displayTitle: String,
    val errorCode: String? = null,
    val friendlyMessage: String? = null,
    val technicalDetailsJson: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "api_diagnostic_entries",
    indices = [
        Index("startedAt"),
        Index("endpoint"),
        Index("success")
    ]
)
data class ApiDiagnosticEntryEntity(
    @PrimaryKey val diagnosticId: String,
    val requestId: String? = null,
    val endpoint: String,
    val method: String,
    val startedAt: Long,
    val durationMs: Long,
    val statusCode: Int? = null,
    val success: Boolean,
    val errorCode: String? = null,
    val sanitizedMessage: String? = null,
    val metadataJson: String? = null
)
