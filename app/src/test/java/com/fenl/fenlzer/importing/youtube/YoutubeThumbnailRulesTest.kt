package com.fenl.fenlzer.importing.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubeThumbnailRulesTest {
    @Test
    fun imageSignaturesChoosePermanentThumbnailExtension() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val webp = "RIFFxxxxWEBP".encodeToByteArray()

        assertEquals("png", YoutubeThumbnailRules.extension(png, "image/jpeg"))
        assertEquals("webp", YoutubeThumbnailRules.extension(webp, null))
        assertEquals("gif", YoutubeThumbnailRules.extension(byteArrayOf(0x47, 0x49, 0x46, 0, 0, 0), null))
        assertEquals("jpg", YoutubeThumbnailRules.extension(byteArrayOf(1, 2, 3), "image/jpeg"))
    }
}
