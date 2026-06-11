package com.fenl.fenlzer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerControllerTest {
    @Test
    fun durationTimerFadesForFinalTenSecondsThenPauses() {
        var now = 1_000L
        val controller = SleepTimerController(now = { now })

        controller.startDuration(20_000L)
        assertTrue(controller.state.active)

        now = 12_000L
        assertEquals(SleepTimerAction.SetVolume(0.9f), controller.tick(0L, 180_000L, 0))

        now = 21_000L
        assertEquals(SleepTimerAction.PauseAndRestoreVolume, controller.tick(0L, 180_000L, 0))
        assertFalse(controller.state.active)
    }

    @Test
    fun endOfSongTimerPausesAtSongEnd() {
        val controller = SleepTimerController(now = { 1_000L })
        controller.startEndOfSong()

        assertEquals(
            SleepTimerAction.SetVolume(0.5f),
            controller.tick(positionMs = 115_000L, durationMs = 120_000L, upcomingCount = 3)
        )
        assertEquals(
            SleepTimerAction.PauseAndRestoreVolume,
            controller.tick(positionMs = 120_000L, durationMs = 120_000L, upcomingCount = 3)
        )
    }

    @Test
    fun endOfSongTimerPausesOnAutomaticTransition() {
        val controller = SleepTimerController(now = { 1_000L })
        controller.startEndOfSong()

        assertEquals(
            SleepTimerAction.PauseAndRestoreVolume,
            controller.onMediaItemTransition(previousHadUpcoming = true)
        )
        assertFalse(controller.state.active)
    }

    @Test
    fun endOfQueueTimerWaitsUntilCurrentSongIsLastUpcomingItem() {
        val controller = SleepTimerController(now = { 1_000L })
        controller.startEndOfQueue()

        assertEquals(
            SleepTimerAction.None,
            controller.tick(positionMs = 115_000L, durationMs = 120_000L, upcomingCount = 1)
        )
        assertEquals(
            SleepTimerAction.SetVolume(0.5f),
            controller.tick(positionMs = 115_000L, durationMs = 120_000L, upcomingCount = 0)
        )
    }

    @Test
    fun endOfQueueTimerPausesOnlyWhenTransitioningFromFinalSong() {
        val controller = SleepTimerController(now = { 1_000L })
        controller.startEndOfQueue()

        assertEquals(
            SleepTimerAction.SetVolume(1f),
            controller.onMediaItemTransition(previousHadUpcoming = true)
        )
        assertTrue(controller.state.active)

        assertEquals(
            SleepTimerAction.PauseAndRestoreVolume,
            controller.onMediaItemTransition(previousHadUpcoming = false)
        )
        assertFalse(controller.state.active)
    }

    @Test
    fun cancelClearsActiveTimer() {
        val controller = SleepTimerController(now = { 1_000L })
        controller.startEndOfSong()

        controller.cancel()

        assertFalse(controller.state.active)
        assertEquals(
            SleepTimerAction.None,
            controller.tick(positionMs = 120_000L, durationMs = 120_000L, upcomingCount = 0)
        )
    }
}
