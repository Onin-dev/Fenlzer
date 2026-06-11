package com.fenl.fenlzer.data.settings

import kotlinx.coroutines.flow.StateFlow

interface AppSettingsRepository {
    val settings: StateFlow<AppSettings>

    fun setThemeMode(themeMode: ThemeMode)

    fun setApiBaseUrl(baseUrl: String)

    fun setPrivateModeEnabledForSession(enabled: Boolean)

    fun setDefaultRepeatMode(repeatMode: RepeatMode)

    fun setDefaultShuffleEnabled(enabled: Boolean)

    fun setDefaultHomeSort(homeSort: HomeSort)

    fun setImportDuplicateBehavior(behavior: ImportDuplicateBehavior)

    fun setDeleteConfirmationEnabled(enabled: Boolean)

    fun setSleepTimerDefaultMinutes(minutes: Int)
}
