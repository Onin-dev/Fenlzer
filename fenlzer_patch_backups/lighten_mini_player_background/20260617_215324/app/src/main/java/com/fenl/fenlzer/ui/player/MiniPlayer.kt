package com.fenl.fenlzer.ui.player

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.fenl.fenlzer.playback.PlaybackUiState
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    playbackState: PlaybackUiState,
    privateModeEnabled: Boolean,
    onMainAreaClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenSongDetails: () -> Unit,
    onEditMetadata: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenDragStart: (() -> Unit)? = null,
    onOpenDragProgress: ((Float) -> Unit)? = null,
    onOpenDragEnd: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentItem = playbackState.currentItem
    if (currentItem == null) {
        EmptyMiniPlayer(
            privateModeEnabled = privateModeEnabled,
            onMainAreaClick = onMainAreaClick,
            modifier = modifier
        )
        return
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember(playbackState.currentItem?.queueItemId) {
        mutableFloatStateOf(playbackState.playbackPositionMs.toFloat())
    }
    var dragX by remember(currentItem.queueItemId) { mutableFloatStateOf(0f) }
    var dragY by remember(currentItem.queueItemId) { mutableFloatStateOf(0f) }
    var swipeHapticSent by remember(currentItem.queueItemId) { mutableStateOf(false) }
    var openDragStarted by remember(currentItem.queueItemId) { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val swipeThresholdPx = with(LocalDensity.current) { MiniSwipeThreshold.toPx() }
    val verticalThresholdPx = with(LocalDensity.current) { MiniVerticalThreshold.toPx() }
    val verticalOpenDistancePx = with(LocalDensity.current) { MiniVerticalOpenDistance.toPx() }
    val maxVisibleDragPx = with(LocalDensity.current) { MiniMaxVisibleDrag.toPx() }

    val duration = playbackState.durationMs.takeIf { it > 0L } ?: currentItem.durationMs
    val displayedPosition = if (isSeeking) seekValue.toLong() else playbackState.playbackPositionMs
    val progress = if (duration > 0L) {
        displayedPosition.coerceIn(0L, duration).toFloat() / duration.toFloat()
    } else {
        0f
    }
    val backgroundColor = rememberAverageThumbnailColor(currentItem.thumbnailUri)
    val surfaceColor = backgroundColor.darkPlaybackTone()
    val dragProgress = (abs(dragX) / swipeThresholdPx).coerceIn(0f, 1f)
    val trackText = MiniTrackText(
        queueItemId = currentItem.queueItemId,
        title = currentItem.displayTitle,
        artist = currentItem.artist.ifBlank { "Unknown artist" },
        privateModeEnabled = privateModeEnabled
    )
    val animatedDragX by animateFloatAsState(
        targetValue = (dragX * 0.72f).coerceIn(-maxVisibleDragPx, maxVisibleDragPx),
        animationSpec = tween(durationMillis = FenlzerMotion.GESTURE_SETTLE_MS),
        label = "miniPlayerDragX"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("miniPlayer")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlayerCardHeight)
                .graphicsLayer {
                    translationX = animatedDragX
                    scaleX = 1f - dragProgress * 0.02f
                    scaleY = 1f - dragProgress * 0.02f
                }
                .pointerInput(currentItem.queueItemId) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                            val isCommittedHorizontalSwipe =
                                abs(dragX) >= swipeThresholdPx &&
                                    abs(dragX) > abs(dragY) * 1.05f
                            val isOpeningDrag =
                                openDragStarted ||
                                    (
                                        dragY < -12f &&
                                            abs(dragY) > abs(dragX) * 1.15f &&
                                            onOpenDragStart != null &&
                                            onOpenDragProgress != null
                                        )
                            if (isOpeningDrag) {
                                if (!openDragStarted) {
                                    onOpenDragStart?.invoke()
                                    openDragStarted = true
                                }
                                onOpenDragProgress?.invoke(
                                    (-dragY / verticalOpenDistancePx).coerceIn(0f, 0.96f)
                                )
                            }
                            if (isCommittedHorizontalSwipe && !swipeHapticSent) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                swipeHapticSent = true
                            } else if (!isCommittedHorizontalSwipe && abs(dragX) < swipeThresholdPx * 0.45f) {
                                swipeHapticSent = false
                            }
                        },
                        onDragEnd = {
                            val verticalOpenProgress = (-dragY / verticalOpenDistancePx).coerceIn(0f, 1f)
                            val shouldOpenFromDrag =
                                dragY < -verticalThresholdPx &&
                                    abs(dragY) > abs(dragX) * 1.15f &&
                                    verticalOpenProgress >= 0.45f
                            when {
                                openDragStarted -> onOpenDragEnd?.invoke(
                                    shouldOpenFromDrag || verticalOpenProgress >= 0.50f
                                )

                                shouldOpenFromDrag -> onMainAreaClick()

                                dragX > swipeThresholdPx &&
                                    abs(dragX) > abs(dragY) * 1.05f -> onPrevious()

                                dragX < -swipeThresholdPx &&
                                    abs(dragX) > abs(dragY) * 1.05f -> onNext()
                            }
                            dragX = 0f
                            dragY = 0f
                            swipeHapticSent = false
                            openDragStarted = false
                        },
                        onDragCancel = {
                            if (openDragStarted) {
                                onOpenDragEnd?.invoke(false)
                            }
                            dragX = 0f
                            dragY = 0f
                            swipeHapticSent = false
                            openDragStarted = false
                        }
                    )
                },
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor
        ) {
            Box {
                if (currentItem.thumbnailUri != null) {
                    AsyncImage(
                        model = currentItem.thumbnailUri,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .blur(22.dp)
                            .graphicsLayer {
                                scaleX = 1.18f
                                scaleY = 1.18f
                                alpha = 0.72f
                            },
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(surfaceColor.copy(alpha = if (currentItem.thumbnailUri == null) 1f else 0.76f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.36f))
                        .align(Alignment.CenterStart)
                )

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(66.dp)
                            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play"
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("miniPlayerMainArea")
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onMainAreaClick)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedContent(
                                targetState = trackText,
                                transitionSpec = {
                                    (slideInVertically(
                                        animationSpec = tween(FenlzerMotion.MINI_TRACK_ENTER_MS),
                                        initialOffsetY = { it / 2 }
                                    ) + fadeIn(animationSpec = tween(FenlzerMotion.MINI_TRACK_ENTER_MS)))
                                        .togetherWith(
                                            slideOutVertically(
                                                animationSpec = tween(FenlzerMotion.MINI_TRACK_EXIT_MS),
                                                targetOffsetY = { -it / 2 }
                                            ) + fadeOut(animationSpec = tween(FenlzerMotion.MINI_TRACK_EXIT_MS))
                                        )
                                        .using(SizeTransform(clip = false))
                                },
                                label = "miniPlayerTrackText"
                            ) { animatedText ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = animatedText.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier.basicMarquee()
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = animatedText.artist,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .basicMarquee()
                                        )
                                        if (animatedText.privateModeEnabled) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            MiniStatusBadge(
                                                text = "Private",
                                                testTag = "miniPlayerPrivateModeIndicator"
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(onClick = onToggleFavourite) {
                            Icon(
                                imageVector = if (currentItem.isFavourite) {
                                    Icons.Rounded.Favorite
                                } else {
                                    Icons.Rounded.FavoriteBorder
                                },
                                contentDescription = "Favourite"
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = "Open Queue") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenQueue()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(text = "Add to Playlist") },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToPlaylist()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(text = "Song Details") },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenSongDetails()
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
                                    text = { Text(text = "Sleep Timer") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Snooze,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenSleepTimer()
                                    }
                                )
                            }
                        }
                    }

                    if (duration > 0L) {
                        Slider(
                            value = displayedPosition.coerceIn(0L, duration).toFloat(),
                            onValueChange = { value ->
                                isSeeking = true
                                seekValue = value
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                onSeekTo(seekValue.toLong())
                            },
                            valueRange = 0f..duration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Transparent,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .testTag("miniPlayerSeekbar")
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                                .testTag("miniPlayerInactiveProgress")
                        )
                    }
                }

                SwipeCue(
                    dragX = dragX,
                    swipeThresholdPx = swipeThresholdPx,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

@Composable
private fun SwipeCue(
    dragX: Float,
    swipeThresholdPx: Float,
    modifier: Modifier = Modifier
) {
    val progress = (abs(dragX) / swipeThresholdPx).coerceIn(0f, 1f)
    if (progress <= 0.02f) return

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f * progress)),
        contentAlignment = if (dragX > 0f) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Icon(
            imageVector = if (dragX > 0f) Icons.Rounded.SkipPrevious else Icons.Rounded.SkipNext,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(28.dp)
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.78f + progress * 0.22f
                    scaleY = 0.78f + progress * 0.22f
                },
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MiniStatusBadge(
    text: String,
    testTag: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .testTag(testTag),
        maxLines = 1
    )
}

