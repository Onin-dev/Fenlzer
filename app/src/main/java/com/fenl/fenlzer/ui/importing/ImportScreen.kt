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
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import com.fenl.fenlzer.ui.components.FenlzerLoadingPlaceholder
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.rounded.Close
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.rounded.Sync



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
    onDismissYoutubeImport: (String) -> Unit,
    onEnterActiveImports: () -> Unit,
    onLeaveActiveImports: () -> Unit,
    onHistoryFilterChanged: (ImportHistoryFilter) -> Unit,
    onClearYoutubeHistory: () -> Unit,
    onRetryYoutubeHistoryItem: (ImportHistoryUiItem) -> Unit,
    onRetryFailed: (List<Uri>) -> Unit,
    onPlayImportedSongs: (List<String>) -> Unit,
    onAddImportedSongsToPlaylist: (List<String>) -> Unit,
    onViewLibrary: (List<String>) -> Unit,
    onOpenSongDetails: (String) -> Unit,
    onClearResult: () -> Unit,
    onClearYoutubeResult: () -> Unit,
    activeImportsRequestId: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedImportSectionName by rememberSaveable { mutableStateOf(ImportSection.HOME.name) }
    val selectedImportSection = selectedImportSectionName.importSectionOrDefault()

    LaunchedEffect(activeImportsRequestId) {
        if (activeImportsRequestId > 0) {
            selectedImportSectionName = ImportSection.ACTIVE_IMPORTS.name
        }
    }

    BackHandler(enabled = selectedImportSection != ImportSection.HOME) {
        if (selectedImportSection == ImportSection.ACTIVE_IMPORTS) onLeaveActiveImports()
        selectedImportSectionName = ImportSection.HOME.name
    }
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
        when (selectedImportSection) {
            ImportSection.HOME -> {
                ImportHomePanel(
                    activeImportCount = youtubeState.activeJobs.size,
                    historyCount = youtubeState.history.size,
                    onOpenSection = { selectedImportSectionName = it.name }
                )
            }

            ImportSection.DEVICE -> {
                ImportDetailHeader(
                    title = ImportSection.DEVICE.label,
                    onBack = { selectedImportSectionName = ImportSection.HOME.name }
                )
                DeviceImportPanel(
                    state = state,
                    onImportFromDevice = onImportFromDevice,
                    onRetryFailed = onRetryFailed,
                    onPlayImportedSongs = onPlayImportedSongs,
                    onAddImportedSongsToPlaylist = onAddImportedSongsToPlaylist,
                    onViewLibrary = onViewLibrary,
                    onOpenSongDetails = onOpenSongDetails,
                    onClearResult = onClearResult
                )
            }

            ImportSection.DOWNLOAD_YOUTUBE -> {
                ImportDetailHeader(
                    title = ImportSection.DOWNLOAD_YOUTUBE.label,
                    onBack = { selectedImportSectionName = ImportSection.HOME.name }
                )
                YoutubeSearchPanel(
                    state = youtubeState,
                    onQueryChanged = onYoutubeQueryChanged,
                    onSearch = onSearchYoutube,
                    onImportResult = onImportYoutubeResult
                )
            }

            ImportSection.PLAYLIST -> {
                ImportDetailHeader(
                    title = ImportSection.PLAYLIST.label,
                    onBack = { selectedImportSectionName = ImportSection.HOME.name }
                )
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

            ImportSection.ACTIVE_IMPORTS -> {
                DisposableEffect(Unit) {
                    onEnterActiveImports()
                    onDispose(onLeaveActiveImports)
                }
                ImportDetailHeader(
                    title = ImportSection.ACTIVE_IMPORTS.label,
                    onBack = {
                        onLeaveActiveImports()
                        selectedImportSectionName = ImportSection.HOME.name
                    }
                )
                ActiveImportsPanel(
                    activeJobs = youtubeState.activeJobs,
                    latestResult = youtubeState.lastImportResult,
                    importError = youtubeState.importError,
                    onCancelImport = onCancelYoutubeImport,
                    onRetryImport = onRetryYoutubeImport,
                    onRetryAllImports = {
                        youtubeState.activeJobs
                            .filter { it.retryable }
                            .forEach { onRetryYoutubeImport(it.importJobId) }
                    },
                    onMoveImport = onMoveYoutubeImport,
                    onDismissImport = onDismissYoutubeImport,
                    onOpenSongDetails = onOpenSongDetails,
                    onClearYoutubeResult = onClearYoutubeResult
                )
            }

            ImportSection.HISTORY -> {
                ImportDetailHeader(
                    title = ImportSection.HISTORY.label,
                    onBack = { selectedImportSectionName = ImportSection.HOME.name }
                )
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
}

private enum class ImportSection(
    val label: String,
    val description: String
) {
    HOME("Import", "Choose an import action."),
    DEVICE("Import From Device", "Choose local audio files stored on this device."),
    DOWNLOAD_YOUTUBE("Download From YouTube", "Search YouTube or paste a video URL to fetch a single song."),
    PLAYLIST("Import YouTube Playlist", "Preview a playlist and import selected songs."),
    ACTIVE_IMPORTS("Active Imports", "View, cancel, retry, or reorder current YouTube imports."),
    HISTORY("Import History", "Review previous local and YouTube imports.")
}

private fun String.importSectionOrDefault(): ImportSection =
    runCatching { ImportSection.valueOf(this) }.getOrDefault(ImportSection.HOME)



@Composable
private fun ImportHomePanel(
    activeImportCount: Int,
    historyCount: Int,
    onOpenSection: (ImportSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Import",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        ImportHomeSectionButton(
            title = ImportSection.DEVICE.label,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = null
                )
            },
            onClick = { onOpenSection(ImportSection.DEVICE) }
        )

        ImportHomeSectionButton(
            title = ImportSection.DOWNLOAD_YOUTUBE.label,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null
                )
            },
            onClick = { onOpenSection(ImportSection.DOWNLOAD_YOUTUBE) }
        )

        ImportHomeSectionButton(
            title = ImportSection.PLAYLIST.label,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null
                )
            },
            onClick = { onOpenSection(ImportSection.PLAYLIST) }
        )

        ImportHomeSectionButton(
            title = ImportSection.ACTIVE_IMPORTS.label,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null
                )
            },
            onClick = { onOpenSection(ImportSection.ACTIVE_IMPORTS) }
        )

        ImportHomeSectionButton(
            title = ImportSection.HISTORY.label,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null
                )
            },
            onClick = { onOpenSection(ImportSection.HISTORY) }
        )
    }
}

