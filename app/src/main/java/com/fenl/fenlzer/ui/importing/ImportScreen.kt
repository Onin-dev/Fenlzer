package com.fenl.fenlzer.ui.importing

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fenl.fenlzer.importing.local.LocalImportBatchResult
import com.fenl.fenlzer.importing.local.LocalImportItemResult
import com.fenl.fenlzer.importing.local.LocalImportOutcome
import com.fenl.fenlzer.importing.youtube.ActiveImportUiItem
import com.fenl.fenlzer.importing.youtube.ImportHistoryFilter
import com.fenl.fenlzer.importing.youtube.ImportHistoryUiItem
import com.fenl.fenlzer.importing.youtube.YoutubeImportItemResult
import com.fenl.fenlzer.importing.youtube.YoutubeImportOutcome
import com.fenl.fenlzer.importing.youtube.YoutubePlaylistPreview
import com.fenl.fenlzer.importing.youtube.YoutubePlaylistPreviewItem
import com.fenl.fenlzer.importing.youtube.YoutubeSearchResultItem
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImportScreen(
    state: LocalImportUiState,
    youtubeState: YoutubeImportUiState,
    onImportFromDevice: () -> Unit,
    onYoutubeQueryChanged: (String) -> Unit,
    onSearchYoutube: () -> Unit,
    onImportYoutubeResult: (YoutubeSearchResultItem) -> Unit,
    onYoutubePlaylistUrlChanged: (String) -> Unit,
    onPreviewYoutubePlaylist: () -> Unit,
    onToggleYoutubePlaylistItem: (String) -> Unit,
    onSelectAllYoutubePlaylistItems: () -> Unit,
    onImportSelectedYoutubePlaylistItems: () -> Unit,
    onImportWholeYoutubePlaylist: () -> Unit,
    onCancelYoutubeImport: (String) -> Unit,
    onRetryYoutubeImport: (String) -> Unit,
    onMoveYoutubeImport: (String, Int) -> Unit,
    onHistoryFilterChanged: (ImportHistoryFilter) -> Unit,
    onClearYoutubeHistory: () -> Unit,
    onRetryYoutubeHistoryItem: (ImportHistoryUiItem) -> Unit,
    onRetryFailed: (List<Uri>) -> Unit,
    onViewLibrary: () -> Unit,
    onOpenSongDetails: (String) -> Unit,
    onClearResult: () -> Unit,
    onClearYoutubeResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showActiveImports by rememberSaveable { mutableStateOf(true) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showPlaylist by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("importScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ImportActionToolbar(
            localImportRunning = state.isRunning,
            showActiveImports = showActiveImports,
            showHistory = showHistory,
            showPlaylist = showPlaylist,
            onImportFromDevice = onImportFromDevice,
            onTogglePlaylist = { showPlaylist = !showPlaylist },
            onToggleActiveImports = { showActiveImports = !showActiveImports },
            onToggleHistory = { showHistory = !showHistory }
        )
        YoutubeSearchPanel(
            state = youtubeState,
            onQueryChanged = onYoutubeQueryChanged,
            onSearch = onSearchYoutube,
            onImportResult = onImportYoutubeResult
        )

        if (showPlaylist) {
            YoutubePlaylistPanel(
                state = youtubeState,
                onUrlChanged = onYoutubePlaylistUrlChanged,
                onPreview = onPreviewYoutubePlaylist,
                onToggleItem = onToggleYoutubePlaylistItem,
                onSelectAll = onSelectAllYoutubePlaylistItems,
                onImportSelected = onImportSelectedYoutubePlaylistItems,
                onImportWholePlaylist = onImportWholeYoutubePlaylist
            )
        }

        if (showActiveImports || youtubeState.latestProgress != null) {
            ActiveImportsPanel(
                activeJobs = youtubeState.activeJobs,
                latestResult = youtubeState.lastImportResult,
                importError = youtubeState.importError,
                onCancelImport = onCancelYoutubeImport,
                onRetryImport = onRetryYoutubeImport,
                onMoveImport = onMoveYoutubeImport,
                onOpenSongDetails = onOpenSongDetails,
                onClearYoutubeResult = onClearYoutubeResult
            )
        }

        if (state.isRunning) {
            LocalImportProgressPanel(state = state)
        }

        state.result?.let { result ->
            ImportResultPanel(
                result = result,
                onRetryFailed = onRetryFailed,
                onViewLibrary = onViewLibrary,
                onOpenSongDetails = onOpenSongDetails,
                onClearResult = onClearResult
            )
        }

        if (showHistory) {
            ImportHistoryPanel(
                history = youtubeState.history,
                selectedFilter = youtubeState.historyFilter,
                onFilterChanged = onHistoryFilterChanged,
                onClearHistory = onClearYoutubeHistory,
                onRetryHistoryItem = onRetryYoutubeHistoryItem,
                onOpenSongDetails = onOpenSongDetails
            )
        }
    }
}