@Composable
private fun rememberAverageThumbnailColor(thumbnailUri: Uri?): Color {
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.surface
    var sampledColor by remember(thumbnailUri) { mutableStateOf<Color?>(null) }

    LaunchedEffect(thumbnailUri) {
        sampledColor = null
        if (thumbnailUri != null) {
            sampledColor = sampleAverageColor(context, thumbnailUri)
        }
    }

    return sampledColor ?: fallback
}

private suspend fun sampleAverageColor(context: Context, uri: Uri): Color? = withContext(Dispatchers.IO) {
    runCatching {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(uri)
                .size(32, 32)
                .build()
        )
        val image = (result as? SuccessResult)?.image ?: return@runCatching null
        val bitmap = image.toBitmap(32, 32)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L

        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                red += AndroidColor.red(pixel)
                green += AndroidColor.green(pixel)
                blue += AndroidColor.blue(pixel)
                count++
            }
        }

        if (count == 0L) {
            null
        } else {
            Color(
                red = (red / count).toInt().coerceIn(0, 255) / 255f,
                green = (green / count).toInt().coerceIn(0, 255) / 255f,
                blue = (blue / count).toInt().coerceIn(0, 255) / 255f,
                alpha = 1f
            )
        }
    }.getOrNull()
}

private fun Color.darkPlaybackTone(): Color =
    Color(
        red = (red * 0.34f).coerceIn(0f, 1f),
        green = (green * 0.34f).coerceIn(0f, 1f),
        blue = (blue * 0.34f).coerceIn(0f, 1f),
        alpha = 1f
    )

private data class MiniTrackText(
    val queueItemId: String,
    val title: String,
    val artist: String,
    val privateModeEnabled: Boolean
)

private val MiniPlayerCardHeight = 84.dp
private val MiniSwipeThreshold = 48.dp
private val MiniVerticalThreshold = 86.dp
private val MiniVerticalOpenDistance = 520.dp
private val MiniMaxVisibleDrag = 92.dp