@Composable
private fun ImportHomeSectionButton(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ImportDetailHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(text = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DeviceImportPanel(
    state: LocalImportUiState,
    onImportFromDevice: () -> Unit,
    onRetryFailed: (List<Uri>) -> Unit,
    onPlayImportedSongs: (List<String>) -> Unit,
    onAddImportedSongsToPlaylist: (List<String>) -> Unit,
    onViewLibrary: (List<String>) -> Unit,
    onOpenSongDetails: (String) -> Unit,
    onClearResult: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipImportPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Choose audio files stored on this device. Fenlzer imports them into private app storage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onImportFromDevice,
            enabled = !state.isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Rounded.FileDownload, contentDescription = null)
            Text(
                text = if (state.isRunning) "Import running" else "Choose audio files",
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (state.isRunning) {
            LocalImportProgressPanel(state = state)
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        state.result?.let { result ->
            ImportResultPanel(
                result = result,
                onRetryFailed = onRetryFailed,
                onPlayImportedSongs = onPlayImportedSongs,
                onAddImportedSongsToPlaylist = onAddImportedSongsToPlaylist,
                onViewLibrary = onViewLibrary,
                onOpenSongDetails = onOpenSongDetails,
                onClearResult = onClearResult
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
                placeholder = { Text(text = "Search YouTube or paste URL") },
                leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
                
                    trailingIcon = {
                        if (state.query.isNotBlank()) {
                            IconButton(onClick = { onQueryChanged("") }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear YouTube search")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onSearch,
                enabled = !state.isSearching && state.query.isNotBlank()
            ) {
                Text(text = if (state.query.isProbablyYoutubeUrl()) "Fetch" else "Search")
            }
        }

        if (state.isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LoadingResultPlaceholders()
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
                
                    trailingIcon = {
                        if (state.playlistUrl.isNotBlank()) {
                            IconButton(onClick = { onUrlChanged("") }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear playlist URL")
                            }
                        }
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
            LoadingResultPlaceholders()
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
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
private fun LoadingResultPlaceholders(
    count: Int = 3
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(count) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FenlzerLoadingPlaceholder(
                    modifier = Modifier.size(width = 76.dp, height = 52.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FenlzerLoadingPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    )
                    FenlzerLoadingPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(12.dp)
                    )
                }
            }
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
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
    onRetryAllImports: () -> Unit,
    onMoveImport: (String, Int) -> Unit,
    onDismissImport: (String) -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Active Imports",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (activeJobs.any { it.retryable }) {
                OutlinedButton(onClick = onRetryAllImports) {
                    Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                    Text(text = "Retry all", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (activeJobs.isEmpty()) {
            Text(
                text = "No imports running.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val queued = activeJobs.filter { it.status == "QUEUED" }
            val failed = activeJobs.filter { it.status.isFailedImportStatus() }
            val done = activeJobs.filter { it.status.isSuccessfulImportStatus() }
            val running = activeJobs - queued.toSet() - failed.toSet() - done.toSet()
            ActiveImportDashboardStats(
                running = running.size,
                queued = queued.size,
                done = done.size,
                failed = failed.size
            )
            ActiveImportSection(
                title = "Running",
                jobs = running,
                onCancelImport = onCancelImport,
                onRetryImport = onRetryImport,
                onMoveImport = onMoveImport,
                onDismissImport = onDismissImport
            )
            ActiveImportSection(
                title = "Queued",
                jobs = queued,
                onCancelImport = onCancelImport,
                onRetryImport = onRetryImport,
                onMoveImport = onMoveImport,
                onDismissImport = onDismissImport
            )
            ActiveImportSection(
                title = "Done",
                jobs = done,
                onCancelImport = onCancelImport,
                onRetryImport = onRetryImport,
                onMoveImport = onMoveImport,
                onDismissImport = onDismissImport
            )
            ActiveImportSection(
                title = "Failed",
                jobs = failed,
                onCancelImport = onCancelImport,
                onRetryImport = onRetryImport,
                onMoveImport = onMoveImport,
                onDismissImport = onDismissImport
            )
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
private fun ActiveImportDashboardStats(
    running: Int,
    queued: Int,
    done: Int,
    failed: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ImportDashboardChip(label = "Running", value = running)
        ImportDashboardChip(label = "Queued", value = queued)
        ImportDashboardChip(label = "Done", value = done)
        ImportDashboardChip(label = "Failed", value = failed)
    }
}

@Composable
private fun ImportDashboardChip(
    label: String,
    value: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ActiveImportSection(
    title: String,
    jobs: List<ActiveImportUiItem>,
    onCancelImport: (String) -> Unit,
    onRetryImport: (String) -> Unit,
    onMoveImport: (String, Int) -> Unit,
    onDismissImport: (String) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (jobs.isEmpty()) {
        Text(
            text = "None",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        jobs.forEach { job ->
            ActiveImportRow(
                job = job,
                onCancelImport = onCancelImport,
                onRetryImport = onRetryImport,
                onMoveImport = onMoveImport,
                onDismissImport = onDismissImport
            )
        }
    }
}

@Composable
private fun ActiveImportRow(
    job: ActiveImportUiItem,
    onCancelImport: (String) -> Unit,
    onRetryImport: (String) -> Unit,
    onMoveImport: (String, Int) -> Unit,
    onDismissImport: (String) -> Unit
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
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
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
            if (job.dismissible) {
                IconButton(onClick = { onDismissImport(job.importJobId) }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss failed import"
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
        YoutubeImportOutcome.QUEUED -> Icons.Rounded.Sync
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

private fun String.isTerminalImportStatus(): Boolean = this in setOf(
    "COMPLETED",
    "TRANSFER_CONFIRMED",
    "DUPLICATE",
    "FAILED",
    "CANCELLED"
)

private fun String.isSuccessfulImportStatus(): Boolean = this in setOf(
    "COMPLETED",
    "TRANSFER_CONFIRMED",
    "DUPLICATE"
)

private fun String.isFailedImportStatus(): Boolean = this in setOf(
    "FAILED",
    "CANCELLED"
)

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
    onPlayImportedSongs: (List<String>) -> Unit,
    onAddImportedSongsToPlaylist: (List<String>) -> Unit,
    onViewLibrary: (List<String>) -> Unit,
    onOpenSongDetails: (String) -> Unit,
    onClearResult: () -> Unit
) {
    val failedUris = result.failures.mapNotNull { it.sourceUri }
    val importedTrackIds = result.successes.mapNotNull { it.trackId }.distinct()

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
                    onClick = { onPlayImportedSongs(importedTrackIds) },
                    enabled = importedTrackIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(text = "Play imported songs", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { onAddImportedSongsToPlaylist(importedTrackIds) },
                    enabled = importedTrackIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null
                    )
                    Text(text = "Add to playlist", modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = { onViewLibrary(importedTrackIds) },
                    enabled = importedTrackIds.isNotEmpty(),
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
        job.progressPercent?.takeIf { it in 1..99 }?.let { "$it%" },
        job.etaSeconds?.takeIf { it > 0 }?.let { "ETA ${it.formatEta()}" },
        job.queuePosition?.let { "queue #$it" },
        job.attemptCount.takeIf { it > 0 }?.let { "attempt $it/${job.maxAttempts}" }
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


private fun String.isProbablyYoutubeUrl(): Boolean {
    val value = trim().lowercase(Locale.US)
    return value.startsWith("http://") ||
        value.startsWith("https://") ||
        value.contains("youtube.com/watch") ||
        value.contains("youtu.be/") ||
        value.contains("music.youtube.com/")
}

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

private fun Long.formatEta(): String {
    val minutes = this / 60L
    val seconds = this % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

private fun Long.formatDateTime(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))
