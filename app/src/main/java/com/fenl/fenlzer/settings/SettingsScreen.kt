package com.fenl.fenlzer.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fenl.fenlzer.data.local.entity.ApiDiagnosticEntryEntity
import com.fenl.fenlzer.data.remote.ApiHealthCheckResult
import com.fenl.fenlzer.data.settings.AppSettings
import com.fenl.fenlzer.data.settings.HomeSort
import com.fenl.fenlzer.data.settings.ImportDuplicateBehavior
import com.fenl.fenlzer.data.settings.RepeatMode
import com.fenl.fenlzer.data.settings.ThemeMode
import com.fenl.fenlzer.data.storage.FenlzerStorageUsage
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    initialApiToken: String,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDefaultRepeatModeChanged: (RepeatMode) -> Unit,
    onDefaultShuffleChanged: (Boolean) -> Unit,
    onDefaultHomeSortChanged: (HomeSort) -> Unit,
    onImportDuplicateBehaviorChanged: (ImportDuplicateBehavior) -> Unit,
    onDeleteConfirmationChanged: (Boolean) -> Unit,
    onSleepTimerDefaultMinutesChanged: (Int) -> Unit,
    onPrivateModeChanged: (Boolean) -> Unit,
    onClearListeningHistory: () -> Unit,
    onResetStatistics: () -> Unit,
    storageUsage: FenlzerStorageUsage?,
    onRefreshStorageUsage: () -> Unit,
    onClearCache: () -> Unit,
    onClearImportHistory: () -> Unit,
    onDeleteAllSongs: () -> Unit,
    apiDiagnostics: List<ApiDiagnosticEntryEntity>,
    onApiSettingsSaved: (String, String) -> Unit,
    onTestApiConnection: suspend (String, String) -> ApiHealthCheckResult,
    onOpenDiagnostics: () -> Unit,
    appVersion: String,
    modifier: Modifier = Modifier
) {
    var apiBaseUrl by rememberSaveable(settings.apiBaseUrl) {
        mutableStateOf(settings.apiBaseUrl)
    }
    var apiToken by rememberSaveable(initialApiToken) {
        mutableStateOf(initialApiToken)
    }
    var healthResult by remember {
        mutableStateOf<ApiHealthCheckResult?>(null)
    }
    var testingConnection by rememberSaveable {
        mutableStateOf(false)
    }
    var pendingStatsAction by rememberSaveable {
        mutableStateOf<PendingStatsAction?>(null)
    }
    var pendingStorageAction by rememberSaveable {
        mutableStateOf<PendingStorageAction?>(null)
    }
    var sleepMinutesInput by rememberSaveable(settings.sleepTimerDefaultMinutes) {
        mutableStateOf(settings.sleepTimerDefaultMinutes.toString())
    }
    val coroutineScope = rememberCoroutineScope()

    pendingStatsAction?.let { action ->
        ConfirmStatsActionDialog(
            action = action,
            onDismiss = { pendingStatsAction = null },
            onConfirm = {
                pendingStatsAction = null
                when (action) {
                    PendingStatsAction.CLEAR_HISTORY -> onClearListeningHistory()
                    PendingStatsAction.RESET_STATISTICS -> onResetStatistics()
                }
            }
        )
    }

    pendingStorageAction?.let { action ->
        ConfirmStorageActionDialog(
            action = action,
            onDismiss = { pendingStorageAction = null },
            onConfirm = {
                pendingStorageAction = null
                when (action) {
                    PendingStorageAction.CLEAR_CACHE -> onClearCache()
                    PendingStorageAction.CLEAR_IMPORT_HISTORY -> onClearImportHistory()
                    PendingStorageAction.DELETE_ALL_SONGS -> onDeleteAllSongs()
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("settingsScreen"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Playback defaults",
            style = MaterialTheme.typography.titleLarge
        )
        EnumSettingDropdown(
            label = "Default repeat mode",
            value = settings.defaultRepeatMode,
            options = RepeatMode.entries,
            optionLabel = { it.displayLabel() },
            onValueChanged = onDefaultRepeatModeChanged
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Default shuffle",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Applies to new queues",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.defaultShuffleEnabled,
                onCheckedChange = onDefaultShuffleChanged
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = sleepMinutesInput,
                onValueChange = { value ->
                    sleepMinutesInput = value.filter { it.isDigit() }.take(3)
                },
                singleLine = true,
                label = { Text(text = "Sleep timer default minutes") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val minutes = sleepMinutesInput.toIntOrNull()?.coerceIn(1, 240) ?: 30
                    sleepMinutesInput = minutes.toString()
                    onSleepTimerDefaultMinutesChanged(minutes)
                }
            ) {
                Text(text = "Save")
            }
        }

        HorizontalDivider()

        Text(
            text = "Library defaults",
            style = MaterialTheme.typography.titleLarge
        )
        EnumSettingDropdown(
            label = "Default Home sort",
            value = settings.defaultHomeSort,
            options = HomeSort.entries,
            optionLabel = { it.displayLabel() },
            onValueChanged = onDefaultHomeSortChanged
        )
        EnumSettingDropdown(
            label = "Import duplicate behavior",
            value = settings.importDuplicateBehavior,
            options = ImportDuplicateBehavior.entries,
            optionLabel = { it.displayLabel() },
            onValueChanged = onImportDuplicateBehaviorChanged
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Delete confirmations",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Keep Delete from Fenlzer prompts enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.deleteConfirmationEnabled,
                onCheckedChange = onDeleteConfirmationChanged
            )
        }

        HorizontalDivider()

        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AMOLED mode",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Pure black backgrounds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.themeMode == ThemeMode.AMOLED,
                onCheckedChange = { checked ->
                    onThemeModeChanged(if (checked) ThemeMode.AMOLED else ThemeMode.DARK)
                }
            )
        }

        HorizontalDivider()

        Text(
            text = "Privacy",
            style = MaterialTheme.typography.titleLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Private mode",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Session-only playback privacy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.privateModeEnabledForSession,
                onCheckedChange = onPrivateModeChanged,
                modifier = Modifier.testTag("privateModeSwitch")
            )
        }

        HorizontalDivider()

        Text(
            text = "Statistics",
            style = MaterialTheme.typography.titleLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    pendingStatsAction = PendingStatsAction.CLEAR_HISTORY
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("clearListeningHistoryButton")
            ) {
                Text(text = "Clear history")
            }
            Button(
                onClick = {
                    pendingStatsAction = PendingStatsAction.RESET_STATISTICS
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("resetStatisticsButton")
            ) {
                Text(text = "Reset stats")
            }
        }

        HorizontalDivider()

        Text(
            text = "Storage Management",
            style = MaterialTheme.typography.titleLarge
        )
        StorageUsageCard(
            usage = storageUsage,
            onRefresh = onRefreshStorageUsage
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { pendingStorageAction = PendingStorageAction.CLEAR_CACHE },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Clear cache")
            }
            OutlinedButton(
                onClick = { pendingStorageAction = PendingStorageAction.CLEAR_IMPORT_HISTORY },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Clear imports")
            }
        }
        Button(
            onClick = { pendingStorageAction = PendingStorageAction.DELETE_ALL_SONGS },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("deleteAllSongsButton")
        ) {
            Text(text = "Delete all songs from Fenlzer")
        }

        HorizontalDivider()

        Text(
            text = "API",
            style = MaterialTheme.typography.titleLarge
        )
        OutlinedTextField(
            value = apiBaseUrl,
            onValueChange = { apiBaseUrl = it },
            singleLine = true,
            label = { Text(text = "Base URL") },
            placeholder = { Text(text = "https://api.example.com/") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apiBaseUrlField")
        )
        OutlinedTextField(
            value = apiToken,
            onValueChange = { apiToken = it },
            singleLine = true,
            label = { Text(text = "API token") },
            visualTransformation = if (apiToken.isBlank()) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apiTokenField")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    onApiSettingsSaved(apiBaseUrl, apiToken)
                    healthResult = null
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("saveApiSettingsButton")
            ) {
                Text(text = "Save API settings")
            }
            Button(
                onClick = {
                    testingConnection = true
                    coroutineScope.launch {
                        healthResult = onTestApiConnection(apiBaseUrl, apiToken)
                        testingConnection = false
                    }
                },
                enabled = !testingConnection,
                modifier = Modifier
                    .weight(1f)
                    .testTag("testApiConnectionButton")
            ) {
                if (testingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(text = "Test connection")
            }
        }

        healthResult?.let { result ->
            HealthResultCard(result = result)
        }

        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.titleLarge
        )
        if (apiDiagnostics.isEmpty()) {
            Text(
                text = "No local API diagnostics yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            apiDiagnostics.take(6).forEach { entry ->
                DiagnosticRow(entry)
            }
        }
        Button(
            onClick = onOpenDiagnostics,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("viewAllDiagnosticsButton")
        ) {
            Text(text = "View all diagnostics (${apiDiagnostics.size})")
        }

        HorizontalDivider()

        Text(
            text = "About Fenlzer",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Version $appVersion",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Fenlzer is a personal offline-first music library with API-backed YouTube import, remote Discover streaming, and local playback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun <T> EnumSettingDropdown(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onValueChanged: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    BoxAnchor {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                Text(text = optionLabel(value), style = MaterialTheme.typography.bodyLarge)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onValueChanged(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun BoxAnchor(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

private fun RepeatMode.displayLabel(): String =
    when (this) {
        RepeatMode.OFF -> "Repeat off"
        RepeatMode.ALL -> "Repeat all"
        RepeatMode.ONE -> "Repeat one"
    }

private fun HomeSort.displayLabel(): String =
    name.lowercase(Locale.US)
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.US) }

