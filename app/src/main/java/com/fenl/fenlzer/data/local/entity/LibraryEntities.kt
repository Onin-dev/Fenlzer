package com.fenl.fenlzer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "thumbnail_assets",
    indices = [
        Index("contentHash"),
        Index("sourceUrl")
    ]
)
data class ThumbnailAssetEntity(
    @PrimaryKey val thumbnailAssetId: String,
    val kind: String,
    val internalFilename: String,
    val sourceUrl: String? = null,
    val contentHash: String? = null,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val isPermanent: Boolean
)

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["audioHash"], unique = true),
        Index(value = ["youtubeVideoId"], unique = true),
        Index("sourceUrl"),
        Index("titleSortKey"),
        Index("artistSortKey"),
        Index("albumSortKey"),
        Index("albumArtistSortKey"),
        Index("importedAt"),
        Index(value = ["isFavourite", "favouritedAt"]),
        Index("thumbnailAssetId"),
        Index("embeddedThumbnailAssetId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ThumbnailAssetEntity::class,
            parentColumns = ["thumbnailAssetId"],
            childColumns = ["thumbnailAssetId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ThumbnailAssetEntity::class,
            parentColumns = ["thumbnailAssetId"],
            childColumns = ["embeddedThumbnailAssetId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class TrackEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val titleSortKey: String,
    val artist: String,
    val artistSortKey: String,
    val album: String,
    val albumSortKey: String,
    val albumArtist: String,
    val albumArtistSortKey: String,
    val genre: String,
    val year: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val durationMs: Long,
    val notes: String,
    val sourceType: String,
    val importReason: String? = null,
    val requestedDownloadFormat: String? = null,
    val youtubeVideoId: String? = null,
    val sourceUrl: String? = null,
    val originalFilename: String? = null,
    val internalFilename: String,
    val audioHash: String,
    val fileSizeBytes: Long,
    val finalAudioFormat: String,
    val thumbnailAssetId: String? = null,
    val embeddedThumbnailAssetId: String? = null,
    val remoteThumbnailUrl: String? = null,
    val isFavourite: Boolean,
    val favouritedAt: Long? = null,
    val importedAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "track_original_metadata",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackOriginalMetadataEntity(
    @PrimaryKey val trackId: String,
    val originalTitle: String,
    val originalArtist: String,
    val originalAlbum: String,
    val originalAlbumArtist: String,
    val originalGenre: String,
    val originalYear: String? = null,
    val originalTrackNumber: Int? = null,
    val originalDiscNumber: Int? = null,
    val originalThumbnailKind: String,
    val rawMetadataJson: String? = null
)

@Entity(tableName = "bulk_metadata_operations")
data class BulkMetadataOperationEntity(
    @PrimaryKey val operationId: String,
    val operationType: String,
    val oldValuesJson: String,
    val newValuesJson: String,
    val affectedTrackIdsJson: String,
    val createdAt: Long
)
