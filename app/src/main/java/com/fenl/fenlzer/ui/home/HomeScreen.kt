package com.fenl.fenlzer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fenl.fenlzer.data.repository.AlbumBulkEditDraft
import com.fenl.fenlzer.ui.theme.Dimensions
import com.fenl.fenlzer.data.repository.AlbumDetail
import com.fenl.fenlzer.data.repository.AlbumSummary
import com.fenl.fenlzer.data.repository.ArtistDetail
import com.fenl.fenlzer.data.repository.ArtistSummary
import com.fenl.fenlzer.data.repository.LibraryTrack
import com.fenl.fenlzer.data.settings.HomeSort
import com.fenl.fenlzer.domain.text.SearchNormalizer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tracks: List<LibraryTrack>,
    artists: List<ArtistSummary>,
    albums: List<AlbumSummary>,
    selectedArtist: ArtistDetail?,
    selectedAlbum: AlbumDetail?,
    onImportFromDevice: () -> Unit,
    onSearchYoutube: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onBackToLibrary: () -> Unit,
    onRenameArtist: (String, String) -> Unit,
    onEditAlbum: (String, AlbumBulkEditDraft) -> Unit,
    onChangeAlbumThumbnail: (String, Boolean) -> Unit,
    onTrackClick: (LibraryTrack, Boolean) -> Unit,
    onPlayNext: (LibraryTrack) -> Unit,
    onAddToQueue: (LibraryTrack) -> Unit,
    onAddToPlaylist: (LibraryTrack) -> Unit,
    onToggleFavourite: (LibraryTrack) -> Unit,
    onOpenSongDetails: (LibraryTrack) -> Unit,
    onEditMetadata: (LibraryTrack) -> Unit,
    onDeleteTracks: (List<LibraryTrack>) -> Unit,
    defaultHomeSort: HomeSort,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable(defaultHomeSort) {
        mutableStateOf(defaultHomeSort.toLibrarySort())
    }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var mode by rememberSaveable { mutableStateOf(HomeMode.SONGS) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf(emptySet<String>()) }
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    val visibleTracks = remember(tracks, searchQuery, sort, filter) {
        tracks
            .filter { track -> track.matches(searchQuery) }
            .filter { track -> filter.matches(track) }
            .sortedWith(sort.comparator)
    }
    val visibleArtists = remember(artists, searchQuery) {
        artists.filter { SearchNormalizer.sortKey(it.name).contains(SearchNormalizer.sortKey(searchQuery)) }
    }
    val visibleAlbums = remember(albums, searchQuery) {
        albums.filter { album ->
            listOf(album.title, album.albumArtist, album.year.orEmpty())
                .any { SearchNormalizer.sortKey(it).contains(SearchNormalizer.sortKey(searchQuery)) }
        }
    }
    val selectionMode = selectedTrackIds.isNotEmpty() && mode == HomeMode.SONGS

    if (selectedArtist != null) {
        ArtistDetailView(
            artist = selectedArtist,
            artists = artists,
            onBack = onBackToLibrary,
            onRenameArtist = onRenameArtist,
            onOpenAlbum = onOpenAlbum,
            onTrackClick = { track -> onTrackClick(track, false) },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onToggleFavourite = onToggleFavourite,
            onOpenSongDetails = onOpenSongDetails,
            onEditMetadata = onEditMetadata,
            onDeleteTracks = onDeleteTracks,
            modifier = modifier
        )
        return
    }

    if (selectedAlbum != null) {
        AlbumDetailView(
            album = selectedAlbum,
            albums = albums,
            onBack = onBackToLibrary,
            onEditAlbum = onEditAlbum,
            onChangeThumbnail = onChangeAlbumThumbnail,
            onTrackClick = { track -> onTrackClick(track, false) },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onToggleFavourite = onToggleFavourite,
            onOpenSongDetails = onOpenSongDetails,
            onEditMetadata = onEditMetadata,
            onDeleteTracks = onDeleteTracks,
            modifier = modifier
        )
        return
    }

    if (showOptions) {
        LibraryOptionsSheet(
            sort = sort,
            filter = filter,
            onSortSelected = { sort = it },
            onFilterSelected = { filter = it },
            onDismiss = { showOptions = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isLandscape) 12.dp else 16.dp,
                vertical = if (isLandscape) 8.dp else 12.dp
            )
            .testTag("homeScreen")
    ) {
        if (selectionMode) {
            SelectionBar(
                selectedCount = selectedTrackIds.size,
                onSelectAll = { selectedTrackIds = visibleTracks.map { it.trackId }.toSet() },
                onClear = { selectedTrackIds = emptySet() },
                onPlayNext = {
                    visibleTracks
                        .filter { it.trackId in selectedTrackIds }
                        .forEach(onPlayNext)
                    selectedTrackIds = emptySet()
                },
                onAddToQueue = {
                    visibleTracks
                        .filter { it.trackId in selectedTrackIds }
                        .forEach(onAddToQueue)
                    selectedTrackIds = emptySet()
                },
                onDelete = {
                    val selected = tracks.filter { it.trackId in selectedTrackIds }
                    onDeleteTracks(selected)
                    selectedTrackIds = emptySet()
                }
            )
        } else {
            HomeTopControls(
                searchQuery = searchQuery,
                onSearchChanged = { searchQuery = it },
                sort = sort,
                filter = filter,
                showCompact = isLandscape,
                onOptions = { showOptions = true },
                onSettings = onSettings,
                onStats = onStats
            )
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 12.dp))

        if (!selectionMode && tracks.isNotEmpty()) {
            HomeModeSelector(
                mode = mode,
                onModeChanged = {
                    mode = it
                    selectedTrackIds = emptySet()
                }
            )
            Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))
        }

        if (isLandscape && !selectionMode && tracks.isNotEmpty() && mode == HomeMode.SONGS) {
            LandscapeFilterStrip(
                filter = filter,
                onFilterSelected = { filter = it },
                onOptions = { showOptions = true }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        when {
            tracks.isEmpty() -> EmptyLibraryState(
                onImportFromDevice = onImportFromDevice,
                onSearchYoutube = onSearchYoutube,
                modifier = Modifier.weight(1f)
            )

            mode == HomeMode.ARTISTS -> ArtistList(
                artists = visibleArtists,
                onOpenArtist = onOpenArtist,
                modifier = Modifier.weight(1f)
            )

            mode == HomeMode.ALBUMS -> AlbumList(
                albums = visibleAlbums,
                onOpenAlbum = onOpenAlbum,
                modifier = Modifier.weight(1f)
            )

            visibleTracks.isEmpty() -> NoMatchingTracksState(
                onClear = {
                    searchQuery = ""
                    filter = LibraryFilter.ALL
                },
                modifier = Modifier.weight(1f)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Dimensions.LIST_ITEM_SPACING)
            ) {
                items(
                    items = visibleTracks,
                    key = { it.trackId }
                ) { track ->
                    TrackRow(
                        track = track,
                        selected = track.trackId in selectedTrackIds,
                        selectionMode = selectionMode,
                        onClick = {
                            if (selectionMode) {
                                selectedTrackIds = selectedTrackIds.toggle(track.trackId)
                            } else {
                                onTrackClick(track, searchQuery.isNotBlank())
                            }
                        },
                        onLongClick = {
                            selectedTrackIds = selectedTrackIds + track.trackId
                        },
                        onToggleFavourite = { onToggleFavourite(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onDelete = { onDeleteTracks(listOf(track)) },
                        onOpenSongDetails = { onOpenSongDetails(track) },
                        onEditMetadata = { onEditMetadata(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopControls(
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    sort: LibrarySort,
    filter: LibraryFilter,
    showCompact: Boolean,
    onOptions: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onStats) {
            Icon(imageVector = Icons.Rounded.BarChart, contentDescription = "Open statistics")
        }
        IconButton(onClick = onSettings) {
            Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Open settings")
        }
    }
    Spacer(modifier = Modifier.height(if (showCompact) 2.dp else 4.dp))

    if (showCompact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactSearchField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOptions) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.Sort, contentDescription = "Library options")
            }
        }
    } else {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            singleLine = true,
            placeholder = { Text(text = "Search library") },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = onOptions) {
                    Icon(imageVector = Icons.Rounded.FilterList, contentDescription = "Library options")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (!showCompact) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${filter.label} - ${sort.label}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeModeSelector(
    mode: HomeMode,
    onModeChanged: (HomeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeMode.entries.forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { onModeChanged(option) },
                label = { Text(text = option.label) }
            )
        }
    }
}

@Composable
private fun ArtistList(
    artists: List<ArtistSummary>,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        EmptyListMessage(text = "No matching artists", modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            ListItem(
                headlineContent = {
                    Text(
                        text = artist.name.displayArtistName(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = "${artist.songCount} songs - ${artist.albumCount} albums - " +
                            artist.totalListenedMs.formatDuration(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenArtist(artist.name) }
            )
        }
    }
}

@Composable
private fun AlbumList(
    albums: List<AlbumSummary>,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) {
        EmptyListMessage(text = "No matching albums", modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(albums, key = { it.albumKey }) { album ->
            ListItem(
                headlineContent = {
                    Text(
                        text = album.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = "${album.albumArtist} - ${album.songCount} songs - " +
                            album.totalDurationMs.formatDuration(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = { AlbumArtwork(album = album) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenAlbum(album.albumKey) }
            )
        }
    }
}

@Composable
private fun ArtistDetailView(
    artist: ArtistDetail,
    artists: List<ArtistSummary>,
    onBack: () -> Unit,
    onRenameArtist: (String, String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onTrackClick: (LibraryTrack) -> Unit,
    onPlayNext: (LibraryTrack) -> Unit,
    onAddToQueue: (LibraryTrack) -> Unit,
    onAddToPlaylist: (LibraryTrack) -> Unit,
    onToggleFavourite: (LibraryTrack) -> Unit,
    onOpenSongDetails: (LibraryTrack) -> Unit,
    onEditMetadata: (LibraryTrack) -> Unit,
    onDeleteTracks: (List<LibraryTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    if (showRenameDialog) {
        ArtistRenameDialog(
            artist = artist,
            artists = artists,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                onRenameArtist(artist.name, newName)
                showRenameDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailHeader(
            title = artist.name.displayArtistName(),
            onBack = onBack,
            action = {
                TextButton(onClick = { showRenameDialog = true }) {
                    Text(text = "Rename")
                }
            }
        )
        Text(
            text = "${artist.songCount} songs - ${artist.albums.size} albums - " +
                artist.totalListenedMs.formatDuration() + " listened",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (artist.mostPlayedSongTitle != null) {
            Text(
                text = "Most played: ${artist.mostPlayedSongTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            artist.albums.forEach { album ->
                FilterChip(
                    selected = false,
                    onClick = { onOpenAlbum(album.albumKey) },
                    label = { Text(text = album.title, maxLines = 1) }
                )
            }
        }
        TrackRows(
            tracks = artist.tracks,
            onTrackClick = onTrackClick,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onToggleFavourite = onToggleFavourite,
            onOpenSongDetails = onOpenSongDetails,
            onEditMetadata = onEditMetadata,
            onDeleteTracks = onDeleteTracks,
            showDeleteAction = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AlbumDetailView(
    album: AlbumDetail,
    albums: List<AlbumSummary>,
    onBack: () -> Unit,
    onEditAlbum: (String, AlbumBulkEditDraft) -> Unit,
    onChangeThumbnail: (String, Boolean) -> Unit,
    onTrackClick: (LibraryTrack) -> Unit,
    onPlayNext: (LibraryTrack) -> Unit,
    onAddToQueue: (LibraryTrack) -> Unit,
    onAddToPlaylist: (LibraryTrack) -> Unit,
    onToggleFavourite: (LibraryTrack) -> Unit,
    onOpenSongDetails: (LibraryTrack) -> Unit,
    onEditMetadata: (LibraryTrack) -> Unit,
    onDeleteTracks: (List<LibraryTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var overwriteThumbnail by rememberSaveable { mutableStateOf(true) }
    if (showEditDialog) {
        AlbumEditDialog(
            album = album.summary,
            albums = albums,
            onDismiss = { showEditDialog = false },
            onConfirm = { draft ->
                onEditAlbum(album.summary.albumKey, draft)
                showEditDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailHeader(
            title = album.summary.title,
            onBack = onBack,
            action = {
                TextButton(onClick = { showEditDialog = true }) {
                    Text(text = "Edit")
                }
            }
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtwork(album = album.summary, size = 72.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = album.summary.albumArtist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${album.summary.songCount} songs - " +
                        album.summary.totalDurationMs.formatDuration(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Listened ${album.totalListenedMs.formatDuration()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = overwriteThumbnail,
                onCheckedChange = { overwriteThumbnail = it }
            )
            Text(
                text = "Overwrite existing custom thumbnails",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onChangeThumbnail(album.summary.albumKey, overwriteThumbnail) }) {
                Text(text = "Change Cover")
            }
        }
        if (album.mostPlayedSongTitle != null) {
            Text(
                text = "Most played: ${album.mostPlayedSongTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TrackRows(
            tracks = album.tracks,
            onTrackClick = onTrackClick,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onToggleFavourite = onToggleFavourite,
            onOpenSongDetails = onOpenSongDetails,
            onEditMetadata = onEditMetadata,
            onDeleteTracks = onDeleteTracks,
            showDeleteAction = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TrackRows(
    tracks: List<LibraryTrack>,
    onTrackClick: (LibraryTrack) -> Unit,
    onPlayNext: (LibraryTrack) -> Unit,
    onAddToQueue: (LibraryTrack) -> Unit,
    onAddToPlaylist: (LibraryTrack) -> Unit,
    onToggleFavourite: (LibraryTrack) -> Unit,
    onOpenSongDetails: (LibraryTrack) -> Unit,
    onEditMetadata: (LibraryTrack) -> Unit,
    onDeleteTracks: (List<LibraryTrack>) -> Unit,
    showDeleteAction: Boolean = true,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tracks, key = { it.trackId }) { track ->
            TrackRow(
                track = track,
                selected = false,
                selectionMode = false,
                onClick = { onTrackClick(track) },
                onLongClick = {},
                onToggleFavourite = { onToggleFavourite(track) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onDelete = { onDeleteTracks(listOf(track)) },
                showDeleteAction = showDeleteAction,
                onOpenSongDetails = { onOpenSongDetails(track) },
                onEditMetadata = { onEditMetadata(track) }
            )
        }
    }
}

@Composable
private fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(text = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        action()
    }
}

@Composable
private fun ArtistRenameDialog(
    artist: ArtistDetail,
    artists: List<ArtistSummary>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(artist.name) }
    val mergeWarning = artists.any { it.name == value && it.name != artist.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Rename Artist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "${artist.songCount} songs will be updated.")
                if (mergeWarning) {
                    Text(
                        text = "This will merge artists.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value != artist.name
            ) {
                Text(text = "Rename")
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
private fun AlbumEditDialog(
    album: AlbumSummary,
    albums: List<AlbumSummary>,
    onDismiss: () -> Unit,
    onConfirm: (AlbumBulkEditDraft) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(album.album) }
    var albumArtist by rememberSaveable { mutableStateOf(album.albumArtist) }
    var year by rememberSaveable { mutableStateOf(album.year.orEmpty()) }
    var genre by rememberSaveable { mutableStateOf("") }
    var overwrite by rememberSaveable { mutableStateOf(false) }
    val mergeWarning = albums.any {
        it.albumKey != album.albumKey &&
            it.album == title &&
            it.albumArtist == albumArtist
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Edit Album") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(text = "Album") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = albumArtist,
                    onValueChange = { albumArtist = it },
                    singleLine = true,
                    label = { Text(text = "Album Artist") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    label = { Text(text = "Year") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    singleLine = true,
                    label = { Text(text = "Genre") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                    Text(text = "Overwrite filled year/genre fields")
                }
                Text(text = "${album.songCount} songs will be updated.")
                if (mergeWarning) {
                    Text(
                        text = "This will merge albums.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        AlbumBulkEditDraft(
                            album = title,
                            albumArtist = albumArtist,
                            year = year.takeIf { it.isNotBlank() },
                            genre = genre,
                            overwriteFilledFields = overwrite
                        )
                    )
                }
            ) {
                Text(text = "Save")
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
private fun AlbumArtwork(
    album: AlbumSummary,
    size: androidx.compose.ui.unit.Dp = 50.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (album.thumbnailUri != null) {
            AsyncImage(
                model = album.thumbnailUri,
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
private fun EmptyListMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .height(40.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .padding(horizontal = 12.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text(
                            text = "Search library",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun LandscapeFilterStrip(
    filter: LibraryFilter,
    onFilterSelected: (LibraryFilter) -> Unit,
    onOptions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 38.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryFilter.entries.take(5).forEach { option ->
            FilterChip(
                selected = filter == option,
                onClick = { onFilterSelected(option) },
                label = { Text(text = option.label) }
            )
        }
        TextButton(onClick = onOptions) {
            Text(text = "More")
        }
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSelectAll) {
            Icon(imageVector = Icons.Rounded.CheckBox, contentDescription = "Select all")
        }
        IconButton(onClick = onPlayNext) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = "Play next")
        }
        IconButton(onClick = onAddToQueue) {
            Icon(imageVector = Icons.Rounded.Queue, contentDescription = "Add to queue")
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete selected")
        }
        IconButton(onClick = onClear) {
            Icon(imageVector = Icons.Rounded.Clear, contentDescription = "Clear selection")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: LibraryTrack,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
    showDeleteAction: Boolean = true,
    onOpenSongDetails: () -> Unit,
    onEditMetadata: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val rowBackground = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface
    }

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
                text = track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            TrackArtwork(track = track)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.durationMs.formatDuration(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
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
                        text = { Text(text = "Song Details") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Info, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenSongDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Edit Tags") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onEditMetadata()
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
                    if (showDeleteAction) {
                        DropdownMenuItem(
                            text = { Text(text = "Delete from Fenlzer") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Delete, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBackground)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}

@Composable
private fun TrackArtwork(track: LibraryTrack) {
    Box(
        modifier = Modifier
            .size(Dimensions.TRACK_THUMBNAIL)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (track.thumbnailUri != null) {
            AsyncImage(
                model = track.thumbnailUri,
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
private fun EmptyLibraryState(
    onImportFromDevice: () -> Unit,
    onSearchYoutube: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Import songs to start listening",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onImportFromDevice,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Rounded.FileDownload, contentDescription = null)
            Text(
                text = "Import from Device",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSearchYoutube,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
            Text(
                text = "Search YouTube",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun NoMatchingTracksState(
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No matching songs",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onClear) {
            Text(text = "Clear search and filters")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryOptionsSheet(
    sort: LibrarySort,
    filter: LibraryFilter,
    onSortSelected: (LibrarySort) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val sheetMaxHeight = if (configuration.screenHeightDp > 280) {
        (configuration.screenHeightDp - 32).dp
    } else {
        248.dp
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.imePadding()
    ) {
        val sheetModifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp)

        if (isLandscape) {
            Row(
                modifier = sheetModifier,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                OptionGroup(
                    title = "Sort",
                    options = LibrarySort.entries,
                    selectedOption = sort,
                    label = { it.label },
                    onSelected = onSortSelected,
                    modifier = Modifier.weight(1f)
                )
                OptionGroup(
                    title = "Filter",
                    options = LibraryFilter.entries,
                    selectedOption = filter,
                    label = { it.label },
                    onSelected = onFilterSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                modifier = sheetModifier
            ) {
                OptionGroup(
                    title = "Sort",
                    options = LibrarySort.entries,
                    selectedOption = sort,
                    label = { it.label },
                    onSelected = onSortSelected
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OptionGroup(
                    title = "Filter",
                    options = LibraryFilter.entries,
                    selectedOption = filter,
                    label = { it.label },
                    onSelected = onFilterSelected
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun <T> OptionGroup(
    title: String,
    options: List<T>,
    selectedOption: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        options.forEach { option ->
            OptionRow(
                text = label(option),
                selected = selectedOption == option,
                onClick = { onSelected(option) }
            )
        }
    }
}

@Composable
private fun OptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private enum class HomeMode(val label: String) {
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums")
}

private enum class LibrarySort(
    val label: String,
    val comparator: Comparator<LibraryTrack>
) {
    TITLE_ASC(
        label = "Title A-Z",
        comparator = compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
    ),
    ARTIST_ASC(
        label = "Artist A-Z",
        comparator = compareBy<LibraryTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
    ),
    RECENTLY_ADDED(
        label = "Recently added",
        comparator = compareByDescending<LibraryTrack> { it.importedAt }
    ),
    RECENTLY_PLAYED(
        label = "Recently played",
        comparator = compareByDescending<LibraryTrack> { it.lastPlayedAt ?: 0L }
            .thenByDescending { it.importedAt }
    ),
    MOST_PLAYED(
        label = "Most played",
        comparator = compareByDescending<LibraryTrack> { it.totalListenedMs }
            .thenByDescending { it.playCount }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
    ),
    LEAST_PLAYED(
        label = "Least played",
        comparator = compareBy<LibraryTrack> { it.totalListenedMs }
            .thenBy { it.playCount }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
    ),
    DURATION_SHORTEST(
        label = "Duration shortest",
        comparator = compareBy<LibraryTrack> { it.durationMs }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
    ),
    DURATION_LONGEST(
        label = "Duration longest",
        comparator = compareByDescending<LibraryTrack> { it.durationMs }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
    ),
    FAVOURITES_FIRST(
        label = "Favourites first",
        comparator = compareByDescending<LibraryTrack> { it.isFavourite }
            .thenByDescending { it.favouritedAt ?: 0L }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
    )
}

private fun HomeSort.toLibrarySort(): LibrarySort =
    when (this) {
        HomeSort.TITLE_A_TO_Z -> LibrarySort.TITLE_ASC
        HomeSort.ARTIST_A_TO_Z -> LibrarySort.ARTIST_ASC
        HomeSort.RECENTLY_ADDED -> LibrarySort.RECENTLY_ADDED
        HomeSort.RECENTLY_PLAYED -> LibrarySort.RECENTLY_PLAYED
        HomeSort.MOST_PLAYED -> LibrarySort.MOST_PLAYED
        HomeSort.LEAST_PLAYED -> LibrarySort.LEAST_PLAYED
        HomeSort.DURATION_SHORTEST -> LibrarySort.DURATION_SHORTEST
        HomeSort.DURATION_LONGEST -> LibrarySort.DURATION_LONGEST
        HomeSort.FAVOURITES_FIRST -> LibrarySort.FAVOURITES_FIRST
    }

private enum class LibraryFilter(val label: String) {
    ALL("All songs"),
    FAVOURITES("Favourites only"),
    MISSING_ARTIST("Missing artist"),
    MISSING_THUMBNAIL("Missing thumbnail"),
    LOCAL_IMPORTS("Downloaded/imported source");

    fun matches(track: LibraryTrack): Boolean {
        return when (this) {
            ALL -> true
            FAVOURITES -> track.isFavourite
            MISSING_ARTIST -> track.artist.isBlank()
            MISSING_THUMBNAIL -> !track.hasThumbnail
            LOCAL_IMPORTS -> track.sourceType == "LOCAL_FILE"
        }
    }
}

private fun LibraryTrack.matches(query: String): Boolean {
    val normalizedQuery = SearchNormalizer.sortKey(query)
    if (normalizedQuery.isBlank()) return true

    return listOf(displayTitle, title, artist, albumArtist, album, genre)
        .any { SearchNormalizer.sortKey(it).contains(normalizedQuery) }
}

private fun Set<String>.toggle(trackId: String): Set<String> =
    if (trackId in this) this - trackId else this + trackId

private fun String.displayArtistName(): String =
    ifBlank { "Unknown artist" }

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
