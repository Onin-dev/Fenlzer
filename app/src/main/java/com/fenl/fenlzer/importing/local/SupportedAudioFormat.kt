package com.fenl.fenlzer.importing.local

data class SupportedAudioFormat(
    val extension: String,
    val label: String
) {
    companion object {
        private val supportedByExtension = mapOf(
            "mp3" to SupportedAudioFormat("mp3", "MP3"),
            "m4a" to SupportedAudioFormat("m4a", "M4A"),
            "wav" to SupportedAudioFormat("wav", "WAV"),
            "flac" to SupportedAudioFormat("flac", "FLAC"),
            "ogg" to SupportedAudioFormat("ogg", "OGG")
        )

        private val supportedByMimeType = mapOf(
            "audio/mpeg" to supportedByExtension.getValue("mp3"),
            "audio/mp3" to supportedByExtension.getValue("mp3"),
            "audio/mp4" to supportedByExtension.getValue("m4a"),
            "audio/x-m4a" to supportedByExtension.getValue("m4a"),
            "audio/aac" to supportedByExtension.getValue("m4a"),
            "audio/wav" to supportedByExtension.getValue("wav"),
            "audio/x-wav" to supportedByExtension.getValue("wav"),
            "audio/flac" to supportedByExtension.getValue("flac"),
            "audio/x-flac" to supportedByExtension.getValue("flac"),
            "audio/ogg" to supportedByExtension.getValue("ogg"),
            "application/ogg" to supportedByExtension.getValue("ogg")
        )

        fun fromFilenameOrMimeType(filename: String?, mimeType: String?): SupportedAudioFormat? {
            val extension = filename
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }

            return extension
                ?.let { supportedByExtension[it] }
                ?: mimeType?.lowercase()?.let { supportedByMimeType[it] }
        }

        fun isSupported(filename: String?, mimeType: String?): Boolean =
            fromFilenameOrMimeType(filename, mimeType) != null
    }
}
