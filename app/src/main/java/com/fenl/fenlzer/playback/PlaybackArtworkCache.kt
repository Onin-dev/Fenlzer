package com.fenl.fenlzer.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.min

class PlaybackArtworkCache(
    private val context: Context,
    private val targetSizePx: Int = 1024
) {
    private val cacheDir = File(context.cacheDir, "playback_artwork")

    fun cachedSquareArtworkUriFor(
        sourceUri: Uri?,
        fallbackUri: Uri? = null
    ): Uri? {
        return artworkCandidates(sourceUri, fallbackUri)
            .firstNotNullOfOrNull(::cachedSquareArtworkUriForCandidate)
    }

    suspend fun squareArtworkUriFor(
        sourceUri: Uri?,
        fallbackUri: Uri? = null
    ): Uri? = withContext(Dispatchers.IO) {
        for (candidate in artworkCandidates(sourceUri, fallbackUri)) {
            val cachedUri = squareArtworkUriForCandidate(candidate)
            if (cachedUri != null) return@withContext cachedUri
        }
        null
    }

    private fun artworkCandidates(sourceUri: Uri?, fallbackUri: Uri?): List<Uri> =
        listOfNotNull(
            sourceUri?.youtubeHighResolutionVariants(),
            listOfNotNull(sourceUri),
            fallbackUri?.youtubeHighResolutionVariants(),
            listOfNotNull(fallbackUri)
        )
            .flatten()
            .distinctBy { it.toString() }

    private fun cachedSquareArtworkUriForCandidate(sourceUri: Uri): Uri? {
        val output = File(cacheDir, "${sourceUri.toString().sha256()}.jpg")
        return if (output.exists() && output.length() > 0L) {
            Uri.fromFile(output)
        } else {
            null
        }
    }

    private suspend fun squareArtworkUriForCandidate(sourceUri: Uri): Uri? =
        runCatching {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val output = File(cacheDir, "${sourceUri.toString().sha256()}.jpg")
            if (output.exists() && output.length() > 0L) {
                return@runCatching Uri.fromFile(output)
            }

            val request = ImageRequest.Builder(context)
                .data(sourceUri)
                .size(targetSizePx, targetSizePx)
                .build()
            val image = (context.imageLoader.execute(request) as? SuccessResult)
                ?.image
                ?: return@runCatching null
            val source = image.toBitmap(
                image.width.takeIf { it > 0 } ?: targetSizePx,
                image.height.takeIf { it > 0 } ?: targetSizePx
            )
            val cropped = source.centerCroppedSquare(targetSizePx)

            FileOutputStream(output).use { stream ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 94, stream)
            }
            Uri.fromFile(output)
        }.getOrNull()

    private fun Bitmap.centerCroppedSquare(size: Int): Bitmap {
        val sourceSide = min(width, height).coerceAtLeast(1)
        val left = ((width - sourceSide) / 2).coerceAtLeast(0)
        val top = ((height - sourceSide) / 2).coerceAtLeast(0)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val sourceRect = Rect(left, top, left + sourceSide, top + sourceSide)
        val targetRect = Rect(0, 0, size, size)
        Canvas(output).drawBitmap(
            this,
            sourceRect,
            targetRect,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return output
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun Uri.youtubeHighResolutionVariants(): List<Uri> {
        val normalizedHost = host?.lowercase() ?: return emptyList()
        if (!normalizedHost.contains("ytimg.com") && !normalizedHost.contains("youtube.com")) {
            return emptyList()
        }
        val segments = pathSegments
        val videoSegmentIndex = segments.indexOfFirst { segment ->
            segment == "vi" || segment == "vi_webp"
        }
        val videoId = segments.getOrNull(videoSegmentIndex + 1)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        return listOf(
            Uri.parse("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"),
            Uri.parse("https://i.ytimg.com/vi/$videoId/sddefault.jpg")
        )
    }
}
