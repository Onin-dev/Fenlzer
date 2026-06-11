package com.fenl.fenlzer.importing.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YoutubeTransferRulesTest {
    @Test
    fun normalizeSha256AcceptsPlainAndPrefixedLowercaseHex() {
        val hash = "6101a990aa8ebe75424659370c16bb5337483a62be9f74504a86c73f2658147e"

        assertEquals(hash, YoutubeTransferRules.normalizeSha256(hash))
        assertEquals(hash, YoutubeTransferRules.normalizeSha256("sha256:$hash"))
        assertEquals(hash, YoutubeTransferRules.normalizeSha256(hash.uppercase()))
    }

    @Test
    fun normalizeSha256RejectsMissingOrInvalidValues() {
        assertNull(YoutubeTransferRules.normalizeSha256(null))
        assertNull(YoutubeTransferRules.normalizeSha256("sha256:not-a-real-hash"))
    }

    @Test
    fun extensionFromFilenameKeepsSupportedAudioTypesAndDefaultsToM4a() {
        assertEquals("m4a", YoutubeTransferRules.extensionFromFilename("video.m4a"))
        assertEquals("flac", YoutubeTransferRules.extensionFromFilename("song.FLAC"))
        assertEquals("m4a", YoutubeTransferRules.extensionFromFilename("download.bin"))
        assertEquals("m4a", YoutubeTransferRules.extensionFromFilename("download"))
    }
}
