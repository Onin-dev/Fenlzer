package com.fenl.fenlzer.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.fenl.fenlzer.data.repository.QueueTrackItem
import com.fenl.fenlzer.playback.PlaybackUiState
import com.fenl.fenlzer.ui.components.DragStepHandle
import com.fenl.fenlzer.ui.theme.Dimensions
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
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
    val rowHeightPx = with(LocalDensity.current) { QueueRowHeight.toPx() }

    var pendingHiddenIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var saveDialogOpen by remember { mutableStateOf(false) }
    var savePlaylistName by remember { mutableStateOf("Saved queue") }

    var initialCurrentScrollDone by remember { mutableStateOf(false) }
    var draggingQueueItemId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    val liveCurrentQueueItemId = playbackState.currentItem?.queueItemId
    val visibleItems = playbackState.queueItems.filterNot { it.queueItemId in pendingHiddenIds }
    val sourceIds = playbackState.queueItems.map { it.queueItemId }.toSet()
    val currentVisibleIndex = visibleItems.indexOfFirst { it.queueItemId == liveCurrentQueueItemId }

    LaunchedEffect(sourceIds.joinToString("|")) {
        pendingHiddenIds = pendingHiddenIds.intersect(sourceIds)
    }

    LaunchedEffect(visibleItems.size, liveCurrentQueueItemId) {
        if (!initialCurrentScrollDone && currentVisibleIndex >= 0) {
            listState.scrollToItem(currentVisibleIndex)
            initialCurrentScrollDone = true
        }
    }

    fun clearDrag(commit: Boolean) {
        val itemId = draggingQueueItemId
        val delta = dragTargetIndex - dragStartIndex
        if (commit && itemId != null && delta != 0) {
            val step = if (delta > 0) 1 else -1
            repeat(abs(delta)) {
                onMoveItem(itemId, step)
            }
        }
        draggingQueueItemId = null
        dragStartIndex = -1
        dragTargetIndex = -1
        dragOffsetPx = 0f
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
            }
        }
    }

    fun startDrag(item: QueueTrackItem) {
        if (item.queueItemId == liveCurrentQueueItemId) return
        val index = visibleItems.indexOfFirst { it.queueItemId == item.queueItemId }
        if (index < 0) return
        draggingQueueItemId = item.queueItemId
        dragStartIndex = index
        dragTargetIndex = index
        dragOffsetPx = 0f
    }

    fun dragBy(deltaY: Float) {
        if (draggingQueueItemId == null || dragStartIndex < 0) return
        dragOffsetPx += deltaY
        maybeAutoScrollQueue(
            scope = scope,
            listState = listState,
            dragTargetIndex = dragTargetIndex,
            deltaY = deltaY
        )
        val rawTarget = (dragStartIndex + (dragOffsetPx / rowHeightPx).toInt())
            .coerceIn(0, visibleItems.lastIndex)
        if (rawTarget != dragTargetIndex) {
            dragTargetIndex = rawTarget
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
                        contentPadding = PaddingValues(vertical = 8.dp),
                        userScrollEnabled = draggingQueueItemId == null
                    ) {
                        itemsIndexed(
                            items = visibleItems,
                            key = { _, item -> item.queueItemId }
                        ) { index, item ->
                            val isCurrent = item.queueItemId == liveCurrentQueueItemId
                            val isDragging = item.queueItemId == draggingQueueItemId
                            QueueRow(
                                item = item,
                                isCurrent = isCurrent,
                                isQueueDragActive = draggingQueueItemId != null,
                                isDragging = isDragging,
                                visualOffsetPx = visualQueueRowOffsetPx(
                                    index = index,
                                    isDragging = isDragging,
                                    dragOffsetPx = dragOffsetPx,
                                    dragStartIndex = dragStartIndex,
                                    dragTargetIndex = dragTargetIndex,
                                    rowHeightPx = rowHeightPx
                                ),
                                canMoveUp = !isCurrent && index > 0,
                                canMoveDown = !isCurrent && index < visibleItems.lastIndex,
                                onRequestUndoableRemoval = ::requestUndoableRemoval,
                                onJumpToItem = onJumpToItem,
                                onRemoveCurrentItem = { onRemoveItem(item.queueItemId) },
                                onMoveUp = { onMoveItem(item.queueItemId, -1) },
                                onMoveDown = { onMoveItem(item.queueItemId, 1) },
                                onDragStart = { startDrag(item) },
                                onDragDelta = ::dragBy,
                                onDragEnd = { clearDrag(commit = true) }
                            )
                        }
                    }
                }
            }

            DismissibleQueueSnackbarHost(
                snackbarHostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

private val QueueRowHeight = 72.dp

private fun visualQueueRowOffsetPx(
    index: Int,
    isDragging: Boolean,
    dragOffsetPx: Float,
    dragStartIndex: Int,
    dragTargetIndex: Int,
    rowHeightPx: Float
): Float {
    if (dragStartIndex < 0 || dragTargetIndex < 0) return 0f
    if (isDragging) return dragOffsetPx

    return when {
        dragTargetIndex > dragStartIndex && index in (dragStartIndex + 1)..dragTargetIndex -> -rowHeightPx
        dragTargetIndex < dragStartIndex && index in dragTargetIndex until dragStartIndex -> rowHeightPx
        else -> 0f
    }
}

private fun maybeAutoScrollQueue(
    scope: CoroutineScope,
    listState: LazyListState,
    dragTargetIndex: Int,
    deltaY: Float
) {
    val visibleInfo = listState.layoutInfo.visibleItemsInfo
    val firstVisible = visibleInfo.firstOrNull()?.index ?: return
    val lastVisible = visibleInfo.lastOrNull()?.index ?: return
    val shouldScrollUp = deltaY < 0f && dragTargetIndex <= firstVisible + 1
    val shouldScrollDown = deltaY > 0f && dragTargetIndex >= lastVisible - 1
    if (shouldScrollUp || shouldScrollDown) {
        scope.launch {
            listState.scrollBy(deltaY * 1.7f)
        }
    }
}

@Composable
private fun DismissibleQueueSnackbarHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    ) { snackbarData ->
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = {
                snackbarData.dismiss()
                true
            }
        )
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = {},
            content = { Snackbar(snackbarData = snackbarData) }
        )
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

