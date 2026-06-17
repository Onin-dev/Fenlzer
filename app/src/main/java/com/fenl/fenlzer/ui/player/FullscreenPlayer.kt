package com.fenl.fenlzer.ui.player

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onGloballyPositioned
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
    onOpenAudioOutput: () -> Unit,
    onShare: () -> Unit,
    onOpenSongDetails: () -> Unit,
    onEditMetadata: () -> Unit,
    onDeleteFromFenlzer: () -> Unit,
    onOpenQueue: () -> Unit,
    onStartSleepTimerDuration: (Long) -> Unit,
    onStartSleepTimerEndOfSong: () -> Unit,
    onStartSleepTimerEndOfQueue: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onMinimizeDragStart: (() -> Unit)? = null,
    onMinimizeDragProgress: ((Float) -> Unit)? = null,
    onMinimizeDragEnd: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    showTrackTitle: Boolean = true,
    onTrackTitleBoundsChanged: ((Rect) -> Unit)? = null
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showSleepSheet by remember { mutableStateOf(false) }
    var fullscreenDragX by remember { mutableFloatStateOf(0f) }
    var fullscreenDragY by remember { mutableFloatStateOf(0f) }
    var minimizeDragStarted by remember { mutableStateOf(false) }
    val minimizeDragDistancePx = with(LocalDensity.current) { FullscreenMinimizeDragDistance.toPx() }
    val artworkTone = rememberAverageArtworkColor(
        thumbnailUri = playbackState.currentItem?.thumbnailUri,
        fallback = MaterialTheme.colorScheme.primary
    )

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
                        val closeProgress = (fullscreenDragY / minimizeDragDistancePx).coerceIn(0f, 0.96f)
                        val isMinimizeDrag =
                            minimizeDragStarted ||
                                (
                                    fullscreenDragY > 12f &&
                                        abs(fullscreenDragY) > abs(fullscreenDragX) * 1.15f &&
                                        onMinimizeDragStart != null &&
                                        onMinimizeDragProgress != null
                                    )
                        if (isMinimizeDrag) {
                            if (!minimizeDragStarted) {
                                onMinimizeDragStart?.invoke()
                                minimizeDragStarted = true
                            }
                            onMinimizeDragProgress?.invoke(closeProgress)
                        }
                    },
                    onDragEnd = {
                        val closeProgress = (fullscreenDragY / minimizeDragDistancePx).coerceIn(0f, 1f)
                        val verticalSwipe = fullscreenDragY > 170f && abs(fullscreenDragY) > abs(fullscreenDragX) * 1.25f
                        if (minimizeDragStarted) {
                            onMinimizeDragEnd?.invoke(verticalSwipe || closeProgress >= 0.45f)
                        } else if (verticalSwipe) {
                            onMinimize()
                        }
                        fullscreenDragX = 0f
                        fullscreenDragY = 0f
                        minimizeDragStarted = false
                    },
                    onDragCancel = {
                        if (minimizeDragStarted) {
                            onMinimizeDragEnd?.invoke(false)
                        }
                        fullscreenDragX = 0f
                        fullscreenDragY = 0f
                        minimizeDragStarted = false
                    }
                )
            },
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FullscreenArtworkBackdrop(
                thumbnailUri = playbackState.currentItem?.thumbnailUri,
                containerColor = containerColor,
                artworkTone = artworkTone,
                modifier = Modifier.matchParentSize()
            )

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
                        onOpenAudioOutput = onOpenAudioOutput,
                        onShare = onShare,
                        onOpenSongDetails = onOpenSongDetails,
                        onEditMetadata = onEditMetadata,
                        onDeleteFromFenlzer = onDeleteFromFenlzer,
                        onOpenQueue = onOpenQueue,
                        onOpenSleepTimer = { showSleepSheet = true },
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight(),
                        showTrackTitle = showTrackTitle,
                        onTrackTitleBoundsChanged = onTrackTitleBoundsChanged
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
                        onOpenAudioOutput = onOpenAudioOutput,
                        onShare = onShare,
                        onOpenSongDetails = onOpenSongDetails,
                        onEditMetadata = onEditMetadata,
                        onDeleteFromFenlzer = onDeleteFromFenlzer,
                        onOpenQueue = onOpenQueue,
                        onOpenSleepTimer = { showSleepSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        showHeader = false,
                        showTrackTitle = showTrackTitle,
                        onTrackTitleBoundsChanged = onTrackTitleBoundsChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenArtworkBackdrop(
    thumbnailUri: Any?,
    containerColor: Color,
    artworkTone: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(containerColor)) {
        if (thumbnailUri != null) {
            AsyncImage(
                model = thumbnailUri,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .blur(34.dp)
                    .graphicsLayer {
                        scaleX = 1.16f
                        scaleY = 1.16f
                        alpha = 0.36f
                    },
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(containerColor.copy(alpha = 0.88f))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            artworkTone.copy(alpha = 0.16f),
                            Color.Transparent,
                            containerColor.copy(alpha = 0.72f)
                        )
                    )
                )
        )
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
    modifier: Modifier = Modifier
) {
    val currentItem = playbackState.currentItem
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragIntent by remember(currentItem?.queueItemId) { mutableStateOf(ArtworkDragIntent.Undecided) }
    var skipHapticSent by remember(currentItem?.queueItemId) { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val swipeThresholdPx = with(density) { ArtworkSwipeThreshold.toPx() }
    val maxVisibleDragPx = with(density) { ArtworkMaxVisibleDrag.toPx() }
    val intentThresholdPx = with(density) { ArtworkIntentThreshold.toPx() }
    val horizontalDrag = if (dragIntent == ArtworkDragIntent.Horizontal) dragX else 0f
    val swipeProgress = (abs(horizontalDrag) / swipeThresholdPx).coerceIn(0f, 1f)
    val animatedDragX by animateFloatAsState(
        targetValue = (horizontalDrag * 0.34f).coerceIn(-maxVisibleDragPx, maxVisibleDragPx),
        animationSpec = tween(durationMillis = FenlzerMotion.GESTURE_SETTLE_MS),
        label = "fullscreenArtworkDragX"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = swipeProgress,
        animationSpec = tween(durationMillis = FenlzerMotion.GESTURE_SETTLE_MS),
        label = "fullscreenArtworkSwipeProgress"
    )
    val committedDirection = when {
        horizontalDrag > 0f -> ArtworkSwipeDirection.Previous
        horizontalDrag < 0f -> ArtworkSwipeDirection.Next
        else -> null
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                translationX = animatedDragX
                rotationZ = animatedDragX / 36f
                scaleX = 1f - animatedProgress * 0.025f
                scaleY = 1f - animatedProgress * 0.025f
            }
            .testTag("fullscreenPlayerArtwork")
            .pointerInput(currentItem?.queueItemId) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                        if (dragIntent == ArtworkDragIntent.Undecided) {
                            val absX = abs(dragX)
                            val absY = abs(dragY)
                            dragIntent = when {
                                absX < intentThresholdPx && absY < intentThresholdPx -> ArtworkDragIntent.Undecided
                                absX > absY * 1.15f -> ArtworkDragIntent.Horizontal
                                absY > absX * 1.15f -> ArtworkDragIntent.Vertical
                                else -> ArtworkDragIntent.Undecided
                            }
                        }
                        if (
                            dragIntent == ArtworkDragIntent.Horizontal &&
                            !skipHapticSent &&
                            abs(dragX) >= swipeThresholdPx
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            skipHapticSent = true
                        } else if (abs(dragX) < swipeThresholdPx * 0.45f) {
                            skipHapticSent = false
                        }
                    },
                    onDragEnd = {
                        if (
                            dragIntent == ArtworkDragIntent.Horizontal &&
                            abs(dragX) >= swipeThresholdPx &&
                            abs(dragX) > abs(dragY) * 1.15f
                        ) {
                            if (dragX > 0f) {
                                onPrevious()
                            } else {
                                onNext()
                            }
                        }
                        dragX = 0f
                        dragY = 0f
                        dragIntent = ArtworkDragIntent.Undecided
                        skipHapticSent = false
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                        dragIntent = ArtworkDragIntent.Undecided
                        skipHapticSent = false
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 18.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
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

            ArtworkSwipeOverlay(
                direction = committedDirection,
                progress = animatedProgress,
                committed = animatedProgress >= 1f
            )
        }
    }
}

