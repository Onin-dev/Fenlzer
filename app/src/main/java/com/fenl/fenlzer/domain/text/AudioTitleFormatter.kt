package com.fenl.fenlzer.domain.text

object AudioTitleFormatter {
    private val supportedAudioExtension = Regex(
        pattern = """\.(mp3|m4a|wav|flac|ogg)$""",
        option = RegexOption.IGNORE_CASE
    )

    fun importedTrackTitle(
        metadataTitle: String,
        filename: String
    ): String {
        return displayTitle(
            title = metadataTitle,
            fallbackFilename = filename
        )
    }

    fun displayTitle(
        title: String,
        fallbackFilename: String?
    ): String {
        val rawTitle = title.trim().ifBlank {
            fallbackFilename.orEmpty().substringAfterLast('/').trim()
        }
        return stripSupportedAudioExtension(rawTitle).ifBlank { "Untitled" }
    }

    fun stripSupportedAudioExtension(value: String): String {
        return value.trim().replace(supportedAudioExtension, "").trim()
    }
}
