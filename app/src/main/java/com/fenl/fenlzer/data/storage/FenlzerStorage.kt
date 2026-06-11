package com.fenl.fenlzer.data.storage

import android.content.Context
import java.io.File

class FenlzerStorage(
    private val context: Context
) {
    val audioDir: File = File(context.filesDir, PrivateStoragePaths.AUDIO_DIR)
    val thumbnailsDir: File = File(context.filesDir, PrivateStoragePaths.THUMBNAIL_DIR)
    val tempImportDir: File = File(context.cacheDir, PrivateStoragePaths.TEMP_IMPORT_DIR)
    val fenlzerCacheDir: File = File(context.cacheDir, PrivateStoragePaths.CACHE_DIR)

    fun ensureDirectories() {
        listOf(audioDir, thumbnailsDir, tempImportDir, fenlzerCacheDir).forEach { directory ->
            if (!directory.exists()) {
                directory.mkdirs()
            }
        }
    }

    fun audioFile(audioHash: String, extension: String): File =
        File(audioDir, PrivateStoragePaths.audioFilenameForHash(audioHash, extension))

    fun thumbnailFile(contentHash: String, extension: String = "jpg"): File =
        File(thumbnailsDir, PrivateStoragePaths.thumbnailFilenameForHash(contentHash, extension))

    fun storageUsage(): FenlzerStorageUsage {
        return FenlzerStorageUsage(
            audioBytes = audioDir.sizeRecursively(),
            thumbnailBytes = thumbnailsDir.sizeRecursively(),
            cacheBytes = context.cacheDir.sizeRecursively(),
            databaseBytes = databaseSizeBytes()
        )
    }

    fun clearCache() {
        if (context.cacheDir.exists()) {
            context.cacheDir.listFiles()?.forEach { child ->
                child.deleteRecursively()
            }
        }
        ensureDirectories()
    }

    private fun databaseSizeBytes(): Long {
        val database = context.getDatabasePath("fenlzer.db")
        val parent = database.parentFile ?: return database.length()
        return listOf(
            database,
            File(parent, "${database.name}-wal"),
            File(parent, "${database.name}-shm")
        ).sumOf { file -> if (file.exists()) file.length() else 0L }
    }

    private fun File.sizeRecursively(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}

data class FenlzerStorageUsage(
    val audioBytes: Long,
    val thumbnailBytes: Long,
    val cacheBytes: Long,
    val databaseBytes: Long
) {
    val totalBytes: Long
        get() = audioBytes + thumbnailBytes + cacheBytes + databaseBytes
}
