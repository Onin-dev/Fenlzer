package com.fenl.fenlzer.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fenl.fenlzer.data.repository.PersistentQueue
import com.fenl.fenlzer.data.repository.QueueCommandResult
import com.fenl.fenlzer.data.repository.QueueListEditor
import com.fenl.fenlzer.data.repository.QueueRepository
import com.fenl.fenlzer.data.repository.QueueTrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaybackController(
    private val context: Context,
    private val queueRepository: QueueRepository,
    private val scope: CoroutineScope,
    private val statsTracker: PlaybackStatsTracker? = null,
    private val remoteStreamResolver: RemoteStreamResolver? = null
) {
    private val mutableUiState = MutableStateFlow(PlaybackUiState(isRestoringQueue = true))
    val uiState: StateFlow<PlaybackUiState> = mutableUiState.asStateFlow()

    private var mediaController: MediaController? = null
    private var restoredInitialQueue = false
    private var suppressTransitionSync = false
    private var pendingQueueHandoffCurrentQueueItemId: String? = null
    private var positionJob: Job? = null
    private val retriedRemoteQueueItems = mutableSetOf<String>()
    private val sleepTimerController = SleepTimerController()
    private val artworkCache = PlaybackArtworkCache(context.applicationContext)

    init {
        observeQueue()
        connectController()
    }

    fun playFromHome(trackId: String, searchActive: Boolean) {
        statsTracker?.onManualSongChange()
        scope.launch {
            val result = queueRepository.playFromHome(trackId, searchActive)
            applyQueueResult(result, playWhenReady = true, forcePositionMs = 0L)
        }
    }

    fun playNext(trackId: String) {
        scope.launch {
            val result = queueRepository.playNext(trackId)
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun playNext(trackIds: List<String>) {
        scope.launch {
            val result = queueRepository.playNext(trackIds)
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun addToQueue(trackId: String) {
        scope.launch {
            val result = queueRepository.addToQueue(trackId)
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun addToQueue(trackIds: List<String>) {
        scope.launch {
            val result = queueRepository.addToQueue(trackIds)
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun playFromDiscover(remoteItemIds: List<String>, startRemoteItemId: String) {
        statsTracker?.onManualSongChange()
        scope.launch {
            val result = queueRepository.playFromDiscover(
                remoteItemIds = remoteItemIds,
                startRemoteItemId = startRemoteItemId
            )
            applyQueueResult(result, playWhenReady = result.queue.currentItem != null, forcePositionMs = 0L)
        }
    }

    fun playNextRemote(remoteItemId: String) {
        scope.launch {
            val result = queueRepository.playNextRemote(remoteItemId)
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun addRemoteToQueue(remoteItemId: String) {
        scope.launch {
            val result = queueRepository.addRemoteToQueue(remoteItemId)
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun refreshQueueFromRepository() {
        scope.launch {
            val queue = queueRepository.snapshot()
            applyQueueToController(
                queue = queue,
                playWhenReady = mediaController?.playWhenReady == true,
                positionMs = currentPlaybackPositionFor(queue)
            )
        }
    }

    suspend fun prepareForTrackDeletion(trackIds: Collection<String>) {
        val deletedTrackIds = trackIds.filter { it.isNotBlank() }.toSet()
        if (deletedTrackIds.isEmpty()) return
        val queue = queueRepository.snapshot()
        val currentItem = queue.currentItem ?: return
        if (currentItem.localTrackId !in deletedTrackIds) return

        statsTracker?.onManualSongChange()
        val shouldResume = mutableUiState.value.isPlaying || mediaController?.isPlaying == true
        val currentIndex = queue.items.indexOfFirst { it.queueItemId == currentItem.queueItemId }
        val nextCurrentId = queue.items
            .drop((currentIndex + 1).coerceAtLeast(0))
            .firstOrNull { it.localTrackId !in deletedTrackIds }
            ?.queueItemId
            ?: queue.items
                .take(currentIndex.coerceAtLeast(0))
                .firstOrNull { it.localTrackId !in deletedTrackIds }
                ?.queueItemId
        val repairedItems = queue.items
            .filterNot { it.localTrackId in deletedTrackIds }
            .mapIndexed { index, item ->
                item.copy(
                    position = index,
                    state = if (item.queueItemId == nextCurrentId) {
                        QueueListEditor.STATE_CURRENT
                    } else {
                        QueueListEditor.STATE_UPCOMING
                    }
                )
            }
        val previewQueue = queue.copy(
            currentQueueItemId = nextCurrentId,
            playbackPositionMs = 0L,
            wasPlaying = shouldResume && nextCurrentId != null,
            items = repairedItems
        )

        mediaController?.let { controller ->
            controller.pause()
            controller.stop()
            controller.clearMediaItems()
        }
        mutableUiState.update {
            it.copy(
                queueItems = previewQueue.items,
                currentItem = previewQueue.currentItem,
                isPlaying = false,
                playbackPositionMs = 0L
            )
        }
        applyQueueToController(
            queue = previewQueue,
            playWhenReady = shouldResume && nextCurrentId != null,
            positionMs = 0L
        )
    }

    fun playFromTrackList(
        trackIds: List<String>,
        startTrackId: String? = null,
        sourceType: String,
        sourceId: String?,
        sourceLabel: String,
        insertedBy: String,
        shuffle: Boolean = false
    ) {
        statsTracker?.onManualSongChange()
        scope.launch {
            val result = queueRepository.replaceWithTrackList(
                trackIds = trackIds,
                startTrackId = startTrackId,
                sourceType = sourceType,
                sourceId = sourceId,
                sourceLabel = sourceLabel,
                insertedBy = insertedBy,
                shuffle = shuffle
            )
            applyQueueResult(result, playWhenReady = result.queue.currentItem != null, forcePositionMs = 0L)
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
        persistPlaybackStateSoon()
        updateFromPlayer(controller)
        samplePlaybackStats(controller)
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) return
        val targetIndex = nextMediaIndex(controller) ?: return
        val targetMediaId = controller.getMediaItemAt(targetIndex).mediaId
        statsTracker?.onManualSongChange()
        controller.seekTo(targetIndex, 0L)
        controller.play()
        syncCurrentQueueItem(targetMediaId, playbackPositionMs = 0L, wasPlaying = true)
        updateFromPlayer(controller)
        samplePlaybackStats(controller)
    }

    fun previous() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) return
        if (controller.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
            controller.seekTo(0L)
            persistPlaybackStateSoon()
        } else {
            val targetIndex = previousMediaIndex(controller)
            if (targetIndex != null) {
                val targetMediaId = controller.getMediaItemAt(targetIndex).mediaId
                statsTracker?.onManualSongChange()
                controller.seekTo(targetIndex, 0L)
                syncCurrentQueueItem(targetMediaId, playbackPositionMs = 0L, wasPlaying = controller.isPlaying)
            } else {
                controller.seekTo(0L)
                persistPlaybackStateSoon()
            }
        }
        updateFromPlayer(controller)
        samplePlaybackStats(controller)
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs.coerceAtLeast(0L))
        persistPlaybackStateSoon()
        updateFromPlayer(controller)
        samplePlaybackStats(controller)
    }

    fun removeQueueItem(queueItemId: String) {
        scope.launch {
            val removingCurrent = mutableUiState.value.currentItem?.queueItemId == queueItemId
            if (removingCurrent) {
                statsTracker?.onManualSongChange()
            }
            val result = queueRepository.removeQueueItem(queueItemId)
            applyQueueResult(
                result = result,
                playWhenReady = removingCurrent && result.queue.currentItem != null,
                forcePositionMs = 0L
            )
        }
    }

    fun jumpToQueueItem(queueItemId: String) {
        scope.launch {
            if (mutableUiState.value.currentItem?.queueItemId != queueItemId) {
                statsTracker?.onManualSongChange()
            }
            val result = queueRepository.markCurrent(
                queueItemId = queueItemId,
                playbackPositionMs = 0L,
                wasPlaying = true
            )
            applyQueueResult(result, playWhenReady = true, forcePositionMs = 0L)
        }
    }

    fun cycleRepeatMode() {
        scope.launch {
            val currentRepeat = mutableUiState.value.repeatMode
            val nextRepeat = when (currentRepeat) {
                QueueRepeatMode.ALL -> QueueRepeatMode.ONE
                QueueRepeatMode.ONE -> QueueRepeatMode.OFF
                else -> QueueRepeatMode.ALL
            }
            val result = queueRepository.setRepeatMode(nextRepeat)
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun setRepeatMode(repeatMode: String) {
        scope.launch {
            val result = queueRepository.setRepeatMode(repeatMode)
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun toggleShuffle() {
    scope.launch {
        val nextShuffle = !mutableUiState.value.shuffleEnabled
        val currentPositionMs = mediaController?.currentPosition?.coerceAtLeast(0L)

        val result = if (nextShuffle) {
            // Turning shuffle on while a queue is already playing should
            // immediately shuffle the upcoming queue while keeping the current
            // song and playback position intact.
            queueRepository.shuffleUpcoming()
        } else {
            queueRepository.setShuffleEnabled(false)
        }

        applyQueueResult(
            result = result,
            playWhenReady = mediaController?.isPlaying == true,
            forcePositionMs = currentPositionMs
        )
    }
}

fun startSleepTimerDuration(durationMs: Long) {
        sleepTimerController.startDuration(durationMs)
        updateSleepTimerState()
    }

    fun startSleepTimerEndOfSong() {
        sleepTimerController.startEndOfSong()
        updateSleepTimerState()
    }

    fun startSleepTimerEndOfQueue() {
        sleepTimerController.startEndOfQueue()
        updateSleepTimerState()
    }

    fun cancelSleepTimer() {
        sleepTimerController.cancel()
        mediaController?.volume = 1f
        updateSleepTimerState()
    }


    fun moveQueueItem(queueItemId: String, offset: Int) {
        scope.launch {
            val result = queueRepository.moveQueueItem(queueItemId, offset)
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun shuffleQueue() {
        scope.launch {
            val result = queueRepository.shuffleQueue()
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun shuffleUpcoming() {
        scope.launch {
            val result = queueRepository.shuffleUpcoming()
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun clearUpcoming() {
        scope.launch {
            val result = queueRepository.clearUpcoming()
            applyQueueResult(result, playWhenReady = false)
        }
    }

    fun consumeMessage() {
        mutableUiState.update { it.copy(message = null) }
    }

    private fun observeQueue() {
        scope.launch {
            queueRepository.observeQueue().collect { queue ->
                mutableUiState.update {
                    val liveCurrentItem = liveCurrentItemFrom(queue)
                    it.copy(
                        queueItems = queue.items,
                        currentItem = liveCurrentItem ?: queue.currentItem,
                        sourceLabel = queue.sourceLabel,
                        isModified = queue.isModified,
                        repeatMode = queue.repeatMode,
                        shuffleEnabled = queue.shuffleEnabled,
                        isRestoringQueue = false
                    )
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun connectController() {
        val token = SessionToken(
            context,
            ComponentName(context, FenlzerMediaService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val controller = future.get()
                mediaController = controller
                controller.addListener(playerListener)
                scope.launch {
                    val queue = queueRepository.snapshot()
                    applyQueueToController(
                        queue = queue,
                        playWhenReady = queue.wasPlaying,
                        positionMs = queue.playbackPositionMs
                    )
                    restoredInitialQueue = true
                }
                startPositionTicker()
                updateFromPlayer(controller)
                samplePlaybackStats(controller)
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private suspend fun applyQueueResult(
        result: QueueCommandResult,
        playWhenReady: Boolean,
        forcePositionMs: Long? = null
    ) {
        if (result.message != null) {
            mutableUiState.update { it.copy(message = result.message) }
        }
        applyQueueToController(
            queue = result.queue,
            playWhenReady = playWhenReady || (mediaController?.isPlaying == true && result.queue.currentItem != null),
            positionMs = forcePositionMs ?: currentPlaybackPositionFor(result.queue)
        )
    }

    private fun currentPlaybackPositionFor(queue: PersistentQueue): Long {
        val controller = mediaController
        val currentQueueItemId = queue.currentQueueItemId
        return if (
            controller != null &&
            currentQueueItemId != null &&
            controller.currentMediaItem?.mediaId == currentQueueItemId
        ) {
            controller.currentPosition.coerceAtLeast(0L)
        } else {
            queue.playbackPositionMs
        }
    }

    private suspend fun applyQueueToController(
        queue: PersistentQueue,
        playWhenReady: Boolean,
        positionMs: Long
    ) {
        val controller = mediaController ?: return
        val currentIndex = queue.items.indexOfFirst { it.queueItemId == queue.currentQueueItemId }
        if (queue.items.isEmpty() || currentIndex == -1) {
            pendingQueueHandoffCurrentQueueItemId = null
            controller.stop()
            controller.clearMediaItems()
            queueRepository.persistPlaybackState(0L, wasPlaying = false)
            updateFromPlayer(controller)
            samplePlaybackStats(controller)
            return
        }

        suppressTransitionSync = true
        val desiredCurrentId = queue.currentQueueItemId
        val currentMediaId = controller.currentMediaItem?.mediaId
        val positionToRestore = positionMs.coerceAtLeast(0L)
        pendingQueueHandoffCurrentQueueItemId = desiredCurrentId
        previewQueueHandoff(
            queue = queue,
            playWhenReady = playWhenReady,
            positionMs = positionToRestore
        )
        val playableQueue = queue.withResolvedRemoteStreamsAround(currentIndex)
        val desiredMediaItems = playableQueue.items.map { item ->
            QueueMediaItemMapper.mediaItemFor(
                item = item,
                artworkUri = artworkCache.cachedSquareArtworkUriFor(
                    sourceUri = item.remoteThumbnailUri ?: item.thumbnailUri,
                    fallbackUri = item.thumbnailUri
                ) ?: item.thumbnailUri
            )
        }

        if (
            desiredCurrentId != null &&
            desiredCurrentId == currentMediaId &&
            controller.mediaItemCount > 0
        ) {
            controller.syncMediaItemsPreservingCurrent(
                desiredMediaItems = desiredMediaItems,
                desiredCurrentIndex = currentIndex,
                positionMs = positionToRestore
            )
        } else {
            controller.setMediaItems(
                desiredMediaItems,
                playableQueue.items.indexOfFirst { it.queueItemId == playableQueue.currentQueueItemId },
                positionToRestore
            )
            controller.prepare()
        }
        controller.repeatMode = when (queue.repeatMode) {
            "ONE" -> Player.REPEAT_MODE_ONE
            "OFF" -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
        controller.shuffleModeEnabled = queue.shuffleEnabled
        controller.playWhenReady = playWhenReady
        suppressTransitionSync = false

        queueRepository.persistPlaybackState(
            playbackPositionMs = positionToRestore,
            wasPlaying = playWhenReady
        )
        updateFromPlayer(controller)
        samplePlaybackStats(controller)
        warmPlaybackArtworkForQueueWindow(playableQueue, currentIndex)
    }

    private fun previewQueueHandoff(
        queue: PersistentQueue,
        playWhenReady: Boolean,
        positionMs: Long
    ) {
        mutableUiState.update { current ->
            val previewCurrentItem = queue.currentItem
            current.copy(
                queueItems = queue.items,
                currentItem = previewCurrentItem ?: current.currentItem,
                sourceLabel = queue.sourceLabel,
                isModified = queue.isModified,
                repeatMode = queue.repeatMode,
                shuffleEnabled = queue.shuffleEnabled,
                playbackPositionMs = positionMs,
                durationMs = previewCurrentItem?.durationMs ?: current.durationMs,
                isPlaying = if (playWhenReady) current.isPlaying else false,
                canSkipNext = queue.items.size > 1 || queue.repeatMode == QueueRepeatMode.ALL
            )
        }
    }

    private fun warmPlaybackArtworkForQueueWindow(queue: PersistentQueue, currentIndex: Int) {
        val itemsToWarm = queue.items
            .drop(currentIndex.coerceAtLeast(0))
            .take(3)
            .filter { item -> item.thumbnailUri != null || item.remoteThumbnailUri != null }
        if (itemsToWarm.isEmpty()) return

        scope.launch {
            itemsToWarm.forEach { item ->
                val artworkUri = artworkCache.squareArtworkUriFor(
                    sourceUri = item.remoteThumbnailUri ?: item.thumbnailUri,
                    fallbackUri = item.thumbnailUri
                ) ?: return@forEach
                val controller = mediaController ?: return@forEach
                val index = controller.indexOfMediaItem(item.queueItemId)
                if (index != -1) {
                    controller.replaceMediaItemIfChanged(
                        index = index,
                        desiredItem = QueueMediaItemMapper.mediaItemFor(
                            item = item,
                            artworkUri = artworkUri
                        )
                    )
                }
            }
        }
    }

    private suspend fun PersistentQueue.withResolvedRemoteStreamsAround(currentIndex: Int): PersistentQueue {
        val resolver = remoteStreamResolver ?: return this
        val resolvedItems = items.toMutableList()
        items.drop(currentIndex.coerceAtLeast(0))
            .take(3)
            .forEach { item ->
                val remoteItemId = item.remoteItemId ?: return@forEach
                if (item.audioUri != Uri.EMPTY) return@forEach
                val resolution = runCatching {
                    resolver.resolve(
                        remoteItemId = remoteItemId,
                        reason = if (item.state == QueueListEditor.STATE_CURRENT) {
                            "CURRENT_REMOTE_STREAM"
                        } else {
                            "PREFETCH_NEXT_TWO"
                        }
                    )
                }.getOrNull()
                if (resolution != null && resolution.canStream) {
                    val index = resolvedItems.indexOfFirst { it.queueItemId == item.queueItemId }
                    if (index != -1) {
                        resolvedItems[index] = item.copy(audioUri = Uri.parse(resolution.playableUrl))
                    }
                }
            }
        return copy(items = resolvedItems)
    }

    private fun MediaController.syncMediaItemsPreservingCurrent(
        desiredMediaItems: List<MediaItem>,
        desiredCurrentIndex: Int,
        positionMs: Long
    ) {
        val desiredIds = desiredMediaItems.map { it.mediaId }

        for (index in mediaItemCount - 1 downTo 0) {
            if (getMediaItemAt(index).mediaId !in desiredIds) {
                removeMediaItem(index)
            }
        }

        desiredMediaItems.forEachIndexed { desiredIndex, desiredItem ->
            val currentIndex = indexOfMediaItem(desiredItem.mediaId)
            when {
                currentIndex == -1 -> addMediaItem(desiredIndex, desiredItem)
                currentIndex != desiredIndex -> {
                    moveMediaItem(currentIndex, desiredIndex)
                    replaceMediaItemIfChanged(desiredIndex, desiredItem)
                }
                else -> replaceMediaItemIfChanged(desiredIndex, desiredItem)
            }
        }

        if (currentMediaItem?.mediaId != desiredMediaItems[desiredCurrentIndex].mediaId) {
            seekTo(desiredCurrentIndex, positionMs)
        }
    }

    private fun MediaController.replaceMediaItemIfChanged(index: Int, desiredItem: MediaItem) {
        if (index !in 0 until mediaItemCount) return

        val currentItem = getMediaItemAt(index)
        val currentUri = currentItem.localConfiguration?.uri
        val desiredUri = desiredItem.localConfiguration?.uri
        val uriChanged = currentUri != desiredUri
        val metadataChanged = currentItem.mediaMetadata != desiredItem.mediaMetadata
        if (!uriChanged && !metadataChanged) return

        val replacingCurrent = currentMediaItem?.mediaId == currentItem.mediaId
        if (uriChanged && replacingCurrent && (isPlaying || playWhenReady)) {
            return
        }

        val positionBeforeReplace = currentPosition.coerceAtLeast(0L)
        val playWhenReadyBeforeReplace = playWhenReady
        val wasPlayingBeforeReplace = isPlaying

        replaceMediaItem(index, desiredItem)

        if (replacingCurrent) {
            seekTo(index, positionBeforeReplace)
            playWhenReady = playWhenReadyBeforeReplace || wasPlayingBeforeReplace
        }
    }

    private fun MediaController.indexOfMediaItem(mediaId: String): Int {
        for (index in 0 until mediaItemCount) {
            if (getMediaItemAt(index).mediaId == mediaId) {
                return index
            }
        }
        return -1
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateFromPlayer(player)
            samplePlaybackStats(player)
            if (
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
            ) {
                persistPlaybackStateSoon()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (suppressTransitionSync || !restoredInitialQueue) return
            val queueItemId = mediaItem?.mediaId
            if (queueItemId == null && pendingQueueHandoffCurrentQueueItemId != null) return
            val previousHadUpcoming = mutableUiState.value.upcomingCount > 0
            if (queueItemId != null) {
                updateLiveCurrentFromMediaId(queueItemId)
            }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                statsTracker?.onAutomaticSongChange()
                handleSleepTimerTransition(previousHadUpcoming)
            }
            syncCurrentQueueItem(
                queueItemId = queueItemId,
                playbackPositionMs = 0L,
                wasPlaying = mediaController?.isPlaying
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = mutableUiState.value.currentItem
            val remoteItemId = item?.remoteItemId
            val resolver = remoteStreamResolver
            if (item == null || remoteItemId == null || resolver == null) {
                mutableUiState.update {
                    it.copy(message = "Fenlzer could not play this song.")
                }
                return
            }

            scope.launch {
                if (item.queueItemId !in retriedRemoteQueueItems) {
                    retriedRemoteQueueItems += item.queueItemId
                    val resolution = runCatching {
                        resolver.resolve(remoteItemId, reason = "REMOTE_STREAM_RETRY")
                    }.getOrNull()
                    if (resolution != null && resolution.canStream) {
                        val queue = queueRepository.snapshot()
                        applyQueueToController(
                            queue = queue.copy(
                                items = queue.items.map { queueItem ->
                                    if (queueItem.queueItemId == item.queueItemId) {
                                        queueItem.copy(audioUri = Uri.parse(resolution.playableUrl))
                                    } else {
                                        queueItem
                                    }
                                }
                            ),
                            playWhenReady = true,
                            positionMs = 0L
                        )
                        mutableUiState.update {
                            it.copy(message = "Refreshed remote stream.")
                        }
                        return@launch
                    }
                }

                resolver.markFailed(remoteItemId)
                mutableUiState.update {
                    it.copy(message = "Remote stream unavailable. Skipping.")
                }
                skipNext()
            }
        }
    }

    private fun syncCurrentQueueItemAndShuffleUpcoming(
    queueItemId: String,
    playbackPositionMs: Long,
    wasPlaying: Boolean?
) {
    scope.launch {
        queueRepository.markCurrent(
            queueItemId = queueItemId,
            playbackPositionMs = playbackPositionMs,
            wasPlaying = wasPlaying
        )

        val result = queueRepository.shuffleUpcoming()
        applyQueueResult(
            result = result,
            playWhenReady = wasPlaying == true,
            forcePositionMs = mediaController?.currentPosition?.coerceAtLeast(0L) ?: playbackPositionMs
        )
    }
}

private fun syncCurrentQueueItem(
        queueItemId: String?,
        playbackPositionMs: Long,
        wasPlaying: Boolean?
    ) {
        scope.launch {
            queueRepository.markCurrent(
                queueItemId = queueItemId,
                playbackPositionMs = playbackPositionMs,
                wasPlaying = wasPlaying
            )
        }
    }

    private fun updateLiveCurrentFromMediaId(mediaId: String) {
        mutableUiState.update { current ->
            current.copy(
                currentItem = current.queueItems.firstOrNull { it.queueItemId == mediaId }
                    ?: current.currentItem,
                playbackPositionMs = 0L
            )
        }
    }

    private fun liveCurrentItemFrom(queue: PersistentQueue): QueueTrackItem? {
        val mediaId = mediaController?.currentMediaItem?.mediaId ?: return null
        return queue.items.firstOrNull { it.queueItemId == mediaId }
    }

    private fun nextMediaIndex(controller: Player): Int? {
        val currentIndex = controller.currentMediaItemIndex
        return when {
            currentIndex < 0 -> null
            currentIndex + 1 < controller.mediaItemCount -> currentIndex + 1
            controller.repeatMode == Player.REPEAT_MODE_ALL && controller.mediaItemCount > 1 -> 0
            else -> null
        }
    }

    private fun previousMediaIndex(controller: Player): Int? {
        val currentIndex = controller.currentMediaItemIndex
        return when {
            currentIndex < 0 -> null
            currentIndex > 0 -> currentIndex - 1
            controller.repeatMode == Player.REPEAT_MODE_ALL && controller.mediaItemCount > 1 -> {
                controller.mediaItemCount - 1
            }
            else -> null
        }
    }

    private fun handleSleepTimerTransition(previousHadUpcoming: Boolean) {
        val controller = mediaController ?: return
        val action = sleepTimerController.onMediaItemTransition(previousHadUpcoming)
        when (action) {
            SleepTimerAction.None -> Unit
            SleepTimerAction.PauseAndRestoreVolume -> {
                controller.volume = 1f
                controller.pause()
            }
            is SleepTimerAction.SetVolume -> {
                controller.volume = action.volume
            }
        }
        updateSleepTimerState()
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                delay(1_000L)
                val controller = mediaController ?: continue
                updateFromPlayer(controller)
                samplePlaybackStats(controller)
                if (controller.mediaItemCount > 0) {
                    queueRepository.persistPlaybackState(
                        playbackPositionMs = controller.currentPosition.coerceAtLeast(0L),
                        wasPlaying = controller.isPlaying
                    )
                    applySleepTimerTick(controller)
                }
            }
        }
    }

    private fun persistPlaybackStateSoon() {
        val controller = mediaController ?: return
        scope.launch {
            queueRepository.persistPlaybackState(
                playbackPositionMs = controller.currentPosition.coerceAtLeast(0L),
                wasPlaying = controller.isPlaying
            )
        }
    }

    private fun updateFromPlayer(player: Player) {
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        mutableUiState.update { current ->
            val mediaId = player.currentMediaItem?.mediaId
            val pendingHandoffId = pendingQueueHandoffCurrentQueueItemId
            val preserveCurrentDuringControllerHandoff =
                current.queueItems.isNotEmpty() &&
                    (
                        player.mediaItemCount == 0 ||
                            (pendingHandoffId != null && mediaId == null)
                        )
            val currentItem = when {
                pendingHandoffId != null && (mediaId == null || mediaId == pendingHandoffId) -> {
                    current.queueItems.firstOrNull { it.queueItemId == pendingHandoffId }
                        ?: current.currentItem?.takeIf { item ->
                            current.queueItems.any { it.queueItemId == item.queueItemId }
                        }
                }
                preserveCurrentDuringControllerHandoff -> {
                    current.currentItem?.takeIf { item ->
                        current.queueItems.any { it.queueItemId == item.queueItemId }
                    } ?: current.queueItems.firstOrNull { it.state == QueueListEditor.STATE_CURRENT }
                }
                mediaId != null -> current.queueItems.firstOrNull { it.queueItemId == mediaId }
                    ?: current.currentItem?.takeIf { it.queueItemId == mediaId }
                else -> current.currentItem?.takeIf { item ->
                    current.queueItems.any { it.queueItemId == item.queueItemId }
                }
            }
            if (mediaId != null && mediaId == pendingHandoffId) {
                pendingQueueHandoffCurrentQueueItemId = null
            }
            current.copy(
                currentItem = currentItem,
                isPlaying = player.isPlaying,
                playbackPositionMs = if (preserveCurrentDuringControllerHandoff) {
                    current.playbackPositionMs
                } else if (player.mediaItemCount == 0) {
                    0L
                } else {
                    player.currentPosition.coerceAtLeast(0L)
                },
                durationMs = duration,
                canSkipNext = player.hasNextMediaItem() || player.repeatMode == Player.REPEAT_MODE_ALL,
                repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "ONE"
                    Player.REPEAT_MODE_OFF -> "OFF"
                    else -> "ALL"
                },
                shuffleEnabled = player.shuffleModeEnabled
            )
        }
        updateSleepTimerState()
    }

    private fun samplePlaybackStats(player: Player) {
        val item = player.currentMediaItem
            ?.mediaId
            ?.let { mediaId ->
                mutableUiState.value.queueItems.firstOrNull { it.queueItemId == mediaId }
            }
        val duration = player.duration.takeIf { it > 0L }
            ?: item?.durationMs
            ?: 0L
        statsTracker?.onPlaybackSample(
            item = item,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            sourceContext = mutableUiState.value.sourceLabel
        )
    }

    private fun applySleepTimerTick(controller: Player) {
        val duration = controller.duration.takeIf { it > 0L } ?: 0L
        val action = sleepTimerController.tick(
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            upcomingCount = mutableUiState.value.upcomingCount
        )
        when (action) {
            SleepTimerAction.None -> Unit
            SleepTimerAction.PauseAndRestoreVolume -> {
                controller.volume = 1f
                controller.pause()
            }
            is SleepTimerAction.SetVolume -> {
                controller.volume = action.volume
            }
        }
        updateSleepTimerState()
    }

    private fun updateSleepTimerState() {
        mutableUiState.update {
            it.copy(sleepTimerState = sleepTimerController.state)
        }
    }

    companion object {
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
    }
}

data class PlaybackUiState(
    val currentItem: QueueTrackItem? = null,
    val queueItems: List<QueueTrackItem> = emptyList(),
    val sourceLabel: String = "Queue from Home",
    val isModified: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canSkipNext: Boolean = false,
    val repeatMode: String = QueueRepeatMode.ALL,
    val shuffleEnabled: Boolean = false,
    val sleepTimerState: SleepTimerState = SleepTimerState(),
    val message: String? = null,
    val isRestoringQueue: Boolean = false
) {
    val hasCurrentItem: Boolean
        get() = currentItem != null

    val upcomingCount: Int
        get() = queueItems.count { it.state == QueueListEditor.STATE_UPCOMING }
}

object QueueRepeatMode {
    const val ALL = "ALL"
    const val ONE = "ONE"
    const val OFF = "OFF"
}
