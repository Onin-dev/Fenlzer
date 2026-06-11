package com.fenl.fenlzer.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fenl.fenlzer.data.local.entity.ImportJobEntity
import com.fenl.fenlzer.data.local.entity.PlaybackEventEntity
import com.fenl.fenlzer.data.local.entity.PlaybackProgressRecoveryEntity
import com.fenl.fenlzer.data.local.entity.PlaybackSessionEntity
import com.fenl.fenlzer.data.local.entity.PlaylistEntity
import com.fenl.fenlzer.data.local.entity.PlaylistTrackEntity
import com.fenl.fenlzer.data.local.entity.QueueItemEntity
import com.fenl.fenlzer.data.local.entity.QueueStateEntity
import com.fenl.fenlzer.data.local.entity.RemoteItemEntity
import com.fenl.fenlzer.data.local.entity.ThumbnailAssetEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.local.entity.TrackOriginalMetadataEntity
import com.fenl.fenlzer.data.repository.AlbumBulkEditDraft
import com.fenl.fenlzer.data.repository.MetadataRepository
import com.fenl.fenlzer.data.repository.QueueRepository
import com.fenl.fenlzer.data.repository.StatsRepository
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.domain.delete.DeleteFromFenlzerUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FenlzerDatabaseSmokeTest {
    private lateinit var database: FenlzerDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FenlzerDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun databaseCreatesAndStoresTrackWithOriginalMetadata() = runTest {
        database.trackDao().insertTrackWithOriginalMetadata(
            track = track(),
            originalMetadata = originalMetadata()
        )

        assertEquals(1, database.trackDao().countTracks())
        assertNotNull(database.trackDao().getTrack("track-1"))
    }

    @Test
    fun importJobsCanReferencePersistedRemoteSearchItems() = runTest {
        database.remoteDiscoverDao().upsertRemoteItem(remoteItem())

        database.importDao().upsertJob(
            ImportJobEntity(
                importJobId = "job-1",
                apiJobId = null,
                jobType = "YOUTUBE_SEARCH",
                priority = 100,
                status = "QUEUED",
                sourceUrl = "https://www.youtube.com/watch?v=video-1",
                youtubeVideoId = "video-1",
                remoteItemId = "remote-1",
                targetFavourite = false,
                preferredFormat = "M4A_AAC",
                progressPercent = 0,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        assertEquals(1, database.importDao().getActiveJobs().size)
        assertEquals(1, database.importDao().getRecoverableYoutubeSearchJobs().size)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun playlistTracksRejectDuplicateSongInSamePlaylist() = runTest {
        database.trackDao().insertTrack(track())
        database.playlistDao().upsertPlaylist(playlist())

        val playlistTrack = PlaylistTrackEntity(
            playlistId = "playlist-1",
            trackId = "track-1",
            position = 0,
            addedAt = 1L
        )
        database.playlistDao().insertPlaylistTrack(playlistTrack)
        database.playlistDao().insertPlaylistTrack(playlistTrack.copy(position = 1))
    }

    @Test
    fun playlistTracksCanBeReorderedManually() = runTest {
        database.trackDao().insertTrack(track(trackId = "track-1", audioHash = "hash-1"))
        database.trackDao().insertTrack(track(trackId = "track-2", audioHash = "hash-2"))
        database.trackDao().insertTrack(track(trackId = "track-3", audioHash = "hash-3"))
        database.playlistDao().upsertPlaylist(playlist())

        listOf("track-1", "track-2", "track-3").forEachIndexed { index, trackId ->
            database.playlistDao().insertPlaylistTrack(
                PlaylistTrackEntity(
                    playlistId = "playlist-1",
                    trackId = trackId,
                    position = index,
                    addedAt = index.toLong()
                )
            )
        }

        database.playlistDao().reorderPlaylistTracks(
            playlistId = "playlist-1",
            orderedTrackIds = listOf("track-3", "track-1", "track-2"),
            modifiedAt = 10L
        )

        assertEquals(
            listOf("track-3", "track-1", "track-2"),
            database.playlistDao().getPlaylistTracks("playlist-1").map { it.trackId }
        )
        assertEquals(10L, database.playlistDao().getPlaylist("playlist-1")?.modifiedAt)
    }

    @Test
    fun queuedTrackReferenceMarksQueueModifiedWhenPlaylistMembershipChanges() = runTest {
        database.trackDao().insertTrack(track(trackId = "track-1", audioHash = "hash-1"))
        database.trackDao().insertTrack(track(trackId = "track-2", audioHash = "hash-2"))
        database.queueDao().upsertQueueState(queueState())
        database.queueDao().insertQueueItems(
            listOf(
                queueItem(queueItemId = "queue-1", trackId = "track-1", position = 0, state = "CURRENT"),
                queueItem(queueItemId = "queue-2", trackId = "track-2", position = 1, state = "UPCOMING")
            )
        )
        val repository = QueueRepository(
            queueDao = database.queueDao(),
            trackDao = database.trackDao(),
            storage = FenlzerStorage(ApplicationProvider.getApplicationContext()),
            now = { 50L }
        )

        val changed = repository.markModifiedIfContainsTrack("track-2")

        assertEquals(true, changed)
        val state = database.queueDao().getQueueState()
        assertEquals(true, state?.isModified)
        assertEquals("QUEUE", state?.sourceType)
        assertEquals(null, state?.sourceId)
        assertEquals("Queue from Playlist: Playlist - Modified", state?.sourceLabel)
        assertEquals(50L, state?.updatedAt)
    }

    @Test
    fun deleteFromFenlzerRemovesFilesPlaylistRefsAndRepairsQueue() = runTest {
        val storage = FenlzerStorage(ApplicationProvider.getApplicationContext())
        storage.ensureDirectories()
        val audioFile = storage.audioDir.resolve("phase13-delete.mp3")
        val thumbnailFile = storage.thumbnailsDir.resolve("phase13-delete-thumb.jpg")
        audioFile.delete()
        thumbnailFile.delete()
        audioFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        thumbnailFile.writeBytes(byteArrayOf(5, 6, 7))

        database.trackDao().upsertThumbnailAsset(
            ThumbnailAssetEntity(
                thumbnailAssetId = "thumb-delete",
                kind = "TRACK_CUSTOM",
                internalFilename = thumbnailFile.name,
                sourceUrl = null,
                contentHash = "thumb-delete-hash",
                createdAt = 1L,
                lastAccessedAt = 1L,
                isPermanent = true
            )
        )
        database.trackDao().insertTrack(
            track(trackId = "track-1", audioHash = "hash-1").copy(
                internalFilename = audioFile.name,
                thumbnailAssetId = "thumb-delete"
            )
        )
        database.trackDao().insertTrack(
            track(trackId = "track-2", audioHash = "hash-2").copy(
                internalFilename = "phase13-keep.mp3"
            )
        )
        database.playlistDao().upsertPlaylist(playlist())
        database.playlistDao().insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = "playlist-1",
                trackId = "track-1",
                position = 0,
                addedAt = 1L
            )
        )
        database.queueDao().upsertQueueState(queueState())
        database.queueDao().insertQueueItems(
            listOf(
                queueItem(queueItemId = "queue-1", trackId = "track-1", position = 0, state = "CURRENT"),
                queueItem(queueItemId = "queue-2", trackId = "track-2", position = 1, state = "UPCOMING")
            )
        )
        val useCase = DeleteFromFenlzerUseCase(
            trackDao = database.trackDao(),
            queueDao = database.queueDao(),
            storage = storage,
            now = { 80L }
        )

        val result = useCase.deleteTracks(listOf("track-1"))

        assertEquals(1, result.deletedTracks)
        assertEquals(null, database.trackDao().getTrack("track-1"))
        assertFalse(audioFile.exists())
        assertFalse(thumbnailFile.exists())
        assertEquals(null, database.trackDao().getThumbnailAsset("thumb-delete"))
        assertTrue(database.playlistDao().getPlaylistTracks("playlist-1").isEmpty())

        val remainingQueueItem = database.queueDao().getQueueItems().single()
        assertEquals("track-2", remainingQueueItem.trackId)
        assertEquals("CURRENT", remainingQueueItem.state)
        assertEquals(0, remainingQueueItem.position)
        val state = database.queueDao().getQueueState()
        assertEquals(true, state?.isModified)
        assertEquals("QUEUE", state?.sourceType)
        assertEquals("Queue from Playlist: Playlist - Modified", state?.sourceLabel)
        assertEquals("queue-2", state?.currentQueueItemId)
        assertEquals(80L, state?.updatedAt)
    }

    @Test
    fun deleteFromFenlzerAdvancesCurrentQueueItemToNextSurvivingSong() = runTest {
        val storage = FenlzerStorage(ApplicationProvider.getApplicationContext())
        database.trackDao().insertTrack(track(trackId = "track-1", audioHash = "hash-1"))
        database.trackDao().insertTrack(track(trackId = "track-2", audioHash = "hash-2"))
        database.trackDao().insertTrack(track(trackId = "track-3", audioHash = "hash-3"))
        database.queueDao().upsertQueueState(queueState().copy(currentQueueItemId = "queue-2"))
        database.queueDao().insertQueueItems(
            listOf(
                queueItem(queueItemId = "queue-1", trackId = "track-1", position = 0, state = "UPCOMING"),
                queueItem(queueItemId = "queue-2", trackId = "track-2", position = 1, state = "CURRENT"),
                queueItem(queueItemId = "queue-3", trackId = "track-3", position = 2, state = "UPCOMING")
            )
        )
        val useCase = DeleteFromFenlzerUseCase(
            trackDao = database.trackDao(),
            queueDao = database.queueDao(),
            storage = storage,
            now = { 90L }
        )

        useCase.deleteTracks(listOf("track-2"))

        val items = database.queueDao().getQueueItems()
        assertEquals(listOf("track-1", "track-3"), items.map { it.trackId })
        assertEquals(listOf(0, 1), items.map { it.position })
        assertEquals("UPCOMING", items[0].state)
        assertEquals("CURRENT", items[1].state)
        val state = database.queueDao().getQueueState()
        assertEquals("queue-3", state?.currentQueueItemId)
        assertEquals(true, state?.isModified)
        assertEquals(90L, state?.updatedAt)
    }

    @Test
    fun remoteQueueAndPlaybackReferencesConvertToTrackOnImport() = runTest {
        database.trackDao().insertTrack(track(trackId = "track-1", audioHash = "hash-1"))
        database.remoteDiscoverDao().upsertRemoteItem(remoteItem())
        database.queueDao().upsertQueueState(queueState())
        database.queueDao().insertQueueItem(
            QueueItemEntity(
                queueItemId = "queue-remote",
                queueStateId = "default_queue",
                trackId = null,
                remoteItemId = "remote-1",
                position = 0,
                state = "CURRENT",
                insertedBy = "DISCOVER_START",
                addedAt = 1L
            )
        )
        database.playbackDao().insertSession(
            PlaybackSessionEntity(
                sessionId = "session-1",
                startedAt = 10L,
                endedAt = 130_010L,
                totalListenedMs = 120_000L,
                eventCount = 1,
                createdFromPrivateMode = false
            )
        )
        database.playbackDao().insertEvent(
            PlaybackEventEntity(
                eventId = "event-1",
                trackId = null,
                remoteItemId = "remote-1",
                sessionId = "session-1",
                startedAt = 10L,
                endedAt = 130_010L,
                listenedMs = 120_000L,
                durationMsAtPlayback = 180_000L,
                validListen = true,
                skip = false,
                completion = false,
                completionPercent = 0.66f,
                stopPositionMs = 120_000L,
                privateMode = false,
                sourceContext = "DISCOVER"
            )
        )
        database.playbackDao().upsertPlaybackProgressRecovery(
            PlaybackProgressRecoveryEntity(
                progressId = "active_playback",
                queueItemId = "queue-remote",
                trackId = null,
                remoteItemId = "remote-1",
                startedAt = 10L,
                lastUpdatedAt = 40_010L,
                listenedMs = 40_000L,
                durationMsAtPlayback = 180_000L,
                lastPositionMs = 40_000L,
                sourceContext = "DISCOVER"
            )
        )
        val statsRepository = StatsRepository(
            playbackDao = database.playbackDao(),
            trackDao = database.trackDao(),
            playlistDao = database.playlistDao()
        )

        database.remoteDiscoverDao().markImported("remote-1", "IMPORTED", "track-1", updatedAt = 200L)
        database.queueDao().convertRemoteItemToTrack("remote-1", "track-1")
        statsRepository.mergeRemoteItemIntoTrack("remote-1", "track-1")

        val queueItem = database.queueDao().getQueueItems().single()
        assertEquals("track-1", queueItem.trackId)
        assertEquals(null, queueItem.remoteItemId)
        val event = database.playbackDao().getNonPrivatePlaybackEvents().single()
        assertEquals("track-1", event.trackId)
        assertEquals(null, event.remoteItemId)
        val recovery = database.playbackDao().getPlaybackProgressRecovery()
        assertEquals("track-1", recovery?.trackId)
        assertEquals(null, recovery?.remoteItemId)
        val remoteItem = database.remoteDiscoverDao().getRemoteItem("remote-1")
        assertEquals("IMPORTED", remoteItem?.importState)
        assertEquals("track-1", remoteItem?.importedTrackId)
        val stats = database.playbackDao().getTrackStats("track-1")
        assertEquals(1, stats?.playCount)
        assertEquals(120_000L, stats?.totalListenedMs)
    }

    @Test
    fun artistRenameUpdatesTracksAndRecordsBulkOperation() = runTest {
        database.trackDao().insertTrack(track(trackId = "track-1", audioHash = "hash-1"))
        database.trackDao().insertTrack(
            track(trackId = "track-2", audioHash = "hash-2").copy(artist = "Other Artist")
        )
        val repository = metadataRepository(now = { 60L })

        val affected = repository.renameArtist("Artist", "Merged Artist")

        assertEquals(1, affected)
        assertEquals("Merged Artist", database.trackDao().getTrack("track-1")?.artist)
        assertEquals("Other Artist", database.trackDao().getTrack("track-2")?.artist)
        val operation = database.trackDao().getBulkMetadataOperations().single()
        assertEquals("ARTIST_RENAME", operation.operationType)
        assertEquals(60L, operation.createdAt)
    }

    @Test
    fun albumBulkEditCanFillEmptyFieldsAndRecordsOperation() = runTest {
        database.trackDao().insertTrack(
            track(trackId = "track-1", audioHash = "hash-1").copy(year = null, genre = "")
        )
        database.trackDao().insertTrack(
            track(trackId = "track-2", audioHash = "hash-2").copy(year = "1999", genre = "Rock")
        )
        val repository = metadataRepository(now = { 70L })
        val albumKey = MetadataRepository.albumIdentityKey("Album", "Artist")

        val affected = repository.editAlbum(
            albumKey = albumKey,
            draft = AlbumBulkEditDraft(
                album = "Album",
                albumArtist = "Artist",
                year = "2026",
                genre = "Pop",
                overwriteFilledFields = false
            )
        )

        assertEquals(2, affected)
        assertEquals("2026", database.trackDao().getTrack("track-1")?.year)
        assertEquals("Pop", database.trackDao().getTrack("track-1")?.genre)
        assertEquals("1999", database.trackDao().getTrack("track-2")?.year)
        assertEquals("Rock", database.trackDao().getTrack("track-2")?.genre)
        val operation = database.trackDao().getBulkMetadataOperations().single()
        assertEquals("ALBUM_EDIT", operation.operationType)
        assertEquals(70L, operation.createdAt)
    }

    private fun track(
        trackId: String = "track-1",
        audioHash: String = "abc"
    ): TrackEntity = TrackEntity(
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
        durationMs = 180_000L,
        notes = "",
        sourceType = "LOCAL_FILE",
        youtubeVideoId = null,
        sourceUrl = null,
        originalFilename = "song.mp3",
        internalFilename = "abc.mp3",
        audioHash = audioHash,
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

    private fun originalMetadata(): TrackOriginalMetadataEntity = TrackOriginalMetadataEntity(
        trackId = "track-1",
        originalTitle = "Song",
        originalArtist = "Artist",
        originalAlbum = "Album",
        originalAlbumArtist = "Artist",
        originalGenre = "",
        originalYear = null,
        originalTrackNumber = null,
        originalDiscNumber = null,
        originalThumbnailKind = "NONE",
        rawMetadataJson = null
    )

    private fun playlist(): PlaylistEntity = PlaylistEntity(
        playlistId = "playlist-1",
        name = "Playlist",
        playlistType = "REGULAR",
        customThumbnailAssetId = null,
        createdAt = 1L,
        modifiedAt = 1L
    )

    private fun queueState(): QueueStateEntity = QueueStateEntity(
        queueStateId = "default_queue",
        sourceType = "PLAYLIST",
        sourceId = "playlist-1",
        sourceLabel = "Queue from Playlist: Playlist",
        isModified = false,
        currentQueueItemId = "queue-1",
        repeatMode = "ALL",
        shuffleEnabled = false,
        playbackPositionMs = 0L,
        wasPlaying = true,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun queueItem(
        queueItemId: String,
        trackId: String,
        position: Int,
        state: String
    ): QueueItemEntity = QueueItemEntity(
        queueItemId = queueItemId,
        queueStateId = "default_queue",
        trackId = trackId,
        remoteItemId = null,
        position = position,
        state = state,
        insertedBy = "PLAYLIST_START",
        addedAt = position.toLong()
    )

    private fun remoteItem(): RemoteItemEntity = RemoteItemEntity(
        remoteItemId = "remote-1",
        youtubeVideoId = "video-1",
        sourceUrl = "https://www.youtube.com/watch?v=video-1",
        title = "Remote Song",
        artistOrChannel = "Channel",
        durationMs = 180_000L,
        thumbnailUrl = null,
        canStream = true,
        canDownload = true,
        streamState = "UNRESOLVED",
        lastPlayableUrl = null,
        playableUrlExpiresAt = null,
        lastResolvedAt = null,
        importState = "NOT_IMPORTED",
        importedTrackId = null,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun metadataRepository(now: () -> Long): MetadataRepository =
        MetadataRepository(
            trackDao = database.trackDao(),
            playlistDao = database.playlistDao(),
            playbackDao = database.playbackDao(),
            storage = FenlzerStorage(ApplicationProvider.getApplicationContext()),
            now = now
        )
}
