package com.fenl.fenlzer.settings

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fenl.fenlzer.data.local.entity.ApiDiagnosticEntryEntity
import com.fenl.fenlzer.ui.theme.FenlzerTheme
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
                    entries = listOf(successEntry(), failedEntry()),
                    onBack = {},
                    onClearDiagnostics = {}
                )
            }
        }

        composeRule.onNodeWithTag("apiDiagnosticsScreen").assertIsDisplayed()
        composeRule.onNodeWithText("GET /v1/health").assertIsDisplayed()
        composeRule.onNodeWithText("POST /v1/youtube/search").assertIsDisplayed()

        composeRule.onNodeWithTag("diagnosticsFilterfailed").performClick()

        composeRule.onNodeWithText("POST /v1/youtube/search").assertIsDisplayed()
        composeRule.onNodeWithText("GET /v1/health").assertDoesNotExist()
        composeRule.onNodeWithText("UNAUTHORIZED").assertIsDisplayed()
    }

    private fun successEntry() = ApiDiagnosticEntryEntity(
        diagnosticId = "diag_success",
        requestId = "req_success",
        endpoint = "/v1/health",
        method = "GET",
        startedAt = 1_780_000_000_000L,
        durationMs = 42,
        statusCode = 200,
        success = true,
        errorCode = null,
        sanitizedMessage = null,
        metadataJson = null
    )

    private fun failedEntry() = ApiDiagnosticEntryEntity(
        diagnosticId = "diag_failed",
        requestId = "req_failed",
        endpoint = "/v1/youtube/search",
        method = "POST",
        startedAt = 1_780_000_001_000L,
        durationMs = 18,
        statusCode = 401,
        success = false,
        errorCode = "UNAUTHORIZED",
        sanitizedMessage = "Missing bearer token.",
        metadataJson = null
    )
}
