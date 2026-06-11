package com.fenl.fenlzer.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppSettingsDefaultsTest {
    @Test
    fun defaultsMatchFenlzerPhaseOneFoundation() {
        val settings = AppSettings()

        assertEquals("", settings.apiBaseUrl)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(RepeatMode.ALL, settings.defaultRepeatMode)
        assertFalse(settings.defaultShuffleEnabled)
        assertEquals(HomeSort.RECENTLY_ADDED, settings.defaultHomeSort)
        assertEquals(ImportDuplicateBehavior.REJECT, settings.importDuplicateBehavior)
        assertEquals(30, settings.sleepTimerDefaultMinutes)
        assertEquals("#9B6BFF", settings.accentColorHex)
        assertFalse(settings.privateModeEnabledForSession)
    }

    @Test
    fun inMemoryRepositoryUpdatesThemeMode() {
        val repository = InMemoryAppSettingsRepository()

        repository.setThemeMode(ThemeMode.AMOLED)

        assertEquals(ThemeMode.AMOLED, repository.settings.value.themeMode)
    }

    @Test
    fun inMemoryRepositoryUpdatesApiBaseUrl() {
        val repository = InMemoryAppSettingsRepository()

        repository.setApiBaseUrl("http://localhost:8000/v1")

        assertEquals("http://localhost:8000/v1", repository.settings.value.apiBaseUrl)
    }
}
