package com.fenl.fenlzer.data.settings

data class AppSettings(
    val apiBaseUrl: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultRepeatMode: RepeatMode = RepeatMode.ALL,
    val defaultShuffleEnabled: Boolean = false,
    val defaultHomeSort: HomeSort = HomeSort.RECENTLY_ADDED,
    val importDuplicateBehavior: ImportDuplicateBehavior = ImportDuplicateBehavior.REJECT,
    val deleteConfirmationEnabled: Boolean = true,
    val sleepTimerDefaultMinutes: Int = 30,
    val accentColorHex: String = "#9B6BFF",
    val privateModeEnabledForSession: Boolean = false
)

enum class ThemeMode {
    DARK,
    AMOLED
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class HomeSort {
    TITLE_A_TO_Z,
    ARTIST_A_TO_Z,
    RECENTLY_ADDED,
    RECENTLY_PLAYED,
    MOST_PLAYED,
    LEAST_PLAYED,
    DURATION_SHORTEST,
    DURATION_LONGEST,
    FAVOURITES_FIRST
}

enum class ImportDuplicateBehavior {
    REJECT
}
