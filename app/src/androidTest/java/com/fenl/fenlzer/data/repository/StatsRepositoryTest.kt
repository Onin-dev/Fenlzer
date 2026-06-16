package com.fenl.fenlzer.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.FenlzerDatabase
import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.PlaybackSessionEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsRepositoryTest {
    private lateinit var database: FenlzerDatabase
    private lateinit var repository: StatsRepository
    private var now = 1_000_000L
    private var idCounter = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FenlzerDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = StatsRepository(
            playbackDao = database.playbackDao(),
            trackDao = database.trackDao(),
            playlistDao = database.playlistDao(),
            dispatchers = FenlzerDispatchers(io = Dispatchers.Unconfined),
            now = { now },
            idFactory = { "stats-id-${idCounter++}" },
            zoneId = ZoneOffset.UTC
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun clearListeningHistoryKeepsTrackStatsAndResetClearsBoth() = runTest {
        database.trackDao().insertTrack(track())

        repository.recordPlayback(
            PlaybackEventDraft(
                trackId = "track-1",
                startedAt = 1_000L,
                endedAt = 21_000L,
                listenedMs = 20_000L,
                durationMsAtPlayback = 60_000L,
                manualSongChange = false,
                stopPositionMs = 20_000L,
                privateMode = false,
                sourceContext = "Queue from Home"
            )
        )

        assertEquals(1, database.playbackDao().countPlaybackEvents())
        assertEquals(1, database.playbackDao().countPlaybackSessions())
        assertEquals(1, database.playbackDao().countTrackStatsSnapshots())

        repository.clearListeningHistory()

        assertEquals(0, database.playbackDao().countPlaybackEvents())
        assertEquals(0, database.playbackDao().countPlaybackSessions())
        assertEquals(1, database.playbackDao().countTrackStatsSnapshots())

        repository.resetStatistics()

        assertEquals(0, database.playbackDao().countPlaybackEvents())
        assertEquals(0, database.playbackDao().countPlaybackSessions())
        assertEquals(0, database.playbackDao().countTrackStatsSnapshots())
    }

    @Test
    fun sessionsFollowFiveMinuteGapRule() = runTest {
        database.trackDao().insertTrack(track())

        repository.recordPlayback(playback(startedAt = 1_000L, endedAt = 11_000L))
        repository.recordPlayback(playback(startedAt = 310_999L, endedAt = 320_999L))
        repository.recordPlayback(playback(startedAt = 620_999L, endedAt = 630_999L))

        assertEquals(2, database.playbackDao().countPlaybackSessions())
        assertEquals(3, database.playbackDao().countPlaybackEvents())
    }

    @Test
    fun recoveryProgressBecomesPlaybackEventAndClearsRecoveryRow() = runTest {
        database.trackDao().insertTrack(track())

        repository.savePlaybackProgress(
            PlaybackRecoveryProgress(
                queueItemId = "queue-1",
                trackId = "track-1",
                remoteItemId = null,
                startedAt = 1_000L,
                lastUpdatedAt = 21_000L,
                listenedMs = 20_000L,
                durationMsAtPlayback = 60_000L,
                lastPositionMs = 20_000L,
                sourceContext = "Queue from Home"
            )
        )
        assertNotNull(database.playbackDao().getPlaybackProgressRecovery())

        repository.recoverPlaybackProgressIfAny()

        assertEquals(1, database.playbackDao().countPlaybackEvents())
        assertEquals(1, database.playbackDao().getTrackStats("track-1")?.playCount)
        assertNull(database.playbackDao().getPlaybackProgressRecovery())
    }

    @Test
    fun completionAverageUsesEveryPlaybackEventAsASample() = runTest {
        database.trackDao().insertTrack(track())

        repository.recordPlayback(playbackWithListenedMs(startedAt = 1_000L, listenedMs = 30_000L))
        repository.recordPlayback(playbackWithListenedMs(startedAt = 40_000L, listenedMs = 5_000L))
        repository.recordPlayback(playbackWithListenedMs(startedAt = 50_000L, listenedMs = 60_000L))

        val stats = database.playbackDao().getTrackStats("track-1")
        assertEquals(3, stats?.completionSampleCount)
        assertEquals((0.5f + (5f / 60f) + 1f) / 3f, stats?.averageCompletionPercent ?: 0f, 0.0001f)
    }

    @Test
    fun globalTimeBucketsIncludeMoreThanFiveHundredEvents() = runTest {
        database.trackDao().insertTrack(track())
        database.playbackDao().insertSession(
            PlaybackSessionEntity(
                sessionId = "session-long-history",
                startedAt = 1_000L,
                endedAt = 1_002_000L,
                totalListenedMs = 501_000L,
                eventCount = 501,
                createdFromPrivateMode = false
            )
        )
        repeat(501) { index ->
            database.playbackDao().insertEvent(
                playbackEvent(
                    eventId = "event-$index",
                    trackId = "track-1",
                    sessionId = "session-long-history",
                    startedAt = 1_000L + index * 2_000L,
                    listenedMs = 1_000L,
                    validListen = false
                )
            )
        }

        val summary = repository.observeStatisticsSummary().first()

        assertEquals(501_000L, summary.listeningTimeByDay.values.sum())
        assertEquals(20, summary.recentEvents.size)
    }

    @Test
    fun privateModeBarrierDiscardsRecoveryAndRejectsStaleProgress() = runTest {
        database.trackDao().insertTrack(track())
        repository.savePlaybackProgress(recoveryProgress(lastUpdatedAt = 100L))

        repository.markPrivateModeRecoveryBarrier(timestamp = 200L)
        repository.savePlaybackProgress(recoveryProgress(lastUpdatedAt = 150L))

        assertEquals(true, database.playbackDao().getPlaybackProgressRecovery()?.privateMode)
        repository.recoverPlaybackProgressIfAny()
        assertEquals(0, database.playbackDao().countPlaybackEvents())
        assertNull(database.playbackDao().getPlaybackProgressRecovery())

        repository.recordPlayback(
            playbackWithListenedMs(
                startedAt = 300L,
                listenedMs = 20_000L,
                privateMode = true
            )
        )
        assertEquals(0, database.playbackDao().countPlaybackEvents())
        assertEquals(0, database.playbackDao().countPlaybackSessions())
        assertEquals(0, database.playbackDao().countTrackStatsSnapshots())
    }

    @Test
    fun streakRediscoveryAndHourlyBucketsAreDeterministic() = runTest {
        now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        database.trackDao().insertTrack(track())
        database.trackDao().insertTrack(track(trackId = "track-2"))
        database.playbackDao().insertSession(
            PlaybackSessionEntity(
                sessionId = "session-trends",
                startedAt = now - 21L * DAY_MS,
                endedAt = now + 1_000L,
                totalListenedMs = 4_000L,
                eventCount = 4,
                createdFromPrivateMode = false
            )
        )
        listOf(
            Triple("event-today", "track-1", now),
            Triple("event-yesterday", "track-1", now - DAY_MS),
            Triple("event-old", "track-2", now - 21L * DAY_MS),
            Triple("event-rediscovered", "track-2", now + 1_000L)
        ).forEach { (eventId, trackId, startedAt) ->
            database.playbackDao().insertEvent(
                playbackEvent(
                    eventId = eventId,
                    trackId = trackId,
                    sessionId = "session-trends",
                    startedAt = startedAt,
                    listenedMs = 1_000L,
                    validListen = true
                )
            )
        }

        val summary = repository.observeStatisticsSummary().first()

        assertEquals(2, summary.listeningStreakDays)
        assertEquals(listOf("track-2"), summary.recentlyRediscoveredSongs.map { it.trackId })
        assertEquals(4_000L, summary.listeningTimeByHour.getValue(12))
    }

    private fun playback(
        startedAt: Long,
        endedAt: Long
    ): PlaybackEventDraft {
        return PlaybackEventDraft(
            trackId = "track-1",
            startedAt = startedAt,
            endedAt = endedAt,
            listenedMs = endedAt - startedAt,
            durationMsAtPlayback = 60_000L,
            manualSongChange = false,
            stopPositionMs = endedAt - startedAt,
            privateMode = false,
            sourceContext = "Queue from Home"
        )
    }

    private fun playbackWithListenedMs(
        startedAt: Long,
        listenedMs: Long,
        privateMode: Boolean = false
    ) = PlaybackEventDraft(
        trackId = "track-1",
        startedAt = startedAt,
        endedAt = startedAt + listenedMs,
        listenedMs = listenedMs,
        durationMsAtPlayback = 60_000L,
        manualSongChange = false,
        stopPositionMs = listenedMs,
        privateMode = privateMode,
        sourceContext = "Queue from Home"
    )

    private fun recoveryProgress(lastUpdatedAt: Long) = PlaybackRecoveryProgress(
        queueItemId = "queue-1",
        trackId = "track-1",
        remoteItemId = null,
        startedAt = 1L,
        lastUpdatedAt = lastUpdatedAt,
        listenedMs = lastUpdatedAt,
        durationMsAtPlayback = 60_000L,
        lastPositionMs = lastUpdatedAt,
        sourceContext = "Queue from Home"
    )

    private fun playbackEvent(
        eventId: String,
        trackId: String,
        sessionId: String,
        startedAt: Long,
        listenedMs: Long,
        validListen: Boolean
    ) = PlaybackEventEntity(
        eventId = eventId,
        trackId = trackId,
        sessionId = sessionId,
        startedAt = startedAt,
        endedAt = startedAt + listenedMs,
        listenedMs = listenedMs,
        durationMsAtPlayback = 60_000L,
        validListen = validListen,
        skip = false,
        completion = false,
        completionPercent = listenedMs.toFloat() / 60_000f,
        stopPositionMs = listenedMs,
        privateMode = false,
        sourceContext = "Queue from Home"
    )

    private fun track(trackId: String = "track-1"): TrackEntity = TrackEntity(
        trackId = trackId,
        title = "Song",
        titleSortKey = "song",
        artist = "Artist",
        artistSortKey = "artist",
        album = "Album",
        albumSortKey = "album",
        albumArtist = "Artist",
        albumArtistSortKey = "artist",
        genre = "",
        year = null,
        trackNumber = null,
        discNumber = null,
        durationMs = 60_000L,
        notes = "",
        sourceType = "LOCAL_FILE",
        youtubeVideoId = null,
        sourceUrl = null,
        originalFilename = "$trackId.mp3",
        internalFilename = "$trackId-internal.mp3",
        audioHash = "$trackId-hash",
        fileSizeBytes = 123L,
        finalAudioFormat = "MP3",
        thumbnailAssetId = null,
        embeddedThumbnailAssetId = null,
        remoteThumbnailUrl = null,
        isFavourite = false,
        favouritedAt = null,
        importedAt = 1L,
        updatedAt = 1L
    )

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1_000L
    }
}
