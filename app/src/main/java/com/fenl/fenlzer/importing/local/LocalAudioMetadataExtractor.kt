package com.fenl.fenlzer.importing.local

import android.media.MediaMetadataRetriever
import java.io.File

interface LocalAudioMetadataExtractor {
    fun extract(file: File): ExtractedLocalAudioMetadata
}

class AndroidLocalAudioMetadataExtractor : LocalAudioMetadataExtractor {
    override fun extract(file: File): ExtractedLocalAudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            ExtractedLocalAudioMetadata(
                title = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                artist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                albumArtist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty(),
                genre = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty(),
                year = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
                trackNumber = retriever
                    .metadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.parseLeadingInt(),
                discNumber = retriever
                    .metadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.parseLeadingInt(),
                durationMs = retriever
                    .metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L,
                embeddedArtwork = retriever.embeddedPicture,
                extractionFailed = false
            )
        } catch (_: RuntimeException) {
            ExtractedLocalAudioMetadata.empty(extractionFailed = true)
        } catch (_: IllegalArgumentException) {
            ExtractedLocalAudioMetadata.empty(extractionFailed = true)
        } finally {
            try {
                retriever.release()
            } catch (_: RuntimeException) {
                // Best-effort cleanup only.
            }
        }
    }

    private fun MediaMetadataRetriever.metadata(keyCode: Int): String? =
        extractMetadata(keyCode)?.trim()?.takeIf { it.isNotBlank() }

    private fun String.parseLeadingInt(): Int? =
        trim().takeWhile { it.isDigit() }.takeIf { it.isNotBlank() }?.toIntOrNull()
}

data class ExtractedLocalAudioMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val embeddedArtwork: ByteArray?,
    val extractionFailed: Boolean
) {
    companion object {
        fun empty(extractionFailed: Boolean): ExtractedLocalAudioMetadata =
            ExtractedLocalAudioMetadata(
                title = "",
                artist = "",
                album = "",
                albumArtist = "",
                genre = "",
                year = null,
                trackNumber = null,
                discNumber = null,
                durationMs = 0L,
                embeddedArtwork = null,
                extractionFailed = extractionFailed
            )
    }
}
