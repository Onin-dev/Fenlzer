package com.fenl.fenlzer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index("customThumbnailAssetId")],
    foreignKeys = [
        ForeignKey(
            entity = ThumbnailAssetEntity::class,
            parentColumns = ["thumbnailAssetId"],
            childColumns = ["customThumbnailAssetId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val playlistType: String,
    val customThumbnailAssetId: String? = null,
    val createdAt: Long,
    val modifiedAt: Long
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [
        Index(value = ["playlistId", "trackId"], unique = true),
        Index(value = ["playlistId", "position"]),
        Index("trackId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long
)

@Entity(tableName = "queue_states")
data class QueueStateEntity(
    @PrimaryKey val queueStateId: String,
    val sourceType: String,
    val sourceId: String? = null,
    val sourceLabel: String,
    val isModified: Boolean,
    val currentQueueItemId: String? = null,
    val repeatMode: String,
    val shuffleEnabled: Boolean,
    val playbackPositionMs: Long,
    val wasPlaying: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "queue_items",
    indices = [
        Index(value = ["queueStateId", "position"], unique = true),
        Index("trackId"),
        Index("remoteItemId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = QueueStateEntity::class,
            parentColumns = ["queueStateId"],
            childColumns = ["queueStateId"],
            onDelete = ForeignKey.CASCADE
        ),
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
data class QueueItemEntity(
    @PrimaryKey val queueItemId: String,
    val queueStateId: String,
    val trackId: String? = null,
    val remoteItemId: String? = null,
    val position: Int,
    val state: String,
    val insertedBy: String,
    val addedAt: Long
) {
    fun hasExactlyOneMediaReference(): Boolean =
        (trackId == null) != (remoteItemId == null)
}
