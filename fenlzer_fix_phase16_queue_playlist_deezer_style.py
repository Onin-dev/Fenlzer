#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


DRAG_HANDLE_SOURCE = '''package com.fenl.fenlzer.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Deezer-like reorder handle used by queue and playlist rows.
 *
 * This is intentionally handle-only: there are no up/down buttons. The user holds
 * the grip and drags vertically. The implementation translates the vertical drag
 * into repeated one-step moves so it stays dependency-free and works for both
 * QueueScreen and PlaylistsScreen.
 */
@Composable
fun DragStepHandle(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canMoveUp: Boolean = enabled,
    canMoveDown: Boolean = enabled,
    contentDescription: String = "Reorder",
    testTag: String = "dragStepHandle"
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    val stepThresholdPx = 28f
    val effectiveCanMoveUp = enabled && canMoveUp
    val effectiveCanMoveDown = enabled && canMoveDown
    val effectiveEnabled = effectiveCanMoveUp || effectiveCanMoveDown

    Box(
        modifier = modifier
            .size(48.dp)
            .testTag(testTag)
            .pointerInput(effectiveEnabled, effectiveCanMoveUp, effectiveCanMoveDown) {
                if (!effectiveEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onDragEnd = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount.y
                    while (abs(accumulatedDrag) >= stepThresholdPx) {
                        if (accumulatedDrag < 0f) {
                            if (effectiveCanMoveUp) {
                                onMoveUp()
                            }
                            accumulatedDrag += stepThresholdPx
                        } else {
                            if (effectiveCanMoveDown) {
                                onMoveDown()
                            }
                            accumulatedDrag -= stepThresholdPx
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = contentDescription,
            tint = if (effectiveEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            }
        )
    }
}
'''

QUEUE_SCREEN_SOURCE = '''package com.fenl.fenlzer.ui.queue

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
    val listState = rememberLazyListState()

    var pendingHiddenIds by remember { mutableStateOf(emptySet<String>()) }
    var saveDialogOpen by remember { mutableStateOf(false) }
    var savePlaylistName by remember { mutableStateOf("Saved queue") }

    val liveCurrentQueueItemId = playbackState.currentItem?.queueItemId
    val visibleItems = playbackState.queueItems.filterNot { it.queueItemId in pendingHiddenIds }
    val currentVisibleIndex = visibleItems.indexOfFirst { it.queueItemId == liveCurrentQueueItemId }

    LaunchedEffect(liveCurrentQueueItemId, visibleItems.size) {
        if (currentVisibleIndex >= 0) {
            listState.scrollToItem(currentVisibleIndex)
        }
    }

    fun requestUndoableRemoval(item: QueueTrackItem) {
        if (item.queueItemId in pendingHiddenIds) return
        pendingHiddenIds = pendingHiddenIds + item.queueItemId
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Removed from queue",
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingHiddenIds = pendingHiddenIds - item.queueItemId
            } else {
                onRemoveItem(item.queueItemId)
                pendingHiddenIds = pendingHiddenIds - item.queueItemId
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
                ) {
                    Text(text = "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogOpen = false }) {
                    Text(text = "Cancel")
                }
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
                    onSaveQueue = { saveDialogOpen = true }
                )

                HorizontalDivider()

                if (visibleItems.isEmpty()) {
                    EmptyQueueState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(
                            items = visibleItems,
                            key = { _, item -> item.queueItemId }
                        ) { index, item ->
                            val isCurrent = item.queueItemId == liveCurrentQueueItemId
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
    onSaveQueue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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

            TextButton(onClick = onBack) {
                Text(text = "Close")
            }
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
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCurrentRemoval = false }) {
                    Text(text = "Cancel")
                }
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
        val containerColor = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
        val headlineColor = if (isCurrent) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val supportingColor = if (isCurrent) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        ListItem(
            headlineContent = {
                Text(
                    text = item.displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = headlineColor
                )
            },
            supportingContent = {
                Text(
                    text = queueSubtitle(item),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = supportingColor
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(Dimensions.QUEUE_ITEM_THUMBNAIL)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
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
                            tint = if (isCurrent) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
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
            colors = ListItemDefaults.colors(containerColor = containerColor),
            modifier = Modifier.clickable { onJumpToItem(item.queueItemId) }
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
'''


