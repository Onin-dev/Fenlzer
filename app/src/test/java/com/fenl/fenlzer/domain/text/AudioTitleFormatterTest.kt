package com.fenl.fenlzer.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTitleFormatterTest {
    @Test
    fun importedTrackTitleFallsBackToFilenameWithoutAudioExtension() {
        assertEquals(
            "Quiet Song",
            AudioTitleFormatter.importedTrackTitle(
                metadataTitle = "",
                filename = "Quiet Song.mp3"
            )
        )
    }

    @Test
    fun importedTrackTitleAlsoStripsExtensionFromMetadataTitle() {
        assertEquals(
            "Bright Track",
            AudioTitleFormatter.importedTrackTitle(
                metadataTitle = "Bright Track.FLAC",
                filename = "ignored.flac"
            )
        )
    }
}
