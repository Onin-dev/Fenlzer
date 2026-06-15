package com.fenl.fenlzer.importing.local

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.dao.ImportDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.ImportHistoryEntryEntity
import com.fenl.fenlzer.data.local.entity.ImportJobEntity
import com.fenl.fenlzer.data.local.entity.ThumbnailAssetEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackOriginalMetadataEntity
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.domain.text.AudioTitleFormatter
import com.fenl.fenlzer.domain.text.SearchNormalizer
import com.fenl.fenlzer.importing.ImportPriorityClass
import com.fenl.fenlzer.importing.ImportReason
import com.fenl.fenlzer.importing.ImportSourceType
import com.fenl.fenlzer.importing.ImportExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

class LocalImportRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val importDao: ImportDao,
    private val storage: FenlzerStorage,
    private val metadataExtractor: LocalAudioMetadataExtractor,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun prepareImportJobs(uris: List<Uri>): List<ImportJobEntity> =
        withContext(dispatchers.io) {
            uris.map { uri ->
                takeReadPermission(uri)
                val source = sourceInfo(uri)
                val format = SupportedAudioFormat.fromFilenameOrMimeType(
                    source.displayName,
                    source.mimeType
                )
                val createdAt = now()
                ImportJobEntity(
                    importJobId = idFactory(),
                    jobType = JOB_TYPE_LOCAL_FILE,
                    sourceType = ImportSourceType.LOCAL_FILE,
                    reason = ImportReason.MANUAL_LOCAL,
                    priorityClass = ImportPriorityClass.MANUAL,
                    priority = PRIORITY_MANUAL,
                    status = STATUS_QUEUED,
                    sourceUrl = uri.toString(),
                    targetFavourite = false,
                    preferredFormat = format?.label.orEmpty(),
                    technicalDetailsJson = source.displayName,
                    createdAt = createdAt,
                    updatedAt = createdAt
                ).also { job -> importDao.upsertJob(job) }
            }
        }

    suspend fun executeImportJob(
        importJobId: String,
        onProgress: (LocalImportProgress) -> Unit = {}
    ): ImportExecutionResult = withContext(dispatchers.io) {
        val job = importDao.getJob(importJobId)
            ?: return@withContext ImportExecutionResult.TerminalFailure("Import job not found.")
        if (job.status == STATUS_CANCELLED) return@withContext ImportExecutionResult.Cancelled
        val sourceUri = job.sourceUrl?.let(Uri::parse)
            ?: return@withContext ImportExecutionResult.TerminalFailure("The selected file is unavailable.")

        importUri(
            uri = sourceUri,
            currentIndex = 1,
            total = 1,
            onProgress = onProgress,
            existingJob = job
        )
        when (val latest = importDao.getJob(importJobId)) {
            null -> ImportExecutionResult.TerminalFailure("Import job not found.")
            else -> when (latest.status) {
                STATUS_COMPLETED, STATUS_DUPLICATE -> ImportExecutionResult.Completed
                STATUS_CANCELLED -> ImportExecutionResult.Cancelled
                STATUS_NEEDS_ATTENTION -> ImportExecutionResult.RetryableFailure(latest.errorMessage)
                else -> ImportExecutionResult.TerminalFailure(latest.errorMessage)
            }
        }
    }

    suspend fun cancelImport(importJobId: String) = withContext(dispatchers.io) {
        val job = importDao.getJob(importJobId) ?: return@withContext
        cleanupTemporaryFiles(importJobId)
        importDao.upsertJob(
            job.copy(
                status = STATUS_CANCELLED,
                errorCode = null,
                errorMessage = "Cancelled by user.",
                isVisibleInActiveImports = true,
                updatedAt = now(),
                completedAt = now()
            )
        )
        if (importDao.countHistoryForJob(importJobId) == 0) {
            insertHistory(
                importJobId = importJobId,
                result = HISTORY_CANCELLED,
                displayTitle = job.technicalDetailsJson ?: "Local import",
                friendlyMessage = "Cancelled by user.",
                sourceUrl = job.sourceUrl
            )
        }
    }

    suspend fun prepareRetry(importJobId: String) = withContext(dispatchers.io) {
        val job = importDao.getJob(importJobId) ?: return@withContext
        importDao.upsertJob(
            job.copy(
                status = STATUS_QUEUED,
                progressPercent = 0,
                errorCode = null,
                errorMessage = null,
                attemptCount = 0,
                isVisibleInActiveImports = true,
                completedAt = null,
                updatedAt = now()
            )
        )
    }

    suspend fun finalizeRetryExhausted(importJobId: String) = withContext(dispatchers.io) {
        val job = importDao.getJob(importJobId) ?: return@withContext
        val message = job.errorMessage ?: "This local import failed after three attempts."
        importDao.upsertJob(
            job.copy(
                status = STATUS_FAILED,
                errorMessage = message,
                isVisibleInActiveImports = true,
                completedAt = now(),
                updatedAt = now()
            )
        )
        if (importDao.countHistoryForJob(importJobId) == 0) {
            insertHistory(
                importJobId = importJobId,
                result = HISTORY_FAILED,
                displayTitle = job.technicalDetailsJson ?: "Local import",
                errorCode = job.errorCode,
                friendlyMessage = message,
                sourceUrl = job.sourceUrl
            )
        }
    }

    suspend fun importUris(
        uris: List<Uri>,
        onProgress: (LocalImportProgress) -> Unit = {}
    ): LocalImportBatchResult = withContext(dispatchers.io) {
        storage.ensureDirectories()
        val startedAt = now()
        val results = uris.mapIndexed { index, uri ->
            importUri(
                uri = uri,
                currentIndex = index + 1,
                total = uris.size,
                onProgress = onProgress
            )
        }

        LocalImportBatchResult(
            items = results,
            startedAt = startedAt,
            completedAt = now()
        )
    }

    private suspend fun importUri(
        uri: Uri,
        currentIndex: Int,
        total: Int,
        onProgress: (LocalImportProgress) -> Unit,
        existingJob: ImportJobEntity? = null
    ): LocalImportItemResult {
        storage.ensureDirectories()
        takeReadPermission(uri)

        val source = sourceInfo(uri)
        val filename = source.displayName
        val format = SupportedAudioFormat.fromFilenameOrMimeType(filename, source.mimeType)
        val importJobId = existingJob?.importJobId ?: idFactory()
        val createdAt = now()
        var job = existingJob ?: ImportJobEntity(
            importJobId = importJobId,
            jobType = JOB_TYPE_LOCAL_FILE,
            sourceType = ImportSourceType.LOCAL_FILE,
            reason = ImportReason.MANUAL_LOCAL,
            priorityClass = ImportPriorityClass.MANUAL,
            priority = PRIORITY_MANUAL,
            status = STATUS_QUEUED,
            sourceUrl = uri.toString(),
            targetFavourite = false,
            preferredFormat = format?.label ?: "",
            technicalDetailsJson = filename,
            createdAt = createdAt,
            updatedAt = createdAt
        )

        suspend fun updateJob(
            status: String,
            progressPercent: Int? = null,
            actualFormat: String? = format?.label,
            errorCode: String? = null,
            errorMessage: String? = null,
            completedAt: Long? = null
        ) {
            if (importDao.getJob(importJobId)?.status == STATUS_CANCELLED) {
                throw CancellationException("Cancelled by user.")
            }
            job = job.copy(
                status = status,
                progressPercent = progressPercent,
                actualFormat = actualFormat,
                errorCode = errorCode,
                errorMessage = errorMessage,
                updatedAt = now(),
                completedAt = completedAt
            )
            importDao.upsertJob(job)
        }

        importDao.upsertJob(job)

        if (format == null) {
            val message = "Unsupported file format. Fenlzer supports MP3, M4A, WAV, FLAC, and OGG."
            updateJob(
                status = STATUS_FAILED,
                errorCode = ERROR_UNSUPPORTED_FORMAT,
                errorMessage = message,
                completedAt = now()
            )
            insertHistory(
                importJobId = importJobId,
                result = HISTORY_FAILED,
                displayTitle = filename,
                errorCode = ERROR_UNSUPPORTED_FORMAT,
                friendlyMessage = message,
                sourceUrl = uri.toString()
            )
            return LocalImportItemResult(
                filename = filename,
                displayTitle = filename,
                outcome = LocalImportOutcome.FAILED,
                sourceUri = uri,
                message = message
            )
        }

        val tempFile = File.createTempFile(
            "local-import-$importJobId-",
            ".${format.extension}",
            storage.tempImportDir
        )
        var stagedAudioFile: File? = null
        var trackInserted = false

        return try {
            onProgress(
                LocalImportProgress(
                    currentIndex = currentIndex,
                    total = total,
                    filename = filename,
                    stage = LocalImportStage.COPYING,
                    percent = 0
                )
            )
            updateJob(status = STATUS_COPYING, progressPercent = 0)
            val copyResult = copyUriToTempFile(
                uri = uri,
                tempFile = tempFile,
                totalBytes = source.sizeBytes,
                onProgress = { percent ->
                    onProgress(
                        LocalImportProgress(
                            currentIndex = currentIndex,
                            total = total,
                            filename = filename,
                            stage = LocalImportStage.COPYING,
                            percent = percent
                        )
                    )
                }
            )
            updateJob(status = STATUS_COPYING, progressPercent = 100)

            val duplicate = trackDao.getTrackByAudioHash(copyResult.sha256)
            if (duplicate != null) {
                tempFile.delete()
                val duplicateTitle = duplicate.displayTitle()
                val message = "Already imported as $duplicateTitle."
                updateJob(
                    status = STATUS_DUPLICATE,
                    progressPercent = 100,
                    errorCode = ERROR_DUPLICATE,
                    errorMessage = message,
                    completedAt = now()
                )
                insertHistory(
                    importJobId = importJobId,
                    result = HISTORY_DUPLICATE,
                    displayTitle = filename,
                    trackId = duplicate.trackId,
                    errorCode = ERROR_DUPLICATE,
                    friendlyMessage = message,
                    sourceUrl = uri.toString()
                )
                return LocalImportItemResult(
                    filename = filename,
                    displayTitle = filename,
                    outcome = LocalImportOutcome.DUPLICATE,
                    sourceUri = uri,
                    duplicateTrackId = duplicate.trackId,
                    message = message
                )
            }

            val finalAudioFile = storage.audioFile(copyResult.sha256, format.extension)
            stagedAudioFile = finalAudioFile
            moveTempFile(tempFile, finalAudioFile)
            val metadata = extractMetadata(
                file = finalAudioFile,
                filename = filename,
                currentIndex = currentIndex,
                total = total,
                onProgress = onProgress,
                updateJob = ::updateJob
            )
            val embeddedThumbnailAsset = metadata.embeddedArtwork?.let { artworkBytes ->
                persistEmbeddedArtwork(artworkBytes)
            }
            val importedAt = now()
            val trackId = idFactory()
            val track = metadata.toTrackEntity(
                trackId = trackId,
                filename = filename,
                internalFilename = finalAudioFile.name,
                audioHash = copyResult.sha256,
                fileSizeBytes = copyResult.bytesCopied,
                format = format,
                embeddedThumbnailAsset = embeddedThumbnailAsset,
                importedAt = importedAt
            )
            val originalMetadata = metadata.toOriginalMetadataEntity(
                trackId = trackId,
                thumbnailKind = if (embeddedThumbnailAsset != null) "EMBEDDED" else "NONE"
            )

            trackDao.insertTrackWithOriginalMetadata(track, originalMetadata)
            trackInserted = true
            updateJob(
                status = STATUS_COMPLETED,
                progressPercent = 100,
                completedAt = now()
            )

            val displayTitle = track.displayTitle()
            val message = if (metadata.extractionFailed) {
                "Imported with empty metadata; tags can be edited later."
            } else {
                "Imported successfully."
            }
            insertHistory(
                importJobId = importJobId,
                result = HISTORY_SUCCESS,
                displayTitle = displayTitle,
                trackId = trackId,
                friendlyMessage = message,
                sourceUrl = uri.toString()
            )
            onProgress(
                LocalImportProgress(
                    currentIndex = currentIndex,
                    total = total,
                    filename = filename,
                    stage = LocalImportStage.COMPLETE,
                    percent = 100
                )
            )

            LocalImportItemResult(
                filename = filename,
                displayTitle = displayTitle,
                outcome = LocalImportOutcome.SUCCESS,
                sourceUri = uri,
                trackId = trackId,
                message = message,
                metadataWarning = metadata.extractionFailed
            )
        } catch (cancellation: CancellationException) {
            tempFile.delete()
            if (!trackInserted) stagedAudioFile?.delete()
            throw cancellation
        } catch (throwable: Throwable) {
            tempFile.delete()
            if (!trackInserted) stagedAudioFile?.delete()
            val message = "Import failed: ${throwable.localizedMessage ?: "Unable to copy this file."}"
            updateJob(
                status = if (existingJob != null) STATUS_NEEDS_ATTENTION else STATUS_FAILED,
                errorCode = ERROR_COPY_FAILED,
                errorMessage = message,
                completedAt = if (existingJob != null) null else now()
            )
            if (existingJob == null) {
                insertHistory(
                    importJobId = importJobId,
                    result = HISTORY_FAILED,
                    displayTitle = filename,
                    errorCode = ERROR_COPY_FAILED,
                    friendlyMessage = message,
                    technicalDetailsJson = throwable::class.qualifiedName,
                    sourceUrl = uri.toString()
                )
            }
            LocalImportItemResult(
                filename = filename,
                displayTitle = filename,
                outcome = LocalImportOutcome.FAILED,
                sourceUri = uri,
                message = message
            )
        }
    }

    private suspend fun extractMetadata(
        file: File,
        filename: String,
        currentIndex: Int,
        total: Int,
        onProgress: (LocalImportProgress) -> Unit,
        updateJob: suspend (
            status: String,
            progressPercent: Int?,
            actualFormat: String?,
            errorCode: String?,
            errorMessage: String?,
            completedAt: Long?
        ) -> Unit
    ): ExtractedLocalAudioMetadata {
        onProgress(
            LocalImportProgress(
                currentIndex = currentIndex,
                total = total,
                filename = filename,
                stage = LocalImportStage.EXTRACTING_METADATA,
                percent = 100
            )
        )
        updateJob(STATUS_EXTRACTING_METADATA, 100, null, null, null, null)
        return metadataExtractor.extract(file)
    }

    private suspend fun copyUriToTempFile(
        uri: Uri,
        tempFile: File,
        totalBytes: Long?,
        onProgress: (Int) -> Unit
    ): FileCopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesCopied = 0L
        var lastPercent = 0

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open selected file.")

        input.use { source ->
            tempFile.outputStream().use { target ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read == -1) break
                    target.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    bytesCopied += read

                    if (totalBytes != null && totalBytes > 0L) {
                        val percent = ((bytesCopied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        if (percent >= lastPercent + 5 || percent == 100) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        }

        return FileCopyResult(
            sha256 = Sha256.bytesToHex(digest.digest()),
            bytesCopied = bytesCopied
        )
    }

    private fun moveTempFile(tempFile: File, finalAudioFile: File) {
        if (finalAudioFile.exists()) {
            tempFile.delete()
            return
        }
        if (!tempFile.renameTo(finalAudioFile)) {
            tempFile.copyTo(finalAudioFile, overwrite = false)
            tempFile.delete()
        }
    }

    private fun cleanupTemporaryFiles(importJobId: String) {
        storage.ensureDirectories()
        storage.tempImportDir.listFiles()
            ?.filter { file -> file.name.startsWith("local-import-$importJobId-") }
            ?.forEach(File::delete)
    }

    private suspend fun persistEmbeddedArtwork(bytes: ByteArray): ThumbnailAssetEntity? {
        return try {
            val contentHash = Sha256.hashBytes(bytes)
            val extension = guessArtworkExtension(bytes)
            val thumbnailFile = storage.thumbnailFile(contentHash, extension)
            if (!thumbnailFile.exists()) {
                thumbnailFile.writeBytes(bytes)
            }
            ThumbnailAssetEntity(
                thumbnailAssetId = idFactory(),
                kind = "EMBEDDED",
                internalFilename = thumbnailFile.name,
                sourceUrl = null,
                contentHash = contentHash,
                createdAt = now(),
                lastAccessedAt = now(),
                isPermanent = true
            ).also { asset ->
                trackDao.upsertThumbnailAsset(asset)
            }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private suspend fun insertHistory(
        importJobId: String,
        result: String,
        displayTitle: String,
        trackId: String? = null,
        errorCode: String? = null,
        friendlyMessage: String? = null,
        technicalDetailsJson: String? = null,
        sourceUrl: String? = null
    ) {
        val job = importDao.getJob(importJobId)
        importDao.insertHistoryEntry(
            ImportHistoryEntryEntity(
                historyId = idFactory(),
                importJobId = importJobId,
                result = result,
                reason = job?.reason ?: ImportReason.MANUAL_LOCAL,
                sourceType = job?.sourceType ?: ImportSourceType.LOCAL_FILE,
                jobType = job?.jobType ?: JOB_TYPE_LOCAL_FILE,
                requestedFormat = job?.preferredFormat,
                finalFormat = job?.actualFormat,
                pendingActionType = job?.pendingActionType,
                trackId = trackId,
                sourceUrl = sourceUrl,
                youtubeVideoId = null,
                displayTitle = displayTitle,
                errorCode = errorCode,
                friendlyMessage = friendlyMessage,
                technicalDetailsJson = technicalDetailsJson,
                createdAt = now()
            )
        )
    }

    private fun sourceInfo(uri: Uri): LocalSourceInfo {
        val displayNameFromQuery = queryOpenableColumn(uri, OpenableColumns.DISPLAY_NAME)
        val sizeFromQuery = queryOpenableColumn(uri, OpenableColumns.SIZE)?.toLongOrNull()
        return LocalSourceInfo(
            displayName = displayNameFromQuery
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "selected-audio-file",
            sizeBytes = sizeFromQuery,
            mimeType = context.contentResolver.getType(uri)
        )
    }

    private fun queryOpenableColumn(uri: Uri, columnName: String): String? {
        val cursor: Cursor = try {
            context.contentResolver.query(uri, arrayOf(columnName), null, null, null)
        } catch (_: RuntimeException) {
            null
        } ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null
            val columnIndex = it.getColumnIndex(columnName)
            if (columnIndex == -1 || it.isNull(columnIndex)) return null
            return it.getString(columnIndex)
        }
    }

    private fun takeReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers grant transient access only; import immediately with that grant.
        } catch (_: IllegalArgumentException) {
            // File URIs and non-document providers cannot be persisted.
        }
    }

    private fun ExtractedLocalAudioMetadata.toTrackEntity(
        trackId: String,
        filename: String,
        internalFilename: String,
        audioHash: String,
        fileSizeBytes: Long,
        format: SupportedAudioFormat,
        embeddedThumbnailAsset: ThumbnailAssetEntity?,
        importedAt: Long
    ): TrackEntity {
        val displayTitle = AudioTitleFormatter.importedTrackTitle(
            metadataTitle = title,
            filename = filename
        )

        return TrackEntity(
            trackId = trackId,
            title = displayTitle,
            titleSortKey = SearchNormalizer.sortKey(displayTitle),
            artist = artist,
            artistSortKey = SearchNormalizer.sortKey(artist),
            album = album,
            albumSortKey = SearchNormalizer.sortKey(album),
            albumArtist = albumArtist,
            albumArtistSortKey = SearchNormalizer.sortKey(albumArtist),
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            durationMs = durationMs,
            notes = "",
            sourceType = ImportSourceType.LOCAL_FILE,
            importReason = ImportReason.MANUAL_LOCAL,
            requestedDownloadFormat = format.label,
            youtubeVideoId = null,
            sourceUrl = null,
            originalFilename = filename,
            internalFilename = internalFilename,
            audioHash = audioHash,
            fileSizeBytes = fileSizeBytes,
            finalAudioFormat = format.label,
            thumbnailAssetId = null,
            embeddedThumbnailAssetId = embeddedThumbnailAsset?.thumbnailAssetId,
            remoteThumbnailUrl = null,
            isFavourite = false,
            favouritedAt = null,
            importedAt = importedAt,
            updatedAt = importedAt
        )
    }

    private fun ExtractedLocalAudioMetadata.toOriginalMetadataEntity(
        trackId: String,
        thumbnailKind: String
    ): TrackOriginalMetadataEntity {
        return TrackOriginalMetadataEntity(
            trackId = trackId,
            originalTitle = title,
            originalArtist = artist,
            originalAlbum = album,
            originalAlbumArtist = albumArtist,
            originalGenre = genre,
            originalYear = year,
            originalTrackNumber = trackNumber,
            originalDiscNumber = discNumber,
            originalThumbnailKind = thumbnailKind,
            rawMetadataJson = null
        )
    }

    private fun TrackEntity.displayTitle(): String =
        AudioTitleFormatter.displayTitle(title = title, fallbackFilename = originalFilename)

    private fun guessArtworkExtension(bytes: ByteArray): String {
        return when {
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> "png"

            bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() &&
                bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() &&
                bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() &&
                bytes[11] == 0x50.toByte() -> "webp"

            else -> "jpg"
        }
    }

    private data class LocalSourceInfo(
        val displayName: String,
        val sizeBytes: Long?,
        val mimeType: String?
    )

    private data class FileCopyResult(
        val sha256: String,
        val bytesCopied: Long
    )

    companion object {
        private const val JOB_TYPE_LOCAL_FILE = "LOCAL_FILE"
        private const val PRIORITY_MANUAL = 1_000
        private const val STATUS_QUEUED = "QUEUED"
        private const val STATUS_COPYING = "COPYING"
        private const val STATUS_EXTRACTING_METADATA = "EXTRACTING_METADATA"
        private const val STATUS_COMPLETED = "COMPLETED"
        private const val STATUS_DUPLICATE = "DUPLICATE"
        private const val STATUS_CANCELLED = "CANCELLED"
        private const val STATUS_NEEDS_ATTENTION = "NEEDS_ATTENTION"
        private const val STATUS_FAILED = "FAILED"
        private const val HISTORY_SUCCESS = "SUCCESS"
        private const val HISTORY_DUPLICATE = "DUPLICATE"
        private const val HISTORY_FAILED = "FAILED"
        private const val HISTORY_CANCELLED = "CANCELLED"
        private const val ERROR_UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT"
        private const val ERROR_DUPLICATE = "DUPLICATE_AUDIO_HASH"
        private const val ERROR_COPY_FAILED = "LOCAL_COPY_FAILED"
    }
}
