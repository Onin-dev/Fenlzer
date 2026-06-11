package com.fenl.fenlzer.playback

class SleepTimerController(
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private var timer: ActiveSleepTimer? = null

    val state: SleepTimerState
        get() = timer?.toState(now()) ?: SleepTimerState()

    fun startDuration(durationMs: Long) {
        val normalizedDuration = durationMs.coerceAtLeast(1_000L)
        timer = ActiveSleepTimer(
            mode = SleepTimerMode.DURATION,
            startedAtMs = now(),
            targetAtMs = now() + normalizedDuration
        )
    }

    fun startEndOfSong() {
        timer = ActiveSleepTimer(
            mode = SleepTimerMode.END_OF_SONG,
            startedAtMs = now(),
            targetAtMs = null
        )
    }

    fun startEndOfQueue() {
        timer = ActiveSleepTimer(
            mode = SleepTimerMode.END_OF_QUEUE,
            startedAtMs = now(),
            targetAtMs = null
        )
    }

    fun cancel() {
        timer = null
    }

    fun tick(
        positionMs: Long,
        durationMs: Long,
        upcomingCount: Int
    ): SleepTimerAction {
        val active = timer ?: return SleepTimerAction.None
        val remainingMs = when (active.mode) {
            SleepTimerMode.DURATION -> (active.targetAtMs ?: now()) - now()
            SleepTimerMode.END_OF_SONG -> durationMs - positionMs
            SleepTimerMode.END_OF_QUEUE -> if (upcomingCount == 0) durationMs - positionMs else null
        }

        if (remainingMs == null || durationMs == 0L && active.mode != SleepTimerMode.DURATION) {
            return SleepTimerAction.None
        }

        if (remainingMs <= 0L) {
            timer = null
            return SleepTimerAction.PauseAndRestoreVolume
        }

        if (remainingMs <= FADE_DURATION_MS) {
            val volume = (remainingMs.toFloat() / FADE_DURATION_MS.toFloat())
                .coerceIn(0f, 1f)
            return SleepTimerAction.SetVolume(volume)
        }

        return SleepTimerAction.SetVolume(1f)
    }

    fun onMediaItemTransition(
        previousHadUpcoming: Boolean
    ): SleepTimerAction {
        val active = timer ?: return SleepTimerAction.None
        val shouldPause = when (active.mode) {
            SleepTimerMode.END_OF_SONG -> true
            SleepTimerMode.END_OF_QUEUE -> !previousHadUpcoming
            SleepTimerMode.DURATION -> false
        }
        return if (shouldPause) {
            timer = null
            SleepTimerAction.PauseAndRestoreVolume
        } else {
            SleepTimerAction.SetVolume(1f)
        }
    }

    private fun ActiveSleepTimer.toState(currentTimeMs: Long): SleepTimerState {
        val remaining = targetAtMs?.let { (it - currentTimeMs).coerceAtLeast(0L) }
        return SleepTimerState(
            active = true,
            mode = mode,
            remainingMs = remaining,
            fadeActive = remaining != null && remaining <= FADE_DURATION_MS
        )
    }

    companion object {
        const val FADE_DURATION_MS = 10_000L
    }
}

private data class ActiveSleepTimer(
    val mode: SleepTimerMode,
    val startedAtMs: Long,
    val targetAtMs: Long?
)

data class SleepTimerState(
    val active: Boolean = false,
    val mode: SleepTimerMode? = null,
    val remainingMs: Long? = null,
    val fadeActive: Boolean = false
)

enum class SleepTimerMode {
    DURATION,
    END_OF_SONG,
    END_OF_QUEUE
}

sealed interface SleepTimerAction {
    data object None : SleepTimerAction
    data class SetVolume(val volume: Float) : SleepTimerAction
    data object PauseAndRestoreVolume : SleepTimerAction
}
