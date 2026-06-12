package com.fenl.fenlzer.ui.player

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import coil3.compose.AsyncImage
import com.fenl.fenlzer.playback.PlaybackUiState
import com.fenl.fenlzer.playback.QueueRepeatMode
import com.fenl.fenlzer.playback.SleepTimerMode
import java.util.Locale
import kotlin.math.abs
import androidx.compose.ui.layout.ContentScale

@Composable
fun FullscreenPlayer(
    playbackState: PlaybackUiState,
    privateModeEnabled: Boolean,
    sleepTimerDefaultMinutes: Int,
    onMinimize: () -> Unit,
    onToggleFavourite: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenSongDetails: () -> Unit,
    onEditMetadata: () -> Unit,
    onDeleteFromFenlzer: () -> Unit,
    onOpenQueue: () -> Unit,
    onStartSleepTimerDuration: (Long) -> Unit,
    onStartSleepTimerEndOfSong: () -> Unit,
    onStartSleepTimerEndOfQueue: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showSleepSheet by remember { mutableStateOf(false) }
    var fullscreenDragX by remember { mutableFloatStateOf(0f) }
    var fullscreenDragY by remember { mutableFloatStateOf(0f) }

    val swipeDownToMinimizeConnection = remember(onMinimize) {
        object : NestedScrollConnection {
            private var accumulatedDownPx = 0f
            private var accumulatedAbsHorizontalPx = 0f

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag) {
                    if (available.y > 0f) {
                        accumulatedDownPx += available.y
                        accumulatedAbsHorizontalPx += abs(available.x)
                    } else if (available.y < 0f) {
                        accumulatedDownPx = 0f
                        accumulatedAbsHorizontalPx = 0f
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag) {
                    val totalY = consumed.y + available.y
                    val totalX = consumed.x + available.x
                    if (totalY > 0f) {
                        accumulatedDownPx += totalY
                        accumulatedAbsHorizontalPx += abs(totalX)
                    } else if (totalY < 0f) {
                        accumulatedDownPx = 0f
                        accumulatedAbsHorizontalPx = 0f
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val shouldMinimize = accumulatedDownPx > 150f &&
                    accumulatedDownPx > accumulatedAbsHorizontalPx * 1.2f
                accumulatedDownPx = 0f
                accumulatedAbsHorizontalPx = 0f
                if (shouldMinimize) {
                    onMinimize()
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    if (showSleepSheet) {
        SleepTimerSheet(
            playbackState = playbackState,
            defaultMinutes = sleepTimerDefaultMinutes,
            onStartDuration = onStartSleepTimerDuration,
            onStartEndOfSong = onStartSleepTimerEndOfSong,
            onStartEndOfQueue = onStartSleepTimerEndOfQueue,
            onCancel = onCancelSleepTimer,
            onDismiss = { showSleepSheet = false }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("fullscreenPlayer")
            .pointerInput(playbackState.currentItem?.queueItemId) {
                detectDragGestures(
                    onDrag = { _, dragAmount ->
                        fullscreenDragX += dragAmount.x
                        fullscreenDragY += dragAmount.y
                    },
                    onDragEnd = {
                        val verticalSwipe = fullscreenDragY > 170f && abs(fullscreenDragY) > abs(fullscreenDragX) * 1.25f
                        if (verticalSwipe) {
                            onMinimize()
                        }
                        fullscreenDragX = 0f
                        fullscreenDragY = 0f
                    },
                    onDragCancel = {
                        fullscreenDragX = 0f
                        fullscreenDragY = 0f
                    }
                )
            },
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(swipeDownToMinimizeConnection)
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerArtwork(
                    playbackState = playbackState,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onMinimize = onMinimize,
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                )

                PlayerControlsColumn(
                    playbackState = playbackState,
                    privateModeEnabled = privateModeEnabled,
                    onMinimize = onMinimize,
                    onToggleFavourite = onToggleFavourite,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeekTo = onSeekTo,
                    onToggleRepeat = onToggleRepeat,
                    onToggleShuffle = onToggleShuffle,
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenSongDetails = onOpenSongDetails,
                    onEditMetadata = onEditMetadata,
                    onDeleteFromFenlzer = onDeleteFromFenlzer,
                    onOpenQueue = onOpenQueue,
                    onOpenSleepTimer = { showSleepSheet = true },
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(swipeDownToMinimizeConnection)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PlayerControlsHeader(onMinimize = onMinimize)

                PlayerArtwork(
                    playbackState = playbackState,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onMinimize = onMinimize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(vertical = 14.dp)
                )

                PlayerControlsColumn(
                    playbackState = playbackState,
                    privateModeEnabled = privateModeEnabled,
                    onMinimize = onMinimize,
                    onToggleFavourite = onToggleFavourite,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeekTo = onSeekTo,
                    onToggleRepeat = onToggleRepeat,
                    onToggleShuffle = onToggleShuffle,
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenSongDetails = onOpenSongDetails,
                    onEditMetadata = onEditMetadata,
                    onDeleteFromFenlzer = onDeleteFromFenlzer,
                    onOpenQueue = onOpenQueue,
                    onOpenSleepTimer = { showSleepSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    showHeader = false
                )
            }
        }
    }
}

@Composable
private fun PlayerControlsHeader(onMinimize: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMinimize) {
            Icon(imageVector = Icons.Rounded.ArrowDownward, contentDescription = "Minimize player")
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PlayerArtwork(
    playbackState: PlaybackUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentItem = playbackState.currentItem
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("fullscreenPlayerArtwork")
            .pointerInput(currentItem?.queueItemId) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    },
                    onDragEnd = {
                        when {
                            dragY > 140f && abs(dragY) > abs(dragX) -> onMinimize()
                            dragX > 140f && abs(dragX) > abs(dragY) -> onPrevious()
                            dragX < -140f && abs(dragX) > abs(dragY) -> onNext()
                        }
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (currentItem?.thumbnailUri != null) {
            AsyncImage(
                model = currentItem.thumbnailUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerControlsColumn(
    playbackState: PlaybackUiState,
    privateModeEnabled: Boolean,
    onMinimize: () -> Unit,
    onToggleFavourite: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenSongDetails: () -> Unit,
    onEditMetadata: () -> Unit,
    onDeleteFromFenlzer: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    val currentItem = playbackState.currentItem
    var menuExpanded by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember(currentItem?.queueItemId) {
        mutableFloatStateOf(playbackState.playbackPositionMs.toFloat())
    }
    val duration = playbackState.durationMs.takeIf { it > 0L } ?: currentItem?.durationMs ?: 0L
    val position = if (isSeeking) seekValue.toLong() else playbackState.playbackPositionMs

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showHeader) {
            PlayerControlsHeader(onMinimize = onMinimize)
        }

        Text(
            text = currentItem?.displayTitle ?: "No song selected",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = currentItem?.artist?.takeIf { it.isNotBlank() } ?: "Unknown artist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (privateModeEnabled) {
                    PlaybackChip(text = "Private mode", testTag = "fullscreenPrivateModeIndicator")
                }
                if (playbackState.sleepTimerState.active) {
                    PlaybackChip(
                        text = playbackState.sleepTimerState.displayText(),
                        testTag = "fullscreenSleepTimerIndicator"
                    )
                }
            }
        }

        Slider(
            value = position.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
            onValueChange = { value ->
                isSeeking = true
                seekValue = value
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeekTo(seekValue.toLong())
            },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            enabled = currentItem != null && duration > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("fullscreenSeekbar")
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = position.formatDuration(), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "-${(duration - position).coerceAtLeast(0L).formatDuration()}",
                style = MaterialTheme.typography.labelMedium
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle, enabled = playbackState.queueItems.size > 1) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playbackState.shuffleEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            IconButton(onClick = onPrevious, enabled = currentItem != null) {
                Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(onClick = onPlayPause, enabled = currentItem != null) {
                Icon(
                    imageVector = if (playbackState.isPlaying) {
                        Icons.Rounded.PauseCircle
                    } else {
                        Icons.Rounded.PlayCircle
                    },
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(68.dp)
                )
            }
            IconButton(onClick = onNext, enabled = currentItem != null) {
                Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = "Next")
            }
            IconButton(onClick = onToggleRepeat, enabled = currentItem != null) {
                Icon(
                    imageVector = if (playbackState.repeatMode == QueueRepeatMode.ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (playbackState.repeatMode != QueueRepeatMode.OFF) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleFavourite, enabled = currentItem != null) {
                Icon(
                    imageVector = if (currentItem?.isFavourite == true) {
                        Icons.Rounded.Favorite
                    } else {
                        Icons.Rounded.FavoriteBorder
                    },
                    contentDescription = "Favourite"
                )
            }
            IconButton(onClick = onAddToPlaylist, enabled = currentItem != null) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add to playlist")
            }
            IconButton(onClick = onOpenQueue, enabled = playbackState.queueItems.isNotEmpty()) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Open queue")
            }
            IconButton(onClick = { }, enabled = false) {
                Icon(imageVector = Icons.Rounded.Speaker, contentDescription = "Audio output")
            }
            IconButton(onClick = onOpenSleepTimer, enabled = currentItem != null) {
                Icon(
                    imageVector = Icons.Rounded.Snooze,
                    contentDescription = "Sleep timer",
                    tint = if (playbackState.sleepTimerState.active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            IconButton(onClick = onOpenSongDetails, enabled = currentItem != null) {
                Icon(imageVector = Icons.Rounded.BarChart, contentDescription = "Song statistics")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = currentItem != null) {
                    Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Add to Playlist") },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Edit Tags") },
                        onClick = {
                            menuExpanded = false
                            onEditMetadata()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Share") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Share, contentDescription = null) },
                        enabled = false,
                        onClick = { }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Sleep Timer") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Snooze, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenSleepTimer()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Delete from Fenlzer") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDeleteFromFenlzer()
                        }
                    )
                }
            }
        }

        HorizontalDivider()
        PlaybackContext(playbackState = playbackState)
    }
}

@Composable
private fun PlaybackChip(
    text: String,
    testTag: String
) {
    AssistChip(
        onClick = { },
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        enabled = false,
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun PlaybackContext(playbackState: PlaybackUiState) {
    val nextSong = playbackState.queueItems
        .firstOrNull { it.position > (playbackState.currentItem?.position ?: Int.MAX_VALUE) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("fullscreenPlaybackContext")
    ) {
        ContextLine(label = "Playing from", value = playbackState.sourceLabel)
        ContextLine(label = "Next song", value = nextSong?.displayTitle ?: "End of queue")
        if (playbackState.sleepTimerState.active) {
            ContextLine(
                label = "Sleep timer",
                value = playbackState.sleepTimerState.displayText()
            )
        }
    }
}

@Composable
private fun ContextLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(116.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    playbackState: PlaybackUiState,
    defaultMinutes: Int,
    onStartDuration: (Long) -> Unit,
    onStartEndOfSong: () -> Unit,
    onStartEndOfQueue: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMinutes by remember { mutableStateOf("") }
    val savedDefaultMinutes = defaultMinutes.coerceIn(1, 240)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier
            .imePadding()
            .testTag("sleepTimerSheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Sleep Timer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (playbackState.sleepTimerState.active) {
                Text(
                    text = playbackState.sleepTimerState.displayText(),
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onStartDuration(15 * 60_000L)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "15 min")
                }
                Button(
                    onClick = {
                        onStartDuration(savedDefaultMinutes * 60_000L)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "$savedDefaultMinutes min")
                }
                Button(
                    onClick = {
                        onStartDuration(60 * 60_000L)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "60 min")
                }
            }

            OutlinedButton(
                onClick = {
                    onStartEndOfSong()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "End of song")
            }
            OutlinedButton(
                onClick = {
                    onStartEndOfQueue()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "End of queue")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { value ->
                        customMinutes = value.filter { it.isDigit() }.take(3)
                    },
                    singleLine = true,
                    label = { Text(text = "Custom minutes") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val minutes = customMinutes.toLongOrNull()
                        if (minutes != null && minutes > 0L) {
                            onStartDuration(minutes * 60_000L)
                            onDismiss()
                        }
                    },
                    enabled = customMinutes.toLongOrNull()?.let { it > 0L } == true
                ) {
                    Text(text = "Start")
                }
            }

            if (playbackState.sleepTimerState.active) {
                TextButton(
                    onClick = {
                        onCancel()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Cancel timer")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
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

private fun com.fenl.fenlzer.playback.SleepTimerState.displayText(): String {
    val modeLabel = when (mode) {
        SleepTimerMode.DURATION -> remainingMs?.let { "in ${it.formatDuration()}" } ?: "duration"
        SleepTimerMode.END_OF_SONG -> "at end of song"
        SleepTimerMode.END_OF_QUEUE -> "at end of queue"
        null -> ""
    }
    return if (fadeActive) {
        "Fading out $modeLabel"
    } else {
        "Pauses $modeLabel"
    }
}