@Composable
private fun ImportActionToolbar(
    localImportRunning: Boolean,
    showActiveImports: Boolean,
    showHistory: Boolean,
    showPlaylist: Boolean,
    onImportFromDevice: () -> Unit,
    onTogglePlaylist: () -> Unit,
    onToggleActiveImports: () -> Unit,
    onToggleHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onImportFromDevice,
            enabled = !localImportRunning
        ) {
            Icon(
                imageVector = Icons.Rounded.FileDownload,
                contentDescription = "Import from device"
            )
        }
        IconButton(
            onClick = onTogglePlaylist
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = if (showPlaylist) {
                    "Hide YouTube playlist import"
                } else {
                    "Show YouTube playlist import"
                }
            )
        }
        IconButton(onClick = onToggleActiveImports) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = if (showActiveImports) {
                    "Hide active imports"
                } else {
                    "Show active imports"
                }
            )
        }
        IconButton(onClick = onToggleHistory) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = if (showHistory) {
                    "Hide import history"
                } else {
                    "Show import history"
                }
            )
        }
    }
}

@Composable
private fun YoutubeSearchPanel(
    state: YoutubeImportUiState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onImportResult: (YoutubeSearchResultItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                singleLine = true,
                placeholder = { Text(text = "Search YouTube") },
                leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onSearch,
                enabled = !state.isSearching && state.query.isNotBlank()
            ) {
                Text(text = "Search")
            }
        }

        if (state.isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.searchError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        state.searchResults.forEach { result ->
            YoutubeSearchResultRow(
                result = result,
                importing = state.isImportRunning,
                onImport = { onImportResult(result) }
            )
        }
    }
}

