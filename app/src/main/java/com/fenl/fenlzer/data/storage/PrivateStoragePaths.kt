package com.fenl.fenlzer.data.storage

object PrivateStoragePaths {
    const val AUDIO_DIR = "audio"
    const val THUMBNAIL_DIR = "thumbnails"
    const val TEMP_IMPORT_DIR = "temp_imports"
    const val CACHE_DIR = "cache"

    fun audioFilenameForHash(audioHash: String, extension: String): String {
        val normalizedHash = audioHash.lowercase().filter { it.isLetterOrDigit() }
        val normalizedExtension = extension
            .lowercase()
            .trim()
            .trimStart('.')
            .filter { it.isLetterOrDigit() }

        require(normalizedHash.isNotBlank()) { "Audio hash must not be blank." }
        require(normalizedExtension.isNotBlank()) { "Extension must not be blank." }

        return "$normalizedHash.$normalizedExtension"
    }

    fun thumbnailFilenameForHash(contentHash: String, extension: String = "jpg"): String {
        val normalizedHash = contentHash.lowercase().filter { it.isLetterOrDigit() }
        val normalizedExtension = extension
            .lowercase()
            .trim()
            .trimStart('.')
            .filter { it.isLetterOrDigit() }

        require(normalizedHash.isNotBlank()) { "Thumbnail hash must not be blank." }
        require(normalizedExtension.isNotBlank()) { "Extension must not be blank." }

        return "$normalizedHash.$normalizedExtension"
    }
}
