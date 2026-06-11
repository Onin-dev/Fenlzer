package com.fenl.fenlzer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "remote_items",
    indices = [
        Index("youtubeVideoId"),
        Index("sourceUrl"),
        Index("importedTrackId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["importedTrackId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class RemoteItemEntity(
    @PrimaryKey val remoteItemId: String,
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val title: String,
    val artistOrChannel: String? = null,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
    val canStream: Boolean,
    val canDownload: Boolean,
    val streamState: String,
    val lastPlayableUrl: String? = null,
    val playableUrlExpiresAt: Long? = null,
    val lastResolvedAt: Long? = null,
    val importState: String,
    val importedTrackId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "discover_snapshots")
data class DiscoverSnapshotEntity(
    @PrimaryKey val snapshotId: String,
    val generatedAt: Long,
    val lastOpenedAt: Long,
    val refreshType: String,
    val candidateRequestTarget: Int,
    val finalDisplayedCount: Int,
    val refreshDetailsVisible: Boolean
)

@Entity(
    tableName = "discover_snapshot_items",
    primaryKeys = ["snapshotId", "remoteItemId"],
    indices = [
        Index(value = ["snapshotId", "position"], unique = true),
        Index("remoteItemId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = DiscoverSnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RemoteItemEntity::class,
            parentColumns = ["remoteItemId"],
            childColumns = ["remoteItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DiscoverSnapshotItemEntity(
    val snapshotId: String,
    val remoteItemId: String,
    val position: Int,
    val recommendationReason: String? = null
)

@Entity(
    tableName = "discover_refresh_diagnostics",
    foreignKeys = [
        ForeignKey(
            entity = DiscoverSnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DiscoverRefreshDiagnosticsEntity(
    @PrimaryKey val snapshotId: String,
    val candidatesRequested: Int,
    val candidatesReceived: Int,
    val alreadyImportedFiltered: Int,
    val invalidOrUnavailableFiltered: Int,
    val finalDisplayedCount: Int,
    val refreshBroaderShown: Boolean,
    val apiRequestIdsJson: String
)