def patch_fenlzer_app(text: str) -> tuple[str, bool]:
    original = text
    replacements = [
        (
            "currentRoute != FenlzerRoute.Diagnostics",
            "currentRoute !in setOf(FenlzerRoute.Diagnostics, FenlzerRoute.Queue)",
        ),
        (
            "currentRoute !in setOf(FenlzerRoute.Diagnostics)",
            "currentRoute !in setOf(FenlzerRoute.Diagnostics, FenlzerRoute.Queue)",
        ),
        (
            "currentRoute !in listOf(FenlzerRoute.Diagnostics)",
            "currentRoute !in listOf(FenlzerRoute.Diagnostics, FenlzerRoute.Queue)",
        ),
        (
            "currentRoute !in setOf(FenlzerRoute.Player, FenlzerRoute.Diagnostics)",
            "currentRoute !in setOf(FenlzerRoute.Player, FenlzerRoute.Diagnostics, FenlzerRoute.Queue)",
        ),
        (
            "currentRoute !in listOf(FenlzerRoute.Player, FenlzerRoute.Diagnostics)",
            "currentRoute !in listOf(FenlzerRoute.Player, FenlzerRoute.Diagnostics, FenlzerRoute.Queue)",
        ),
    ]
    for old, new in replacements:
        text = text.replace(old, new)

    text = re.sub(
        r"val\\s+(\\w*(?:TopBar|OuterTopBar|AppBar)\\w*)\\s*=\\s*currentRoute\\s*!in\\s*setOf\\(FenlzerRoute\\.Diagnostics\\)",
        r"val \\1 = currentRoute !in setOf(FenlzerRoute.Diagnostics, FenlzerRoute.Queue)",
        text,
    )

    text = re.sub(
        r"val\\s+(\\w*(?:MiniPlayer|BottomNav|BottomNavigation|NavigationBar)\\w*)\\s*=\\s*currentRoute\\s*!in\\s*setOf\\(([^)]*FenlzerRoute\\.Diagnostics[^)]*)\\)",
        lambda m: (
            f"val {m.group(1)} = currentRoute !in setOf("
            f"{m.group(2) if 'FenlzerRoute.Queue' in m.group(2) else m.group(2) + ', FenlzerRoute.Queue'}"
            f")"
        ),
        text,
    )

    return text, text != original


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python3 fenlzer_fix_phase16_queue_playlist_deezer_style.py /path/to/Fenlzer")
        return 2

    root = Path(sys.argv[1]).resolve()
    drag_handle = root / "app/src/main/java/com/fenl/fenlzer/ui/components/DragStepHandle.kt"
    queue_screen = root / "app/src/main/java/com/fenl/fenlzer/ui/queue/QueueScreen.kt"
    fenlzer_app = root / "app/src/main/java/com/fenl/fenlzer/ui/FenlzerApp.kt"

    missing = [p for p in [drag_handle, queue_screen, fenlzer_app] if not p.exists()]
    if missing:
        print("ERROR: Missing expected files:")
        for p in missing:
            print(f"  - {p}")
        return 1

    drag_handle.write_text(DRAG_HANDLE_SOURCE)
    queue_screen.write_text(QUEUE_SCREEN_SOURCE)

    app_text = fenlzer_app.read_text()
    app_text, changed = patch_fenlzer_app(app_text)
    fenlzer_app.write_text(app_text)

    print("Updated DragStepHandle.kt to be handle-only with vertical drag reorder.")
    print("Rewrote QueueScreen.kt with:")
    print("  - live current-item highlighting based on playbackState.currentItem")
    print("  - auto-scroll to current item when opened")
    print("  - shorter dismissible snackbar")
    print("  - reduced header padding and no nested Scaffold")
    print("  - highlighted current item using primaryContainer")
    if changed:
        print("Adjusted FenlzerApp.kt chrome conditions to hide outer top bar/navigation on Queue.")
    else:
        print("WARNING: FenlzerApp.kt chrome conditions were not matched. If the outer Queue back arrow remains, share/push the current FenlzerApp.kt.")
    print("Next: ./gradlew assembleDebug")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
