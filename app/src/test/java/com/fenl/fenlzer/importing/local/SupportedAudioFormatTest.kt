package com.fenl.fenlzer.importing.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedAudioFormatTest {
    @Test
    fun acceptsV1AudioFormatsByExtension() {
        assertEquals("MP3", SupportedAudioFormat.fromFilenameOrMimeType("song.MP3", null)?.label)
        assertEquals("M4A", SupportedAudioFormat.fromFilenameOrMimeType("song.m4a", null)?.label)
        assertEquals("WAV", SupportedAudioFormat.fromFilenameOrMimeType("song.wav", null)?.label)
        assertEquals("FLAC", SupportedAudioFormat.fromFilenameOrMimeType("song.flac", null)?.label)
        assertEquals("OGG", SupportedAudioFormat.fromFilenameOrMimeType("song.ogg", null)?.label)
    }

    @Test
    fun acceptsV1AudioFormatsByMimeTypeWhenFilenameHasNoExtension() {
        assertEquals("MP3", SupportedAudioFormat.fromFilenameOrMimeType("song", "audio/mpeg")?.label)
        assertEquals("M4A", SupportedAudioFormat.fromFilenameOrMimeType("song", "audio/mp4")?.label)
        assertEquals("WAV", SupportedAudioFormat.fromFilenameOrMimeType("song", "audio/wav")?.label)
        assertEquals("FLAC", SupportedAudioFormat.fromFilenameOrMimeType("song", "audio/flac")?.label)
        assertEquals("OGG", SupportedAudioFormat.fromFilenameOrMimeType("song", "application/ogg")?.label)
    }

    @Test
    fun rejectsUnsupportedFormats() {
        assertFalse(SupportedAudioFormat.isSupported("notes.txt", null))
        assertTrue(SupportedAudioFormat.isSupported("voice.ogg", null))
    }
}
