package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.dao.DiscoverRemoteItemRow
import com.fenl.fenlzer.data.local.dao.PlaybackDao
import com.fenl.fenlzer.data.local.dao.RemoteDiscoverDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.DiscoverRefreshDiagnosticsEntity
import com.fenl.fenlzer.data.local.entity.DiscoverSnapshotEntity
import com.fenl.fenlzer.data.local.entity.DiscoverSnapshotItemEntity
import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.RemoteItemEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.remote.ApiJobReason
import com.fenl.fenlzer.data.remote.ApiRepository
import com.fenl.fenlzer.data.remote.CompleteUploadRequest
import com.fenl.fenlzer.data.remote.CreateHistoryUploadRequest
import com.fenl.fenlzer.data.remote.DiscoverItem
import com.fenl.fenlzer.data.remote.DiscoverRefreshData
import com.fenl.fenlzer.data.remote.DiscoverRefreshRequest
import com.fenl.fenlzer.data.remote.FenlzerApiFactory
import com.fenl.fenlzer.importing.youtube.YoutubeImportCoordinator
import com.fenl.fenlzer.importing.youtube.YoutubeImportItemResult
import com.fenl.fenlzer.importing.ImportIntent
import com.fenl.fenlzer.importing.youtube.YoutubeSearchResultItem
import com.fenl.fenlzer.playback.RemoteStreamResolver
import com.fenl.fenlzer.playback.RemoteStreamResolver.Companion.STREAM_REMOTE_ONLY
import com.fenl.fenlzer.playback.RemoteStreamResolver.Companion.STREAM_READY
import com.github.luben.zstd.Zstd
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class DiscoverRepository(
    private val apiRepository: ApiRepository,
    private val trackDao: TrackDao,
    private val playbackDao: PlaybackDao,
    private val remoteDiscoverDao: RemoteDiscoverDao,
    private val queueRepository: QueueRepository,
    private val streamResolver: RemoteStreamResolver,
    private val youtubeImportCoordinator: YoutubeImportCoordinator,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun observeDiscover(): Flow<DiscoverUiState> {
        return combine(
            remoteDiscoverDao.observeLatestDiscoverSnapshot(),
            remoteDiscoverDao.observeLatestDiscoverItems()
        ) { snapshot, rows ->
            DiscoverUiState(
                snapshotId = snapshot?.snapshotId,
                generatedAt = snapshot?.generatedAt,
                refreshType = snapshot?.refreshType,
                finalDisplayedCount = snapshot?.finalDisplayedCount ?: rows.size,
                showBroaderRefresh = snapshot?.finalDisplayedCount?.let { it < 5 } == true,
                items = rows.map { it.toUiItem() }
            )
        }
    }

    suspend fun markOpened() = withContext(dispatchers.io) {
        val snapshot = remoteDiscoverDao.getLatestDiscoverSnapshot() ?: return@withContext
        remoteDiscoverDao.markSnapshotOpened(snapshot.snapshotId, now())
    }

    suspend fun refresh(broader: Boolean = false): DiscoverRefreshSummary = withContext(dispatchers.io) {
        val tracks = trackDao.getTracksByRecentlyAdded()
        val events = playbackDao.getNonPrivatePlaybackEvents()
        val historyUploadId = uploadHistory(events = events, tracks = tracks)
        val previousSnapshot = remoteDiscoverDao.getLatestDiscoverSnapshot()
        val request = DiscoverRefreshRequest(
            historyUploadId = historyUploadId,
            targetDisplayCount = 25,
            maxCandidateCount = 75,
            strictlyExcludeImported = true,
            clientLibrary = tracks.toClientLibrary(),
            broadenReason = if (broader) "TOO_FEW_RESULTS" else null,
            previousSnapshotId = if (broader) previousSnapshot?.snapshotId else null
        )
        val response = if (broader) {
            apiRepository.refreshDiscoverBroader(request)
        } else {
            apiRepository.refreshDiscover(request)
        }
        persistRefresh(response, tracks)
        DiscoverRefreshSummary(
            itemCount = response.items.count { !it.alreadyImported },
            broaderAvailable = response.refreshBroaderAvailable || response.finalDisplayedCount < 5,
            refreshType = response.refreshType
        )
    }

    suspend fun playFromDiscover(remoteItemId: String) = withContext(dispatchers.io) {
        val rows = remoteDiscoverDao.getLatestDiscoverItems()
        if (rows.none { it.remoteItemId == remoteItemId }) return@withContext
        prefetchRows(rows, remoteItemId)
        queueRepository.playFromDiscover(
            remoteItemIds = rows.map { it.remoteItemId },
            startRemoteItemId = remoteItemId
        )
    }

    suspend fun preparePlayFromDiscover(remoteItemId: String): List<String> = withContext(dispatchers.io) {
        val rows = remoteDiscoverDao.getLatestDiscoverItems()
        if (rows.none { it.remoteItemId == remoteItemId }) return@withContext emptyList()
        prefetchRows(rows, remoteItemId)
        rows.map { it.remoteItemId }
    }

    suspend fun prepareRemote(remoteItemId: String, reason: String) = withContext(dispatchers.io) {
        streamResolver.resolve(remoteItemId, reason = reason)
    }

    suspend fun playNext(remoteItemId: String) = withContext(dispatchers.io) {
        streamResolver.resolve(remoteItemId, reason = "PLAY_NEXT_REMOTE")
        queueRepository.playNextRemote(remoteItemId)
    }

    suspend fun addToQueue(remoteItemId: String) = withContext(dispatchers.io) {
        streamResolver.resolve(remoteItemId, reason = "ADD_REMOTE_TO_QUEUE")
        queueRepository.addRemoteToQueue(remoteItemId)
    }

    suspend fun importRemote(remoteItemId: String, favourite: Boolean = false): YoutubeImportItemResult =
        withContext(dispatchers.io) {
            val remoteItem = remoteDiscoverDao.getRemoteItem(remoteItemId)
                ?: throw IllegalArgumentException("Remote item no longer exists.")
            youtubeImportCoordinator.importSearchResult(
                result = remoteItem.toYoutubeSearchResult(),
                intent = if (favourite) {
                    ImportIntent.discoverAutoFavourite()
                } else {
                    ImportIntent.discoverManual()
                }
            ).await()
        }

    private suspend fun prefetchRows(rows: List<DiscoverRemoteItemRow>, currentRemoteItemId: String) {
        val currentIndex = rows.indexOfFirst { it.remoteItemId == currentRemoteItemId }
        if (currentIndex == -1) return
        rows.drop(currentIndex)
            .take(3)
            .filter { it.canStream }
            .forEachIndexed { index, row ->
                streamResolver.resolve(
                    remoteItemId = row.remoteItemId,
                    reason = if (index == 0) "CURRENT_REMOTE_STREAM" else "PREFETCH_NEXT_TWO"
                )
            }
    }

    private suspend fun uploadHistory(
        events: List<PlaybackEventEntity>,
        tracks: List<TrackEntity>
    ): String {
        val tracksById = tracks.associateBy { it.trackId }
        val chunkJson = buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("chunkIndex", JsonPrimitive(0))
            put("chunkCount", JsonPrimitive(1))
            put(
                "events",
                buildJsonArray {
                    events.forEach { event -> add(event.toDiscoverJson(tracksById[event.trackId])) }
                }
            )
        }
        val rawBytes = FenlzerApiFactory.json.encodeToString(JsonObject.serializer(), chunkJson)
            .toByteArray()
        val compressed = Zstd.compress(rawBytes)
        val upload = apiRepository.createHistoryUpload(
            CreateHistoryUploadRequest(
                clientUploadId = "hist_${idFactory()}",
                compression = "zstd",
                estimatedEventCount = events.size,
                estimatedCompressedChunkCount = 1,
                schemaVersion = 1,
                excludedPrivateModeEvents = true
            )
        )
        apiRepository.uploadHistoryChunk(
            uploadId = upload.uploadId,
            chunkIndex = 0,
            chunkCount = 1,
            compressedBody = compressed
        )
        apiRepository.completeHistoryUpload(
            uploadId = upload.uploadId,
            request = CompleteUploadRequest(
                chunkCount = 1,
                overallSha256 = compressed.sha256(),
                totalEventCount = events.size
            )
        )
        return upload.uploadId
    }

    private suspend fun persistRefresh(response: DiscoverRefreshData, tracks: List<TrackEntity>) {
        val importedVideoIds = tracks.mapNotNull { it.youtubeVideoId }.toSet()
        val importedUrls = tracks.mapNotNull { it.sourceUrl }.toSet()
        val createdAt = now()
        val validItems = response.items
            .filterNot { it.alreadyImported }
            .filterNot { it.youtubeVideoId != null && it.youtubeVideoId in importedVideoIds }
            .filterNot { it.sourceUrl != null && it.sourceUrl in importedUrls }
            .filterNot { it.isLive || it.isUnavailable }
            .take(25)
        val snapshot = DiscoverSnapshotEntity(
            snapshotId = response.snapshotId,
            generatedAt = parseInstant(response.generatedAt) ?: createdAt,
            lastOpenedAt = createdAt,
            refreshType = response.refreshType,
            candidateRequestTarget = response.candidateRequestTarget,
            finalDisplayedCount = validItems.size,
            refreshDetailsVisible = response.refreshBroaderAvailable || validItems.size < 5
        )
        val remoteItems = validItems.map { item -> item.toRemoteItem(createdAt) }
        val snapshotItems = validItems.mapIndexed { index, item ->
            DiscoverSnapshotItemEntity(
                snapshotId = response.snapshotId,
                remoteItemId = item.remoteItemId,
                position = item.position ?: index,
                recommendationReason = item.recommendationReason
            )
        }
        val diagnostics = DiscoverRefreshDiagnosticsEntity(
            snapshotId = response.snapshotId,
            candidatesRequested = response.diagnostics.intValue("candidatesRequested")
                ?: response.candidateRequestTarget,
            candidatesReceived = response.diagnostics.intValue("candidatesReceived")
                ?: response.items.size,
            alreadyImportedFiltered = response.diagnostics.intValue("alreadyImportedFiltered")
                ?: (response.items.size - validItems.size),
            invalidOrUnavailableFiltered = response.diagnostics.intValue("invalidOrUnavailableFiltered")
                ?: 0,
            finalDisplayedCount = validItems.size,
            refreshBroaderShown = response.refreshBroaderAvailable || validItems.size < 5,
            apiRequestIdsJson = "{}"
        )
        remoteDiscoverDao.replaceSnapshot(
            snapshot = snapshot,
            remoteItems = remoteItems,
            snapshotItems = snapshotItems,
            diagnostics = diagnostics
        )
    }

    private fun DiscoverItem.toRemoteItem(createdAt: Long): RemoteItemEntity =
        RemoteItemEntity(
            remoteItemId = remoteItemId,
            youtubeVideoId = youtubeVideoId,
            sourceUrl = sourceUrl,
            title = title,
            artistOrChannel = artistOrChannel,
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl,
            canStream = canStream,
            canDownload = canDownload,
            streamState = STREAM_REMOTE_ONLY,
            importState = "NOT_IMPORTED",
            createdAt = createdAt,
            updatedAt = createdAt
        )

    private fun RemoteItemEntity.toYoutubeSearchResult(): YoutubeSearchResultItem =
        YoutubeSearchResultItem(
            remoteItemId = remoteItemId,
            youtubeVideoId = youtubeVideoId,
            sourceUrl = sourceUrl,
            title = title,
            artistOrChannel = artistOrChannel,
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl,
            canStream = canStream,
            canDownload = canDownload,
            isLive = false,
            isUnavailable = !canDownload
        )

    private fun DiscoverRemoteItemRow.toUiItem(): DiscoverUiItem =
        DiscoverUiItem(
            remoteItemId = remoteItemId,
            title = title,
            artistOrChannel = artistOrChannel.orEmpty(),
            durationMs = durationMs ?: 0L,
            thumbnailUrl = thumbnailUrl,
            recommendationReason = recommendationReason,
            canStream = canStream,
            canDownload = canDownload,
            streamState = streamState,
            importState = importState,
            importedTrackId = importedTrackId,
            isReadyToStream = streamState == STREAM_READY && !lastPlayableUrl.isNullOrBlank()
        )

    private fun PlaybackEventEntity.toDiscoverJson(track: TrackEntity?): JsonObject =
        buildJsonObject {
            put("eventId", JsonPrimitive(eventId))
            trackId?.let { put("trackId", JsonPrimitive(it)) }
            remoteItemId?.let { put("remoteItemId", JsonPrimitive(it)) }
            track?.youtubeVideoId?.let { put("youtubeVideoId", JsonPrimitive(it)) }
            track?.title?.let { put("title", JsonPrimitive(it)) }
            track?.artist?.let { put("artist", JsonPrimitive(it)) }
            track?.album?.let { put("album", JsonPrimitive(it)) }
            track?.genre?.let { put("genre", JsonPrimitive(it)) }
            put("startedAt", JsonPrimitive(Instant.ofEpochMilli(startedAt).toString()))
            put("listenedMs", JsonPrimitive(listenedMs))
            put("durationMsAtPlayback", JsonPrimitive(durationMsAtPlayback))
            put("validListen", JsonPrimitive(validListen))
            put("skip", JsonPrimitive(skip))
            put("completion", JsonPrimitive(completion))
            put("completionPercent", JsonPrimitive(completionPercent))
            put("sourceContext", JsonPrimitive(sourceContext))
        }

    private fun List<TrackEntity>.toClientLibrary(): JsonObject =
        buildJsonObject {
            put("trackCount", JsonPrimitive(size))
            put("youtubeVideoIds", JsonArray(mapNotNull { it.youtubeVideoId }.map(::JsonPrimitive)))
            put("sourceUrls", JsonArray(mapNotNull { it.sourceUrl }.map(::JsonPrimitive)))
            put("audioHashes", JsonArray(map { JsonPrimitive(it.audioHash) }))
        }

    private fun JsonObject.intValue(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun parseInstant(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

    private fun ByteArray.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class DiscoverUiState(
    val snapshotId: String? = null,
    val generatedAt: Long? = null,
    val refreshType: String? = null,
    val finalDisplayedCount: Int = 0,
    val showBroaderRefresh: Boolean = false,
    val items: List<DiscoverUiItem> = emptyList()
)

data class DiscoverUiItem(
    val remoteItemId: String,
    val title: String,
    val artistOrChannel: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val recommendationReason: String?,
    val canStream: Boolean,
    val canDownload: Boolean,
    val streamState: String,
    val importState: String,
    val importedTrackId: String?,
    val isReadyToStream: Boolean
)

data class DiscoverRefreshSummary(
    val itemCount: Int,
    val broaderAvailable: Boolean,
    val refreshType: String
)
