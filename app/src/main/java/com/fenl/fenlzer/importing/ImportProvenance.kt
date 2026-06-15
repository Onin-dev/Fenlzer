package com.fenl.fenlzer.importing

object ImportSourceType {
    const val LOCAL_FILE = "LOCAL_FILE"
    const val YOUTUBE_SEARCH = "YOUTUBE_SEARCH"
    const val YOUTUBE_PLAYLIST = "YOUTUBE_PLAYLIST"
    const val DISCOVER_MANUAL = "DISCOVER_MANUAL"
    const val DISCOVER_AUTO_FAVOURITE = "DISCOVER_AUTO_FAVOURITE"
    const val DISCOVER_AUTO_PLAYLIST = "DISCOVER_AUTO_PLAYLIST"
    const val LEGACY_YOUTUBE = "LEGACY_YOUTUBE"
}

object ImportReason {
    const val MANUAL_LOCAL = "MANUAL_LOCAL"
    const val MANUAL_SINGLE = "MANUAL_SINGLE"
    const val YOUTUBE_PLAYLIST = "YOUTUBE_PLAYLIST"
    const val DISCOVER_MANUAL = "DISCOVER_MANUAL"
    const val AUTO_FAVOURITE = "AUTO_FAVOURITE"
    const val AUTO_PLAYLIST_ADD = "AUTO_PLAYLIST_ADD"
    const val LEGACY_YOUTUBE = "LEGACY_YOUTUBE"
}

object ImportPriorityClass {
    const val MANUAL = "MANUAL"
    const val AUTO = "AUTO"
}

object ImportPendingAction {
    const val FAVOURITE = "FAVOURITE"
    const val ADD_TO_PLAYLIST = "ADD_TO_PLAYLIST"
}

data class ImportIntent(
    val sourceType: String,
    val reason: String,
    val priorityClass: String,
    val targetFavourite: Boolean = false,
    val targetPlaylistId: String? = null,
    val pendingActionType: String? = null
) {
    companion object {
        fun youtubeSearch() = ImportIntent(
            sourceType = ImportSourceType.YOUTUBE_SEARCH,
            reason = ImportReason.MANUAL_SINGLE,
            priorityClass = ImportPriorityClass.MANUAL
        )

        fun discoverManual() = ImportIntent(
            sourceType = ImportSourceType.DISCOVER_MANUAL,
            reason = ImportReason.DISCOVER_MANUAL,
            priorityClass = ImportPriorityClass.MANUAL
        )

        fun discoverAutoFavourite() = ImportIntent(
            sourceType = ImportSourceType.DISCOVER_AUTO_FAVOURITE,
            reason = ImportReason.AUTO_FAVOURITE,
            priorityClass = ImportPriorityClass.AUTO,
            targetFavourite = true,
            pendingActionType = ImportPendingAction.FAVOURITE
        )

        fun discoverAutoPlaylist(playlistId: String) = ImportIntent(
            sourceType = ImportSourceType.DISCOVER_AUTO_PLAYLIST,
            reason = ImportReason.AUTO_PLAYLIST_ADD,
            priorityClass = ImportPriorityClass.AUTO,
            targetPlaylistId = playlistId,
            pendingActionType = ImportPendingAction.ADD_TO_PLAYLIST
        )
    }
}
