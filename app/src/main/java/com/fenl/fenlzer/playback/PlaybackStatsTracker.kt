package com.fenl.fenlzer.playback

import com.fenl.fenlzer.data.repository.PlaybackEventDraft
import com.fenl.fenlzer.data.repository.PlaybackRecoveryProgress
import com.fenl.fenlzer.data.repository.QueueTrackItem
import com.fenl.fenlzer.data.repository.StatsRepository
import com.fenl.fenlzer.data.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PlaybackStatsTracker(
    private val statsRepository: StatsRepository,
    private val settingsRepository: AppSettingsRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private var activePlayback: ActivePlayback? = null
    private var privateModeRecoveryCleared = false

    init {
        scope.launch {
            statsRepository.recoverPlaybackProgressIfAny()
        }
    }

    fun onManualSongChange() {
        finishActivePlayback(
            manualSongChange = true,
            endedAt = now(),
            stopPositionMs = activePlayback?.lastPositionMs
        )
    }

    fun onAutomaticSongChange() {
        finishActivePlayback(
            manualSongChange = false,
            endedAt = now(),
            stopPositionMs = activePlayback?.lastPositionMs
        )
    }

    fun onPlaybackSample(
        item: QueueTrackItem?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        sourceContext: String
    ) {
        val timestamp = now()
        val active = activePlayback
        val itemChanged = active != null && item?.queueItemId != active.queueItemId
        if (itemChanged) {
            finishActivePlayback(
                manualSongChange = false,
                endedAt = timestamp,
                stopPositionMs = active.lastPositionMs
            )
        }

        if (settingsRepository.settings.value.privateModeEnabledForSession) {
            if (activePlayback != null) {
                finishActivePlayback(
                    manualSongChange = false,
                    endedAt = timestamp,
                    stopPositionMs = positionMs
                )
                privateModeRecoveryCleared = true
            } else if (!privateModeRecoveryCleared) {
                scope.launch {
                    statsRepository.clearPlaybackProgressRecovery()
                }
                privateModeRecoveryCleared = true
            }
            return
        }
        privateModeRecoveryCleared = false

        if (item == null) {
            finishActivePlayback(
                manualSongChange = false,
                endedAt = timestamp,
                stopPositionMs = null
            )
            return
        }

        if (!isPlaying) {
            finishActivePlayback(
                manualSongChange = false,
                endedAt = timestamp,
                stopPositionMs = positionMs
            )
            return
        }

        val currentActive = activePlayback
        if (currentActive == null) {
            startActivePlayback(
                item = item,
                startedAt = timestamp,
                positionMs = positionMs,
                durationMs = durationMs,
                sourceContext = sourceContext
            )
            return
        }

        if (currentActive.queueItemId != item.queueItemId) {
            startActivePlayback(
                item = item,
                startedAt = timestamp,
                positionMs = positionMs,
                durationMs = durationMs,
                sourceContext = sourceContext
            )
            return
        }

        val listenedMs = currentActive.listenedMs +
            (timestamp - currentActive.lastSampleAt).coerceAtLeast(0L)
        if (
            isRepeatLoopDetected(
                previousPositionMs = currentActive.lastPositionMs,
                positionMs = positionMs,
                durationMs = durationMs
            )
        ) {
            activePlayback = currentActive.copy(
                listenedMs = listenedMs,
                lastSampleAt = timestamp,
                durationMsAtPlayback = durationMs.coerceAtLeast(currentActive.durationMsAtPlayback)
            )
            finishActivePlayback(
                manualSongChange = false,
                endedAt = timestamp,
                stopPositionMs = currentActive.durationMsAtPlayback
            )
            startActivePlayback(
                item = item,
                startedAt = timestamp,
                positionMs = positionMs,
                durationMs = durationMs,
                sourceContext = sourceContext
            )
            return
        }

        val updated = currentActive.copy(
            listenedMs = listenedMs,
            lastSampleAt = timestamp,
            lastPositionMs = positionMs.coerceAtLeast(0L),
            durationMsAtPlayback = durationMs.coerceAtLeast(currentActive.durationMsAtPlayback),
            sourceContext = sourceContext
        )
        activePlayback = updated

        if (timestamp - updated.lastRecoverySavedAt >= RECOVERY_SAVE_INTERVAL_MS) {
            activePlayback = updated.copy(lastRecoverySavedAt = timestamp)
            saveRecovery(activePlayback ?: updated)
        }
    }

    private fun startActivePlayback(
        item: QueueTrackItem,
        startedAt: Long,
        positionMs: Long,
        durationMs: Long,
        sourceContext: String
    ) {
        activePlayback = ActivePlayback(
            queueItemId = item.queueItemId,
            trackId = item.localTrackId,
            remoteItemId = item.remoteItemId,
            startedAt = startedAt,
            lastSampleAt = startedAt,
            listenedMs = 0L,
            durationMsAtPlayback = durationMs.coerceAtLeast(item.durationMs),
            lastPositionMs = positionMs.coerceAtLeast(0L),
            sourceContext = sourceContext,
            lastRecoverySavedAt = startedAt
        )
        saveRecovery(activePlayback ?: return)
    }

    private fun finishActivePlayback(
        manualSongChange: Boolean,
        endedAt: Long,
        stopPositionMs: Long?
    ) {
        val active = activePlayback ?: return
        val finalized = active.copy(
            listenedMs = active.listenedMs +
                (endedAt - active.lastSampleAt).coerceAtLeast(0L),
            lastSampleAt = endedAt,
            lastPositionMs = stopPositionMs ?: active.lastPositionMs
        )
        activePlayback = null

        scope.launch {
            statsRepository.recordPlayback(
                PlaybackEventDraft(
                    trackId = finalized.trackId,
                    remoteItemId = finalized.remoteItemId,
                    startedAt = finalized.startedAt,
                    endedAt = endedAt,
                    listenedMs = finalized.listenedMs,
                    durationMsAtPlayback = finalized.durationMsAtPlayback,
                    manualSongChange = manualSongChange,
                    stopPositionMs = finalized.lastPositionMs,
                    privateMode = false,
                    sourceContext = finalized.sourceContext
                )
            )
        }
    }

    private fun saveRecovery(active: ActivePlayback) {
        scope.launch {
            statsRepository.savePlaybackProgress(
                PlaybackRecoveryProgress(
                    queueItemId = active.queueItemId,
                    trackId = active.trackId,
                    remoteItemId = active.remoteItemId,
                    startedAt = active.startedAt,
                    lastUpdatedAt = active.lastSampleAt,
                    listenedMs = active.listenedMs,
                    durationMsAtPlayback = active.durationMsAtPlayback,
                    lastPositionMs = active.lastPositionMs,
                    sourceContext = active.sourceContext
                )
            )
        }
    }

    private fun isRepeatLoopDetected(
        previousPositionMs: Long,
        positionMs: Long,
        durationMs: Long
    ): Boolean {
        if (durationMs <= 0L) return false
        return previousPositionMs >= durationMs - REPEAT_LOOP_EDGE_MS &&
            positionMs <= REPEAT_LOOP_EDGE_MS &&
            previousPositionMs - positionMs > REPEAT_LOOP_EDGE_MS
    }

    private data class ActivePlayback(
        val queueItemId: String,
        val trackId: String?,
        val remoteItemId: String?,
        val startedAt: Long,
        val lastSampleAt: Long,
        val listenedMs: Long,
        val durationMsAtPlayback: Long,
        val lastPositionMs: Long,
        val sourceContext: String,
        val lastRecoverySavedAt: Long
    )

    companion object {
        private const val RECOVERY_SAVE_INTERVAL_MS = 5_000L
        private const val REPEAT_LOOP_EDGE_MS = 2_000L
    }
}
