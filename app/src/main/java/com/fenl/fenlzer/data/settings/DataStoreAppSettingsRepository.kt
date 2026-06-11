package com.fenl.fenlzer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.fenlzerSettingsDataStore by preferencesDataStore(
    name = "fenlzer_app_settings"
)

class DataStoreAppSettingsRepository(
    context: Context,
    private val scope: CoroutineScope,
    dataStoreOverride: DataStore<Preferences>? = null
) : AppSettingsRepository {
    private val dataStore = dataStoreOverride ?: context.fenlzerSettingsDataStore
    private val privateModeForSession = MutableStateFlow(false)

    override val settings: StateFlow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map(::toAppSettings)
        .combine(privateModeForSession) { settings, privateMode ->
            settings.copy(privateModeEnabledForSession = privateMode)
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    override fun setThemeMode(themeMode: ThemeMode) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.themeMode] = themeMode.name
            }
        }
    }

    override fun setApiBaseUrl(baseUrl: String) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.apiBaseUrl] = baseUrl.trim()
            }
        }
    }

    override fun setPrivateModeEnabledForSession(enabled: Boolean) {
        privateModeForSession.value = enabled
    }

    override fun setDefaultRepeatMode(repeatMode: RepeatMode) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.defaultRepeatMode] = repeatMode.name
            }
        }
    }

    override fun setDefaultShuffleEnabled(enabled: Boolean) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.defaultShuffleEnabled] = enabled
            }
        }
    }

    override fun setDefaultHomeSort(homeSort: HomeSort) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.defaultHomeSort] = homeSort.name
            }
        }
    }

    override fun setImportDuplicateBehavior(behavior: ImportDuplicateBehavior) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.importDuplicateBehavior] = behavior.name
            }
        }
    }

    override fun setDeleteConfirmationEnabled(enabled: Boolean) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.deleteConfirmationEnabled] = enabled
            }
        }
    }

    override fun setSleepTimerDefaultMinutes(minutes: Int) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.sleepTimerDefaultMinutes] = minutes.coerceIn(1, 240)
            }
        }
    }

    private fun toAppSettings(preferences: Preferences): AppSettings {
        return AppSettings(
            apiBaseUrl = preferences[Keys.apiBaseUrl].orEmpty(),
            themeMode = preferences.enumValue(Keys.themeMode, ThemeMode.DARK),
            defaultRepeatMode = preferences.enumValue(Keys.defaultRepeatMode, RepeatMode.ALL),
            defaultShuffleEnabled = preferences[Keys.defaultShuffleEnabled] ?: false,
            defaultHomeSort = preferences.enumValue(Keys.defaultHomeSort, HomeSort.RECENTLY_ADDED),
            importDuplicateBehavior = preferences.enumValue(
                Keys.importDuplicateBehavior,
                ImportDuplicateBehavior.REJECT
            ),
            deleteConfirmationEnabled = preferences[Keys.deleteConfirmationEnabled] ?: true,
            sleepTimerDefaultMinutes = preferences[Keys.sleepTimerDefaultMinutes] ?: 30,
            accentColorHex = preferences[Keys.accentColorHex] ?: "#9B6BFF",
            privateModeEnabledForSession = false
        )
    }

    private inline fun <reified T : Enum<T>> Preferences.enumValue(
        key: Preferences.Key<String>,
        defaultValue: T
    ): T {
        return this[key]?.let { rawValue ->
            enumValues<T>().firstOrNull { it.name == rawValue }
        } ?: defaultValue
    }

    private object Keys {
        val apiBaseUrl = stringPreferencesKey("api_base_url")
        val themeMode = stringPreferencesKey("theme_mode")
        val defaultRepeatMode = stringPreferencesKey("default_repeat_mode")
        val defaultShuffleEnabled = booleanPreferencesKey("default_shuffle_enabled")
        val defaultHomeSort = stringPreferencesKey("default_home_sort")
        val importDuplicateBehavior = stringPreferencesKey("import_duplicate_behavior")
        val deleteConfirmationEnabled = booleanPreferencesKey("delete_confirmation_enabled")
        val sleepTimerDefaultMinutes = intPreferencesKey("sleep_timer_default_minutes")
        val accentColorHex = stringPreferencesKey("accent_color_hex")
    }
}
