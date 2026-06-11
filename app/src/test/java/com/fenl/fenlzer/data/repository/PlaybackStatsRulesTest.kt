package com.fenl.fenlzer.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStatsRulesTest {
    @Test
    fun validListenUsesHalfDurationForShortSongs() {
        assertEquals(5_000L, PlaybackStatsRules.validListenThreshold(10_000L))
        assertFalse(PlaybackStatsRules.isValidListen(4_999L, 10_000L))
        assertTrue(PlaybackStatsRules.isValidListen(5_000L, 10_000L))
    }

    @Test
    fun validListenUsesFifteenSecondCapForLongSongs() {
        assertEquals(15_000L, PlaybackStatsRules.validListenThreshold(300_000L))
        assertFalse(PlaybackStatsRules.isValidListen(14_999L, 300_000L))
        assertTrue(PlaybackStatsRules.isValidListen(15_000L, 300_000L))
    }

    @Test
    fun skipRequiresManualSongChangeBeforeValidThreshold() {
        assertTrue(
            PlaybackStatsRules.isSkip(
                manualSongChange = true,
                listenedMs = 5_000L,
                durationMs = 300_000L
            )
        )
        assertFalse(
            PlaybackStatsRules.isSkip(
                manualSongChange = false,
                listenedMs = 5_000L,
                durationMs = 300_000L
            )
        )
        assertFalse(
            PlaybackStatsRules.isSkip(
                manualSongChange = true,
                listenedMs = 15_000L,
                durationMs = 300_000L
            )
        )
    }

    @Test
    fun completionRequiresNinetyPercentActualListening() {
        assertFalse(PlaybackStatsRules.isCompletion(269_999L, 300_000L))
        assertTrue(PlaybackStatsRules.isCompletion(270_000L, 300_000L))
        assertEquals(0.9f, PlaybackStatsRules.completionPercent(270_000L, 300_000L), 0.0001f)
    }

    @Test
    fun sessionGapIsFiveMinutes() {
        assertEquals(300_000L, PlaybackStatsRules.SESSION_GAP_MS)
    }
}
