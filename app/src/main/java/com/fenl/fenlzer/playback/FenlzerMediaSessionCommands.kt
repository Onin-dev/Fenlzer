package com.fenl.fenlzer.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.fenl.fenlzer.data.repository.QueueTrackItem

object FenlzerMediaSessionCommands {
    const val ACTION_TOGGLE_FAVOURITE = "com.fenl.fenlzer.session.TOGGLE_FAVOURITE"

    val ToggleFavourite = SessionCommand(ACTION_TOGGLE_FAVOURITE, Bundle.EMPTY)

    @OptIn(UnstableApi::class)
    fun favouriteButton(isFavourite: Boolean): CommandButton =
        CommandButton.Builder(
            if (isFavourite) {
                CommandButton.ICON_HEART_FILLED
            } else {
                CommandButton.ICON_HEART_UNFILLED
            }
        )
            .setDisplayName(if (isFavourite) "Remove favourite" else "Favourite")
            .setSessionCommand(ToggleFavourite)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
}

data class FavouriteCommandTarget(
    val trackId: String,
    val isFavourite: Boolean
)

object MediaFavouriteCommandTargetResolver {
    fun resolve(
        currentMediaId: String?,
        queueItems: List<QueueTrackItem>
    ): FavouriteCommandTarget? {
        val mediaId = currentMediaId?.takeIf { it.isNotBlank() } ?: return null
        val carTrackId = CarMediaIds.trackId(mediaId)
        if (carTrackId != null) {
            return queueItems
                .firstOrNull { item -> item.localTrackId == carTrackId }
                ?.let { item ->
                    FavouriteCommandTarget(
                        trackId = carTrackId,
                        isFavourite = item.isFavourite
                    )
                }
        }

        val item = queueItems.firstOrNull { it.queueItemId == mediaId } ?: return null
        val localTrackId = item.localTrackId ?: return null
        return FavouriteCommandTarget(
            trackId = localTrackId,
            isFavourite = item.isFavourite
        )
    }
}
