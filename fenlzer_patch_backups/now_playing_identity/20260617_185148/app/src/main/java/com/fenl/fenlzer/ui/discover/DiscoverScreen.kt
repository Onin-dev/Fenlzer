package com.fenl.fenlzer.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.fenl.fenlzer.data.repository.DiscoverUiItem
import com.fenl.fenlzer.data.repository.DiscoverUiState
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale

@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    isRefreshing: Boolean,
    message: String?,
    preparingRemoteItemId: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onRefreshBroader: () -> Unit,
    onPlay: (String) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    onImport: (String) -> Unit,
    onFavourite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 1040.dp)
            .testTag("discoverScreen")
    ) {
        DiscoverToolbar(
            state = state,
            isRefreshing = isRefreshing,
            onBack = onBack,
            onRefresh = onRefresh,
            onRefreshBroader = onRefreshBroader
        )
        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (state.items.isEmpty()) {
            EmptyDiscover(
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.remoteItemId }) { item ->
                    DiscoverRow(
                        item = item,
                        isRefreshing = isRefreshing,
                        isPreparing = preparingRemoteItemId == item.remoteItemId ||
                            item.streamState == "GETTING_STREAM",
                        onPlay = { onPlay(item.remoteItemId) },
                        onPlayNext = { onPlayNext(item.remoteItemId) },
                        onAddToQueue = { onAddToQueue(item.remoteItemId) },
                        onAddToPlaylist = { onAddToPlaylist(item.remoteItemId, item.title) },
                        onImport = { onImport(item.remoteItemId) },
                        onFavourite = { onFavourite(item.remoteItemId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverToolbar(
    state: DiscoverUiState,
    isRefreshing: Boolean,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onRefreshBroader: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Discover",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.generatedAt?.let {
                    "${state.finalDisplayedCount} songs - ${state.refreshType.orEmpty()} - ${it.formatDateTime()}"
                } ?: "API recommendations from your listening history",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Refresh Discover")
        }
        if (state.showBroaderRefresh) {
            OutlinedButton(onClick = onRefreshBroader, enabled = !isRefreshing) {
                Text(text = "Broader")
            }
        }
    }
}

@Composable
private fun EmptyDiscover(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "No Discover songs yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
            Text(text = "Refresh", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun DiscoverRow(
    item: DiscoverUiItem,
    isRefreshing: Boolean,
    isPreparing: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onImport: () -> Unit,
    onFavourite: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val enabled = !isRefreshing && !isPreparing
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.canStream && enabled,
                onClick = onPlay
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(MaterialTheme.shapes.small)
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
                    Icon(imageVector = Icons.Rounded.MusicNote, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        item.artistOrChannel.ifBlank { null },
                        item.durationMs.takeIf { it > 0L }?.formatDuration()
                    ).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isPreparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            IconButton(onClick = onImport, enabled = item.canDownload && enabled) {
                Icon(imageVector = Icons.Rounded.Download, contentDescription = "Import remote song")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                    Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "Song actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Play Next") },
                        leadingIcon = {
                            Icon(imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                        },
                        enabled = item.canStream,
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Add to Queue") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Queue, contentDescription = null) },
                        enabled = item.canStream,
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Add to Playlist") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = null
                            )
                        },
                        enabled = item.canDownload,
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Favourite") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Favorite, contentDescription = null) },
                        enabled = item.canDownload,
                        onClick = {
                            menuExpanded = false
                            onFavourite()
                        }
                    )
                }
            }
        }
    }
}

private fun String.labelForDisplay(): String =
    lowercase(Locale.US)
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.US) }

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun Long.formatDateTime(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))
