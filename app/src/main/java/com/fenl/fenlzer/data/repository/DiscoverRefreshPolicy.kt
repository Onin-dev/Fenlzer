package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.local.entity.DiscoverSnapshotEntity

object DiscoverRefreshPolicy {
    const val STARTUP_REFRESH_AGE_MS = 8L * 60L * 60L * 1_000L

    fun isStartupRefreshEligible(
        snapshot: DiscoverSnapshotEntity?,
        now: Long
    ): Boolean {
        if (snapshot == null) return false
        val ageMs = now - snapshot.generatedAt
        return ageMs > STARTUP_REFRESH_AGE_MS && snapshot.lastOpenedAt > snapshot.generatedAt
    }
}
