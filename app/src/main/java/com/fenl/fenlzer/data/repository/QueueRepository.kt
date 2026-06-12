package com.fenl.fenlzer.data.repository

import android.net.Uri
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.dao.QueueDao
import com.fenl.fenlzer.data.local.dao.RemoteDiscoverDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.QueueItemEntity
import com.fenl.fenlzer.data.local.entity.QueueStateEntity
import com.fenl.fenlzer.data.local.entity.RemoteItemEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.settings.AppSettingsRepository
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.domain.text.AudioTitleFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.random.Random

class QueueRepository(
    private val queueDao: QueueDao,
    private val trackDao: TrackDao,
    private val remoteDiscoverDao: RemoteDiscoverDao? = null,
    private val storage: FenlzerStorage,
    private val settingsRepository: AppSettingsRepository? = null,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun observeQueue(): Flow<PersistentQueue> {
        return combine(
            queueDao.observeQueueState(),
            queueDao.observeQueueItems(),
            trackDao.observeTracksByRecentlyAdded()
        ) { state, items, _ ->
            val queueState = state ?: defaultQueueState()
            queueState.toPersistentQueue(items)
        }
    }

    suspend fun snapshot(): PersistentQueue = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState().also {
            queueDao.upsertQueueState(it)
        }
        state.toPersistentQueue(queueDao.getQueueItems())
    }

    suspend fun playFromHome(
        trackId: String,
        searchActive: Boolean
    ): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val item = newQueueItem(trackId, insertedBy = QueueListEditor.INSERTED_BY_TAP)
        val edited = QueueListEditor.playFromHome(queueDao.getQueueItems(), item)
        val updatedState = state.copy(
            sourceType = if (searchActive) "HOME_SEARCH" else "HOME",
            sourceId = null,
            sourceLabel = if (searchActive) "Queue from Home Search" else "Queue from Home",
            isModified = false,
            currentQueueItemId = edited.currentQueueItemId,
            shuffleEnabled = settingsRepository?.settings?.value?.defaultShuffleEnabled ?: state.shuffleEnabled,
            playbackPositionMs = 0L,
            wasPlaying = true,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun playNext(trackId: String): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.playNext(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = state.currentQueueItemId,
            newItem = newQueueItem(trackId, insertedBy = QueueListEditor.INSERTED_BY_PLAY_NEXT)
        )
        val updatedState = state.markModified(edited)
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun addToQueue(trackId: String): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.addToQueue(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = state.currentQueueItemId,
            newItem = newQueueItem(trackId, insertedBy = QueueListEditor.INSERTED_BY_ADD_TO_QUEUE)
        )
        val updatedState = state.markModified(edited)
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun playFromDiscover(
        remoteItemIds: List<String>,
        startRemoteItemId: String
    ): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val uniqueRemoteIds = remoteItemIds.distinct()
        val currentId = startRemoteItemId.takeIf { it in uniqueRemoteIds } ?: uniqueRemoteIds.firstOrNull()
        val items = uniqueRemoteIds.map { remoteItemId ->
            newRemoteQueueItem(
                remoteItemId = remoteItemId,
                insertedBy = QueueListEditor.INSERTED_BY_DISCOVER_START
            )
        }
        val currentQueueItemId = items.firstOrNull { it.remoteItemId == currentId }?.queueItemId
        val edited = QueueListEditor.markCurrent(items, currentQueueItemId)
        val updatedState = state.copy(
            sourceType = "DISCOVER",
            sourceId = null,
            sourceLabel = "Queue from Discover",
            isModified = false,
            currentQueueItemId = edited.currentQueueItemId,
            playbackPositionMs = 0L,
            wasPlaying = edited.currentQueueItemId != null,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun playNextRemote(remoteItemId: String): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.playNext(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = state.currentQueueItemId,
            newItem = newRemoteQueueItem(
                remoteItemId = remoteItemId,
                insertedBy = QueueListEditor.INSERTED_BY_PLAY_NEXT
            )
        )
        val updatedState = state.markModified(edited)
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun addRemoteToQueue(remoteItemId: String): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.addToQueue(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = state.currentQueueItemId,
            newItem = newRemoteQueueItem(
                remoteItemId = remoteItemId,
                insertedBy = QueueListEditor.INSERTED_BY_ADD_TO_QUEUE
            )
        )
        val updatedState = state.markModified(edited)
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun replaceWithTrackList(
        trackIds: List<String>,
        startTrackId: String? = null,
        sourceType: String,
        sourceId: String?,
        sourceLabel: String,
        insertedBy: String,
        shuffle: Boolean = false
    ): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val effectiveShuffle = shuffle || (settingsRepository?.settings?.value?.defaultShuffleEnabled == true)
        val orderedTrackIds = if (effectiveShuffle) {
            trackIds.distinct().shuffled(Random(now()))
        } else {
            trackIds.distinct()
        }
        val edited = QueueListEditor.replaceWithTrackIds(
            trackIds = orderedTrackIds,
            startTrackId = startTrackId,
            queueStateId = QueueDao.DEFAULT_QUEUE_STATE_ID,
            insertedBy = insertedBy,
            now = now,
            idFactory = idFactory
        )
        val updatedState = state.copy(
            sourceType = sourceType,
            sourceId = sourceId,
            sourceLabel = sourceLabel,
            isModified = false,
            currentQueueItemId = edited.currentQueueItemId,
            repeatMode = state.repeatMode,
            shuffleEnabled = effectiveShuffle,
            playbackPositionMs = 0L,
            wasPlaying = edited.currentQueueItemId != null,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun markCurrent(
        queueItemId: String?,
        playbackPositionMs: Long = 0L,
        wasPlaying: Boolean? = null
    ): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.markCurrent(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = queueItemId
        )
        val updatedState = state.copy(
            currentQueueItemId = queueItemId,
            playbackPositionMs = playbackPositionMs,
            wasPlaying = wasPlaying ?: state.wasPlaying,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun persistPlaybackState(
        playbackPositionMs: Long,
        wasPlaying: Boolean
    ) = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        queueDao.upsertQueueState(
            state.copy(
                playbackPositionMs = playbackPositionMs.coerceAtLeast(0L),
                wasPlaying = wasPlaying,
                updatedAt = now()
            )
        )
    }

    suspend fun removeQueueItem(queueItemId: String): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.removeItem(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = state.currentQueueItemId,
            queueItemIdToRemove = queueItemId
        )
        val updatedState = state.copy(
            isModified = true,
            currentQueueItemId = edited.currentQueueItemId,
            playbackPositionMs = 0L,
            wasPlaying = edited.currentQueueItemId != null && state.wasPlaying,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun clearUpcoming(): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.clearUpcoming(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = state.currentQueueItemId
        )
        val updatedState = state.copy(
            isModified = true,
            currentQueueItemId = edited.currentQueueItemId,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, edited.message)
    }


    suspend fun moveQueueItem(queueItemId: String, offset: Int): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.moveItem(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = state.currentQueueItemId,
            queueItemIdToMove = queueItemId,
            offset = offset
        )
        val updatedState = state.copy(
            sourceType = "QUEUE",
            sourceId = null,
            sourceLabel = modifiedSourceLabel(state.sourceLabel),
            isModified = state.isModified || edited.changed,
            currentQueueItemId = edited.currentQueueItemId,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun shuffleQueue(): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.shuffleEntireQueue(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = state.currentQueueItemId,
            random = Random(now())
        )
        val updatedState = state.copy(
            sourceType = "QUEUE",
            sourceId = null,
            sourceLabel = modifiedSourceLabel(state.sourceLabel),
            isModified = state.isModified || edited.changed,
            shuffleEnabled = true,
            currentQueueItemId = edited.currentQueueItemId,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun shuffleUpcoming(): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val edited = QueueListEditor.shuffleUpcoming(
            existingItems = queueDao.getQueueItems(),
            currentQueueItemId = state.currentQueueItemId,
            random = Random(now())
        )
        val updatedState = state.copy(
            sourceType = "QUEUE",
            sourceId = null,
            sourceLabel = modifiedSourceLabel(state.sourceLabel),
            isModified = state.isModified || edited.changed,
            shuffleEnabled = true,
            currentQueueItemId = edited.currentQueueItemId,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, edited.message)
    }

    suspend fun markModifiedIfContainsTrack(trackId: String): Boolean = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: return@withContext false
        val items = queueDao.getQueueItems()
        if (items.none { it.trackId == trackId }) return@withContext false

        queueDao.upsertQueueState(
            state.copy(
                sourceType = "QUEUE",
                sourceId = null,
                sourceLabel = if (state.sourceLabel.endsWith("Modified")) {
                    state.sourceLabel
                } else {
                    "${state.sourceLabel} - Modified"
                },
                isModified = true,
                updatedAt = now()
            )
        )
        true
    }

    suspend fun setRepeatMode(repeatMode: String): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val updatedState = state.copy(
            repeatMode = repeatMode,
            updatedAt = now()
        )
        replaceQueue(updatedState, queueDao.getQueueItems(), null)
    }

    suspend fun setShuffleEnabled(enabled: Boolean): QueueCommandResult = withContext(dispatchers.io) {
        val state = queueDao.getQueueState() ?: defaultQueueState()
        val items = queueDao.getQueueItems().sortedBy { it.position }
        val currentId = state.currentQueueItemId
        val shuffledItems = if (enabled && items.size > 1 && currentId != null) {
            shuffleAroundCurrent(items, currentId)
        } else {
            items
        }
        val edited = QueueListEditor.markCurrent(shuffledItems, currentId)
        val updatedState = state.copy(
            shuffleEnabled = enabled,
            isModified = state.isModified || enabled,
            sourceType = if (enabled) "QUEUE" else state.sourceType,
            sourceId = if (enabled) null else state.sourceId,
            sourceLabel = if (enabled && !state.sourceLabel.endsWith("Modified")) {
                "${state.sourceLabel} - Modified"
            } else {
                state.sourceLabel
            },
            currentQueueItemId = edited.currentQueueItemId,
            updatedAt = now()
        )
        replaceQueue(updatedState, edited.items, null)
    }


    private fun modifiedSourceLabel(sourceLabel: String): String =
        if (sourceLabel.endsWith("Modified")) sourceLabel else "$sourceLabel - Modified"

    private suspend fun replaceQueue(
        state: QueueStateEntity,
        items: List<QueueItemEntity>,
        message: String?
    ): QueueCommandResult {
        queueDao.replaceQueue(state, items)
        return QueueCommandResult(
            queue = state.toPersistentQueue(items),
            message = message
        )
    }

    private suspend fun QueueStateEntity.toPersistentQueue(
        queueItems: List<QueueItemEntity>
    ): PersistentQueue {
        val sortedItems = queueItems.sortedBy { it.position }
        val tracksById = sortedItems
            .mapNotNull { it.trackId }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { trackIds -> trackDao.getTracksByIds(trackIds).associateBy { it.trackId } }
            ?: emptyMap()
        val remoteItemsById = sortedItems
            .mapNotNull { it.remoteItemId }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { remoteItemIds ->
                remoteDiscoverDao?.getRemoteItems(remoteItemIds)?.associateBy { it.remoteItemId }
            }
            ?: emptyMap()
        val thumbnailIds = tracksById.values
            .mapNotNull { it.thumbnailAssetId ?: it.embeddedThumbnailAssetId }
            .distinct()
        val thumbnailsById = if (thumbnailIds.isEmpty()) {
            emptyMap()
        } else {
            trackDao.getThumbnailAssets(thumbnailIds).associateBy { it.thumbnailAssetId }
        }

        return PersistentQueue(
            queueStateId = queueStateId,
            sourceLabel = sourceLabel,
            isModified = isModified,
            currentQueueItemId = currentQueueItemId,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            playbackPositionMs = playbackPositionMs,
            wasPlaying = wasPlaying,
            items = sortedItems.mapNotNull { queueItem ->
                if (queueItem.trackId != null) {
                    val track = tracksById[queueItem.trackId] ?: return@mapNotNull null
                    val thumbnailId = track.thumbnailAssetId ?: track.embeddedThumbnailAssetId
                    val localThumbnailUri = thumbnailId
                        ?.let(thumbnailsById::get)
                        ?.internalFilename
                        ?.let { filename -> Uri.fromFile(File(storage.thumbnailsDir, filename)) }
                    val thumbnailUri = localThumbnailUri ?: track.remoteThumbnailUrl?.let(Uri::parse)
                    queueItem.toQueueTrackItem(track, thumbnailUri)
                } else {
                    val remoteItem = queueItem.remoteItemId?.let(remoteItemsById::get)
                        ?: return@mapNotNull null
                    queueItem.toRemoteQueueTrackItem(remoteItem)
                }
            }
        )
    }

    private fun QueueItemEntity.toQueueTrackItem(
        track: TrackEntity,
        thumbnailUri: Uri?
    ): QueueTrackItem {
        return QueueTrackItem(
            queueItemId = queueItemId,
            trackId = track.trackId,
            displayTitle = AudioTitleFormatter.displayTitle(
                title = track.title,
                fallbackFilename = track.originalFilename
            ),
            artist = track.artist,
            durationMs = track.durationMs,
            position = position,
            state = state,
            insertedBy = insertedBy,
            isFavourite = track.isFavourite,
            audioUri = Uri.fromFile(File(storage.audioDir, track.internalFilename)),
            thumbnailUri = thumbnailUri
        )
    }

    private fun QueueItemEntity.toRemoteQueueTrackItem(remoteItem: RemoteItemEntity): QueueTrackItem {
        return QueueTrackItem(
            queueItemId = queueItemId,
            trackId = remoteItem.remoteItemId,
            localTrackId = null,
            remoteItemId = remoteItem.remoteItemId,
            displayTitle = remoteItem.title,
            artist = remoteItem.artistOrChannel.orEmpty(),
            durationMs = remoteItem.durationMs ?: 0L,
            position = position,
            state = state,
            insertedBy = insertedBy,
            isFavourite = false,
            audioUri = remoteItem.lastPlayableUrl?.let(Uri::parse) ?: Uri.EMPTY,
            thumbnailUri = remoteItem.thumbnailUrl?.let(Uri::parse),
            streamState = remoteItem.streamState,
            isRemote = true
        )
    }

    private fun QueueStateEntity.markModified(edited: EditedQueue): QueueStateEntity =
        copy(
            sourceType = if (edited.changed) "QUEUE" else sourceType,
            sourceId = if (edited.changed) null else sourceId,
            sourceLabel = if (edited.changed) {
                if (sourceLabel.endsWith("Modified")) sourceLabel else "$sourceLabel - Modified"
            } else {
                sourceLabel
            },
            isModified = isModified || edited.changed,
            currentQueueItemId = edited.currentQueueItemId,
            playbackPositionMs = playbackPositionMs,
            wasPlaying = edited.currentQueueItemId != null && (wasPlaying || edited.changed),
            updatedAt = now()
        )

    private fun shuffleAroundCurrent(
        items: List<QueueItemEntity>,
        currentId: String
    ): List<QueueItemEntity> {
        val currentIndex = items.indexOfFirst { it.queueItemId == currentId }
        if (currentIndex == -1) return items
        val shuffledOthers = items
            .filterNot { it.queueItemId == currentId }
            .shuffled(Random(now()))
            .toMutableList()
        shuffledOthers.add(currentIndex, items[currentIndex])
        return shuffledOthers
    }

    private fun newQueueItem(trackId: String, insertedBy: String): QueueItemEntity =
        QueueItemEntity(
            queueItemId = idFactory(),
            queueStateId = QueueDao.DEFAULT_QUEUE_STATE_ID,
            trackId = trackId,
            remoteItemId = null,
            position = Int.MAX_VALUE,
            state = QueueListEditor.STATE_UPCOMING,
            insertedBy = insertedBy,
            addedAt = now()
        )

    private fun newRemoteQueueItem(remoteItemId: String, insertedBy: String): QueueItemEntity =
        QueueItemEntity(
            queueItemId = idFactory(),
            queueStateId = QueueDao.DEFAULT_QUEUE_STATE_ID,
            trackId = null,
            remoteItemId = remoteItemId,
            position = Int.MAX_VALUE,
            state = QueueListEditor.STATE_UPCOMING,
            insertedBy = insertedBy,
            addedAt = now()
        )

    private fun defaultQueueState(): QueueStateEntity {
        val createdAt = now()
        return QueueStateEntity(
            queueStateId = QueueDao.DEFAULT_QUEUE_STATE_ID,
            sourceType = "HOME",
            sourceId = null,
            sourceLabel = "Queue from Home",
            isModified = false,
            currentQueueItemId = null,
            repeatMode = settingsRepository?.settings?.value?.defaultRepeatMode?.name ?: "ALL",
            shuffleEnabled = settingsRepository?.settings?.value?.defaultShuffleEnabled ?: false,
            playbackPositionMs = 0L,
            wasPlaying = false,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }
}

data class PersistentQueue(
    val queueStateId: String,
    val sourceLabel: String,
    val isModified: Boolean,
    val currentQueueItemId: String?,
    val repeatMode: String,
    val shuffleEnabled: Boolean,
    val playbackPositionMs: Long,
    val wasPlaying: Boolean,
    val items: List<QueueTrackItem>
) {
    val currentItem: QueueTrackItem?
        get() = currentQueueItemId?.let { id -> items.firstOrNull { it.queueItemId == id } }

    val upcomingItems: List<QueueTrackItem>
        get() = items.filter { it.state == QueueListEditor.STATE_UPCOMING }
}

data class QueueTrackItem(
    val queueItemId: String,
    val trackId: String,
    val localTrackId: String? = trackId,
    val remoteItemId: String? = null,
    val displayTitle: String,
    val artist: String,
    val durationMs: Long,
    val position: Int,
    val state: String,
    val insertedBy: String,
    val isFavourite: Boolean,
    val audioUri: Uri,
    val thumbnailUri: Uri?,
    val streamState: String? = null,
    val isRemote: Boolean = false
)

data class QueueCommandResult(
    val queue: PersistentQueue,
    val message: String?
)
