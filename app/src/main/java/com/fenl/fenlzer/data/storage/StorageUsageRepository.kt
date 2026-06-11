package com.fenl.fenlzer.data.storage

import com.fenl.fenlzer.common.FenlzerDispatchers
import kotlinx.coroutines.withContext

class StorageUsageRepository(
    private val storage: FenlzerStorage,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers()
) {
    suspend fun snapshot(): FenlzerStorageUsage = withContext(dispatchers.io) {
        storage.ensureDirectories()
        storage.storageUsage()
    }

    suspend fun clearCache(): FenlzerStorageUsage = withContext(dispatchers.io) {
        storage.clearCache()
        storage.storageUsage()
    }
}
