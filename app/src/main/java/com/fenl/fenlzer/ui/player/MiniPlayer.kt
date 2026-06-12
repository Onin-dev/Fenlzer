package com.fenl.fenlzer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fenl.fenlzer.playback.PlaybackUiState
import com.fenl.fenlzer.ui.theme.Dimensions
import androidx.compose.ui.layout.ContentScale

@Composable
fun MiniPlayer(
    playbackState: PlaybackUiState,
    privateModeEnabled: Boolean,
    onMainAreaClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenSongDetails: () -> Unit,
    onEditMetadata: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
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
    val duration = playbackState.durationMs.takeIf { it > 0L } ?: currentItem.durationMs
    val displayedPosition = if (isSeeking) seekValue.toLong() else playbackState.playbackPositionMs

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("miniPlayer"),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("miniPlayerMainArea")
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onMainAreaClick)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimensions.MINI_PLAYER_THUMBNAIL)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentItem.thumbnailUri != null) {
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentItem.displayTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentItem.artist,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (privateModeEnabled) {
                                Spacer(modifier = Modifier.width(6.dp))
                                MiniStatusBadge(text = "Private", testTag = "miniPlayerPrivateModeIndicator")
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
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = "Next")
                }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Open Queue") },
                        leadingIcon = { Icon(imageVector = Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
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
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Snooze, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenSleepTimer()
                        }
                    )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .padding(horizontal = 0.dp)
                        .testTag("miniPlayerSeekbar")
                )
            } else {
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .testTag("miniPlayerInactiveProgress")
                )
            }
        }
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
