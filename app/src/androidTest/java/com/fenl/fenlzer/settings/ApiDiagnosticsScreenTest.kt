package com.fenl.fenlzer.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fenl.fenlzer.data.repository.ApiDiagnosticItem
import com.fenl.fenlzer.data.repository.ApiDiagnosticSource
import com.fenl.fenlzer.ui.theme.FenlzerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiDiagnosticsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysEntriesAndFiltersFailures() {
        composeRule.setContent {
            FenlzerTheme {
                ApiDiagnosticsScreen(
                    localEntries = listOf(successEntry()),
                    serverEntries = listOf(failedEntry()),
                    serverLoading = false,
                    serverError = null,
                    onBack = {},
                    onClearDiagnostics = {},
                    onRefreshServer = {}
                )
            }
        }

        composeRule.onNodeWithTag("apiDiagnosticsScreen").assertIsDisplayed()
        composeRule.onNodeWithText("GET /v1/health").assertIsDisplayed()
        composeRule.onNodeWithText("POST /v1/youtube/search").assertIsDisplayed()

        composeRule.onNodeWithTag("diagnosticsFilterfailed").performClick()

        composeRule.onNodeWithText("POST /v1/youtube/search").assertIsDisplayed()
        composeRule.onAllNodesWithText("GET /v1/health").assertCountEquals(0)
        composeRule.onNodeWithTag("diagnosticsList").performTouchInput { swipeUp() }
        composeRule.onNodeWithText("Error: UNAUTHORIZED").assertIsDisplayed()
        composeRule.onNodeWithText("Server").assertIsDisplayed()
    }

    @Test
    fun clearingDiagnosticsRequiresConfirmationAndClearsLocalOnly() {
        var clearCount = 0
        composeRule.setContent {
            FenlzerTheme {
                ApiDiagnosticsScreen(
                    localEntries = listOf(successEntry()),
                    serverEntries = listOf(failedEntry()),
                    serverLoading = false,
                    serverError = null,
                    onBack = {},
                    onClearDiagnostics = { clearCount += 1 },
                    onRefreshServer = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Clear diagnostics").performClick()
        composeRule.onNodeWithText("Clear API diagnostics?").assertIsDisplayed()
        composeRule.onNodeWithText("Clear").performClick()

        assertEquals(1, clearCount)
        composeRule.onNodeWithText("POST /v1/youtube/search").assertIsDisplayed()
    }

    private fun successEntry() = ApiDiagnosticItem(
        diagnosticId = "diag_success",
        source = ApiDiagnosticSource.LOCAL,
        requestId = "req_success",
        endpoint = "/v1/health",
        method = "GET",
        startedAt = 1_780_000_000_000L,
        durationMs = 42,
        statusCode = 200,
        success = true,
        errorCode = null,
        sanitizedMessage = null
    )

    private fun failedEntry() = ApiDiagnosticItem(
        diagnosticId = "diag_failed",
        source = ApiDiagnosticSource.SERVER,
        requestId = "req_failed",
        endpoint = "/v1/youtube/search",
        method = "POST",
        startedAt = 1_780_000_001_000L,
        durationMs = 18,
        statusCode = 401,
        success = false,
        errorCode = "UNAUTHORIZED",
        sanitizedMessage = "Missing bearer token."
    )
}
