package com.fenl.fenlzer.importing

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportWorkRequestTest {
    @Test
    fun youtubeWorkRequiresNetworkAndUsesStableUniqueNameAndTags() {
        val request = ImportQueueCoordinator.requestFor(
            importJobId = "job-123",
            requiresNetwork = true,
            priorityClass = ImportPriorityClass.MANUAL
        )

        assertEquals("fenlzer_import_job-123", ImportQueueCoordinator.uniqueWorkName("job-123"))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(ImportQueueCoordinator.TAG_GLOBAL_IMPORT in request.tags)
        assertTrue("fenlzer_import_job-123" in request.tags)
        assertTrue("fenlzer_import_MANUAL" in request.tags)
    }

    @Test
    fun localWorkCanRunOfflineAndAutomaticPriorityIsTagged() {
        val request = ImportQueueCoordinator.requestFor(
            importJobId = "local-1",
            requiresNetwork = false,
            priorityClass = ImportPriorityClass.AUTO
        )

        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertTrue("fenlzer_import_AUTO" in request.tags)
    }

    @Test
    fun retryPolicyAllowsExactlyThreeTotalAttempts() {
        assertTrue(ImportRetryPolicy.canRetry(attemptCount = 1, maxAttempts = 3))
        assertTrue(ImportRetryPolicy.canRetry(attemptCount = 2, maxAttempts = 3))
        assertTrue(!ImportRetryPolicy.canRetry(attemptCount = 3, maxAttempts = 3))
    }
}
