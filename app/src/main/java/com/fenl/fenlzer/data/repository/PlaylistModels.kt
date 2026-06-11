package com.fenl.fenlzer.data.repository

import android.net.Uri

data class RegularPlaylistSummary(
    val playlistId: String,
    val name: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val modifiedAt: Long,
    val thumbnailUris: List<Uri?>,
    val hasCustomThumbnail: Boolean
)

data class RegularPlaylistDetail(
    val playlistId: String,
    val name: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val modifiedAt: Long,
    val tracks: List<PlaylistTrackItem>,
    val thumbnailUris: List<Uri?>,
    val hasCustomThumbnail: Boolean
)

data class PlaylistTrackItem(
    val trackId: String,
    val displayTitle: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String?,
    val durationMs: Long,
    val isFavourite: Boolean,
    val thumbnailUri: Uri?
)

data class PlaylistMembershipTarget(
    val targetId: String,
    val name: String,
    val selected: Boolean,
    val favouritesTarget: Boolean = false
)

data class SmartPlaylistSummary(
    val smartPlaylistId: String,
    val name: String,
    val description: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val thumbnailUris: List<Uri?>
)

data class SmartPlaylistDetail(
    val smartPlaylistId: String,
    val name: String,
    val description: String,
    val tracks: List<PlaylistTrackItem>
) {
    val songCount: Int
        get() = tracks.size

    val totalDurationMs: Long
        get() = tracks.sumOf { it.durationMs }
}

object SmartPlaylistIds {
    const val DISCOVER = "discover"
    const val FAVOURITES = "favourites"
    const val MOST_LISTENED = "most_listened"
    const val RECENTLY_PLAYED = "recently_played"
    const val NEVER_PLAYED = "never_played"
    const val MISSING_METADATA = "missing_metadata"
    const val MORNING = "morning"
    const val AFTERNOON = "afternoon"
    const val EVENING = "evening"
    const val NIGHT = "night"

    val ordered = listOf(
        DISCOVER,
        FAVOURITES,
        MOST_LISTENED,
        RECENTLY_PLAYED,
        NEVER_PLAYED,
        MISSING_METADATA,
        MORNING,
        AFTERNOON,
        EVENING,
        NIGHT
    )
}
