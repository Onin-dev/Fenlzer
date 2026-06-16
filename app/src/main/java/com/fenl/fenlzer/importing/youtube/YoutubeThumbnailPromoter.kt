package com.fenl.fenlzer.importing.youtube

import com.fenl.fenlzer.data.local.entity.ThumbnailAssetEntity
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.importing.local.Sha256
import java.io.File
import java.io.IOException
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Request

class YoutubeThumbnailPromoter(
    private val storage: FenlzerStorage,
    private val client: OkHttpClient = OkHttpClient(),
    private val now: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun download(sourceUrl: String?): PromotedYoutubeThumbnail? {
        val url = sourceUrl?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) throw IOException("Thumbnail request failed with HTTP ${it.code}.")
                val body = it.body ?: throw IOException("Thumbnail response was empty.")
                val bytes = body.bytes()
                if (bytes.isEmpty()) throw IOException("Thumbnail response was empty.")
                if (bytes.size > MAX_THUMBNAIL_BYTES) throw IOException("Thumbnail is too large.")

                storage.ensureDirectories()
                val contentHash = Sha256.hashBytes(bytes)
                val extension = YoutubeThumbnailRules.extension(bytes, body.contentType()?.toString())
                val file = storage.thumbnailFile(contentHash, extension)
                val createdFile = !file.exists()
                if (createdFile) file.writeBytes(bytes)
                val timestamp = now()
                PromotedYoutubeThumbnail(
                    asset = ThumbnailAssetEntity(
                        thumbnailAssetId = idFactory(),
                        kind = "YOUTUBE",
                        internalFilename = file.name,
                        sourceUrl = url,
                        contentHash = contentHash,
                        createdAt = timestamp,
                        lastAccessedAt = timestamp,
                        isPermanent = true
                    ),
                    file = file,
                    createdFile = createdFile
                )
            }
        }.getOrNull()
    }

    companion object {
        private const val MAX_THUMBNAIL_BYTES = 10 * 1024 * 1024
    }
}

data class PromotedYoutubeThumbnail(
    val asset: ThumbnailAssetEntity,
    val file: File,
    val createdFile: Boolean
) {
    fun cleanupUncommitted() {
        if (createdFile) file.delete()
    }
}

object YoutubeThumbnailRules {
    fun extension(bytes: ByteArray, contentType: String?): String = when {
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() -> "png"
        bytes.size >= 6 &&
            bytes[0] == 0x47.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() -> "gif"
        bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() &&
            bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() &&
            bytes[11] == 0x50.toByte() -> "webp"
        contentType?.contains("png", ignoreCase = true) == true -> "png"
        contentType?.contains("gif", ignoreCase = true) == true -> "gif"
        contentType?.contains("webp", ignoreCase = true) == true -> "webp"
        else -> "jpg"
    }
}
