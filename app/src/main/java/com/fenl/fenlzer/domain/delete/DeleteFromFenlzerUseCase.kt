package com.fenl.fenlzer.domain.delete

import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.dao.QueueDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.QueueItemEntity
import com.fenl.fenlzer.data.local.entity.QueueStateEntity
import com.fenl.fenlzer.data.local.entity.ThumbnailAssetEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.storage.FenlzerStorage
import java.io.File
import kotlinx.coroutines.withContext

class DeleteFromFenlzerUseCase(
    private val trackDao: TrackDao,
    private val queueDao: QueueDao,
    private val storage: FenlzerStorage,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun deleteTracks(trackIds: Collection<String>): DeleteFromFenlzerResult =
        withContext(dispatchers.io) {
            val uniqueTrackIds = trackIds.filter { it.isNotBlank() }.distinct()
            if (uniqueTrackIds.isEmpty()) return@withContext DeleteFromFenlzerResult()
            deleteTrackEntities(trackDao.getTracksByIds(uniqueTrackIds))
        }

    suspend fun deleteAllSongs(): DeleteFromFenlzerResult = withContext(dispatchers.io) {
        deleteTrackEntities(trackDao.getAllTracks())
    }

    private suspend fun deleteTrackEntities(tracks: List<TrackEntity>): DeleteFromFenlzerResult {
        if (tracks.isEmpty()) return DeleteFromFenlzerResult()

        val audioFiles = tracks.map { track -> File(storage.audioDir, track.internalFilename) }
        val thumbnailAssets = tracks
            .flatMap { track -> listOfNotNull(track.thumbnailAssetId, track.embeddedThumbnailAssetId) }
            .distinct()
            .mapNotNull { thumbnailAssetId -> trackDao.getThumbnailAsset(thumbnailAssetId) }
        val trackIds = tracks.map { it.trackId }
        val stateBeforeDelete = queueDao.getQueueState()
        val currentPositionBeforeDelete = stateBeforeDelete
            ?.currentQueueItemId
            ?.let { currentQueueItemId ->
                queueDao.getQueueItems().firstOrNull { it.queueItemId == currentQueueItemId }
            }
            ?.takeIf { it.trackId in trackIds }
            ?.position

        trackDao.deleteTracks(trackIds)
        repairQueueAfterCascade(currentPositionBeforeDelete)

        val deletedAudioFiles = audioFiles.count { file -> !file.exists() || file.delete() }
        val deletedThumbnailFiles = deleteOrphanThumbnailAssets(thumbnailAssets)

        return DeleteFromFenlzerResult(
            deletedTracks = tracks.size,
            deletedAudioFiles = deletedAudioFiles,
            deletedThumbnailFiles = deletedThumbnailFiles
        )
    }

    private suspend fun repairQueueAfterCascade(deletedCurrentPosition: Int?) {
        val state = queueDao.getQueueState() ?: return
        val remainingItems = queueDao.getQueueItems()
        val currentStillExists = remainingItems.any { it.queueItemId == state.currentQueueItemId }
        val nextCurrentId = when {
            remainingItems.isEmpty() -> null
            currentStillExists -> state.currentQueueItemId
            deletedCurrentPosition != null -> {
                remainingItems
                    .firstOrNull { it.position > deletedCurrentPosition }
                    ?.queueItemId
                    ?: remainingItems.first().queueItemId
            }
            else -> remainingItems.first().queueItemId
        }
        val repairedItems = remainingItems.mapIndexed { index, item ->
            item.copy(
                position = index,
                state = if (item.queueItemId == nextCurrentId) STATE_CURRENT else STATE_UPCOMING
            )
        }
        val repairedState = state.copy(
            currentQueueItemId = nextCurrentId,
            sourceType = if (state.sourceType == "QUEUE") state.sourceType else "QUEUE",
            sourceId = null,
            sourceLabel = state.sourceLabel
                .takeIf { it.endsWith("Modified") }
                ?: "${state.sourceLabel} - Modified",
            isModified = true,
            playbackPositionMs = 0L,
            wasPlaying = nextCurrentId != null && state.wasPlaying,
            updatedAt = now()
        )
        queueDao.replaceQueue(repairedState, repairedItems)
    }

    private suspend fun deleteOrphanThumbnailAssets(thumbnailAssets: List<ThumbnailAssetEntity>): Int {
        var deletedFiles = 0
        thumbnailAssets.forEach { asset ->
            val trackRefs = trackDao.countTrackThumbnailReferences(asset.thumbnailAssetId)
            val playlistRefs = trackDao.countPlaylistThumbnailReferences(asset.thumbnailAssetId)
            if (trackRefs == 0 && playlistRefs == 0) {
                trackDao.deleteThumbnailAsset(asset.thumbnailAssetId)
                val file = File(storage.thumbnailsDir, asset.internalFilename)
                if (!file.exists() || file.delete()) {
                    deletedFiles += 1
                }
            }
        }
        return deletedFiles
    }

    private companion object {
        const val STATE_CURRENT = "CURRENT"
        const val STATE_UPCOMING = "UPCOMING"
    }
}

data class DeleteFromFenlzerResult(
    val deletedTracks: Int = 0,
    val deletedAudioFiles: Int = 0,
    val deletedThumbnailFiles: Int = 0
)
