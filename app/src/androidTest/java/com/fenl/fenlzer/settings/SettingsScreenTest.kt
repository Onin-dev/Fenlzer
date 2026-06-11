package com.fenl.fenlzer.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.fenl.fenlzer.data.remote.ApiHealthCheckResult
import com.fenl.fenlzer.data.settings.AppSettings
import com.fenl.fenlzer.data.storage.FenlzerStorageUsage
import com.fenl.fenlzer.ui.theme.FenlzerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deleteAllSongsRequiresTypedDeleteConfirmation() {
        var deleteAllCount = 0

        composeRule.setContent {
            FenlzerTheme {
                SettingsScreen(
                    settings = AppSettings(),
                    initialApiToken = "",
                    onThemeModeChanged = {},
                    onDefaultRepeatModeChanged = {},
                    onDefaultShuffleChanged = {},
                    onDefaultHomeSortChanged = {},
                    onImportDuplicateBehaviorChanged = {},
                    onDeleteConfirmationChanged = {},
                    onSleepTimerDefaultMinutesChanged = {},
                    onPrivateModeChanged = {},
                    onClearListeningHistory = {},
                    onResetStatistics = {},
                    storageUsage = FenlzerStorageUsage(
                        audioBytes = 1L,
                        thumbnailBytes = 2L,
                        cacheBytes = 3L,
                        databaseBytes = 4L
                    ),
                    onRefreshStorageUsage = {},
                    onClearCache = {},
                    onClearImportHistory = {},
                    onDeleteAllSongs = { deleteAllCount += 1 },
                    apiDiagnostics = emptyList(),
                    onApiSettingsSaved = { _, _ -> },
                    onTestApiConnection = { _, _ ->
                        ApiHealthCheckResult.Failure(
                            message = "unused",
                            errorCode = "UNUSED",
                            retryable = false
                        )
                    },
                    appVersion = "test"
                )
            }
        }

        composeRule.onNodeWithTag("deleteAllSongsButton")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("confirmStorageActionButton").assertIsNotEnabled()
        composeRule.onNodeWithTag("deleteAllSongsConfirmationField").performTextInput("DELETE")
        composeRule.onNodeWithTag("confirmStorageActionButton")
            .assertIsEnabled()
            .performClick()

        assertEquals(1, deleteAllCount)
    }
}