private fun ImportDuplicateBehavior.displayLabel(): String =
    when (this) {
        ImportDuplicateBehavior.REJECT -> "Reject duplicates"
    }

private fun Long.formatBytes(): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${toLong()} ${units[unitIndex]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

@Composable
private fun ConfirmStatsActionDialog(
    action: PendingStatsAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = when (action) {
        PendingStatsAction.CLEAR_HISTORY -> "Clear listening history?"
        PendingStatsAction.RESET_STATISTICS -> "Reset statistics?"
    }
    val body = when (action) {
        PendingStatsAction.CLEAR_HISTORY -> {
            "This clears playback history and sessions. Aggregate song stats stay intact."
        }

        PendingStatsAction.RESET_STATISTICS -> {
            "This clears listening history, sessions, and all aggregate song statistics."
        }
    }
    val confirmText = when (action) {
        PendingStatsAction.CLEAR_HISTORY -> "Clear history"
        PendingStatsAction.RESET_STATISTICS -> "Reset stats"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirmStatsActionButton")
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancelStatsActionButton")
            ) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier.testTag("confirmStatsActionDialog")
    )
}

private enum class PendingStatsAction {
    CLEAR_HISTORY,
    RESET_STATISTICS
}

private enum class PendingStorageAction {
    CLEAR_CACHE,
    CLEAR_IMPORT_HISTORY,
    DELETE_ALL_SONGS
}

