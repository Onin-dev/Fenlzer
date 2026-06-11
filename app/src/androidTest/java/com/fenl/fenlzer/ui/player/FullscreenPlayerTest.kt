package com.fenl.fenlzer.ui.player

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.fenl.fenlzer.data.repository.QueueTrackItem
import com.fenl.fenlzer.playback.PlaybackUiState
import com.fenl.fenlzer.ui.theme.FenlzerTheme
import org.junit.Rule
import org.junit.Test

class FullscreenPlayerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullscreenPlayerShowsCoreControlsForCurrentTrack() {
        composeRule.setContent {
            FenlzerTheme {
                FullscreenPlayer(
                    playbackState = PlaybackUiState(
                        currentItem = trackItem(),
                        queueItems = listOf(trackItem()),
                        sourceLabel = "Queue from Home",
                        durationMs = 180_000L
                    ),
                    privateModeEnabled = false,
                    sleepTimerDefaultMinutes = 30,
                    onMinimize = {},
                    onToggleFavourite = {},
                    onPlayPause = {},
                    onPrevious = {},
                    onNext = {},
                    onSeekTo = {},
                    onToggleRepeat = {},
                    onToggleShuffle = {},
                    onAddToPlaylist = {},
                    onOpenSongDetails = {},
                    onEditMetadata = {},
                    onDeleteFromFenlzer = {},
                    onOpenQueue = {},
                    onStartSleepTimerDuration = {},
                    onStartSleepTimerEndOfSong = {},
                    onStartSleepTimerEndOfQueue = {},
                    onCancelSleepTimer = {}
                )
            }
        }

        composeRule.onNodeWithTag("fullscreenPlayer").assertIsDisplayed()
        composeRule.onNodeWithText("Fullscreen Test Song").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Previous").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sleep timer").assertIsDisplayed()
    }

    private fun trackItem(): QueueTrackItem =
        QueueTrackItem(
            queueItemId = "queue-item-1",
            trackId = "track-1",
            displayTitle = "Fullscreen Test Song",
            artist = "Fenlzer Tests",
            durationMs = 180_000L,
            position = 0,
            state = "CURRENT",
            insertedBy = "TAP",
            isFavourite = false,
            audioUri = Uri.EMPTY,
            thumbnailUri = null
        )
}