@Composable
private fun ArtworkSwipeOverlay(
    direction: ArtworkSwipeDirection?,
    progress: Float,
    committed: Boolean
) {
    if (direction == null || progress <= 0.01f) return

    val primary = MaterialTheme.colorScheme.primary
    val label = if (direction == ArtworkSwipeDirection.Previous) "Previous" else "Next"
    val icon = if (direction == ArtworkSwipeDirection.Previous) {
        Icons.Rounded.SkipPrevious
    } else {
        Icons.Rounded.SkipNext
    }
    val alignment = if (direction == ArtworkSwipeDirection.Previous) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }
    val gradientColors = if (direction == ArtworkSwipeDirection.Previous) {
        listOf(
            primary.copy(alpha = 0.30f * progress),
            primary.copy(alpha = 0.10f * progress),
            Color.Transparent
        )
    } else {
        listOf(
            Color.Transparent,
            primary.copy(alpha = 0.10f * progress),
            primary.copy(alpha = 0.30f * progress)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(gradientColors)),
        contentAlignment = alignment
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.90f + progress * 0.10f
                    scaleY = 0.90f + progress * 0.10f
                },
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            tonalElevation = if (committed) 8.dp else 2.dp,
            shadowElevation = if (committed) 8.dp else 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(if (committed) 30.dp else 26.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onOpenAudioOutput: () -> Unit,
    onShare: () -> Unit,
    onOpenSongDetails: () -> Unit,
    onEditMetadata: () -> Unit,
    onDeleteFromFenlzer: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    showTrackTitle: Boolean = true,
    onTrackTitleBoundsChanged: ((Rect) -> Unit)? = null
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
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    onTrackTitleBoundsChanged?.invoke(coordinates.boundsInRoot())
                }
                .graphicsLayer {
                    alpha = if (showTrackTitle) 1f else 0f
                }
                .basicMarquee()
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
            IconButton(onClick = onOpenAudioOutput, enabled = currentItem != null) {
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
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
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
        PlaybackContext(
            playbackState = playbackState,
            onOpenQueue = onOpenQueue
        )
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
private fun PlaybackContext(
    playbackState: PlaybackUiState,
    onOpenQueue: () -> Unit
) {
    val nextSong = playbackState.queueItems
        .firstOrNull { it.position > (playbackState.currentItem?.position ?: Int.MAX_VALUE) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fullscreenPlaybackContext")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Up next",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = playbackState.sourceLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (playbackState.isModified) {
                    PlaybackChip(text = "Modified", testTag = "fullscreenQueueModifiedIndicator")
                }
                IconButton(
                    onClick = onOpenQueue,
                    enabled = playbackState.queueItems.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Edit queue"
                    )
                }
            }

            if (nextSong == null) {
                ContextLine(label = "Next song", value = "End of queue")
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (nextSong.thumbnailUri != null) {
                            AsyncImage(
                                model = nextSong.thumbnailUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(imageVector = Icons.Rounded.MusicNote, contentDescription = null)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nextSong.displayTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = nextSong.artist.ifBlank { "Unknown artist" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = nextSong.durationMs.formatDuration(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (playbackState.sleepTimerState.active) {
                ContextLine(
                    label = "Sleep timer",
                    value = playbackState.sleepTimerState.displayText()
                )
            }
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

private val FullscreenMinimizeDragDistance = 560.dp
private val ArtworkSwipeThreshold = 72.dp
private val ArtworkMaxVisibleDrag = 56.dp
private val ArtworkIntentThreshold = 10.dp

private enum class ArtworkDragIntent {
    Undecided,
    Horizontal,
    Vertical
}

private enum class ArtworkSwipeDirection {
    Previous,
    Next
}
