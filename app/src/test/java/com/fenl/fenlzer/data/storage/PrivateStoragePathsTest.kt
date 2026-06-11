package com.fenl.fenlzer.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateStoragePathsTest {
    @Test
    fun audioFilenameNormalizesHashAndExtension() {
        assertEquals(
            "abc123.m4a",
            PrivateStoragePaths.audioFilenameForHash("ABC-123", ".M4A")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun audioFilenameRejectsBlankHash() {
        PrivateStoragePaths.audioFilenameForHash(" --- ", "mp3")
    }
}
