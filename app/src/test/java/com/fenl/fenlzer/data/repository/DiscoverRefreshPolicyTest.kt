package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.local.entity.DiscoverSnapshotEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverRefreshPolicyTest {
    @Test
    fun startupRefreshRequiresOldSnapshotOpenedAfterRefresh() {
        val generatedAt = 1_000L
        val eligibleNow = generatedAt + DiscoverRefreshPolicy.STARTUP_REFRESH_AGE_MS + 1L

        assertFalse(DiscoverRefreshPolicy.isStartupRefreshEligible(null, eligibleNow))
        assertFalse(
            DiscoverRefreshPolicy.isStartupRefreshEligible(
                snapshot(generatedAt = generatedAt, lastOpenedAt = generatedAt),
                eligibleNow
            )
        )
        assertFalse(
            DiscoverRefreshPolicy.isStartupRefreshEligible(
                snapshot(generatedAt = generatedAt, lastOpenedAt = generatedAt + 1L),
                generatedAt + DiscoverRefreshPolicy.STARTUP_REFRESH_AGE_MS
            )
        )
        assertTrue(
            DiscoverRefreshPolicy.isStartupRefreshEligible(
                snapshot(generatedAt = generatedAt, lastOpenedAt = generatedAt + 1L),
                eligibleNow
            )
        )
    }

    private fun snapshot(generatedAt: Long, lastOpenedAt: Long) = DiscoverSnapshotEntity(
        snapshotId = "snapshot",
        generatedAt = generatedAt,
        lastOpenedAt = lastOpenedAt,
        refreshType = "NORMAL",
        candidateRequestTarget = 25,
        finalDisplayedCount = 20,
        refreshDetailsVisible = false
    )
}
