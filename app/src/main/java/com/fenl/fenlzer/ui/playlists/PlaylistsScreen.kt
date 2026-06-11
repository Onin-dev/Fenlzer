package com.fenl.fenlzer.ui.playlists

import android.net.Uri
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fenl.fenlzer.data.repository.LibraryTrack
import com.fenl.fenlzer.data.repository.PlaylistTrackItem
import com.fenl.fenlzer.data.repository.QueueListEditor
import com.fenl.fenlzer.data.repository.RegularPlaylistDetail
import com.fenl.fenlzer.data.repository.RegularPlaylistSummary
import com.fenl.fenlzer.data.repository.DiscoverUiState
import com.fenl.fenlzer.data.repository.SmartPlaylistDetail
import com.fenl.fenlzer.data.repository.SmartPlaylistIds
import com.fenl.fenlzer.data.repository.SmartPlaylistSummary
import com.fenl.fenlzer.domain.text.SearchNormalizer
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlaylistsScreen(
    regularPlaylists: List<RegularPlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary>,
    selectedRegularPlaylist: RegularPlaylistDetail?,
    selectedSmartPlaylist: SmartPlaylistDetail?,
    discoverState: DiscoverUiState,
    discoverContent: (@Composable () -> Unit)?,
    libraryTracks: List<LibraryTrack>,
    onOpenRegularPlaylist: (String) -> Unit,
    onOpenSmartPlaylist: (String) -> Unit,
    onBackToList: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRequestCustomThumbnail: (String) -> Unit,
    onClearCustomThumbnail: (String) -> Unit,
    onAddTrackToPlaylist: (String, String) -> Unit,
    onRemoveTrackFromPlaylist: (String, String) -> Unit,
    onReorderPlaylist: (String, List<String>) -> Unit,
    onSaveSmartPlaylist: (String, List<String>) -> Unit,
    onPlayTrackList: (
        trackIds: List<String>,
        startTrackId: String?,
        sourceType: String,
        sourceId: String?,
        sourceLabel: String,
        insertedBy: String,
        shuffle: Boolean
    ) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    onToggleFavourite: (String, Boolean) -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreateDialog) {
        TextInputDialog(
            title = "Create Playlist",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }

    when {
        selectedRegularPlaylist != null -> RegularPlaylistDetailView(
            playlist = selectedRegularPlaylist,
            libraryTracks = libraryTracks,
            onBack = onBackToList,
            onRenamePlaylist = onRenamePlaylist,
            onDeletePlaylist = onDeletePlaylist,
            onRequestCustomThumbnail = onRequestCustomThumbnail,
            onClearCustomThumbnail = onClearCustomThumbnail,
            onAddTrackToPlaylist = onAddTrackToPlaylist,
            onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylist,
            onReorderPlaylist = onReorderPlaylist,
            onPlayTrackList = onPlayTrackList,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onToggleFavourite = onToggleFavourite,
            onAddToPlaylist = onAddToPlaylist,
            modifier = modifier
        )

        selectedSmartPlaylist?.smartPlaylistId == SmartPlaylistIds.DISCOVER &&
            discoverContent != null -> discoverContent()

        selectedSmartPlaylist != null -> {
            SmartPlaylistDetailView(
                playlist = selectedSmartPlaylist,
                onBack = onBackToList,
                onSaveSmartPlaylist = onSaveSmartPlaylist,
                onPlayTrackList = onPlayTrackList,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onToggleFavourite = onToggleFavourite,
                onAddToPlaylist = onAddToPlaylist,
                modifier = modifier
            )
        }

        else -> PlaylistOverview(
            regularPlaylists = regularPlaylists,
            smartPlaylists = smartPlaylists,
            discoverState = discoverState,
            onOpenRegularPlaylist = onOpenRegularPlaylist,
            onOpenSmartPlaylist = onOpenSmartPlaylist,
            onCreatePlaylist = { showCreateDialog = true },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistOverview(
    regularPlaylists: List<RegularPlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary>,
    discoverState: DiscoverUiState,
    onOpenRegularPlaylist: (String) -> Unit,
    onOpenSmartPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardWidth = if (isLandscape) 220.dp else Dp.Unspecified

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (isLandscape) 14.dp else 16.dp,
                vertical = if (isLandscape) 10.dp else 14.dp
            )
            .testTag("playlistsScreen"),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onCreatePlaylist) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                Text(text = "Create", modifier = Modifier.padding(start = 8.dp))
            }
        }

        SectionHeader(title = "Smart Playlists")
        if (isLandscape) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                smartPlaylists.forEach { playlist ->
                    val card = playlist.discoverAwareCard(discoverState)
                    PlaylistSummaryCard(
                        name = card.name,
                        subtitle = card.subtitle,
                        songCount = card.songCount,
                        durationMs = card.durationMs,
                        modifiedAt = null,
                        thumbnailUris = card.thumbnailUris,
                        onClick = { onOpenSmartPlaylist(playlist.smartPlaylistId) },
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(smartPlaylists, key = { it.smartPlaylistId }) { playlist ->
                    val card = playlist.discoverAwareCard(discoverState)
                    PlaylistSummaryCard(
                        name = card.name,
                        subtitle = card.subtitle,
                        songCount = card.songCount,
                        durationMs = card.durationMs,
                        modifiedAt = null,
                        thumbnailUris = card.thumbnailUris,
                        onClick = { onOpenSmartPlaylist(playlist.smartPlaylistId) },
                        modifier = Modifier.width(218.dp)
                    )
                }
            }
        }

        SectionHeader(title = "Regular Playlists")
        if (regularPlaylists.isEmpty()) {
            EmptyRegularPlaylistState(onCreatePlaylist = onCreatePlaylist)
        } else if (isLandscape) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                regularPlaylists.forEach { playlist ->
                    PlaylistSummaryCard(
                        name = playlist.name,
                        subtitle = if (playlist.hasCustomThumbnail) "Custom cover" else "Generated cover",
                        songCount = playlist.songCount,
                        durationMs = playlist.totalDurationMs,
                        modifiedAt = playlist.modifiedAt,
                        thumbnailUris = playlist.thumbnailUris,
                        onClick = { onOpenRegularPlaylist(playlist.playlistId) },
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                regularPlaylists.forEach { playlist ->
                    PlaylistSummaryCard(
                        name = playlist.name,
                        subtitle = if (playlist.hasCustomThumbnail) "Custom cover" else "Generated cover",
                        songCount = playlist.songCount,
                        durationMs = playlist.totalDurationMs,
                        modifiedAt = playlist.modifiedAt,
                        thumbnailUris = playlist.thumbnailUris,
                        onClick = { onOpenRegularPlaylist(playlist.playlistId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private data class SmartPlaylistCardModel(
    val name: String,
    val subtitle: String,
    val songCount: Int,
    val durationMs: Long,
    val thumbnailUris: List<Uri?>
)

private fun SmartPlaylistSummary.discoverAwareCard(
    discoverState: DiscoverUiState
): SmartPlaylistCardModel {
    if (smartPlaylistId != SmartPlaylistIds.DISCOVER) {
        return SmartPlaylistCardModel(
            name = name,
            subtitle = description,
            songCount = songCount,
            durationMs = totalDurationMs,
            thumbnailUris = thumbnailUris
        )
    }

    return SmartPlaylistCardModel(
        name = name,
        subtitle = discoverState.generatedAt?.let {
            "Remote recommendations refreshed ${it.formatDate()}"
        } ?: "Remote recommendations from your listening history.",
        songCount = discoverState.items.size,
        durationMs = discoverState.items.sumOf { it.durationMs },
        thumbnailUris = discoverState.items
            .mapNotNull { item -> item.thumbnailUrl?.let { url -> runCatching { Uri.parse(url) }.getOrNull() } }
            .take(4)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegularPlaylistDetailView(
    playlist: RegularPlaylistDetail,
    libraryTracks: List<LibraryTrack>,
    onBack: () -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRequestCustomThumbnail: (String) -> Unit,
    onClearCustomThumbnail: (String) -> Unit,
    onAddTrackToPlaylist: (String, String) -> Unit,
    onRemoveTrackFromPlaylist: (String, String) -> Unit,
    onReorderPlaylist: (String, List<String>) -> Unit,
    onPlayTrackList: (
        trackIds: List<String>,
        startTrackId: String?,
        sourceType: String,
        sourceId: String?,
        sourceLabel: String,
        insertedBy: String,
        shuffle: Boolean
    ) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    onToggleFavourite: (String, Boolean) -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable(playlist.playlistId) { mutableStateOf("") }
    var sort by rememberSaveable(playlist.playlistId) { mutableStateOf(PlaylistDetailSort.MANUAL) }
    var showAddSongsSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val visibleTracks = remember(playlist.tracks, searchQuery, sort) {
        playlist.tracks.visibleTracks(searchQuery, sort)
    }
    val canReorder = searchQuery.isBlank() && sort == PlaylistDetailSort.MANUAL
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (showAddSongsSheet) {
        PlaylistAddSongsSheet(
            playlist = playlist,
            libraryTracks = libraryTracks,
            onToggleTrack = { track ->
                if (playlist.tracks.any { it.trackId == track.trackId }) {
                    onRemoveTrackFromPlaylist(playlist.playlistId, track.trackId)
                } else {
                    onAddTrackToPlaylist(playlist.playlistId, track.trackId)
                }
            },
            onDismiss = { showAddSongsSheet = false }
        )
    }

    if (showRenameDialog) {
        TextInputDialog(
            title = "Rename Playlist",
            initialValue = playlist.name,
            confirmLabel = "Rename",
            onDismiss = { showRenameDialog = false },
            onConfirm = { name ->
                onRenamePlaylist(playlist.playlistId, name)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        ConfirmActionDialog(
            title = "Delete Playlist?",
            message = "This keeps the songs and only removes the playlist.",
            confirmLabel = "Delete",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDeletePlaylist(playlist.playlistId)
                showDeleteDialog = false
                onBack()
            }
        )
    }

    val header: @Composable () -> Unit = {
        DetailTopRow(
            title = playlist.name,
            onBack = onBack,
            menu = {
                PlaylistActionsMenu(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    hasCustomThumbnail = playlist.hasCustomThumbnail,
                    onRename = { showRenameDialog = true },
                    onRequestCustomThumbnail = { onRequestCustomThumbnail(playlist.playlistId) },
                    onClearCustomThumbnail = { onClearCustomThumbnail(playlist.playlistId) },
                    onDelete = { showDeleteDialog = true }
                )
            }
        )
        PlaylistDetailSummary(
            thumbnailUris = playlist.thumbnailUris,
            songCount = playlist.songCount,
            durationMs = playlist.totalDurationMs,
            extra = "Modified ${playlist.modifiedAt.formatDate()}"
        )
        PlaylistActionRow(
            enabled = visibleTracks.isNotEmpty(),
            onPlayAll = {
                playRegularPlaylist(
                    playlist = playlist,
                    tracks = visibleTracks,
                    startTrackId = null,
                    shuffle = false,
                    onPlayTrackList = onPlayTrackList
                )
            },
            onShuffle = {
                playRegularPlaylist(
                    playlist = playlist,
                    tracks = visibleTracks,
                    startTrackId = null,
                    shuffle = true,
                    onPlayTrackList = onPlayTrackList
                )
            },
            secondary = {
                OutlinedButton(onClick = { showAddSongsSheet = true }) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                    Text(text = "Add", modifier = Modifier.padding(start = 8.dp))
                }
            }
        )
        if (!canReorder) {
            Text(
                text = "Reorder is available in manual order with no search.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    val tracks: @Composable (Modifier) -> Unit = { tracksModifier ->
        Column(
            modifier = tracksModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaylistSearchAndSortRow(
                searchQuery = searchQuery,
                onSearchChanged = { searchQuery = it },
                sort = sort,
                onSortChanged = { sort = it }
            )
            PlaylistTrackList(
                tracks = visibleTracks,
                emptyText = if (playlist.tracks.isEmpty()) {
                    "No songs in this playlist"
                } else {
                    "No matching songs"
                },
                canReorder = canReorder,
                sourceTracks = playlist.tracks,
                showRemove = true,
                onTrackClick = { track ->
                    playRegularPlaylist(
                        playlist = playlist,
                        tracks = visibleTracks,
                        startTrackId = track.trackId,
                        shuffle = false,
                        onPlayTrackList = onPlayTrackList
                    )
                },
                onMove = { trackId, offset ->
                    val reordered = playlist.tracks.move(trackId, offset)
                    onReorderPlaylist(playlist.playlistId, reordered.map { it.trackId })
                },
                onRemove = { trackId -> onRemoveTrackFromPlaylist(playlist.playlistId, trackId) },
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onToggleFavourite = onToggleFavourite,
                onAddToPlaylist = onAddToPlaylist,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("regularPlaylistDetail"),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 236.dp, max = 310.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                header()
            }
            tracks(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("regularPlaylistDetail"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            header()
            tracks(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmartPlaylistDetailView(
    playlist: SmartPlaylistDetail,
    onBack: () -> Unit,
    onSaveSmartPlaylist: (String, List<String>) -> Unit,
    onPlayTrackList: (
        trackIds: List<String>,
        startTrackId: String?,
        sourceType: String,
        sourceId: String?,
        sourceLabel: String,
        insertedBy: String,
        shuffle: Boolean
    ) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    onToggleFavourite: (String, Boolean) -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable(playlist.smartPlaylistId) { mutableStateOf("") }
    var sort by rememberSaveable(playlist.smartPlaylistId) { mutableStateOf(PlaylistDetailSort.MANUAL) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    val visibleTracks = remember(playlist.tracks, searchQuery, sort) {
        playlist.tracks.visibleTracks(searchQuery, sort)
    }
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (showSaveDialog) {
        TextInputDialog(
            title = "Save Smart Playlist",
            initialValue = "${playlist.name} Snapshot",
            confirmLabel = "Save",
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                onSaveSmartPlaylist(name, visibleTracks.map { it.trackId })
                showSaveDialog = false
            }
        )
    }

    val header: @Composable () -> Unit = {
        DetailTopRow(
            title = playlist.name,
            onBack = onBack,
            menu = {
                AssistChip(
                    onClick = { showSaveDialog = true },
                    enabled = visibleTracks.isNotEmpty(),
                    label = { Text(text = "Save") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = null)
                    }
                )
            }
        )
        PlaylistDetailSummary(
            thumbnailUris = playlist.tracks.take(4).map { it.thumbnailUri },
            songCount = playlist.songCount,
            durationMs = playlist.totalDurationMs,
            extra = playlist.description
        )
        PlaylistActionRow(
            enabled = visibleTracks.isNotEmpty(),
            onPlayAll = {
                playSmartPlaylist(
                    playlist = playlist,
                    tracks = visibleTracks,
                    startTrackId = null,
                    shuffle = false,
                    onPlayTrackList = onPlayTrackList
                )
            },
            onShuffle = {
                playSmartPlaylist(
                    playlist = playlist,
                    tracks = visibleTracks,
                    startTrackId = null,
                    shuffle = true,
                    onPlayTrackList = onPlayTrackList
                )
            },
            secondary = {
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    enabled = visibleTracks.isNotEmpty()
                ) {
                    Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = null)
                    Text(text = "Save", modifier = Modifier.padding(start = 8.dp))
                }
            }
        )
    }
    val tracks: @Composable (Modifier) -> Unit = { tracksModifier ->
        Column(
            modifier = tracksModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaylistSearchAndSortRow(
                searchQuery = searchQuery,
                onSearchChanged = { searchQuery = it },
                sort = sort,
                onSortChanged = { sort = it }
            )
            PlaylistTrackList(
                tracks = visibleTracks,
                emptyText = if (playlist.tracks.isEmpty()) {
                    "No songs yet"
                } else {
                    "No matching songs"
                },
                canReorder = false,
                sourceTracks = playlist.tracks,
                showRemove = false,
                onTrackClick = { track ->
                    playSmartPlaylist(
                        playlist = playlist,
                        tracks = visibleTracks,
                        startTrackId = track.trackId,
                        shuffle = false,
                        onPlayTrackList = onPlayTrackList
                    )
                },
                onMove = { _, _ -> },
                onRemove = {},
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onToggleFavourite = onToggleFavourite,
                onAddToPlaylist = onAddToPlaylist,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("smartPlaylistDetail"),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 236.dp, max = 310.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                header()
            }
            tracks(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("smartPlaylistDetail"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            header()
            tracks(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun EmptyRegularPlaylistState(onCreatePlaylist: () -> Unit) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "No regular playlists yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Create one and add songs from the library, player, or playlist detail.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onCreatePlaylist) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                Text(text = "Create Playlist", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PlaylistSummaryCard(
    name: String,
    subtitle: String,
    songCount: Int,
    durationMs: Long,
    modifiedAt: Long?,
    thumbnailUris: List<Uri?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.testTag("playlistCard")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaylistCover(
                thumbnailUris = thumbnailUris,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.45f)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append("$songCount ")
                    append(if (songCount == 1) "song" else "songs")
                    append(" - ")
                    append(durationMs.formatDuration())
                    modifiedAt?.let { append(" - ${it.formatDate()}") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailTopRow(
    title: String,
    onBack: () -> Unit,
    menu: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        menu()
    }
}

@Composable
private fun PlaylistActionsMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    hasCustomThumbnail: Boolean,
    onRename: () -> Unit,
    onRequestCustomThumbnail: () -> Unit,
    onClearCustomThumbnail: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "Playlist actions")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text(text = "Rename") },
                leadingIcon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(text = "Change Cover") },
                leadingIcon = { Icon(imageVector = Icons.Rounded.Image, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onRequestCustomThumbnail()
                }
            )
            DropdownMenuItem(
                text = { Text(text = "Use Generated Cover") },
                enabled = hasCustomThumbnail,
                onClick = {
                    onExpandedChange(false)
                    onClearCustomThumbnail()
                }
            )
            DropdownMenuItem(
                text = { Text(text = "Delete") },
                leadingIcon = { Icon(imageVector = Icons.Rounded.Delete, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun PlaylistDetailSummary(
    thumbnailUris: List<Uri?>,
    songCount: Int,
    durationMs: Long,
    extra: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistCover(
            thumbnailUris = thumbnailUris,
            modifier = Modifier.size(86.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$songCount ${if (songCount == 1) "song" else "songs"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = durationMs.formatDuration(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = extra,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaylistActionRow(
    enabled: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    secondary: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onPlayAll, enabled = enabled) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null)
            Text(text = "Play All", modifier = Modifier.padding(start = 8.dp))
        }
        FilledTonalButton(onClick = onShuffle, enabled = enabled) {
            Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null)
            Text(text = "Shuffle", modifier = Modifier.padding(start = 8.dp))
        }
        secondary()
    }
}

@Composable
private fun PlaylistSearchAndSortRow(
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    sort: PlaylistDetailSort,
    onSortChanged: (PlaylistDetailSort) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            singleLine = true,
            placeholder = { Text(text = "Search songs") },
            leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { sortExpanded = true }) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort songs")
            }
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                PlaylistDetailSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option.label) },
                        leadingIcon = {
                            if (sort == option) {
                                Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            sortExpanded = false
                            onSortChanged(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistTrackList(
    tracks: List<PlaylistTrackItem>,
    emptyText: String,
    canReorder: Boolean,
    sourceTracks: List<PlaylistTrackItem>,
    showRemove: Boolean,
    onTrackClick: (PlaylistTrackItem) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    onToggleFavourite: (String, Boolean) -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tracks, key = { it.trackId }) { track ->
            val sourceIndex = sourceTracks.indexOfFirst { it.trackId == track.trackId }
            PlaylistTrackRow(
                track = track,
                canReorder = canReorder,
                canMoveUp = sourceIndex > 0,
                canMoveDown = sourceIndex in 0 until sourceTracks.lastIndex,
                showRemove = showRemove,
                onClick = { onTrackClick(track) },
                onMoveUp = { onMove(track.trackId, -1) },
                onMoveDown = { onMove(track.trackId, 1) },
                onRemove = { onRemove(track.trackId) },
                onPlayNext = { onPlayNext(track.trackId) },
                onAddToQueue = { onAddToQueue(track.trackId) },
                onToggleFavourite = {
                    onToggleFavourite(track.trackId, !track.isFavourite)
                },
                onAddToPlaylist = {
                    onAddToPlaylist(track.trackId, track.displayTitle)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTrackRow(
    track: PlaylistTrackItem,
    canReorder: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showRemove: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavourite: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                text = track.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = track.artist.ifBlank { "Unknown artist" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            TrackArtwork(thumbnailUri = track.thumbnailUri)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.durationMs.formatDuration(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canReorder) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(imageVector = Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(imageVector = Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "Song actions")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "Play Next") },
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = "Add to Queue") },
                            leadingIcon = { Icon(imageVector = Icons.Rounded.Queue, contentDescription = null) },
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
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = if (track.isFavourite) "Remove Favourite" else "Favourite")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (track.isFavourite) {
                                        Icons.Rounded.Favorite
                                    } else {
                                        Icons.Rounded.FavoriteBorder
                                    },
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleFavourite()
                            }
                        )
                        if (showRemove) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(text = "Remove from Playlist") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRemove()
                                }
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick)
            .testTag("playlistTrackRow_${track.trackId}")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistAddSongsSheet(
    playlist: RegularPlaylistDetail,
    libraryTracks: List<LibraryTrack>,
    onToggleTrack: (LibraryTrack) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val memberIds = remember(playlist.tracks) { playlist.tracks.map { it.trackId }.toSet() }
    val visibleTracks = remember(libraryTracks, searchQuery) {
        libraryTracks
            .filter { it.matches(searchQuery) }
            .sortedBy { SearchNormalizer.sortKey(it.displayTitle) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Add Songs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text(text = "Search library") },
                leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(visibleTracks, key = { it.trackId }) { track ->
                    val selected = track.trackId in memberIds
                    ListItem(
                        headlineContent = {
                            Text(
                                text = track.displayTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                text = track.artist.ifBlank { "Unknown artist" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = { TrackArtwork(thumbnailUri = track.thumbnailUri) },
                        trailingContent = {
                            if (selected) {
                                Icon(imageVector = Icons.Rounded.Check, contentDescription = "Included")
                            } else {
                                Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCover(
    thumbnailUris: List<Uri?>,
    modifier: Modifier = Modifier
) {
    val usableUris = thumbnailUris.filterNotNull().take(4)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when (usableUris.size) {
            0 -> Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp)
            )

            1 -> AsyncImage(
                model = usableUris.first(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    CoverCell(uri = usableUris.getOrNull(0), modifier = Modifier.weight(1f))
                    CoverCell(uri = usableUris.getOrNull(1), modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.weight(1f)) {
                    CoverCell(uri = usableUris.getOrNull(2), modifier = Modifier.weight(1f))
                    CoverCell(uri = usableUris.getOrNull(3), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CoverCell(uri: Uri?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
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
}

@Composable
private fun TrackArtwork(thumbnailUri: Uri?) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailUri != null) {
            AsyncImage(
                model = thumbnailUri,
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
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable(title, initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

private fun playRegularPlaylist(
    playlist: RegularPlaylistDetail,
    tracks: List<PlaylistTrackItem>,
    startTrackId: String?,
    shuffle: Boolean,
    onPlayTrackList: (
        trackIds: List<String>,
        startTrackId: String?,
        sourceType: String,
        sourceId: String?,
        sourceLabel: String,
        insertedBy: String,
        shuffle: Boolean
    ) -> Unit
) {
    onPlayTrackList(
        tracks.map { it.trackId },
        startTrackId,
        "PLAYLIST",
        playlist.playlistId,
        "Queue from Playlist: ${playlist.name}",
        QueueListEditor.INSERTED_BY_PLAYLIST_START,
        shuffle
    )
}

private fun playSmartPlaylist(
    playlist: SmartPlaylistDetail,
    tracks: List<PlaylistTrackItem>,
    startTrackId: String?,
    shuffle: Boolean,
    onPlayTrackList: (
        trackIds: List<String>,
        startTrackId: String?,
        sourceType: String,
        sourceId: String?,
        sourceLabel: String,
        insertedBy: String,
        shuffle: Boolean
    ) -> Unit
) {
    onPlayTrackList(
        tracks.map { it.trackId },
        startTrackId,
        "SMART_PLAYLIST",
        playlist.smartPlaylistId,
        "Queue from Smart Playlist: ${playlist.name}",
        QueueListEditor.INSERTED_BY_SMART_PLAYLIST_START,
        shuffle
    )
}

private enum class PlaylistDetailSort(val label: String) {
    MANUAL("Manual order"),
    TITLE("Title A-Z"),
    ARTIST("Artist A-Z"),
    DURATION("Duration"),
    FAVOURITES("Favourites first")
}

private fun List<PlaylistTrackItem>.visibleTracks(
    query: String,
    sort: PlaylistDetailSort
): List<PlaylistTrackItem> {
    val filtered = filter { it.matches(query) }
    return when (sort) {
        PlaylistDetailSort.MANUAL -> filtered
        PlaylistDetailSort.TITLE -> filtered.sortedBy { SearchNormalizer.sortKey(it.displayTitle) }
        PlaylistDetailSort.ARTIST -> filtered.sortedWith(
            compareBy<PlaylistTrackItem> { SearchNormalizer.sortKey(it.artist) }
                .thenBy { SearchNormalizer.sortKey(it.displayTitle) }
        )
        PlaylistDetailSort.DURATION -> filtered.sortedWith(
            compareBy<PlaylistTrackItem> { it.durationMs }
                .thenBy { SearchNormalizer.sortKey(it.displayTitle) }
        )
        PlaylistDetailSort.FAVOURITES -> filtered.sortedWith(
            compareByDescending<PlaylistTrackItem> { it.isFavourite }
                .thenBy { SearchNormalizer.sortKey(it.displayTitle) }
        )
    }
}

private fun PlaylistTrackItem.matches(query: String): Boolean {
    val normalizedQuery = SearchNormalizer.sortKey(query)
    if (normalizedQuery.isBlank()) return true
    return listOf(displayTitle, artist, album, genre, year.orEmpty())
        .any { SearchNormalizer.sortKey(it).contains(normalizedQuery) }
}

private fun LibraryTrack.matches(query: String): Boolean {
    val normalizedQuery = SearchNormalizer.sortKey(query)
    if (normalizedQuery.isBlank()) return true
    return listOf(displayTitle, title, artist, albumArtist, album, genre)
        .any { SearchNormalizer.sortKey(it).contains(normalizedQuery) }
}

private fun List<PlaylistTrackItem>.move(
    trackId: String,
    offset: Int
): List<PlaylistTrackItem> {
    val currentIndex = indexOfFirst { it.trackId == trackId }
    val targetIndex = (currentIndex + offset).coerceIn(0, lastIndex)
    if (currentIndex == -1 || currentIndex == targetIndex) return this
    return toMutableList().apply {
        add(targetIndex, removeAt(currentIndex))
    }
}

private fun Long.formatDate(): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

private fun Long.formatDuration(): String {
    if (this <= 0L) return "--:--"
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