@Composable
private fun YoutubePlaylistPanel(
    state: YoutubeImportUiState,
    onUrlChanged: (String) -> Unit,
    onPreview: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onImportSelected: () -> Unit,
    onImportWholePlaylist: () -> Unit
) {
    val preview = state.playlistPreview
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.playlistUrl,
                onValueChange = onUrlChanged,
                singleLine = true,
                placeholder = { Text(text = "YouTube playlist URL") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null
                    )
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onPreview,
                enabled = !state.playlistLoading && state.playlistUrl.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Preview YouTube playlist"
                )
            }
        }

        if (state.playlistLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.playlistError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (preview != null) {
            PlaylistPreviewHeader(
                preview = preview,
                selectedCount = state.selectedPlaylistRemoteItemIds.size,
                importing = state.isImportRunning,
                onSelectAll = onSelectAll,
                onImportSelected = onImportSelected,
                onImportWholePlaylist = onImportWholePlaylist
            )
            if (preview.items.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    preview.items.forEach { item ->
                        PlaylistPreviewRow(
                            item = item,
                            selected = item.remoteItemId in state.selectedPlaylistRemoteItemIds,
                            importing = state.isImportRunning,
                            onToggle = { onToggleItem(item.remoteItemId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPreviewHeader(
    preview: YoutubePlaylistPreview,
    selectedCount: Int,
    importing: Boolean,
    onSelectAll: () -> Unit,
    onImportSelected: () -> Unit,
    onImportWholePlaylist: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (preview.thumbnailUrl != null) {
                AsyncImage(
                    model = preview.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preview.title ?: "Playlist preview",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlistPreviewStatus(preview, selectedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onSelectAll,
            enabled = preview.items.isNotEmpty() && !importing
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Select all playlist songs"
            )
        }
        IconButton(
            onClick = onImportSelected,
            enabled = selectedCount > 0 && !importing
        ) {
            Icon(
                imageVector = Icons.Rounded.FileDownload,
                contentDescription = "Import selected playlist songs"
            )
        }
        IconButton(
            onClick = onImportWholePlaylist,
            enabled = preview.items.isNotEmpty() &&
                !importing &&
                preview.status.isPreviewReadyForWholeImport()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = "Import whole playlist"
            )
        }
    }
}

@Composable
private fun PlaylistPreviewRow(
    item: YoutubePlaylistPreviewItem,
    selected: Boolean,
    importing: Boolean,
    onToggle: () -> Unit
) {
    val disabledReason = when {
        !item.canDownload -> "Download unavailable."
        item.availability.equals("PRIVATE", ignoreCase = true) -> "Private."
        item.availability.equals("DELETED", ignoreCase = true) -> "Deleted."
        item.availability.equals("UNAVAILABLE", ignoreCase = true) -> "Unavailable."
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = disabledReason == null && !importing
        )
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(item.position?.let { "$it." }, item.title)
                    .joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    item.artistOrChannel?.ifBlank { null },
                    item.durationMs?.formatDuration(),
                    disabledReason
                ).joinToString(" - ").ifBlank { "YouTube playlist" },
                style = MaterialTheme.typography.bodySmall,
                color = if (disabledReason == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun YoutubeSearchResultRow(
    result: YoutubeSearchResultItem,
    importing: Boolean,
    onImport: () -> Unit
) {
    val disabledReason = when {
        result.isLive -> "Live streams cannot be imported."
        result.isUnavailable -> "Unavailable."
        !result.canDownload -> "Download unavailable."
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (result.thumbnailUrl != null) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    result.artistOrChannel?.ifBlank { null },
                    result.durationMs?.formatDuration()
                ).joinToString(" - ").ifBlank { "YouTube" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            disabledReason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        IconButton(
            onClick = onImport,
            enabled = disabledReason == null && !importing
        ) {
            Icon(
                imageVector = Icons.Rounded.FileDownload,
                contentDescription = "Import ${result.title}"
            )
        }
    }
}

@Composable
private fun LocalImportProgressPanel(state: LocalImportUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Rounded.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Importing ${state.currentIndex.coerceAtLeast(1)} of ${state.total.coerceAtLeast(1)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = state.currentFilename ?: "Preparing selected files",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { (state.currentPercent ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ActiveImportsPanel(
    activeJobs: List<ActiveImportUiItem>,
    latestResult: YoutubeImportItemResult?,
    importError: String?,
    onCancelImport: (String) -> Unit,
    onRetryImport: (String) -> Unit,
    onMoveImport: (String, Int) -> Unit,
    onOpenSongDetails: (String) -> Unit,
    onClearYoutubeResult: () -> Unit
) {
    if (activeJobs.isEmpty() && latestResult == null && importError == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Active Imports",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (activeJobs.isEmpty()) {
            Text(
                text = "No imports running.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            activeJobs.forEach { job ->
                ActiveImportRow(
                    job = job,
                    onCancelImport = onCancelImport,
                    onRetryImport = onRetryImport,
                    onMoveImport = onMoveImport
                )
            }
        }
        importError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        latestResult?.let { result ->
            HorizontalImportDivider()
            YoutubeImportResultSummary(
                result = result,
                onOpenSongDetails = onOpenSongDetails,
                onClear = onClearYoutubeResult
            )
        }
    }
}

@Composable
private fun ActiveImportRow(
    job: ActiveImportUiItem,
    onCancelImport: (String) -> Unit,
    onRetryImport: (String) -> Unit,
    onMoveImport: (String, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (job.thumbnailUrl != null) {
                    AsyncImage(
                        model = job.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(imageVector = Icons.Rounded.FileDownload, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activeImportSubtitle(job),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = job.progressPercent?.let { "$it%" } ?: "",
                style = MaterialTheme.typography.labelLarge
            )
            if (job.status == "QUEUED") {
                IconButton(onClick = { onMoveImport(job.importJobId, -1) }) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Move import up"
                    )
                }
                IconButton(onClick = { onMoveImport(job.importJobId, 1) }) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Move import down"
                    )
                }
            }
            if (job.retryable) {
                IconButton(onClick = { onRetryImport(job.importJobId) }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Retry import"
                    )
                }
            }
            if (job.cancellable) {
                IconButton(onClick = { onCancelImport(job.importJobId) }) {
                    Icon(
                        imageVector = Icons.Rounded.Cancel,
                        contentDescription = "Cancel import"
                    )
                }
            }
        }
        if (job.progressPercent != null) {
            LinearProgressIndicator(
                progress = { job.progressPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
        job.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun YoutubeImportResultSummary(
    result: YoutubeImportItemResult,
    onOpenSongDetails: (String) -> Unit,
    onClear: () -> Unit
) {
    val icon = when (result.outcome) {
        YoutubeImportOutcome.SUCCESS -> Icons.Rounded.CheckCircle
        YoutubeImportOutcome.DUPLICATE -> Icons.Rounded.ContentCopy
        YoutubeImportOutcome.FAILED -> Icons.Rounded.Error
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = result.message ?: result.outcome.name.lowercase().replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.trackId != null && result.outcome != YoutubeImportOutcome.FAILED) {
                    TextButton(onClick = { onOpenSongDetails(result.trackId) }) {
                        Text(text = "Song Details")
                    }
                }
                TextButton(onClick = onClear) {
                    Text(text = "Clear")
                }
            }
        }
    }
}

@Composable
private fun ImportHistoryPanel(
    history: List<ImportHistoryUiItem>,
    selectedFilter: ImportHistoryFilter,
    onFilterChanged: (ImportHistoryFilter) -> Unit,
    onClearHistory: () -> Unit,
    onRetryHistoryItem: (ImportHistoryUiItem) -> Unit,
    onOpenSongDetails: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Import History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClearHistory) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Clear import history"
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImportHistoryFilter.values().forEach { filter ->
                if (filter == selectedFilter) {
                    Button(onClick = { onFilterChanged(filter) }) {
                        Text(text = filter.label())
                    }
                } else {
                    OutlinedButton(onClick = { onFilterChanged(filter) }) {
                        Text(text = filter.label())
                    }
                }
            }
        }
        if (history.isEmpty()) {
            Text(
                text = "No import history yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.forEach { item ->
                    ImportHistoryRow(
                        item = item,
                        onRetryHistoryItem = onRetryHistoryItem,
                        onOpenSongDetails = onOpenSongDetails
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportHistoryRow(
    item: ImportHistoryUiItem,
    onRetryHistoryItem: (ImportHistoryUiItem) -> Unit,
    onOpenSongDetails: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = when (item.result) {
                "SUCCESS" -> Icons.Rounded.CheckCircle
                "DUPLICATE" -> Icons.Rounded.ContentCopy
                else -> Icons.Rounded.Error
            },
            contentDescription = null
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.result.lowercase().replaceFirstChar { it.titlecase() }} - ${item.sourceLabel} - ${item.createdAt.formatDateTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.trackId != null && item.result != "FAILED") {
                TextButton(onClick = { onOpenSongDetails(item.trackId) }) {
                    Text(text = "Song Details")
                }
            }
        }
        if (item.importJobId != null && item.result in retryableHistoryResults) {
            IconButton(onClick = { onRetryHistoryItem(item) }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Retry import"
                )
            }
        }
    }
}

@Composable
private fun ImportResultPanel(
    result: LocalImportBatchResult,
    onRetryFailed: (List<Uri>) -> Unit,
    onViewLibrary: () -> Unit,
    onOpenSongDetails: (String) -> Unit,
    onClearResult: () -> Unit
) {
    val failedUris = result.failures.mapNotNull { it.sourceUri }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Import Result",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${result.successes.size} imported - ${result.duplicates.size} duplicate - ${result.failures.size} failed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ResultGroup(
            title = "Success",
            items = result.successes,
            icon = { Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null) },
            onOpenSongDetails = onOpenSongDetails
        )
        ResultGroup(
            title = "Duplicate",
            items = result.duplicates,
            icon = { Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null) }
        )
        ResultGroup(
            title = "Failed",
            items = result.failures,
            icon = { Icon(imageVector = Icons.Rounded.Error, contentDescription = null) }
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result.successes.isNotEmpty()) {
                Button(
                    onClick = onViewLibrary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = null)
                    Text(
                        text = "View in Library",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (failedUris.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onRetryFailed(failedUris) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                    Text(
                        text = "Retry failed",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            OutlinedButton(
                onClick = onClearResult,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Clear")
            }
        }
    }
}

@Composable
private fun ResultGroup(
    title: String,
    items: List<LocalImportItemResult>,
    icon: @Composable () -> Unit,
    onOpenSongDetails: ((String) -> Unit)? = null
) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                icon()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val message = item.messageForDisplay()
                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (onOpenSongDetails != null && item.trackId != null) {
                        TextButton(onClick = { onOpenSongDetails(item.trackId) }) {
                            Text(text = "Song Details")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.clipImportPanel(): Modifier =
    this
        .background(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        )

private fun LocalImportItemResult.messageForDisplay(): String? {
    if (metadataWarning) return message
    return when (outcome) {
        LocalImportOutcome.SUCCESS -> null
        LocalImportOutcome.DUPLICATE,
        LocalImportOutcome.FAILED -> message ?: filename
    }
}

@Composable
private fun HorizontalImportDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

private fun String.labelForImport(): String =
    lowercase()
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.US) }

private fun activeImportSubtitle(job: ActiveImportUiItem): String =
    listOfNotNull(
        job.sourceLabel,
        job.status.labelForImport(),
        job.queuePosition?.let { "queue #$it" }
    ).joinToString(" - ")

private fun playlistPreviewStatus(
    preview: YoutubePlaylistPreview,
    selectedCount: Int
): String {
    val loaded = preview.loadedItemCount ?: preview.items.size
    val total = preview.totalExpectedItems
    val countLabel = if (total != null) {
        "$loaded of $total loaded"
    } else {
        "$loaded loaded"
    }
    return "$countLabel - $selectedCount selected - ${preview.status.labelForImport()}"
}

private fun ImportHistoryFilter.label(): String =
    when (this) {
        ImportHistoryFilter.ALL -> "All"
        ImportHistoryFilter.SUCCESS -> "Success"
        ImportHistoryFilter.DUPLICATE -> "Duplicate"
        ImportHistoryFilter.FAILED -> "Failed"
        ImportHistoryFilter.CANCELLED -> "Cancelled"
    }

private fun String.isPreviewReadyForWholeImport(): Boolean =
    !equals("LOADING", ignoreCase = true) &&
        !equals("PENDING", ignoreCase = true) &&
        !equals("PROCESSING", ignoreCase = true) &&
        !equals("RUNNING", ignoreCase = true) &&
        !equals("FAILED", ignoreCase = true) &&
        !equals("EXPIRED", ignoreCase = true)

private val retryableHistoryResults = setOf("FAILED", "CANCELLED")

private fun Long.formatDuration(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun Long.formatDateTime(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))
