package com.fenl.fenlzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fenl.fenlzer.data.settings.AppSettings
import com.fenl.fenlzer.data.settings.InMemoryAppSettingsRepository
import com.fenl.fenlzer.ui.FenlzerApp
import com.fenl.fenlzer.ui.theme.FenlzerTheme
import org.junit.Rule
import org.junit.Test

class FenlzerAppSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomTabsSwitchBetweenPrimarySections() {
        setFenlzerContent()

        composeRule.onNodeWithTag("homeScreen").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_playlists").performClick()
        composeRule.onNodeWithTag("playlistsScreen").assertIsDisplayed()
        composeRule.onNodeWithText("Smart Playlists").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_import").performClick()
        composeRule.onNodeWithTag("importScreen").assertIsDisplayed()
    }

    @Test
    fun emptyMiniPlayerOpensImportTab() {
        setFenlzerContent()

        composeRule.onNodeWithTag("emptyMiniPlayerMainArea").performClick()

        composeRule.onNodeWithTag("importScreen").assertIsDisplayed()
    }

    private fun setFenlzerContent() {
        composeRule.setContent {
            FenlzerTheme {
                FenlzerApp(
                    appGraph = AppGraph(
                        settingsRepository = InMemoryAppSettingsRepository(AppSettings())
                    )
                )
            }
        }
    }
}
