package com.fenl.fenlzer.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.FenlzerDatabase
import com.fenl.fenlzer.data.local.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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
            idFactory = { "stats-id-${idCounter++}" }
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

    private fun track(): TrackEntity = TrackEntity(
        trackId = "track-1",
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
        originalFilename = "song.mp3",
        internalFilename = "abc.mp3",
        audioHash = "abc",
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
}
