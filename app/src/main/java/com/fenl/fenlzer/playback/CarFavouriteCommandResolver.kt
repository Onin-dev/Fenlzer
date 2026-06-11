package com.fenl.fenlzer.playback

object CarFavouriteCommandResolver {
    fun trackIdForMediaId(
        currentMediaId: String?,
        queueTrackIdForMediaId: (String) -> String?
    ): String? {
        val mediaId = currentMediaId?.takeIf { it.isNotBlank() } ?: return null
        return CarMediaIds.trackId(mediaId) ?: queueTrackIdForMediaId(mediaId)
    }
}
