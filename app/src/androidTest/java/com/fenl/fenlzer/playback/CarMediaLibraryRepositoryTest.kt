package com.fenl.fenlzer.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fenl.fenlzer.data.local.FenlzerDatabase
import com.fenl.fenlzer.data.local.entity.PlaylistEntity
import com.fenl.fenlzer.data.local.entity.PlaylistTrackEntity
import com.fenl.fenlzer.data.local.entity.ThumbnailAssetEntity
import com.fenl.fenlzer.data.local.entity.TrackEntity
import com.fenl.fenlzer.data.repository.QueueRepository
import com.fenl.fenlzer.data.storage.FenlzerStorage
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
class CarMediaLibraryRepositoryTest {
    private lateinit var database: FenlzerDatabase
    private lateinit var storage: FenlzerStorage
    private lateinit var repository: CarMediaLibraryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FenlzerDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        storage = FenlzerStorage(ApplicationProvider.getApplicationContext())
        repository = CarMediaLibraryRepository(
            database = database,
            storage = storage,
            queueRepository = QueueRepository(
                queueDao = database.queueDao(),
                trackDao = database.trackDao(),
                remoteDiscoverDao = database.remoteDiscoverDao(),
                storage = storage
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun childrenExposeValidBrowsableAndPlayableMediaItems() = runTest {
        database.trackDao().upsertThumbnailAsset(thumbnail())
        database.trackDao().insertTrack(track("alpha", title = "Alpha", thumbnailAssetId = "thumb-1"))

        val rootChildren = repository.children(CarMediaIds.ROOT, page = 0, pageSize = 20)
        val songs = repository.children(CarMediaIds.SONGS, page = 0, pageSize = 20)

        assertEquals(listOf(CarMediaIds.SONGS, CarMediaIds.PLAYLISTS, CarMediaIds.SMART_PLAYLISTS), rootChildren.map { it.mediaId })
        assertTrue(rootChildren.first().mediaMetadata.isBrowsable == true)
        assertFalse(rootChildren.first().mediaMetadata.isPlayable == true)
        assertEquals(listOf(CarMediaIds.track("alpha")), songs.map { it.mediaId })
        assertTrue(songs.first().mediaMetadata.isPlayable == true)
        assertFalse(songs.first().mediaMetadata.isBrowsable == true)
        assertNotNull(songs.first().localConfiguration?.uri)
        assertTrue(songs.first().mediaMetadata.artworkUri.toString().endsWith("thumb-1.jpg"))
    }

    @Test
    fun replaceQueueFromPlaylistRequestCreatesPersistentLocalAutoQueue() = runTest {
        database.trackDao().insertTrack(track("alpha", title = "Alpha"))
        database.trackDao().insertTrack(track("bravo", title = "Bravo"))
        database.playlistDao().upsertPlaylist(playlist())
        database.playlistDao().insertPlaylistTrack(playlistTrack("alpha", position = 0))
        database.playlistDao().insertPlaylistTrack(playlistTrack("bravo", position = 1))

        val resolution = repository.replaceQueueFromRequest(
            mediaItems = listOf(MediaItem.Builder().setMediaId(CarMediaIds.playlist("playlist-1")).build()),
            requestedStartIndex = C.INDEX_UNSET
        )
        val queue = database.queueDao().getQueueState()

        assertEquals(listOf("Alpha", "Bravo"), resolution.mediaItems.map { it.mediaMetadata.title.toString() })
        assertEquals(0, resolution.startIndex)
        assertEquals("Android Auto: Road Songs", queue?.sourceLabel)
        assertEquals(2, database.queueDao().getQueueItems().size)
    }

    @Test
    fun searchRequestCreatesDownloadedOnlyQueue() = runTest {
        database.trackDao().insertTrack(track("alpha", title = "Alpha", artist = "Fen"))
        database.trackDao().insertTrack(track("bravo", title = "Bravo", artist = "Other"))

        val request = MediaItem.Builder()
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setSearchQuery("Alpha Fen")
                    .build()
            )
            .build()

        val resolution = repository.replaceQueueFromRequest(
            mediaItems = listOf(request),
            requestedStartIndex = C.INDEX_UNSET
        )
        val queue = database.queueDao().getQueueState()

        assertEquals(listOf("Alpha"), resolution.mediaItems.map { it.mediaMetadata.title.toString() })
        assertEquals("Android Auto Search: Alpha Fen", queue?.sourceLabel)
    }

    @Test
    fun addQueueItemsFromRequestPersistsQueueItemsAndReturnsQueueMediaIds() = runTest {
        database.trackDao().insertTrack(track("alpha", title = "Alpha"))

        val addedItems = repository.addQueueItemsFromRequest(
            listOf(MediaItem.Builder().setMediaId(CarMediaIds.track("alpha")).build())
        )
        val queueItems = database.queueDao().getQueueItems()

        assertEquals(1, queueItems.size)
        assertEquals(queueItems.first().queueItemId, addedItems.first().mediaId)
        assertEquals("alpha", queueItems.first().trackId)
    }

    private fun track(
        trackId: String,
        title: String,
        artist: String = "Artist",
        thumbnailAssetId: String? = null
    ): TrackEntity = TrackEntity(
        trackId = trackId,
        title = title,
        titleSortKey = title.lowercase(),
        artist = artist,
        artistSortKey = artist.lowercase(),
        album = "Album",
        albumSortKey = "album",
        albumArtist = artist,
        albumArtistSortKey = artist.lowercase(),
        genre = "Genre",
        year = "2026",
        trackNumber = null,
        discNumber = null,
        durationMs = 180_000L,
        notes = "",
        sourceType = "LOCAL_FILE",
        youtubeVideoId = null,
        sourceUrl = null,
        originalFilename = "$trackId.mp3",
        internalFilename = "$trackId.mp3",
        audioHash = "hash-$trackId",
        fileSizeBytes = 100L,
        finalAudioFormat = "MP3",
        thumbnailAssetId = thumbnailAssetId,
        embeddedThumbnailAssetId = null,
        remoteThumbnailUrl = null,
        isFavourite = false,
        favouritedAt = null,
        importedAt = 1L,
        updatedAt = 1L
    )

    private fun thumbnail(): ThumbnailAssetEntity = ThumbnailAssetEntity(
        thumbnailAssetId = "thumb-1",
        kind = "REMOTE",
        internalFilename = "thumb-1.jpg",
        sourceUrl = null,
        contentHash = "thumb-1",
        createdAt = 1L,
        lastAccessedAt = 1L,
        isPermanent = true
    )

    private fun playlist(): PlaylistEntity = PlaylistEntity(
        playlistId = "playlist-1",
        name = "Road Songs",
        playlistType = CarMediaLibraryTreeBuilder.REGULAR_PLAYLIST_TYPE,
        customThumbnailAssetId = null,
        createdAt = 1L,
        modifiedAt = 1L
    )

    private fun playlistTrack(trackId: String, position: Int): PlaylistTrackEntity =
        PlaylistTrackEntity(
            playlistId = "playlist-1",
            trackId = trackId,
            position = position,
            addedAt = 1L
        )
}
