package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.dao.PlaybackDao
import com.fenl.fenlzer.data.local.dao.PlaylistDao
import com.fenl.fenlzer.data.local.dao.TrackDao
import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.PlaybackProgressRecoveryEntity
import com.fenl.fenlzer.data.local.entity.PlaybackSessionEntity
import com.fenl.fenlzer.data.local.entity.PlaylistEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackStatsSnapshotEntity
import com.fenl.fenlzer.domain.text.AudioTitleFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class StatsRepository(
    private val playbackDao: PlaybackDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val writeMutex = Mutex()
    private val recoveryMutex = Mutex()

    fun observeStatisticsSummary(): Flow<StatisticsSummary> {
        return combine(
            trackDao.observeTracksByRecentlyAdded(),
            playlistDao.observePlaylists(),
            playbackDao.observeTrackStatsSnapshots(),
            playbackDao.observePlaybackEvents(),
            playbackDao.observeSessions()
        ) { tracks, playlists, stats, events, sessions ->
            buildSummary(
                tracks = tracks,
                playlists = playlists,
                stats = stats,
                events = events,
                sessions = sessions
            )
        }
    }

    fun observeTrackStats(trackId: String): Flow<TrackStatsSnapshotEntity?> {
        return playbackDao.observeTrackStats(trackId)
    }

    suspend fun recordPlayback(draft: PlaybackEventDraft) = withContext(dispatchers.io) {
        if (draft.privateMode) return@withContext
        if (draft.trackId == null && draft.remoteItemId == null) return@withContext

        writeMutex.withLock {
            val endedAt = draft.endedAt ?: now()
            val listenedMs = draft.listenedMs.coerceAtLeast(0L)
            val durationMs = draft.durationMsAtPlayback.coerceAtLeast(0L)
            val validListen = PlaybackStatsRules.isValidListen(listenedMs, durationMs)
            val skip = PlaybackStatsRules.isSkip(
                manualSongChange = draft.manualSongChange,
                listenedMs = listenedMs,
                durationMs = durationMs
            )
            val completion = PlaybackStatsRules.isCompletion(listenedMs, durationMs)
            val completionPercent = PlaybackStatsRules.completionPercent(
                listenedMs = listenedMs,
                durationMs = durationMs
            )
            val sessionId = sessionIdForEvent(
                startedAt = draft.startedAt,
                endedAt = endedAt,
                listenedMs = listenedMs
            )

            val event = PlaybackEventEntity(
                eventId = idFactory(),
                trackId = draft.trackId,
                remoteItemId = draft.remoteItemId,
                sessionId = sessionId,
                startedAt = draft.startedAt,
                endedAt = endedAt,
                listenedMs = listenedMs,
                durationMsAtPlayback = durationMs,
                validListen = validListen,
                skip = skip,
                completion = completion,
                completionPercent = completionPercent,
                stopPositionMs = draft.stopPositionMs.takeUnless { completion },
                privateMode = false,
                sourceContext = draft.sourceContext
            )
            playbackDao.insertEvent(event)
            draft.trackId?.let { trackId ->
                updateTrackStats(trackId = trackId, event = event)
            }
            recoveryMutex.withLock {
                playbackDao.clearPlaybackProgressRecoveryForPlayback(startedAt = event.startedAt)
            }
        }
    }

    suspend fun savePlaybackProgress(progress: PlaybackRecoveryProgress) = withContext(dispatchers.io) {
        if (progress.trackId == null && progress.remoteItemId == null) return@withContext
        recoveryMutex.withLock {
            val existing = playbackDao.getPlaybackProgressRecovery()
            if (
                existing?.privateMode == true &&
                existing.lastUpdatedAt >= progress.lastUpdatedAt
            ) {
                return@withLock
            }
            playbackDao.upsertPlaybackProgressRecovery(
                PlaybackProgressRecoveryEntity(
                    progressId = PlaybackDao.DEFAULT_RECOVERY_PROGRESS_ID,
                    queueItemId = progress.queueItemId,
                    trackId = progress.trackId,
                    remoteItemId = progress.remoteItemId,
                    startedAt = progress.startedAt,
                    lastUpdatedAt = progress.lastUpdatedAt,
                    listenedMs = progress.listenedMs.coerceAtLeast(0L),
                    durationMsAtPlayback = progress.durationMsAtPlayback.coerceAtLeast(0L),
                    lastPositionMs = progress.lastPositionMs.coerceAtLeast(0L),
                    sourceContext = progress.sourceContext,
                    privateMode = progress.privateMode
                )
            )
        }
    }

    suspend fun recoverPlaybackProgressIfAny() = withContext(dispatchers.io) {
        val recovery = recoveryMutex.withLock {
            val stored = playbackDao.getPlaybackProgressRecovery() ?: return@withLock null
            if (stored.privateMode) {
                playbackDao.clearPlaybackProgressRecovery()
                null
            } else {
                stored
            }
        } ?: return@withContext
        recordPlayback(
            PlaybackEventDraft(
                trackId = recovery.trackId,
                remoteItemId = recovery.remoteItemId,
                startedAt = recovery.startedAt,
                endedAt = recovery.lastUpdatedAt,
                listenedMs = recovery.listenedMs,
                durationMsAtPlayback = recovery.durationMsAtPlayback,
                manualSongChange = false,
                stopPositionMs = recovery.lastPositionMs,
                privateMode = false,
                sourceContext = recovery.sourceContext
            )
        )
    }

    suspend fun clearPlaybackProgressRecovery() = withContext(dispatchers.io) {
        recoveryMutex.withLock {
            playbackDao.clearPlaybackProgressRecovery()
        }
    }

    suspend fun markPrivateModeRecoveryBarrier(timestamp: Long = now()) = withContext(dispatchers.io) {
        recoveryMutex.withLock {
            playbackDao.upsertPlaybackProgressRecovery(
                PlaybackProgressRecoveryEntity(
                    progressId = PlaybackDao.DEFAULT_RECOVERY_PROGRESS_ID,
                    queueItemId = null,
                    trackId = null,
                    remoteItemId = null,
                    startedAt = timestamp,
                    lastUpdatedAt = timestamp,
                    listenedMs = 0L,
                    durationMsAtPlayback = 0L,
                    lastPositionMs = 0L,
                    sourceContext = PRIVATE_MODE_RECOVERY_SOURCE,
                    privateMode = true
                )
            )
        }
    }

    suspend fun clearListeningHistory() = withContext(dispatchers.io) {
        writeMutex.withLock {
            playbackDao.clearPlaybackEvents()
            playbackDao.clearPlaybackSessions()
            recoveryMutex.withLock {
                playbackDao.clearPlaybackProgressRecovery()
            }
        }
    }

    suspend fun resetStatistics() = withContext(dispatchers.io) {
        writeMutex.withLock {
            playbackDao.clearPlaybackEvents()
            playbackDao.clearPlaybackSessions()
            playbackDao.clearTrackStatsSnapshots()
            recoveryMutex.withLock {
                playbackDao.clearPlaybackProgressRecovery()
            }
        }
    }

    suspend fun mergeRemoteItemIntoTrack(remoteItemId: String, trackId: String) =
        withContext(dispatchers.io) {
            writeMutex.withLock {
                val remoteEvents = playbackDao.getPlaybackEventsForRemoteItem(remoteItemId)
                remoteEvents.forEach { event ->
                    updateTrackStats(trackId = trackId, event = event.copy(trackId = trackId, remoteItemId = null))
                }
                playbackDao.convertRemoteEventsToTrack(remoteItemId = remoteItemId, trackId = trackId)
                recoveryMutex.withLock {
                    playbackDao.convertRemoteProgressToTrack(remoteItemId = remoteItemId, trackId = trackId)
                }
            }
        }

    private suspend fun sessionIdForEvent(
        startedAt: Long,
        endedAt: Long,
        listenedMs: Long
    ): String {
        val latest = playbackDao.getLatestSession()
        val existingSession = latest?.takeIf { session ->
            val previousActivityEndedAt = session.endedAt ?: session.startedAt
            startedAt - previousActivityEndedAt < PlaybackStatsRules.SESSION_GAP_MS
        }

        if (existingSession != null) {
            playbackDao.updateSession(
                sessionId = existingSession.sessionId,
                endedAt = maxOf(existingSession.endedAt ?: endedAt, endedAt),
                totalListenedMs = existingSession.totalListenedMs + listenedMs,
                eventCount = existingSession.eventCount + 1
            )
            return existingSession.sessionId
        }

        val sessionId = idFactory()
        playbackDao.insertSession(
            PlaybackSessionEntity(
                sessionId = sessionId,
                startedAt = startedAt,
                endedAt = endedAt,
                totalListenedMs = listenedMs,
                eventCount = 1,
                createdFromPrivateMode = false
            )
        )
        return sessionId
    }

    private suspend fun updateTrackStats(
        trackId: String,
        event: PlaybackEventEntity
    ) {
        val current = playbackDao.getTrackStats(trackId)
        val oldAverageSamples = current?.completionSampleCount ?: 0
        val nextAverage = if (oldAverageSamples == 0) {
            event.completionPercent
        } else {
            (((current?.averageCompletionPercent ?: 0f) * oldAverageSamples) + event.completionPercent) /
                (oldAverageSamples + 1)
        }
        val validListenIncrement = if (event.validListen) 1 else 0

        playbackDao.upsertTrackStats(
            TrackStatsSnapshotEntity(
                trackId = trackId,
                playCount = (current?.playCount ?: 0) + validListenIncrement,
                skipCount = (current?.skipCount ?: 0) + if (event.skip) 1 else 0,
                completionCount = (current?.completionCount ?: 0) + if (event.completion) 1 else 0,
                totalListenedMs = (current?.totalListenedMs ?: 0L) + event.listenedMs,
                firstPlayedAt = if (event.validListen) {
                    current?.firstPlayedAt ?: event.startedAt
                } else {
                    current?.firstPlayedAt
                },
                lastPlayedAt = if (event.validListen) {
                    event.endedAt ?: event.startedAt
                } else {
                    current?.lastPlayedAt
                },
                completionSampleCount = oldAverageSamples + 1,
                averageCompletionPercent = nextAverage.coerceIn(0f, 1f)
            )
        )
    }

    private fun buildSummary(
        tracks: List<TrackEntity>,
        playlists: List<PlaylistEntity>,
        stats: List<TrackStatsSnapshotEntity>,
        events: List<PlaybackEventEntity>,
        sessions: List<PlaybackSessionEntity>
    ): StatisticsSummary {
        val tracksById = tracks.associateBy { it.trackId }
        val statsByTrackId = stats.associateBy { it.trackId }
        val validEvents = events.filter { it.validListen }
        val latestValidEventByTrack = validEvents
            .groupBy { it.trackId }
            .mapNotNull { (trackId, trackEvents) ->
                trackId?.let { id -> id to trackEvents.maxBy { it.startedAt } }
            }
            .toMap()

        return StatisticsSummary(
            totalListeningMs = stats.sumOf { it.totalListenedMs },
            totalSongsImported = tracks.size,
            totalPlaylists = playlists.count { it.playlistType == "REGULAR" },
            mostListenedSong = stats
                .filter { it.totalListenedMs > 0L }
                .maxByOrNull { it.totalListenedMs }
                ?.let { rankedTrack(it, tracksById) },
            mostListenedArtist = mostListenedArtist(stats, tracksById),
            mostSkippedSong = stats
                .filter { it.skipCount > 0 }
                .maxByOrNull { it.skipCount }
                ?.let { rankedTrack(it, tracksById, value = it.skipCount.toLong()) },
            favouriteArtist = favouriteArtist(tracks),
            songsNeverPlayed = tracks.count { track ->
                (statsByTrackId[track.trackId]?.playCount ?: 0) == 0
            },
            listeningTimeByDay = events
                .groupBy { event -> event.startedAt.toLocalDate() }
                .mapValues { (_, dayEvents) -> dayEvents.sumOf { it.listenedMs } }
                .toSortedMap(compareByDescending<LocalDate> { it }),
            listeningTimeByHour = events
                .groupBy { event -> event.startedAt.toLocalHour() }
                .mapValues { (_, hourEvents) -> hourEvents.sumOf { it.listenedMs } }
                .toSortedMap(),
            listeningStreakDays = listeningStreakDays(validEvents),
            longestSessionMs = sessions.maxOfOrNull { it.totalListenedMs } ?: 0L,
            recentlyRediscoveredSongs = recentlyRediscoveredSongs(validEvents, tracksById),
            recentEvents = events.take(20).map { event ->
                StatisticsRecentEvent(
                    trackId = event.trackId,
                    title = event.trackId
                        ?.let(tracksById::get)
                        ?.displayTitle()
                        ?: "Remote song",
                    artist = event.trackId
                        ?.let(tracksById::get)
                        ?.artist
                        .orEmpty(),
                    startedAt = event.startedAt,
                    listenedMs = event.listenedMs,
                    validListen = event.validListen,
                    skip = event.skip,
                    completion = event.completion
                )
            }
        )
    }

    private fun rankedTrack(
        stats: TrackStatsSnapshotEntity,
        tracksById: Map<String, TrackEntity>,
        value: Long = stats.totalListenedMs
    ): StatisticsRankedTrack? {
        val track = tracksById[stats.trackId] ?: return null
        return StatisticsRankedTrack(
            trackId = track.trackId,
            title = track.displayTitle(),
            artist = track.artist,
            value = value
        )
    }

    private fun mostListenedArtist(
        stats: List<TrackStatsSnapshotEntity>,
        tracksById: Map<String, TrackEntity>
    ): StatisticsRankedArtist? {
        return stats
            .groupBy { statsEntry -> tracksById[statsEntry.trackId]?.artist.orEmpty() }
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, entries) -> entries.sumOf { it.totalListenedMs } }
            .filterValues { it > 0L }
            .maxByOrNull { it.value }
            ?.let { StatisticsRankedArtist(name = it.key, value = it.value) }
    }

    private fun favouriteArtist(tracks: List<TrackEntity>): StatisticsRankedArtist? {
        return tracks
            .filter { it.isFavourite && it.artist.isNotBlank() }
            .groupingBy { it.artist }
            .eachCount()
            .maxByOrNull { it.value }
            ?.let { StatisticsRankedArtist(name = it.key, value = it.value.toLong()) }
    }

    private fun recentlyRediscoveredSongs(
        validEvents: List<PlaybackEventEntity>,
        tracksById: Map<String, TrackEntity>
    ): List<StatisticsRankedTrack> {
        val newestAllowed = now() - RECENTLY_REDISCOVERED_WINDOW_MS
        val rediscoveryGap = RECENTLY_REDISCOVERED_GAP_MS

        return validEvents
            .filter { it.trackId != null }
            .groupBy { it.trackId.orEmpty() }
            .mapNotNull { (trackId, trackEvents) ->
                val sorted = trackEvents.sortedBy { it.startedAt }
                val latest = sorted.lastOrNull() ?: return@mapNotNull null
                if (latest.startedAt < newestAllowed) return@mapNotNull null
                val previous = sorted.dropLast(1).lastOrNull() ?: return@mapNotNull null
                if (latest.startedAt - previous.startedAt < rediscoveryGap) return@mapNotNull null
                tracksById[trackId]?.let { track ->
                    StatisticsRankedTrack(
                        trackId = track.trackId,
                        title = track.displayTitle(),
                        artist = track.artist,
                        value = latest.startedAt
                    )
                }
            }
            .sortedByDescending { it.value }
            .take(10)
    }

    private fun listeningStreakDays(validEvents: List<PlaybackEventEntity>): Int {
        val daysWithValidListen = validEvents
            .map { it.startedAt.toLocalDate() }
            .toSet()
        if (daysWithValidListen.isEmpty()) return 0

        var day = Instant.ofEpochMilli(now()).atZone(zoneId).toLocalDate()
        var streak = 0
        while (day in daysWithValidListen) {
            streak += 1
            day = day.minusDays(1)
        }
        return streak
    }

    private fun Long.toLocalDate(): LocalDate {
        return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    }

    private fun Long.toLocalHour(): Int {
        return Instant.ofEpochMilli(this).atZone(zoneId).hour
    }

    private fun TrackEntity.displayTitle(): String {
        return AudioTitleFormatter.displayTitle(
            title = title,
            fallbackFilename = originalFilename
        )
    }

    companion object {
        private const val RECENTLY_REDISCOVERED_WINDOW_MS = 14L * 24L * 60L * 60L * 1_000L
        private const val RECENTLY_REDISCOVERED_GAP_MS = 20L * 24L * 60L * 60L * 1_000L
        private const val PRIVATE_MODE_RECOVERY_SOURCE = "PRIVATE_MODE_EXCLUDED"
    }
}

