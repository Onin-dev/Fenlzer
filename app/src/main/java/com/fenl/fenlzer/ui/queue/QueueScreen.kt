package com.fenl.fenlzer.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.fenl.fenlzer.data.repository.QueueListEditor
import com.fenl.fenlzer.data.repository.QueueTrackItem
import com.fenl.fenlzer.playback.PlaybackUiState
import com.fenl.fenlzer.ui.components.DragStepHandle
import com.fenl.fenlzer.ui.theme.Dimensions
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onJumpToItem: (String) -> Unit,
    onClearUpcoming: () -> Unit,
    modifier: Modifier = Modifier,
    isPanel: Boolean = false,
    onMoveItem: (String, Int) -> Unit = { _, _ -> },
    onShuffleQueue: () -> Unit = {},
    onShuffleUpcoming: () -> Unit = {},
    onSaveAsPlaylist: (String) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var saveDialogVisible by rememberSaveable { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf(defaultQueuePlaylistName(playbackState.sourceLabel)) }

    if (saveDialogVisible) {
        AlertDialog(
            onDismissRequest = { saveDialogVisible = false },
            title = { Text(text = "Save queue as playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Only local/imported songs are saved. Remote Discover items stay in the queue but are skipped for the new playlist.")
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        singleLine = true,
                        label = { Text(text = "Playlist name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = playlistName.isNotBlank(),
                    onClick = {
                        onSaveAsPlaylist(playlistName.trim())
                        saveDialogVisible = false
                    }
                ) { Text(text = "Save") }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogVisible = false }) { Text(text = "Cancel") }
            }
        )
    }

    Surface(
        modifier = modifier
            .then(if (isPanel) Modifier.fillMaxHeight() else Modifier.fillMaxSize())
            .testTag("queueScreen"),
        tonalElevation = if (isPanel) 8.dp else 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                QueueHeader(
                    playbackState = playbackState,
                    onBack = onBack,
                    onClearUpcoming = onClearUpcoming,
                    onShuffleQueue = onShuffleQueue,
                    onShuffleUpcoming = onShuffleUpcoming,
                    onSaveAsPlaylist = { saveDialogVisible = true }
                )
                HorizontalDivider()
                if (playbackState.queueItems.isEmpty()) {
                    EmptyQueueState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(
                            items = playbackState.queueItems,
                            key = { _, item -> item.queueItemId }
                        ) { index, item ->
                            val isCurrent = item.state == QueueListEditor.STATE_CURRENT
                            QueueRow(
                                item = item,
                                isCurrent = isCurrent,
                                canMoveUp = !isCurrent && index > 0,
                                canMoveDown = !isCurrent && index < playbackState.queueItems.lastIndex,
                                onRequestRemoveItem = { queueItem ->
                                    if (queueItem.state == QueueListEditor.STATE_CURRENT) {
                                        onRemoveItem(queueItem.queueItemId)
                                    } else {
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Remove ${queueItem.displayTitle} from queue?",
                                                actionLabel = "Undo",
                                                withDismissAction = true,
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result != SnackbarResult.ActionPerformed) {
                                                onRemoveItem(queueItem.queueItemId)
                                            }
                                        }
                                    }
                                },
                                onJumpToItem = onJumpToItem,
                                onMoveItem = onMoveItem
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun QueueHeader(
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onClearUpcoming: () -> Unit,
    onShuffleQueue: () -> Unit,
    onShuffleUpcoming: () -> Unit,
    onSaveAsPlaylist: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = playbackState.sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "Queue actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Shuffle whole queue") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null) },
                        enabled = playbackState.queueItems.size > 1,
                        onClick = {
                            menuExpanded = false
                            onShuffleQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Shuffle upcoming") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null) },
                        enabled = playbackState.upcomingCount > 1,
                        onClick = {
                            menuExpanded = false
                            onShuffleUpcoming()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Save queue as playlist") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Save, contentDescription = null) },
                        enabled = playbackState.queueItems.any { it.localTrackId != null },
                        onClick = {
                            menuExpanded = false
                            onSaveAsPlaylist()
                        }
                    )
                }
            }
            TextButton(onClick = onBack) { Text(text = "Close") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onClearUpcoming,
                enabled = playbackState.upcomingCount > 0,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Rounded.ClearAll, contentDescription = null)
                Text(text = "Clear Upcoming", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onSaveAsPlaylist,
                enabled = playbackState.queueItems.any { it.localTrackId != null },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = null)
                Text(text = "Save", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun EmptyQueueState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Queue is empty",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueRow(
    item: QueueTrackItem,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRequestRemoveItem: (QueueTrackItem) -> Unit,
    onJumpToItem: (String) -> Unit,
    onMoveItem: (String, Int) -> Unit
) {
    var confirmCurrentRemoval by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                false
            } else if (value == SwipeToDismissBoxValue.StartToEnd) {
                if (isCurrent) {
                    confirmCurrentRemoval = true
                } else {
                    onRequestRemoveItem(item)
                }
                false
            } else {
                false
            }
        }
    )

    if (confirmCurrentRemoval) {
        AlertDialog(
            onDismissRequest = { confirmCurrentRemoval = false },
            title = { Text(text = "Remove current song?") },
            text = { Text(text = "Playback will skip to the next song if one is queued.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCurrentRemoval = false
                        onRequestRemoveItem(item)
                    }
                ) { Text(text = "Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCurrentRemoval = false }) { Text(text = "Cancel") }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (isCurrent) "Confirm remove" else "Remove",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) {
        val rowColor = if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        }
        ListItem(
            headlineContent = {
                Text(
                    text = item.displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            supportingContent = {
                Text(
                    text = queueSubtitle(item),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(Dimensions.QUEUE_ITEM_THUMBNAIL)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.thumbnailUri != null) {
                        AsyncImage(
                            model = item.thumbnailUri,
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
            },
            trailingContent = {
                DragStepHandle(
                    enabled = !isCurrent && (canMoveUp || canMoveDown),
                    contentDescription = if (isCurrent) "Current song cannot be moved" else "Drag to reorder queue item",
                    testTag = "queueDragHandle_${item.queueItemId}",
                    onMoveUp = { if (canMoveUp) onMoveItem(item.queueItemId, -1) },
                    onMoveDown = { if (canMoveDown) onMoveItem(item.queueItemId, 1) }
                )
            },
            modifier = Modifier
                .background(rowColor)
                .clickable { onJumpToItem(item.queueItemId) }
        )
    }
}

private fun queueSubtitle(item: QueueTrackItem): String {
    val parts = mutableListOf<String>()
    item.artist.takeIf { it.isNotBlank() }?.let(parts::add)
    if (item.isRemote) {
        parts += "Remote"
        parts += item.streamState.queueLabel()
    }
    return parts.joinToString(" - ").ifBlank { "Unknown artist" }
}

private fun String?.queueLabel(): String = when (this) {
    "READY" -> "Ready"
    "GETTING_STREAM" -> "Downloading"
    "UNAVAILABLE", "STREAM_FAILED" -> "Unavailable"
    "IMPORTED" -> "Imported"
    null -> "Remote only"
    else -> lowercase(Locale.US)
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.US) }
}

private fun defaultQueuePlaylistName(sourceLabel: String): String =
    sourceLabel
        .removePrefix("Queue from ")
        .removeSuffix(" - Modified")
        .takeIf { it.isNotBlank() && it != "Queue" }
        ?.let { "$it Queue" }
        ?: "Saved Queue"
