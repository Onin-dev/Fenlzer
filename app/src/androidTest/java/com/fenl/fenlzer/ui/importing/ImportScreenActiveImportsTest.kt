package com.fenl.fenlzer.ui.importing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fenl.fenlzer.importing.youtube.ActiveImportUiItem
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
                    onRetryYoutubeImport = {},
                    onMoveYoutubeImport = { _, _ -> },
                    onDismissYoutubeImport = { dismissedJobId = it },
                    onEnterActiveImports = {},
                    onLeaveActiveImports = {},
                    onHistoryFilterChanged = {},
                    onClearYoutubeHistory = {},
                    onRetryYoutubeHistoryItem = {},
                    onRetryFailed = {},
                    onViewLibrary = {},
                    onOpenSongDetails = {},
                    onClearResult = {},
                    onClearYoutubeResult = {},
                    activeImportsRequestId = 1
                )
            }
        }

        composeRule.onNodeWithText("Downloading Now").assertIsDisplayed()
        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("Recently Finished").assertIsDisplayed()
        composeRule.onNodeWithText("running").assertIsDisplayed()
        composeRule.onNodeWithText("queued").assertIsDisplayed()
        composeRule.onNodeWithText("failed").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Dismiss failed import").performClick()
        assertEquals("failed", dismissedJobId)
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
