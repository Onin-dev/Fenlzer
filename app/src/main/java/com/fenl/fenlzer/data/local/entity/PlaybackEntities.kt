package com.fenl.fenlzer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_sessions",
    indices = [
        Index("startedAt"),
        Index("endedAt")
    ]
)
data class PlaybackSessionEntity(
    @PrimaryKey val sessionId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val totalListenedMs: Long,
    val eventCount: Int,
    val createdFromPrivateMode: Boolean
)

@Entity(
    tableName = "playback_events",
    indices = [
        Index(value = ["trackId", "startedAt"]),
        Index(value = ["remoteItemId", "startedAt"]),
        Index("sessionId"),
        Index("startedAt")
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RemoteItemEntity::class,
            parentColumns = ["remoteItemId"],
            childColumns = ["remoteItemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlaybackSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaybackEventEntity(
    @PrimaryKey val eventId: String,
    val trackId: String? = null,
    val remoteItemId: String? = null,
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val listenedMs: Long,
    val durationMsAtPlayback: Long,
    val validListen: Boolean,
    val skip: Boolean,
    val completion: Boolean,
    val completionPercent: Float,
    val stopPositionMs: Long? = null,
    val privateMode: Boolean,
    val sourceContext: String
)

@Entity(
    tableName = "track_stats_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackStatsSnapshotEntity(
    @PrimaryKey val trackId: String,
    val playCount: Int,
    val skipCount: Int,
    val completionCount: Int,
    val totalListenedMs: Long,
    val firstPlayedAt: Long? = null,
    val lastPlayedAt: Long? = null,
    val averageCompletionPercent: Float
)

@Entity(
    tableName = "playback_progress_recovery",
    indices = [
        Index("trackId"),
        Index("remoteItemId"),
        Index("lastUpdatedAt")
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
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
data class PlaybackProgressRecoveryEntity(
    @PrimaryKey val progressId: String,
    val queueItemId: String? = null,
    val trackId: String? = null,
    val remoteItemId: String? = null,
    val startedAt: Long,
    val lastUpdatedAt: Long,
    val listenedMs: Long,
    val durationMsAtPlayback: Long,
    val lastPositionMs: Long,
    val sourceContext: String
)
