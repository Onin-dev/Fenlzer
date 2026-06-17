package com.fenl.fenlzer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fenl.fenlzer.playback.PlaybackUiState
import kotlin.math.roundToInt

@Composable
fun PlayerMorphOverlay(
    playbackState: PlaybackUiState,
    privateModeEnabled: Boolean,
    sleepTimerDefaultMinutes: Int,
    expanded: Boolean,
    manualProgress: Float? = null,
    miniPlayerBounds: Rect?,
    onCollapsed: () -> Unit,
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
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val miniSurfaceColor = rememberAverageArtworkColor(
        thumbnailUri = playbackState.currentItem?.thumbnailUri,
        fallback = MaterialTheme.colorScheme.surface
    ).miniPlayerTone()
    val fullscreenBackgroundColor = MaterialTheme.colorScheme.background

    LaunchedEffect(manualProgress) {
        manualProgress?.let { progress.snapTo(it.coerceIn(0f, 0.98f)) }
    }

    LaunchedEffect(expanded, manualProgress) {
        if (manualProgress != null) return@LaunchedEffect
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (expanded) {
                    FenlzerMotion.PLAYER_EXPAND_MS
                } else {
                    FenlzerMotion.PLAYER_COLLAPSE_MS
                },
                easing = FenlzerMotion.PlayerEasing
            )
        )
        if (!expanded) {
            onCollapsed()
        }
    }

    BackHandler(enabled = expanded) {
        onMinimize()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val screenWidthDp = maxWidth
        val screenHeightDp = maxHeight
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val fallbackHeight = with(density) { 84.dp.toPx() }
        val fallbackInset = with(density) { 12.dp.toPx() }
        val fallbackBottomInset = with(density) { 92.dp.toPx() }
        val collapsedHorizontalInset = with(density) { 12.dp.toPx() }
        val collapsedVerticalInset = with(density) { 6.dp.toPx() }
        val collapsed = miniPlayerBounds
            ?.let { bounds ->
                Rect(
                    left = bounds.left + collapsedHorizontalInset,
                    top = bounds.top + collapsedVerticalInset,
                    right = bounds.right - collapsedHorizontalInset,
                    bottom = bounds.bottom - collapsedVerticalInset
                )
            }
            ?.takeIf { bounds -> bounds.width > 0f && bounds.height > 0f }
            ?: Rect(
                left = fallbackInset,
                top = (screenHeightPx - fallbackBottomInset - fallbackHeight).coerceAtLeast(0f),
                right = (screenWidthPx - fallbackInset).coerceAtLeast(fallbackInset),
                bottom = (screenHeightPx - fallbackBottomInset).coerceAtLeast(fallbackHeight)
            )

        val p = progress.value.coerceIn(0f, 1f)
        val left = lerp(collapsed.left, 0f, p)
        val top = lerp(collapsed.top, 0f, p)
        val right = lerp(collapsed.right, screenWidthPx, p)
        val bottom = lerp(collapsed.bottom, screenHeightPx, p)
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)
        val cornerRadius = lerp(with(density) { 16.dp.toPx() }, 0f, p)
        val miniAlpha = 1f - ((p - 0.04f) / 0.16f).coerceIn(0f, 1f)
        val expandedAlpha = ((p - 0.05f) / 0.85f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = left.roundToInt(),
                        y = top.roundToInt()
                    )
                }
                .width(with(density) { width.toDp() })
                .height(with(density) { height.toDp() })
                .clip(RoundedCornerShape(with(density) { cornerRadius.toDp() }))
        ) {
            MorphTransitionBackground(
                thumbnailUri = playbackState.currentItem?.thumbnailUri,
                miniSurfaceColor = miniSurfaceColor,
                fullscreenBackgroundColor = fullscreenBackgroundColor,
                progress = p,
                modifier = Modifier.matchParentSize()
            )

            MiniPlayerMorphContent(
                playbackState = playbackState,
                privateModeEnabled = privateModeEnabled,
                onPlayPause = onPlayPause,
                onToggleFavourite = onToggleFavourite,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = miniAlpha }
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (-left).roundToInt(),
                            y = (-top).roundToInt()
                        )
                    }
                    .width(screenWidthDp)
                    .height(screenHeightDp)
                    .graphicsLayer { alpha = expandedAlpha }
            ) {
                FullscreenPlayer(
                    playbackState = playbackState,
                    privateModeEnabled = privateModeEnabled,
                    sleepTimerDefaultMinutes = sleepTimerDefaultMinutes,
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
                    onStartSleepTimerDuration = onStartSleepTimerDuration,
                    onStartSleepTimerEndOfSong = onStartSleepTimerEndOfSong,
                    onStartSleepTimerEndOfQueue = onStartSleepTimerEndOfQueue,
                    onCancelSleepTimer = onCancelSleepTimer,
                    onMinimizeDragStart = onMinimizeDragStart,
                    onMinimizeDragProgress = onMinimizeDragProgress,
                    onMinimizeDragEnd = onMinimizeDragEnd
                )
            }
        }
    }
}

@Composable
private fun MorphTransitionBackground(
    thumbnailUri: Any?,
    miniSurfaceColor: Color,
    fullscreenBackgroundColor: Color,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val p = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier.background(miniSurfaceColor)
    ) {
        if (thumbnailUri != null) {
            AsyncImage(
                model = thumbnailUri,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .blur(22.dp)
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                        alpha = 0.72f * (1f - p)
                    },
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(miniSurfaceColor.copy(alpha = 0.76f * (1f - p)))
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(fullscreenBackgroundColor.copy(alpha = p))
        )
    }
}

@Composable
private fun MiniPlayerMorphContent(
    playbackState: PlaybackUiState,
    privateModeEnabled: Boolean,
    onPlayPause: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentItem = playbackState.currentItem
    val duration = playbackState.durationMs.takeIf { it > 0L } ?: currentItem?.durationMs ?: 0L
    val progress = if (duration > 0L) {
        playbackState.playbackPositionMs.coerceIn(0L, duration).toFloat() / duration.toFloat()
    } else {
        0f
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.36f))
                .align(Alignment.CenterStart)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
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
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentItem?.displayTitle ?: "Import songs to start listening",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentItem?.artist?.ifBlank { "Unknown artist" } ?: "Offline library",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (privateModeEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Private",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            maxLines = 1
                        )
                    }
                }
            }
            IconButton(onClick = onToggleFavourite, enabled = currentItem != null) {
                Icon(
                    imageVector = if (currentItem?.isFavourite == true) {
                        Icons.Rounded.Favorite
                    } else {
                        Icons.Rounded.FavoriteBorder
                    },
                    contentDescription = "Favourite",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun Color.miniPlayerTone(): Color =
    Color(
        red = (red * 0.34f).coerceIn(0f, 1f),
        green = (green * 0.34f).coerceIn(0f, 1f),
        blue = (blue * 0.34f).coerceIn(0f, 1f),
        alpha = 1f
    )
