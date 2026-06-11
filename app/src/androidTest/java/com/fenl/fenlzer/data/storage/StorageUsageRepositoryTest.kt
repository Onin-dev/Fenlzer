package com.fenl.fenlzer.data.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageUsageRepositoryTest {
    @Test
    fun clearCacheUpdatesUsageAndKeepsPermanentThumbnails() = runTest {
        val storage = FenlzerStorage(ApplicationProvider.getApplicationContext())
        storage.ensureDirectories()
        val cacheFile = storage.fenlzerCacheDir.resolve("phase13-cache.bin")
        val thumbnailFile = storage.thumbnailsDir.resolve("phase13-permanent-thumb.jpg")
        cacheFile.delete()
        thumbnailFile.delete()
        cacheFile.writeBytes(ByteArray(9) { it.toByte() })
        thumbnailFile.writeBytes(ByteArray(11) { (it + 20).toByte() })
        val repository = StorageUsageRepository(storage)

        val before = repository.snapshot()

        assertTrue(before.cacheBytes >= cacheFile.length())
        assertTrue(before.thumbnailBytes >= thumbnailFile.length())

        val after = repository.clearCache()

        assertFalse(cacheFile.exists())
        assertTrue(thumbnailFile.exists())
        assertTrue(after.thumbnailBytes >= thumbnailFile.length())
    }
}
