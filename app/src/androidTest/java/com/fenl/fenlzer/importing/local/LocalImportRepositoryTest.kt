package com.fenl.fenlzer.importing.local

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.FenlzerDatabase
import com.fenl.fenlzer.data.storage.FenlzerStorage
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalImportRepositoryTest {
    private lateinit var database: FenlzerDatabase
    private lateinit var storage: FenlzerStorage
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, FenlzerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = FenlzerStorage(context)
        storage.ensureDirectories()
        sourceFile = File(context.cacheDir, "fenlzer-import-repository-test.mp3")
        sourceFile.writeBytes("phase-four-import".encodeToByteArray())
    }

    @After
    fun tearDown() {
        database.close()
        sourceFile.delete()
        storage.audioDir.deleteRecursively()
        storage.thumbnailsDir.deleteRecursively()
        storage.tempImportDir.deleteRecursively()
    }

    @Test
    fun importsLocalFileAndRejectsDuplicateBySha256() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var clock = 1L
        val repository = LocalImportRepository(
            context = ApplicationProvider.getApplicationContext(),
            trackDao = database.trackDao(),
            importDao = database.importDao(),
            storage = storage,
            metadataExtractor = FakeMetadataExtractor(),
            dispatchers = FenlzerDispatchers(
                main = dispatcher,
                io = dispatcher,
                default = dispatcher
            ),
            now = { clock++ },
            idFactory = { "id-${clock++}" }
        )
        val uri = Uri.fromFile(sourceFile)

        val firstResult = repository.importUris(listOf(uri))
        val importedHash = Sha256.hashFile(sourceFile)
        val importedTrack = database.trackDao().getTrackByAudioHash(importedHash)

        assertEquals(LocalImportOutcome.SUCCESS, firstResult.items.single().outcome)
        assertEquals(1, database.trackDao().countTracks())
        assertEquals("Repository Test Song", importedTrack?.title)
        assertTrue(storage.audioFile(importedHash, "mp3").exists())

        val secondResult = repository.importUris(listOf(uri))
        val historyResults = database.importDao().getImportHistory().map { it.result }

        assertEquals(LocalImportOutcome.DUPLICATE, secondResult.items.single().outcome)
        assertEquals(1, database.trackDao().countTracks())
        assertTrue("SUCCESS" in historyResults)
        assertTrue("DUPLICATE" in historyResults)
    }

    @Test
    fun importWithoutEmbeddedTitleUsesFilenameWithoutExtension() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var clock = 1L
        val repository = LocalImportRepository(
            context = ApplicationProvider.getApplicationContext(),
            trackDao = database.trackDao(),
            importDao = database.importDao(),
            storage = storage,
            metadataExtractor = FakeMetadataExtractor(title = ""),
            dispatchers = FenlzerDispatchers(
                main = dispatcher,
                io = dispatcher,
                default = dispatcher
            ),
            now = { clock++ },
            idFactory = { "id-${clock++}" }
        )

        val result = repository.importUris(listOf(Uri.fromFile(sourceFile)))
        val importedHash = Sha256.hashFile(sourceFile)
        val importedTrack = database.trackDao().getTrackByAudioHash(importedHash)

        assertEquals(LocalImportOutcome.SUCCESS, result.items.single().outcome)
        assertEquals("fenlzer-import-repository-test", result.items.single().displayTitle)
        assertEquals("fenlzer-import-repository-test", importedTrack?.title)
        assertEquals("fenlzer-import-repository-test.mp3", importedTrack?.originalFilename)
    }

    @Test
    fun cancellingPreparedImportRemovesPartialFileAndPersistsTerminalState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = LocalImportRepository(
            context = ApplicationProvider.getApplicationContext(),
            trackDao = database.trackDao(),
            importDao = database.importDao(),
            storage = storage,
            metadataExtractor = FakeMetadataExtractor(),
            dispatchers = FenlzerDispatchers(
                main = dispatcher,
                io = dispatcher,
                default = dispatcher
            ),
            now = { 100L },
            idFactory = { "prepared-local-job" }
        )
        val job = repository.prepareImportJobs(listOf(Uri.fromFile(sourceFile))).single()
        val partial = File(storage.tempImportDir, "local-import-${job.importJobId}-partial.mp3")
        partial.writeText("partial")

        repository.cancelImport(job.importJobId)

        assertEquals("CANCELLED", database.importDao().getJob(job.importJobId)?.status)
        assertTrue(!partial.exists())
        assertEquals("CANCELLED", database.importDao().getImportHistory().single().result)
    }

    private class FakeMetadataExtractor(
        private val title: String = "Repository Test Song"
    ) : LocalAudioMetadataExtractor {
        override fun extract(file: File): ExtractedLocalAudioMetadata =
            ExtractedLocalAudioMetadata(
                title = title,
                artist = "Fenlzer Tests",
                album = "Phase 4",
                albumArtist = "Fenlzer Tests",
                genre = "Test",
                year = "2026",
                trackNumber = 1,
                discNumber = null,
                durationMs = 42_000L,
                embeddedArtwork = null,
                extractionFailed = false
            )
    }
}
