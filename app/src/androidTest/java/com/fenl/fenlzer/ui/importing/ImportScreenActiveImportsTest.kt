package com.fenl.fenlzer.ui.importing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.fenl.fenlzer.importing.youtube.ActiveImportUiItem
import com.fenl.fenlzer.importing.local.LocalImportBatchResult
import com.fenl.fenlzer.importing.local.LocalImportItemResult
import com.fenl.fenlzer.importing.local.LocalImportOutcome
import com.fenl.fenlzer.ui.theme.FenlzerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImportScreenActiveImportsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeImportsAreSplitIntoSectionsAndFailedJobsCanBeDismissed() {
        var dismissedJobId: String? = null
        val retriedJobIds = mutableListOf<String>()
        composeRule.setContent {
            FenlzerTheme {
                ImportScreen(
                    state = LocalImportUiState(),
                    youtubeState = YoutubeImportUiState(
                        activeJobs = listOf(
                            job("running", "DOWNLOADING", cancellable = true),
                            job("queued", "QUEUED", cancellable = true),
                            job("failed", "FAILED", retryable = true, dismissible = true)
                        )
                    ),
                    onImportFromDevice = {},
                    onYoutubeQueryChanged = {},
                    onSearchYoutube = {},
                    onImportYoutubeResult = {},
                    onYoutubePlaylistUrlChanged = {},
                    onPreviewYoutubePlaylist = {},
                    onToggleYoutubePlaylistItem = {},
                    onSelectAllYoutubePlaylistItems = {},
                    onImportSelectedYoutubePlaylistItems = {},
                    onImportWholeYoutubePlaylist = {},
                    onCancelYoutubeImport = {},
                    onRetryYoutubeImport = { retriedJobIds += it },
                    onMoveYoutubeImport = { _, _ -> },
                    onDismissYoutubeImport = { dismissedJobId = it },
                    onEnterActiveImports = {},
                    onLeaveActiveImports = {},
                    onHistoryFilterChanged = {},
                    onClearYoutubeHistory = {},
                    onRetryYoutubeHistoryItem = {},
                    onRetryFailed = {},
                    onPlayImportedSongs = {},
                    onAddImportedSongsToPlaylist = {},
                    onViewLibrary = {},
                    onOpenSongDetails = {},
                    onClearResult = {},
                    onClearYoutubeResult = {},
                    activeImportsRequestId = 1
                )
            }
        }

        composeRule.onNodeWithText("Running").assertIsDisplayed()
        composeRule.onNodeWithText("Queued").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        composeRule.onNodeWithText("Failed").assertIsDisplayed()
        composeRule.onNodeWithText("running").assertIsDisplayed()
        composeRule.onNodeWithText("queued").assertIsDisplayed()
        composeRule.onNodeWithText("failed").assertIsDisplayed()

        composeRule.onNodeWithText("Retry all").performClick()
        assertEquals(listOf("failed"), retriedJobIds)

        composeRule.onNodeWithContentDescription("Dismiss failed import").performClick()
        assertEquals("failed", dismissedJobId)
    }

    @Test
    fun deviceImportResultExposesAllCompletionActions() {
        var playedTrackIds = emptyList<String>()
        var playlistTrackIds = emptyList<String>()
        var highlightedTrackIds = emptyList<String>()
        val result = LocalImportBatchResult(
            items = listOf(
                LocalImportItemResult(
                    filename = "song.mp3",
                    displayTitle = "Song",
                    outcome = LocalImportOutcome.SUCCESS,
                    trackId = "track-1"
                )
            ),
            startedAt = 1L,
            completedAt = 2L
        )
        composeRule.setContent {
            FenlzerTheme {
                ImportScreen(
                    state = LocalImportUiState(result = result),
                    youtubeState = YoutubeImportUiState(),
                    onImportFromDevice = {},
                    onYoutubeQueryChanged = {},
                    onSearchYoutube = {},
                    onImportYoutubeResult = {},
                    onYoutubePlaylistUrlChanged = {},
                    onPreviewYoutubePlaylist = {},
                    onToggleYoutubePlaylistItem = {},
                    onSelectAllYoutubePlaylistItems = {},
                    onImportSelectedYoutubePlaylistItems = {},
                    onImportWholeYoutubePlaylist = {},
                    onCancelYoutubeImport = {},
                    onRetryYoutubeImport = {},
                    onMoveYoutubeImport = { _, _ -> },
                    onDismissYoutubeImport = {},
                    onEnterActiveImports = {},
                    onLeaveActiveImports = {},
                    onHistoryFilterChanged = {},
                    onClearYoutubeHistory = {},
                    onRetryYoutubeHistoryItem = {},
                    onRetryFailed = {},
                    onPlayImportedSongs = { playedTrackIds = it },
                    onAddImportedSongsToPlaylist = { playlistTrackIds = it },
                    onViewLibrary = { highlightedTrackIds = it },
                    onOpenSongDetails = {},
                    onClearResult = {},
                    onClearYoutubeResult = {}
                )
            }
        }

        composeRule.onNodeWithText("Import From Device").performClick()
        composeRule.onNodeWithText("Play imported songs").performScrollTo().performClick()
        composeRule.onNodeWithText("Add to playlist").performScrollTo().performClick()
        composeRule.onNodeWithText("View in Library").performScrollTo().performClick()

        assertEquals(listOf("track-1"), playedTrackIds)
        assertEquals(listOf("track-1"), playlistTrackIds)
        assertEquals(listOf("track-1"), highlightedTrackIds)
    }

    private fun job(
        id: String,
        status: String,
        retryable: Boolean = false,
        cancellable: Boolean = false,
        dismissible: Boolean = false
    ) = ActiveImportUiItem(
        importJobId = id,
        apiJobId = null,
        title = id,
        sourceLabel = "YouTube search",
        status = status,
        progressPercent = if (status == "DOWNLOADING") 40 else null,
        queuePosition = if (status == "QUEUED") 1 else null,
        thumbnailUrl = null,
        errorMessage = if (status == "FAILED") "Download failed" else null,
        retryable = retryable,
        cancellable = cancellable,
        attemptCount = 1,
        maxAttempts = 3,
        dismissible = dismissible
    )
}