@Composable
private fun QueueRow(
    item: QueueTrackItem,
    isCurrent: Boolean,
    isQueueDragActive: Boolean,
    isDragging: Boolean,
    visualOffsetPx: Float,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRequestUndoableRemoval: (QueueTrackItem) -> Unit,
    onJumpToItem: (String) -> Unit,
    onRemoveCurrentItem: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    var confirmCurrentRemoval by remember { mutableStateOf(false) }
    var swipeOffsetX by remember(item.queueItemId) { mutableFloatStateOf(0f) }
    val rowSwipeThreshold = with(LocalDensity.current) { 92.dp.toPx() }
    val rowMaxSwipe = with(LocalDensity.current) { 132.dp.toPx() }

    if (confirmCurrentRemoval) {
        AlertDialog(
            onDismissRequest = { confirmCurrentRemoval = false },
            title = { Text(text = "Remove current song?") },
            text = { Text(text = "Playback will skip to the next song if one is queued.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCurrentRemoval = false
                        onRemoveCurrentItem()
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = QueueRowHeight)
            .graphicsLayer {
                translationY = visualOffsetPx
                shadowElevation = if (isDragging) 16f else 0f
                scaleX = if (isDragging) 1.015f else 1f
                scaleY = if (isDragging) 1.015f else 1f
            }
            .zIndex(if (isDragging) 2f else 0f)
    ) {
        if (swipeOffsetX > 0f && !isQueueDragActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = QueueRowHeight)
                .graphicsLayer { translationX = if (isQueueDragActive) 0f else swipeOffsetX }
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = QueueRowHeight)
                    .pointerInput(isQueueDragActive, item.queueItemId) {
                        if (isQueueDragActive) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffsetX >= rowSwipeThreshold) {
                                    if (isCurrent) {
                                        confirmCurrentRemoval = true
                                    } else {
                                        onRequestUndoableRemoval(item)
                                    }
                                }
                                swipeOffsetX = 0f
                            },
                            onDragCancel = { swipeOffsetX = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(0f, rowMaxSwipe)
                        }
                    }
                    .clickable(enabled = !isQueueDragActive) { onJumpToItem(item.queueItemId) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QueueThumbnail(item = item, isCurrent = isCurrent)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = queueSubtitle(item),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            DragStepHandle(
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                enabled = !isCurrent,
                contentDescription = if (isCurrent) "Current song stays fixed" else "Reorder queue item",
                modifier = Modifier.padding(end = 8.dp),
                onDragStart = {
                    swipeOffsetX = 0f
                    onDragStart()
                },
                onDragDelta = onDragDelta,
                onDragEnd = onDragEnd
            )
        }
    }
}

@Composable
private fun QueueThumbnail(
    item: QueueTrackItem,
    isCurrent: Boolean
) {
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