@Composable
private fun ConfirmStorageActionDialog(
    action: PendingStorageAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var typedDelete by rememberSaveable { mutableStateOf("") }
    val isDeleteAll = action == PendingStorageAction.DELETE_ALL_SONGS
    val title = when (action) {
        PendingStorageAction.CLEAR_CACHE -> "Clear cache?"
        PendingStorageAction.CLEAR_IMPORT_HISTORY -> "Clear import history?"
        PendingStorageAction.DELETE_ALL_SONGS -> "Delete from Fenlzer"
    }
    val body = when (action) {
        PendingStorageAction.CLEAR_CACHE -> "This removes temporary cache files. Permanent thumbnails stay intact."
        PendingStorageAction.CLEAR_IMPORT_HISTORY -> "This clears Import History entries and keeps imported songs."
        PendingStorageAction.DELETE_ALL_SONGS ->
            "This permanently deletes every song copied into Fenlzer, removes playlist and queue references, and has no undo. Type DELETE to continue."
    }
    val confirmEnabled = !isDeleteAll || typedDelete == "DELETE"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = body)
                if (isDeleteAll) {
                    OutlinedTextField(
                        value = typedDelete,
                        onValueChange = { typedDelete = it },
                        singleLine = true,
                        label = { Text(text = "Type DELETE") },
                        modifier = Modifier.testTag("deleteAllSongsConfirmationField")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.testTag("confirmStorageActionButton")
            ) {
                Text(
                    text = when (action) {
                        PendingStorageAction.CLEAR_CACHE -> "Clear cache"
                        PendingStorageAction.CLEAR_IMPORT_HISTORY -> "Clear history"
                        PendingStorageAction.DELETE_ALL_SONGS -> "Delete from Fenlzer"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier.testTag("confirmStorageActionDialog")
    )
}

@Composable
private fun StorageUsageCard(
    usage: FenlzerStorageUsage?,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StorageUsageLine("Audio files", usage?.audioBytes)
            StorageUsageLine("Thumbnails", usage?.thumbnailBytes)
            StorageUsageLine("Cache", usage?.cacheBytes)
            StorageUsageLine("Database", usage?.databaseBytes)
            HorizontalDivider()
            StorageUsageLine("Total Fenlzer storage", usage?.totalBytes)
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Refresh storage usage")
            }
        }
    }
}

@Composable
private fun StorageUsageLine(label: String, bytes: Long?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Text(
            text = bytes?.formatBytes() ?: "Calculating",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DiagnosticRow(entry: ApiDiagnosticEntryEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "${entry.method} ${entry.endpoint}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = listOfNotNull(
                    if (entry.success) "success" else "failed",
                    entry.statusCode?.let { "HTTP $it" },
                    entry.errorCode,
                    "${entry.durationMs} ms"
                ).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entry.sanitizedMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HealthResultCard(
    result: ApiHealthCheckResult,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("apiHealthResult"),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (result) {
                is ApiHealthCheckResult.Success -> {
                    Text(
                        text = "API connected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(text = "Status: ${result.health.status}")
                    Text(text = "Version: ${result.health.apiVersion}")
                    Text(text = "Request: ${result.requestId}")
                    Text(text = "Duration: ${result.durationMs} ms")
                    if (result.health.features.isNotEmpty()) {
                        Text(
                            text = result.health.features.entries
                                .sortedBy { it.key }
                                .joinToString { "${it.key}: ${it.value}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    result.health.tools?.let { tools ->
                        Text(
                            text = "Tools: $tools",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    result.health.limits?.let { limits ->
                        Text(
                            text = "Limits: $limits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ApiHealthCheckResult.Failure -> {
                    Text(
                        text = "API unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(text = result.message)
                    Text(
                        text = "Code: ${result.errorCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