object PlaybackStatsRules {
    const val VALID_LISTEN_MAX_THRESHOLD_MS = 15_000L
    const val SESSION_GAP_MS = 5L * 60L * 1_000L
    const val REPEAT_LOOP_EDGE_MS = 2_000L

    fun validListenThreshold(durationMs: Long): Long {
        return if (durationMs > 0L) {
            minOf(VALID_LISTEN_MAX_THRESHOLD_MS, durationMs / 2L)
        } else {
            VALID_LISTEN_MAX_THRESHOLD_MS
        }
    }

    fun isValidListen(listenedMs: Long, durationMs: Long): Boolean {
        return listenedMs.coerceAtLeast(0L) >= validListenThreshold(durationMs)
    }

    fun isSkip(
        manualSongChange: Boolean,
        listenedMs: Long,
        durationMs: Long
    ): Boolean {
        return manualSongChange && !isValidListen(listenedMs, durationMs)
    }

    fun isCompletion(listenedMs: Long, durationMs: Long): Boolean {
        return durationMs > 0L &&
            listenedMs.coerceAtLeast(0L).toFloat() >= durationMs.toFloat() * 0.9f
    }

    fun completionPercent(listenedMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (listenedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun isRepeatLoopDetected(
        previousPositionMs: Long,
        positionMs: Long,
        durationMs: Long
    ): Boolean {
        if (durationMs <= 0L) return false
        return previousPositionMs >= durationMs - REPEAT_LOOP_EDGE_MS &&
            positionMs <= REPEAT_LOOP_EDGE_MS &&
            previousPositionMs - positionMs > REPEAT_LOOP_EDGE_MS
    }
}

data class PlaybackEventDraft(
    val trackId: String? = null,
    val remoteItemId: String? = null,
    val startedAt: Long,
    val endedAt: Long?,
    val listenedMs: Long,
    val durationMsAtPlayback: Long,
    val manualSongChange: Boolean,
    val stopPositionMs: Long?,
    val privateMode: Boolean,
    val sourceContext: String
)

data class PlaybackRecoveryProgress(
    val queueItemId: String?,
    val trackId: String?,
    val remoteItemId: String?,
    val startedAt: Long,
    val lastUpdatedAt: Long,
    val listenedMs: Long,
    val durationMsAtPlayback: Long,
    val lastPositionMs: Long,
    val sourceContext: String,
    val privateMode: Boolean = false
)

data class StatisticsSummary(
    val totalListeningMs: Long = 0L,
    val totalSongsImported: Int = 0,
    val totalPlaylists: Int = 0,
    val mostListenedSong: StatisticsRankedTrack? = null,
    val mostListenedArtist: StatisticsRankedArtist? = null,
    val mostSkippedSong: StatisticsRankedTrack? = null,
    val favouriteArtist: StatisticsRankedArtist? = null,
    val songsNeverPlayed: Int = 0,
    val listeningTimeByDay: Map<LocalDate, Long> = emptyMap(),
    val listeningTimeByHour: Map<Int, Long> = emptyMap(),
    val listeningStreakDays: Int = 0,
    val longestSessionMs: Long = 0L,
    val recentlyRediscoveredSongs: List<StatisticsRankedTrack> = emptyList(),
    val recentEvents: List<StatisticsRecentEvent> = emptyList()
)

data class StatisticsRankedTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val value: Long
)

data class StatisticsRankedArtist(
    val name: String,
    val value: Long
)

data class StatisticsRecentEvent(
    val trackId: String?,
    val title: String,
    val artist: String,
    val startedAt: Long,
    val listenedMs: Long,
    val validListen: Boolean,
    val skip: Boolean,
    val completion: Boolean
)
