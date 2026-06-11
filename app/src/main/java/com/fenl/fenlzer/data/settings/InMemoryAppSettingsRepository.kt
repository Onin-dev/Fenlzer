package com.fenl.fenlzer.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryAppSettingsRepository(
    initialSettings: AppSettings = AppSettings()
) : AppSettingsRepository {
    private val mutableSettings = MutableStateFlow(initialSettings)

    override val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    override fun setThemeMode(themeMode: ThemeMode) {
        mutableSettings.update { current ->
            current.copy(themeMode = themeMode)
        }
    }

    override fun setApiBaseUrl(baseUrl: String) {
        mutableSettings.update { current ->
            current.copy(apiBaseUrl = baseUrl)
        }
    }

    override fun setPrivateModeEnabledForSession(enabled: Boolean) {
        mutableSettings.update { current ->
            current.copy(privateModeEnabledForSession = enabled)
        }
    }

    override fun setDefaultRepeatMode(repeatMode: RepeatMode) {
        mutableSettings.update { current ->
            current.copy(defaultRepeatMode = repeatMode)
        }
    }

    override fun setDefaultShuffleEnabled(enabled: Boolean) {
        mutableSettings.update { current ->
            current.copy(defaultShuffleEnabled = enabled)
        }
    }

    override fun setDefaultHomeSort(homeSort: HomeSort) {
        mutableSettings.update { current ->
            current.copy(defaultHomeSort = homeSort)
        }
    }

    override fun setImportDuplicateBehavior(behavior: ImportDuplicateBehavior) {
        mutableSettings.update { current ->
            current.copy(importDuplicateBehavior = behavior)
        }
    }

    override fun setDeleteConfirmationEnabled(enabled: Boolean) {
        mutableSettings.update { current ->
            current.copy(deleteConfirmationEnabled = enabled)
        }
    }

    override fun setSleepTimerDefaultMinutes(minutes: Int) {
        mutableSettings.update { current ->
            current.copy(sleepTimerDefaultMinutes = minutes.coerceIn(1, 240))
        }
    }
}
