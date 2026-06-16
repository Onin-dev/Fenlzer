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
        migrateEmptyDatabase(fromVersion = 1, name = "migration-1-to-6")
    }

    @Test
    fun migrateVersion2ToLatest() {
        migrateEmptyDatabase(fromVersion = 2, name = "migration-2-to-6")
    }

    @Test
    fun migrateVersion3ToLatestPreservesDataAndBackfillsProvenance() {
        val name = "migration-3-to-6"
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
            6,
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
        migrateEmptyDatabase(fromVersion = 4, name = "migration-4-to-6")
    }

    @Test
    fun migrateVersion5ToLatestBackfillsCompletionSamplesAndPrivateRecoveryState() {
        val name = "migration-5-to-6"
        helper.createDatabase(name, 5).apply {
            execSQL(
                """
                INSERT INTO tracks (
                    trackId, title, titleSortKey, artist, artistSortKey, album, albumSortKey,
                    albumArtist, albumArtistSortKey, genre, durationMs, notes, sourceType,
                    youtubeVideoId, sourceUrl, originalFilename, internalFilename, audioHash,
                    fileSizeBytes, finalAudioFormat, isFavourite, importedAt, updatedAt,
                    year, trackNumber, discNumber, thumbnailAssetId, embeddedThumbnailAssetId,
                    remoteThumbnailUrl, favouritedAt, importReason, requestedDownloadFormat
                ) VALUES (
                    'track-stats', 'Stats song', 'stats song', 'Artist', 'artist', '', '',
                    '', '', '', 60000, '', 'LOCAL_FILE', NULL, NULL, 'song.mp3', 'song.mp3',
                    'hash-stats', 100, 'MP3', 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL,
                    NULL, NULL, NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO playback_sessions (
                    sessionId, startedAt, endedAt, totalListenedMs, eventCount, createdFromPrivateMode
                ) VALUES ('session-stats', 1, 120001, 90000, 2, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO playback_events (
                    eventId, trackId, remoteItemId, sessionId, startedAt, endedAt, listenedMs,
                    durationMsAtPlayback, validListen, skip, completion, completionPercent,
                    stopPositionMs, privateMode, sourceContext
                ) VALUES
                    ('event-a', 'track-stats', NULL, 'session-stats', 1, 30001, 30000,
                     60000, 1, 0, 0, 0.5, 30000, 0, 'HOME'),
                    ('event-b', 'track-stats', NULL, 'session-stats', 60001, 120001, 60000,
                     60000, 1, 0, 1, 1.0, NULL, 0, 'HOME')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO track_stats_snapshots (
                    trackId, playCount, skipCount, completionCount, totalListenedMs,
                    firstPlayedAt, lastPlayedAt, averageCompletionPercent
                ) VALUES ('track-stats', 2, 0, 1, 90000, 1, 120001, 0.5)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name,
            6,
            true,
            *FenlzerDatabase.ALL_MIGRATIONS
        )

        migrated.query(
            """
            SELECT completionSampleCount, averageCompletionPercent
            FROM track_stats_snapshots WHERE trackId = 'track-stats'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
            assertEquals(0.75f, cursor.getFloat(1), 0.0001f)
        }
        migrated.query("PRAGMA table_info(playback_progress_recovery)").use { cursor ->
            var foundPrivateMode = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "privateMode") foundPrivateMode = true
            }
            assertEquals(true, foundPrivateMode)
        }
        migrated.close()
    }

    private fun migrateEmptyDatabase(fromVersion: Int, name: String) {
        helper.createDatabase(name, fromVersion).close()
        helper.runMigrationsAndValidate(
            name,
            6,
            true,
            *FenlzerDatabase.ALL_MIGRATIONS
        ).close()
    }
}
