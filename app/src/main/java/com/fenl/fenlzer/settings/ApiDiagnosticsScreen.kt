package com.fenl.fenlzer.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fenl.fenlzer.data.local.entity.ApiDiagnosticEntryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DiagnosticFilter(val label: String) {
    ALL("All"),
    FAILED("Failed"),
    SUCCESS("Success")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDiagnosticsScreen(
    entries: List<ApiDiagnosticEntryEntity>,
    onBack: () -> Unit,
    onClearDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by rememberSaveable { mutableStateOf(DiagnosticFilter.ALL) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val filteredEntries = when (filter) {
        DiagnosticFilter.ALL -> entries
        DiagnosticFilter.FAILED -> entries.filterNot { it.success }
        DiagnosticFilter.SUCCESS -> entries.filter { it.success }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(text = "Clear API diagnostics?") },
            text = {
                Text(
                    text = "This removes local diagnostic entries only. It does not change API settings, imports, tracks, or playback history."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearDiagnostics()
                    }
                ) {
                    Text(text = "Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.testTag("apiDiagnosticsScreen"),
        topBar = {
            TopAppBar(
                title = { Text(text = "API Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { confirmClear = true },
                        enabled = entries.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear diagnostics"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DiagnosticsSummary(
                totalCount = entries.size,
                failedCount = entries.count { !it.success },
                successCount = entries.count { it.success },
                filter = filter,
                onFilterChanged = { filter = it }
            )

            HorizontalDivider()

            if (filteredEntries.isEmpty()) {
                EmptyDiagnosticsState(filter = filter)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredEntries,
                        key = { it.diagnosticId }
                    ) { entry ->
                        DiagnosticEntryCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSummary(
    totalCount: Int,
    failedCount: Int,
    successCount: Int,
    filter: DiagnosticFilter,
    onFilterChanged: (DiagnosticFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "$totalCount local diagnostic entries",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "$failedCount failed · $successCount successful",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiagnosticFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChanged(option) },
                    label = { Text(text = option.label) },
                    modifier = Modifier.testTag("diagnosticsFilter${option.name.lowercase(Locale.ROOT)}")
                )
            }
        }
    }
}

@Composable
private fun EmptyDiagnosticsState(filter: DiagnosticFilter) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (filter) {
                    DiagnosticFilter.ALL -> "No API diagnostics yet"
                    DiagnosticFilter.FAILED -> "No failed API calls"
                    DiagnosticFilter.SUCCESS -> "No successful API calls"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "API calls are logged locally after search, import, Discover, stream resolution, or connection tests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiagnosticEntryCard(entry: ApiDiagnosticEntryEntity) {
    val statusColor = if (entry.success) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnosticEntry_${entry.diagnosticId}"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${entry.method.uppercase(Locale.ROOT)} ${entry.endpoint}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTimestamp(entry.startedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (entry.success) "Success" else "Failed",
                            color = statusColor
                        )
                    }
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticMiniField("Status", entry.statusCode?.toString() ?: "—")
                DiagnosticMiniField("Duration", "${entry.durationMs} ms")
                entry.errorCode?.let { DiagnosticMiniField("Error", it) }
            }

            entry.sanitizedMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            DiagnosticMonoLine(label = "Request ID", value = entry.requestId ?: "none")
        }
    }
}

@Composable
private fun DiagnosticMiniField(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = {
            Text(text = "$label: $value")
        }
    )
}

@Composable
private fun DiagnosticMonoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val diagnosticTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

private fun formatTimestamp(epochMs: Long): String =
    runCatching { diagnosticTimestampFormatter.format(Instant.ofEpochMilli(epochMs)) }
        .getOrElse { epochMs.toString() }
