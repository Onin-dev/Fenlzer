package com.fenl.fenlzer.ui.importing

import com.fenl.fenlzer.importing.youtube.YoutubePlaylistPreview
import com.fenl.fenlzer.importing.youtube.YoutubePlaylistPreviewItem
import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubePlaylistSelectionTest {
    @Test
    fun selectAllSelectsOnlyDownloadableAvailableItems() {
        val preview = preview(
            item("one"),
            item("private", availability = "PRIVATE"),
            item("no-download", canDownload = false),
            item("two")
        )

        assertEquals(
            setOf("one", "two"),
            toggledPlaylistSelectAll(preview, currentSelection = emptySet())
        )
    }

    @Test
    fun selectAllClearsSelectionWhenAllSelectableItemsAreAlreadySelected() {
        val preview = preview(item("one"), item("two"))

        assertEquals(
            emptySet<String>(),
            toggledPlaylistSelectAll(preview, currentSelection = setOf("one", "two"))
        )
    }

    private fun preview(vararg items: YoutubePlaylistPreviewItem): YoutubePlaylistPreview =
        YoutubePlaylistPreview(
            previewId = "preview",
            status = "READY",
            title = "Playlist",
            thumbnailUrl = null,
            totalExpectedItems = items.size,
            loadedItemCount = items.size,
            items = items.toList()
        )

    private fun item(
        remoteItemId: String,
        canDownload: Boolean = true,
        availability: String? = null
    ): YoutubePlaylistPreviewItem =
        YoutubePlaylistPreviewItem(
            position = null,
            remoteItemId = remoteItemId,
            youtubeVideoId = null,
            sourceUrl = null,
            title = remoteItemId,
            artistOrChannel = null,
            durationMs = null,
            thumbnailUrl = null,
            canDownload = canDownload,
            availability = availability,
            alreadyKnownByClient = false
        )
}
