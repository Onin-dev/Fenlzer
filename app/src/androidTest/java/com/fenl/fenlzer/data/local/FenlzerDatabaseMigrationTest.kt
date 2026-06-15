package com.fenl.fenlzer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FenlzerDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FenlzerDatabase::class.java
    )

    @Test
    fun migrateVersion1ToLatest() {
        migrateEmptyDatabase(fromVersion = 1, name = "migration-1-to-5")
    }

    @Test
    fun migrateVersion2ToLatest() {
        migrateEmptyDatabase(fromVersion = 2, name = "migration-2-to-5")
    }

    @Test
    fun migrateVersion3ToLatestPreservesDataAndBackfillsProvenance() {
        val name = "migration-3-to-5"
        helper.createDatabase(name, 3).apply {
            execSQL(
                """
                INSERT INTO tracks (
                    trackId, title, titleSortKey, artist, artistSortKey, album, albumSortKey,
                    albumArtist, albumArtistSortKey, genre, durationMs, notes, sourceType,
                    youtubeVideoId, sourceUrl, originalFilename, internalFilename, audioHash,
                    fileSizeBytes, finalAudioFormat, isFavourite, importedAt, updatedAt
                ) VALUES (
                    'track-legacy', 'Legacy song', 'legacy song', 'Artist', 'artist', '', '',
                    '', '', '', 180000, '', 'YOUTUBE', 'video-1',
                    'https://youtube.example/video-1', 'video-1.m4a', 'hash.m4a', 'hash-1',
                    1024, 'M4A_AAC', 1, 10, 10
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO import_jobs (
                    importJobId, apiJobId, jobType, priority, status, sourceUrl, youtubeVideoId,
                    targetFavourite, preferredFormat, actualFormat, progressPercent, createdAt,
                    updatedAt, completedAt
                ) VALUES (
                    'job-legacy', 'api-job-1', 'YOUTUBE_SEARCH', 50, 'TRANSFER_CONFIRMED',
                    'https://youtube.example/video-1', 'video-1', 1, 'M4A_AAC', 'M4A_AAC',
                    100, 10, 20, 20
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO import_history_entries (
                    historyId, importJobId, result, reason, trackId, sourceUrl, youtubeVideoId,
                    displayTitle, friendlyMessage, createdAt
                ) VALUES (
                    'history-legacy', 'job-legacy', 'SUCCESS', 'Manual YouTube search import',
                    'track-legacy', 'https://youtube.example/video-1', 'video-1', 'Legacy song',
                    'Imported successfully.', 20
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name,
            5,
            true,
            *FenlzerDatabase.ALL_MIGRATIONS
        )

        migrated.query(
            """
            SELECT sourceType, reason, priorityClass, pendingActionType,
                   preferredFormat, actualFormat
            FROM import_jobs WHERE importJobId = 'job-legacy'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("DISCOVER_AUTO_FAVOURITE", cursor.getString(0))
            assertEquals("AUTO_FAVOURITE", cursor.getString(1))
            assertEquals("AUTO", cursor.getString(2))
            assertEquals("FAVOURITE", cursor.getString(3))
            assertEquals("M4A_AAC", cursor.getString(4))
            assertEquals("M4A_AAC", cursor.getString(5))
        }
        migrated.query(
            """
            SELECT sourceType, importReason, requestedDownloadFormat, finalAudioFormat
            FROM tracks WHERE trackId = 'track-legacy'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("DISCOVER_AUTO_FAVOURITE", cursor.getString(0))
            assertEquals("AUTO_FAVOURITE", cursor.getString(1))
            assertEquals("M4A_AAC", cursor.getString(2))
            assertEquals("M4A_AAC", cursor.getString(3))
        }
        migrated.query(
            """
            SELECT sourceType, reason, jobType, requestedFormat, finalFormat, pendingActionType
            FROM import_history_entries WHERE historyId = 'history-legacy'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("DISCOVER_AUTO_FAVOURITE", cursor.getString(0))
            assertEquals("AUTO_FAVOURITE", cursor.getString(1))
            assertEquals("YOUTUBE_SEARCH", cursor.getString(2))
            assertEquals("M4A_AAC", cursor.getString(3))
            assertEquals("M4A_AAC", cursor.getString(4))
            assertEquals("FAVOURITE", cursor.getString(5))
        }
        migrated.query(
            """
            SELECT attemptCount, maxAttempts, isVisibleInActiveImports
            FROM import_jobs WHERE importJobId = 'job-legacy'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
    }

    @Test
    fun migrateVersion4ToLatestAddsDurableWorkerState() {
        migrateEmptyDatabase(fromVersion = 4, name = "migration-4-to-5")
    }

    private fun migrateEmptyDatabase(fromVersion: Int, name: String) {
        helper.createDatabase(name, fromVersion).close()
        helper.runMigrationsAndValidate(
            name,
            5,
            true,
            *FenlzerDatabase.ALL_MIGRATIONS
        ).close()
    }
}
