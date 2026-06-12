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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    onSaveQueueAsPlaylist: (String) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingHiddenIds by remember { mutableStateOf(emptySet<String>()) }
    var saveDialogOpen by remember { mutableStateOf(false) }
    var savePlaylistName by remember { mutableStateOf("Saved queue") }

    fun requestUndoableRemoval(item: QueueTrackItem) {
        if (item.queueItemId in pendingHiddenIds) return
        pendingHiddenIds = pendingHiddenIds + item.queueItemId
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Removed from queue",
                actionLabel = "Undo",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingHiddenIds = pendingHiddenIds - item.queueItemId
            } else {
                onRemoveItem(item.queueItemId)
            }
        }
    }

    if (saveDialogOpen) {
        AlertDialog(
            onDismissRequest = { saveDialogOpen = false },
            title = { Text(text = "Save queue as playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Only local/imported songs can be saved. Remote-only queue items are skipped.")
                    OutlinedTextField(
                        value = savePlaylistName,
                        onValueChange = { savePlaylistName = it },
                        singleLine = true,
                        label = { Text(text = "Playlist name") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = savePlaylistName.trim().ifBlank { "Saved queue" }
                        saveDialogOpen = false
                        onSaveQueueAsPlaylist(name)
                    }
                ) { Text(text = "Save") }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogOpen = false }) { Text(text = "Cancel") }
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
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
            ) {
                QueueHeader(
                    playbackState = playbackState,
                    onBack = onBack,
                    onClearUpcoming = onClearUpcoming,
                    onShuffleQueue = onShuffleQueue,
                    onShuffleUpcoming = onShuffleUpcoming,
                    onSaveQueue = { saveDialogOpen = true }
                )
                HorizontalDivider()
                val visibleItems = playbackState.queueItems.filterNot { it.queueItemId in pendingHiddenIds }
                if (visibleItems.isEmpty()) {
                    EmptyQueueState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(
                            items = visibleItems,
                            key = { _, item -> item.queueItemId }
                        ) { index, item ->
                            val isCurrent = item.state == QueueListEditor.STATE_CURRENT
                            QueueRow(
                                item = item,
                                isCurrent = isCurrent,
                                canMoveUp = !isCurrent && index > 0,
                                canMoveDown = !isCurrent && index < visibleItems.lastIndex,
                                onRemoveItem = onRemoveItem,
                                onRequestUndoableRemoval = ::requestUndoableRemoval,
                                onJumpToItem = onJumpToItem,
                                onMoveUp = { onMoveItem(item.queueItemId, -1) },
                                onMoveDown = { onMoveItem(item.queueItemId, 1) }
                            )
                        }
                    }
                }
            }
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
    onSaveQueue: () -> Unit
) {
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
            TextButton(onClick = onBack) { Text(text = "Close") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onClearUpcoming,
                enabled = playbackState.upcomingCount > 0,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Rounded.ClearAll, contentDescription = null)
                Text(text = "Clear", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = onShuffleUpcoming,
                enabled = playbackState.upcomingCount > 1,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null)
                Text(text = "Upcoming", modifier = Modifier.padding(start = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onShuffleQueue,
                enabled = playbackState.queueItems.size > 2,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null)
                Text(text = "Shuffle all", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onSaveQueue,
                enabled = playbackState.queueItems.any { !it.isRemote && (it.localTrackId ?: it.trackId).isNotBlank() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Rounded.Save, contentDescription = null)
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
    onRemoveItem: (String) -> Unit,
    onRequestUndoableRemoval: (QueueTrackItem) -> Unit,
    onJumpToItem: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var confirmCurrentRemoval by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (isCurrent) {
                        confirmCurrentRemoval = true
                    } else {
                        onRequestUndoableRemoval(item)
                    }
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> false
                SwipeToDismissBoxValue.Settled -> false
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
                        onRemoveItem(item.queueItemId)
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
                    text = "Remove",
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
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
                    enabled = !isCurrent,
                    contentDescription = if (isCurrent) "Current song stays fixed" else "Reorder queue item"
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
    "GETTING_STREAM" -> "Resolving"
    "UNAVAILABLE", "STREAM_FAILED" -> "Unavailable"
    "IMPORTED" -> "Imported"
    null -> "Remote only"
    else -> lowercase(Locale.US)
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.US) }
}
