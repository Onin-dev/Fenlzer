package com.fenl.fenlzer.ui.player

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import com.fenl.fenlzer.data.repository.QueueTrackItem
import com.fenl.fenlzer.playback.PlaybackUiState
import com.fenl.fenlzer.ui.theme.FenlzerTheme
import org.junit.Assert.assertEquals
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
                    onOpenAudioOutput = {},
                    onShare = {},
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

    @Test
    fun fullscreenPlayerShowsUpNextSourceAndModifiedState() {
        var openQueueCount = 0
        val current = trackItem()
        val next = trackItem(
            queueItemId = "queue-item-2",
            trackId = "track-2",
            displayTitle = "Next Fullscreen Song",
            position = 1,
            state = "UPCOMING"
        )

        composeRule.setContent {
            FenlzerTheme {
                FullscreenPlayer(
                    playbackState = PlaybackUiState(
                        currentItem = current,
                        queueItems = listOf(current, next),
                        sourceLabel = "Playlist - Testing",
                        isModified = true,
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
                    onOpenAudioOutput = {},
                    onShare = {},
                    onOpenSongDetails = {},
                    onEditMetadata = {},
                    onDeleteFromFenlzer = {},
                    onOpenQueue = { openQueueCount += 1 },
                    onStartSleepTimerDuration = {},
                    onStartSleepTimerEndOfSong = {},
                    onStartSleepTimerEndOfQueue = {},
                    onCancelSleepTimer = {}
                )
            }
        }

        composeRule.onNodeWithTag("fullscreenPlaybackContext").assertIsDisplayed()
        composeRule.onNodeWithText("Playlist - Testing").assertIsDisplayed()
        composeRule.onNodeWithTag("fullscreenQueueModifiedIndicator").assertIsDisplayed()
        composeRule.onNodeWithText("Next Fullscreen Song").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit queue").performClick()

        composeRule.runOnIdle {
            assertEquals(1, openQueueCount)
        }
    }

    @Test
    fun artworkVerticalDragDoesNotMinimizePlayer() {
        var minimizeCount = 0

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
                    onMinimize = { minimizeCount += 1 },
                    onToggleFavourite = {},
                    onPlayPause = {},
                    onPrevious = {},
                    onNext = {},
                    onSeekTo = {},
                    onToggleRepeat = {},
                    onToggleShuffle = {},
                    onAddToPlaylist = {},
                    onOpenAudioOutput = {},
                    onShare = {},
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

        composeRule.onNodeWithTag("fullscreenPlayerArtwork")
            .performTouchInput { swipeDown() }

        composeRule.runOnIdle {
            assertEquals(0, minimizeCount)
        }
    }

    @Test
    fun artworkHorizontalSwipeChangesSong() {
        var nextCount = 0
        var previousCount = 0

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
                    onPrevious = { previousCount += 1 },
                    onNext = { nextCount += 1 },
                    onSeekTo = {},
                    onToggleRepeat = {},
                    onToggleShuffle = {},
                    onAddToPlaylist = {},
                    onOpenAudioOutput = {},
                    onShare = {},
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

        composeRule.onNodeWithTag("fullscreenPlayerArtwork")
            .performTouchInput { swipeLeft() }

        composeRule.runOnIdle {
            assertEquals(1, nextCount)
            assertEquals(0, previousCount)
        }
    }

    private fun trackItem(
        queueItemId: String = "queue-item-1",
        trackId: String = "track-1",
        displayTitle: String = "Fullscreen Test Song",
        position: Int = 0,
        state: String = "CURRENT"
    ): QueueTrackItem =
        QueueTrackItem(
            queueItemId = queueItemId,
            trackId = trackId,
            displayTitle = displayTitle,
            artist = "Fenlzer Tests",
            durationMs = 180_000L,
            position = position,
            state = state,
            insertedBy = "TAP",
            isFavourite = false,
            audioUri = Uri.EMPTY,
            thumbnailUri = null
        )
}
