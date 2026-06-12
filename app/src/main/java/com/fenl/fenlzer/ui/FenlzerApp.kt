package com.fenl.fenlzer.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fenl.fenlzer.AppGraph
import com.fenl.fenlzer.data.repository.AlbumBulkEditDraft
import com.fenl.fenlzer.data.repository.DiscoverUiState
import com.fenl.fenlzer.data.repository.LibraryTrack
import com.fenl.fenlzer.data.repository.PlaylistMembershipTarget
import com.fenl.fenlzer.data.repository.SmartPlaylistIds
import com.fenl.fenlzer.data.repository.StatisticsSummary
import com.fenl.fenlzer.data.storage.FenlzerStorageUsage
import com.fenl.fenlzer.settings.ApiDiagnosticsScreen
import com.fenl.fenlzer.settings.SettingsScreen
import com.fenl.fenlzer.ui.components.AddToPlaylistDialog
import com.fenl.fenlzer.ui.discover.DiscoverScreen
import com.fenl.fenlzer.ui.home.HomeScreen
import com.fenl.fenlzer.ui.importing.ImportScreen
import com.fenl.fenlzer.ui.importing.LocalImportViewModel
import com.fenl.fenlzer.ui.importing.YoutubeImportViewModel
import com.fenl.fenlzer.ui.metadata.MetadataEditorSheet
import com.fenl.fenlzer.ui.metadata.SongDetailsSheet
import com.fenl.fenlzer.ui.navigation.FenlzerRoute
import com.fenl.fenlzer.ui.player.FullscreenPlayer
import com.fenl.fenlzer.ui.player.MiniPlayer
import com.fenl.fenlzer.ui.playlists.PlaylistsScreen
import com.fenl.fenlzer.ui.queue.QueueScreen
import com.fenl.fenlzer.ui.stats.StatisticsScreen
import com.fenl.fenlzer.playback.PlaybackUiState
import kotlinx.coroutines.launch

@Composable
fun FenlzerApp(
    appGraph: AppGraph,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val settings by appGraph.settingsRepository.settings.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: FenlzerRoute.Home.route
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun navigateToMainTab(tab: FenlzerRoute) {
 navController.navigate(tab.route) {
 popUpTo(navController.graph.findStartDestination().id) {
 saveState = tab != FenlzerRoute.Home
 }
 launchSingleTop = true
 restoreState = tab != FenlzerRoute.Home
 }
 }

    if (isLandscape && currentRoute !in setOf(FenlzerRoute.Player.route, FenlzerRoute.Queue.route)) {
        Row(modifier = modifier.fillMaxSize()) {
            FenlzerNavigationRail(
                currentRoute = currentRoute,
                onTabSelected = ::navigateToMainTab,
                modifier = Modifier.testTag("navigationRail")
            )
            FenlzerScaffold(
                appGraph = appGraph,
                navController = navController,
                currentRoute = currentRoute,
                privateModeEnabled = settings.privateModeEnabledForSession,
                showBottomNavigation = false,
                onImportRequested = { navigateToMainTab(FenlzerRoute.Import) },
                onSettingsRequested = { navController.navigate(FenlzerRoute.Settings.route) },
                onStatsRequested = { navController.navigate(FenlzerRoute.Statistics.route) },
                onTabSelected = ::navigateToMainTab,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        FenlzerScaffold(
            appGraph = appGraph,
            navController = navController,
            currentRoute = currentRoute,
            privateModeEnabled = settings.privateModeEnabledForSession,
            showBottomNavigation = !isLandscape,
            onImportRequested = { navigateToMainTab(FenlzerRoute.Import) },
            onSettingsRequested = { navController.navigate(FenlzerRoute.Settings.route) },
            onStatsRequested = { navController.navigate(FenlzerRoute.Statistics.route) },
            onTabSelected = ::navigateToMainTab,
            modifier = modifier
        )
    }
}

@Composable
private fun FenlzerScaffold(
    appGraph: AppGraph,
    navController: NavHostController,
    currentRoute: String,
    privateModeEnabled: Boolean,
    showBottomNavigation: Boolean,
    onImportRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    onStatsRequested: () -> Unit,
    onTabSelected: (FenlzerRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val scaffoldSettings by appGraph.settingsRepository.settings.collectAsStateWithLifecycle()
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val isPlayerRoute = currentRoute == FenlzerRoute.Player.route
    val isDiagnosticsRoute = currentRoute in setOf(FenlzerRoute.Diagnostics.route, FenlzerRoute.Queue.route)
    val hidesAppChrome = isPlayerRoute || isDiagnosticsRoute || currentRoute == FenlzerRoute.Queue.route
    val hideTopBar =
        hidesAppChrome ||
            currentRoute == FenlzerRoute.Home.route ||
            currentRoute == FenlzerRoute.Import.route ||
            currentRoute == FenlzerRoute.Playlists.route ||
            (isLandscape && currentRoute == FenlzerRoute.Home.route)
    val playbackState by appGraph.playbackController
        ?.uiState
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(PlaybackUiState()) }
    var showQueuePanel by remember { mutableStateOf(false) }
    var addToPlaylistTarget by remember { mutableStateOf<PendingAddToPlaylist?>(null) }
    var detailsTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var thumbnailTrackId by remember { mutableStateOf<String?>(null) }
    var albumThumbnailRequest by remember { mutableStateOf<PendingAlbumThumbnail?>(null) }
    var pendingDetailsDelete by remember { mutableStateOf<PendingDeleteSong?>(null) }
    val membershipTargetsFlow = remember(
        appGraph.playlistRepository,
        addToPlaylistTarget?.trackId
    ) {
        addToPlaylistTarget?.trackId?.let { trackId ->
            appGraph.playlistRepository?.observeMembershipTargets(trackId)
        }
    }
    val membershipTargets by membershipTargetsFlow
        ?.collectAsStateWithLifecycle(initialValue = emptyList<PlaylistMembershipTarget>())
        ?: remember { mutableStateOf(emptyList()) }

    fun openAddToPlaylist(trackId: String, title: String) {
        addToPlaylistTarget = PendingAddToPlaylist(trackId = trackId, title = title)
    }

    fun openSongDetails(trackId: String) {
        detailsTrackId = trackId
        editorTrackId = null
    }

    fun openMetadataEditor(trackId: String) {
        editorTrackId = trackId
        detailsTrackId = null
    }

    fun performDeleteTrackIds(trackIds: Collection<String>) {
        val repository = appGraph.trackRepository ?: return
        val distinctTrackIds = trackIds.distinct()
        if (distinctTrackIds.isEmpty()) return
        coroutineScope.launch {
            runCatching {
                appGraph.playbackController?.prepareForTrackDeletion(distinctTrackIds)
            }
            repository.deleteTracks(distinctTrackIds)
            appGraph.playbackController?.refreshQueueFromRepository()
        }
    }

    fun requestDetailsDelete(trackId: String, title: String) {
        if (scaffoldSettings.deleteConfirmationEnabled) {
            pendingDetailsDelete = PendingDeleteSong(trackId = trackId, title = title)
        } else {
            performDeleteTrackIds(listOf(trackId))
        }
    }

    val songDetailsFlow = remember(appGraph.metadataRepository, detailsTrackId, editorTrackId) {
        (editorTrackId ?: detailsTrackId)?.let { trackId ->
            appGraph.metadataRepository?.observeSongDetails(trackId)
        }
    }
    val activeSongDetails by songDetailsFlow
        ?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) }
    val thumbnailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            val trackId = thumbnailTrackId
            val albumRequest = albumThumbnailRequest
            thumbnailTrackId = null
            albumThumbnailRequest = null
            if (uri != null) {
                when {
                    trackId != null -> appGraph.metadataRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.setTrackCustomThumbnail(
                                trackId = trackId,
                                sourceUri = uri,
                                contentResolver = context.contentResolver
                            )
                        }
                    }
                    albumRequest != null -> appGraph.metadataRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.setAlbumCustomThumbnail(
                                albumKey = albumRequest.albumKey,
                                sourceUri = uri,
                                contentResolver = context.contentResolver,
                                overwriteExistingCustom = albumRequest.overwriteExistingCustom
                            )
                        }
                    }
                }
            }
        }
    )

    LaunchedEffect(playbackState.message) {
        playbackState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            appGraph.playbackController?.consumeMessage()
        }
    }

    LaunchedEffect(isLandscape, currentRoute) {
        if (isLandscape && currentRoute == FenlzerRoute.Queue.route) {
            showQueuePanel = true
            if (!navController.popBackStack()) {
                navController.navigate(FenlzerRoute.Home.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    fun openQueue() {
        if (isLandscape) {
            showQueuePanel = true
        } else {
            navController.navigate(FenlzerRoute.Queue.route)
        }
    }

    fun openPlayer() {
        navController.navigate(FenlzerRoute.Player.route) {
            launchSingleTop = true
        }
    }

    fun closeQueue() {
        if (isLandscape) {
            showQueuePanel = false
        } else {
            navController.popBackStack()
        }
    }

    addToPlaylistTarget?.let { target ->
        AddToPlaylistDialog(
            trackTitle = target.title,
            targets = membershipTargets,
            onToggleTarget = { membershipTarget ->
                appGraph.playlistRepository?.let { repository ->
                    coroutineScope.launch {
                        repository.toggleMembership(membershipTarget, target.trackId)
                    }
                }
            },
            onDismiss = { addToPlaylistTarget = null }
        )
    }

    pendingDetailsDelete?.let { target ->
        DeleteFromFenlzerDialog(
            trackCount = 1,
            singleTrackTitle = target.title,
            onDismiss = { pendingDetailsDelete = null },
            onConfirm = {
                pendingDetailsDelete = null
                performDeleteTrackIds(listOf(target.trackId))
            }
        )
    }

    if (detailsTrackId != null) {
        activeSongDetails?.let { details ->
            SongDetailsSheet(
                details = details,
                onPlay = { trackId -> appGraph.playbackController?.playFromHome(trackId, false) },
                onEdit = ::openMetadataEditor,
                onDelete = { trackId ->
                    requestDetailsDelete(trackId, details.title)
                    detailsTrackId = null
                },
                onDismiss = { detailsTrackId = null }
            )
        }
    }

    if (editorTrackId != null) {
        activeSongDetails?.let { details ->
            MetadataEditorSheet(
                details = details,
                onSave = { trackId, draft ->
                    appGraph.metadataRepository?.let { repository ->
                        coroutineScope.launch { repository.updateTrackMetadata(trackId, draft) }
                    }
                },
                onResetAll = { trackId, resetThumbnail ->
                    appGraph.metadataRepository?.let { repository ->
                        coroutineScope.launch { repository.resetTrackMetadata(trackId, resetThumbnail) }
                    }
                },
                onPickThumbnail = { trackId ->
                    thumbnailTrackId = trackId
                    thumbnailLauncher.launch("image/*")
                },
                onClearThumbnail = { trackId ->
                    appGraph.metadataRepository?.let { repository ->
                        coroutineScope.launch { repository.clearTrackCustomThumbnail(trackId) }
                    }
                },
                onDismiss = { editorTrackId = null }
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (!hideTopBar) {
                FenlzerTopBar(
                    currentRoute = currentRoute,
                    onBack = { navController.popBackStack() },
                    onSettings = onSettingsRequested,
                    onStats = onStatsRequested
                )
            }
        },
        bottomBar = {
            if (!hidesAppChrome && !isKeyboardVisible) {
                Column {
                MiniPlayer(
                    playbackState = playbackState,
                    privateModeEnabled = privateModeEnabled,
                    onMainAreaClick = {
                        if (playbackState.hasCurrentItem) {
                            openPlayer()
                        } else {
                            onImportRequested()
                        }
                    },
                    onToggleFavourite = {
                        val currentItem = playbackState.currentItem
                        val localTrackId = currentItem?.localTrackId
                        if (localTrackId != null) {
                            appGraph.trackRepository?.let { repository ->
                                coroutineScope.launch {
                                    repository.setFavourite(
                                        trackId = localTrackId,
                                        isFavourite = !currentItem.isFavourite
                                    )
                                }
                            }
                        }
                    },
                    onPlayPause = { appGraph.playbackController?.togglePlayPause() },
                    onNext = { appGraph.playbackController?.skipNext() },
                    onSeekTo = { positionMs -> appGraph.playbackController?.seekTo(positionMs) },
                    onAddToPlaylist = {
                        playbackState.currentItem?.let { item ->
                            item.localTrackId?.let { trackId ->
                                openAddToPlaylist(trackId, item.displayTitle)
                            }
                        }
                    },
                    onOpenSongDetails = {
                        playbackState.currentItem?.localTrackId?.let(::openSongDetails)
                    },
                    onEditMetadata = {
                        playbackState.currentItem?.localTrackId?.let(::openMetadataEditor)
                    },
                    onOpenQueue = ::openQueue,
                    onOpenSleepTimer = ::openPlayer,
                    modifier = Modifier.testTag("miniPlayer")
                )
                if (showBottomNavigation) {
                    FenlzerBottomNavigation(
                        currentRoute = currentRoute,
                        onTabSelected = onTabSelected,
                        modifier = Modifier.testTag("bottomNavigation")
                    )
                }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FenlzerNavHost(
                navController = navController,
                appGraph = appGraph,
                playbackState = playbackState,
                contentPadding = PaddingValues(),
                onImportRequested = onImportRequested,
                onSettingsRequested = onSettingsRequested,
                onStatsRequested = onStatsRequested,
                onCloseQueue = ::closeQueue,
                onOpenQueue = ::openQueue,
                onAddToPlaylist = ::openAddToPlaylist,
                onOpenSongDetails = ::openSongDetails,
                onEditMetadata = ::openMetadataEditor,
                onChangeAlbumThumbnail = { albumKey, overwriteExistingCustom ->
                    albumThumbnailRequest = PendingAlbumThumbnail(albumKey, overwriteExistingCustom)
                    thumbnailLauncher.launch("image/*")
                }
            )

            if (showQueuePanel) {
                QueueScreen(
                    playbackState = playbackState,
                    onBack = { showQueuePanel = false },
                    onRemoveItem = { queueItemId ->
                        appGraph.playbackController?.removeQueueItem(queueItemId)
                    },
                    onJumpToItem = { queueItemId ->
                        appGraph.playbackController?.jumpToQueueItem(queueItemId)
                    },
                    onClearUpcoming = {
                        appGraph.playbackController?.clearUpcoming()
                    },
                    isPanel = true,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .widthIn(min = 360.dp, max = 440.dp)
                ,
                    onMoveItem = { queueItemId, offset ->
                        coroutineScope.launch { appGraph.queueRepository?.moveQueueItem(queueItemId, offset) }
                    },
                    onShuffleQueue = {
                        coroutineScope.launch { appGraph.queueRepository?.shuffleQueue() }
                    },
                    onShuffleUpcoming = {
                        coroutineScope.launch { appGraph.queueRepository?.shuffleUpcoming() }
                    },
                    onSaveQueueAsPlaylist = { name ->
                        coroutineScope.launch {
                            val trackIds = playbackState.queueItems
                                .filterNot { it.isRemote }
                                .mapNotNull { it.localTrackId ?: it.trackId }
                                .distinct()
                            if (trackIds.isNotEmpty()) {
                                appGraph.playlistRepository?.saveStaticPlaylist(name, trackIds)
                            }
                        }
                    })
            }
        }
    }
}

@Composable
private fun FenlzerNavHost(
    navController: NavHostController,
    appGraph: AppGraph,
    playbackState: PlaybackUiState,
    contentPadding: PaddingValues,
    onImportRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    onStatsRequested: () -> Unit,
    onCloseQueue: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    onOpenSongDetails: (String) -> Unit,
    onEditMetadata: (String) -> Unit,
    onChangeAlbumThumbnail: (String, Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val appSettings by appGraph.settingsRepository.settings.collectAsStateWithLifecycle()
    val appVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("unknown")
    }
    val localImportViewModel: LocalImportViewModel = viewModel(
        factory = LocalImportViewModel.factory(appGraph.localImportRepository)
    )
    val localImportState by localImportViewModel.uiState.collectAsStateWithLifecycle()
    val youtubeImportViewModel: YoutubeImportViewModel = viewModel(
        factory = YoutubeImportViewModel.factory(appGraph.youtubeImportCoordinator)
    )
    val youtubeImportState by youtubeImportViewModel.uiState.collectAsStateWithLifecycle()
    val trackFlow = remember(appGraph.trackRepository) {
        appGraph.trackRepository?.observeLibraryTracks()
    }
    val libraryTracks = trackFlow
        ?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?.value
        ?: emptyList()
    val localImportMimeTypes = remember {
        arrayOf("audio/*", "application/ogg")
    }
    val localImportLauncher = if (appGraph.localImportRepository != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
            onResult = localImportViewModel::importUris
        )
    } else {
        null
    }
    var selectedRegularPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSmartPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedArtistName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumKey by rememberSaveable { mutableStateOf<String?>(null) }
    var customThumbnailPlaylistId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteTracks by remember { mutableStateOf(emptyList<LibraryTrack>()) }
    var storageUsage by remember { mutableStateOf<FenlzerStorageUsage?>(null) }
    val apiDiagnosticsFlow = remember(appGraph.database) {
        appGraph.database?.apiDiagnosticDao()?.observeRecent(limit = 500)
    }
    val apiDiagnostics by apiDiagnosticsFlow
        ?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val customThumbnailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            val playlistId = customThumbnailPlaylistId
            customThumbnailPlaylistId = null
            if (uri != null && playlistId != null) {
                appGraph.playlistRepository?.let { repository ->
                    coroutineScope.launch {
                        repository.setCustomThumbnail(
                            playlistId = playlistId,
                            sourceUri = uri,
                            contentResolver = context.contentResolver
                        )
                    }
                }
            }
        }
    )
    val regularPlaylistsFlow = remember(appGraph.playlistRepository) {
        appGraph.playlistRepository?.observeRegularPlaylists()
    }
    val regularPlaylists by regularPlaylistsFlow
        ?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val smartPlaylistsFlow = remember(appGraph.smartPlaylistRepository) {
        appGraph.smartPlaylistRepository?.observeSmartPlaylists()
    }
    val smartPlaylists by smartPlaylistsFlow
        ?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val selectedRegularPlaylistFlow = remember(
        appGraph.playlistRepository,
        selectedRegularPlaylistId
    ) {
        selectedRegularPlaylistId?.let { playlistId ->
            appGraph.playlistRepository?.observePlaylistDetail(playlistId)
        }
    }
    val selectedRegularPlaylist by selectedRegularPlaylistFlow
        ?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) }
    val selectedSmartPlaylistFlow = remember(
        appGraph.smartPlaylistRepository,
        selectedSmartPlaylistId
    ) {
        selectedSmartPlaylistId?.let { playlistId ->
            appGraph.smartPlaylistRepository?.observeSmartPlaylistDetail(playlistId)
        }
    }
    val selectedSmartPlaylist by selectedSmartPlaylistFlow
        ?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) }
    val discoverFlow = remember(appGraph.discoverRepository) {
        appGraph.discoverRepository?.observeDiscover()
    }
    val discoverState by discoverFlow
        ?.collectAsStateWithLifecycle(initialValue = DiscoverUiState())
        ?: remember { mutableStateOf(DiscoverUiState()) }
    var isDiscoverRefreshing by rememberSaveable { mutableStateOf(false) }
    var discoverMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var preparingRemoteItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val artistsFlow = remember(appGraph.metadataRepository) {
        appGraph.metadataRepository?.observeArtists()
    }
    val artists by artistsFlow
        ?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val albumsFlow = remember(appGraph.metadataRepository) {
        appGraph.metadataRepository?.observeAlbums()
    }
    val albums by albumsFlow
        ?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val selectedArtistFlow = remember(appGraph.metadataRepository, selectedArtistName) {
        selectedArtistName?.let { artistName ->
            appGraph.metadataRepository?.observeArtistDetail(artistName)
        }
    }
    val selectedArtist by selectedArtistFlow
        ?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) }
    val selectedAlbumFlow = remember(appGraph.metadataRepository, selectedAlbumKey) {
        selectedAlbumKey?.let { albumKey ->
            appGraph.metadataRepository?.observeAlbumDetail(albumKey)
        }
    }
    val selectedAlbum by selectedAlbumFlow
        ?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) }

    fun refreshStorageUsage() {
        val repository = appGraph.storageUsageRepository ?: return
        coroutineScope.launch {
            storageUsage = repository.snapshot()
        }
    }

    fun performDeleteTracks(trackIds: Collection<String>) {
        val repository = appGraph.trackRepository ?: return
        val distinctTrackIds = trackIds.distinct()
        if (distinctTrackIds.isEmpty()) return
        coroutineScope.launch {
            runCatching {
                appGraph.playbackController?.prepareForTrackDeletion(distinctTrackIds)
            }
            repository.deleteTracks(distinctTrackIds)
            appGraph.playbackController?.refreshQueueFromRepository()
            storageUsage = appGraph.storageUsageRepository?.snapshot()
        }
    }

    fun requestDeleteTracks(tracks: List<LibraryTrack>) {
        val distinctTracks = tracks.distinctBy { it.trackId }
        if (distinctTracks.isEmpty()) return
        if (appSettings.deleteConfirmationEnabled || distinctTracks.size > 1) {
            pendingDeleteTracks = distinctTracks
        } else {
            performDeleteTracks(distinctTracks.map { it.trackId })
        }
    }

    LaunchedEffect(appGraph.storageUsageRepository) {
        storageUsage = appGraph.storageUsageRepository?.snapshot()
    }

    pendingDeleteTracks.takeIf { it.isNotEmpty() }?.let { tracks ->
        DeleteFromFenlzerDialog(
            trackCount = tracks.size,
            singleTrackTitle = tracks.singleOrNull()?.displayTitle,
            onDismiss = { pendingDeleteTracks = emptyList() },
            onConfirm = {
                pendingDeleteTracks = emptyList()
                performDeleteTracks(tracks.map { it.trackId })
            }
        )
    }

    fun launchLocalImportPicker() {
        onImportRequested()
        localImportLauncher?.launch(localImportMimeTypes)
    }

    LaunchedEffect(appGraph.discoverRepository, selectedSmartPlaylistId) {
        if (selectedSmartPlaylistId == SmartPlaylistIds.DISCOVER) {
            appGraph.discoverRepository?.markOpened()
        }
    }

    fun refreshDiscover(broader: Boolean) {
        val repository = appGraph.discoverRepository ?: return
        coroutineScope.launch {
            isDiscoverRefreshing = true
            discoverMessage = null
            runCatching { repository.refresh(broader = broader) }
                .onSuccess { summary ->
                    discoverMessage =
                        "${summary.refreshType.lowercase().replaceFirstChar { it.titlecase() }} Discover: ${summary.itemCount} songs"
                }
                .onFailure { throwable ->
                    discoverMessage = throwable.localizedMessage
                        ?: "Fenlzer could not refresh Discover."
                }
            isDiscoverRefreshing = false
        }
    }

    fun playDiscoverItem(remoteItemId: String) {
        val repository = appGraph.discoverRepository ?: return
        coroutineScope.launch {
            preparingRemoteItemId = remoteItemId
            runCatching { repository.preparePlayFromDiscover(remoteItemId) }
                .onSuccess { remoteItemIds ->
                    if (remoteItemIds.isNotEmpty()) {
                        appGraph.playbackController?.playFromDiscover(remoteItemIds, remoteItemId)
                    }
                }
                .onFailure { throwable ->
                    discoverMessage = throwable.localizedMessage
                        ?: "Fenlzer could not stream this Discover song."
                }
            preparingRemoteItemId = null
        }
    }

    fun playDiscoverItemNext(remoteItemId: String) {
        val repository = appGraph.discoverRepository ?: return
        coroutineScope.launch {
            preparingRemoteItemId = remoteItemId
            runCatching { repository.prepareRemote(remoteItemId, "PLAY_NEXT_REMOTE") }
                .onSuccess { appGraph.playbackController?.playNextRemote(remoteItemId) }
                .onFailure { throwable ->
                    discoverMessage = throwable.localizedMessage
                        ?: "Fenlzer could not prepare this remote song."
                }
            preparingRemoteItemId = null
        }
    }

    fun addDiscoverItemToQueue(remoteItemId: String) {
        val repository = appGraph.discoverRepository ?: return
        coroutineScope.launch {
            preparingRemoteItemId = remoteItemId
            runCatching { repository.prepareRemote(remoteItemId, "ADD_REMOTE_TO_QUEUE") }
                .onSuccess { appGraph.playbackController?.addRemoteToQueue(remoteItemId) }
                .onFailure { throwable ->
                    discoverMessage = throwable.localizedMessage
                        ?: "Fenlzer could not prepare this remote song."
                }
            preparingRemoteItemId = null
        }
    }

    fun importDiscoverItem(remoteItemId: String, favourite: Boolean) {
        val repository = appGraph.discoverRepository ?: return
        coroutineScope.launch {
            runCatching { repository.importRemote(remoteItemId, favourite = favourite) }
                .onSuccess { result ->
                    appGraph.playbackController?.refreshQueueFromRepository()
                    discoverMessage = result.message ?: if (favourite) {
                        "Favourite import started."
                    } else {
                        "Import started."
                    }
                }
                .onFailure { throwable ->
                    discoverMessage = throwable.localizedMessage ?: if (favourite) {
                        "Fenlzer could not favourite this Discover song."
                    } else {
                        "Fenlzer could not import this Discover song."
                    }
                }
        }
    }

    NavHost(
        navController = navController,
        startDestination = FenlzerRoute.Home.route,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        composable(FenlzerRoute.Home.route) {
            BackHandler(enabled = selectedArtistName != null || selectedAlbumKey != null) {
                selectedArtistName = null
                selectedAlbumKey = null
            }

            HomeScreen(
                tracks = libraryTracks,
                artists = artists,
                albums = albums,
                selectedArtist = selectedArtist,
                selectedAlbum = selectedAlbum,
                onImportFromDevice = ::launchLocalImportPicker,
                onSearchYoutube = onImportRequested,
                onSettings = onSettingsRequested,
                onStats = onStatsRequested,
                onOpenArtist = { artistName ->
                    selectedArtistName = artistName
                    selectedAlbumKey = null
                },
                onOpenAlbum = { albumKey ->
                    selectedAlbumKey = albumKey
                    selectedArtistName = null
                },
                onBackToLibrary = {
                    selectedArtistName = null
                    selectedAlbumKey = null
                },
                onRenameArtist = { oldArtist, newArtist ->
                    appGraph.metadataRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.renameArtist(oldArtist, newArtist)
                            selectedArtistName = newArtist
                        }
                    }
                },
                onEditAlbum = { albumKey, draft ->
                    appGraph.metadataRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.editAlbum(albumKey, draft)
                            selectedAlbumKey = null
                        }
                    }
                },
                onChangeAlbumThumbnail = onChangeAlbumThumbnail,
                onTrackClick = { track, searchActive ->
                    appGraph.playbackController?.playFromHome(track.trackId, searchActive)
                },
                onPlayNext = { track ->
                    appGraph.playbackController?.playNext(track.trackId)
                },
                onAddToQueue = { track ->
                    appGraph.playbackController?.addToQueue(track.trackId)
                },
                onAddToPlaylist = { track ->
                    onAddToPlaylist(track.trackId, track.displayTitle)
                },
                onToggleFavourite = { track ->
                    appGraph.trackRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.toggleFavourite(track)
                        }
                    }
                },
                onOpenSongDetails = { track -> onOpenSongDetails(track.trackId) },
                onEditMetadata = { track -> onEditMetadata(track.trackId) },
                onDeleteTracks = ::requestDeleteTracks,
                defaultHomeSort = appSettings.defaultHomeSort
            )
        }
        composable(FenlzerRoute.Playlists.route) {
            BackHandler(
                enabled = selectedRegularPlaylistId != null || selectedSmartPlaylistId != null
            ) {
                selectedRegularPlaylistId = null
                selectedSmartPlaylistId = null
            }

            PlaylistsScreen(
                regularPlaylists = regularPlaylists,
                smartPlaylists = smartPlaylists,
                selectedRegularPlaylist = selectedRegularPlaylist,
                selectedSmartPlaylist = selectedSmartPlaylist,
                discoverState = discoverState,
                discoverContent = {
                    DiscoverScreen(
                        state = discoverState,
                        isRefreshing = isDiscoverRefreshing,
                        message = discoverMessage,
                        preparingRemoteItemId = preparingRemoteItemId,
                        onBack = {
                            selectedSmartPlaylistId = null
                            selectedRegularPlaylistId = null
                        },
                        onRefresh = { refreshDiscover(false) },
                        onRefreshBroader = { refreshDiscover(true) },
                        onPlay = ::playDiscoverItem,
                        onPlayNext = ::playDiscoverItemNext,
                        onAddToQueue = ::addDiscoverItemToQueue,
                        onImport = { remoteItemId -> importDiscoverItem(remoteItemId, favourite = false) },
                        onFavourite = { remoteItemId -> importDiscoverItem(remoteItemId, favourite = true) }
                    )
                },
                libraryTracks = libraryTracks,
                onOpenRegularPlaylist = { playlistId ->
                    selectedRegularPlaylistId = playlistId
                    selectedSmartPlaylistId = null
                },
                onOpenSmartPlaylist = { playlistId ->
                    selectedSmartPlaylistId = playlistId
                    selectedRegularPlaylistId = null
                },
                onBackToList = {
                    selectedRegularPlaylistId = null
                    selectedSmartPlaylistId = null
                },
                onCreatePlaylist = { name ->
                    appGraph.playlistRepository?.let { repository ->
                        coroutineScope.launch {
                            selectedRegularPlaylistId = repository.createPlaylist(name)
                            selectedSmartPlaylistId = null
                        }
                    }
                },
                onRenamePlaylist = { playlistId, name ->
                    appGraph.playlistRepository?.let { repository ->
                        coroutineScope.launch { repository.renamePlaylist(playlistId, name) }
                    }
                },
                onDeletePlaylist = { playlistId ->
                    appGraph.playlistRepository?.let { repository ->
                        coroutineScope.launch { repository.deletePlaylist(playlistId) }
                    }
                },
                onRequestCustomThumbnail = { playlistId ->
                    customThumbnailPlaylistId = playlistId
                    customThumbnailLauncher.launch("image/*")
                },
                onClearCustomThumbnail = { playlistId ->
                    appGraph.playlistRepository?.let { repository ->
                        coroutineScope.launch { repository.clearCustomThumbnail(playlistId) }
                    }
                },
                onAddTrackToPlaylist = { playlistId, trackId ->
                    appGraph.playlistRepository?.let { repository ->
                        coroutineScope.launch { repository.addTrackToPlaylist(playlistId, trackId) }
                    }
                },
                onRemoveTrackFromPlaylist = { playlistId, trackId ->
                    appGraph.playlistRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.removeTrackFromPlaylist(playlistId, trackId)
                            appGraph.queueRepository?.markModifiedIfContainsTrack(trackId)
                        }
                    }
                },
                onReorderPlaylist = { playlistId, orderedTrackIds ->
                    appGraph.playlistRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.reorderPlaylist(playlistId, orderedTrackIds)
                        }
                    }
                },
                onSaveSmartPlaylist = { name, trackIds ->
                    appGraph.playlistRepository?.let { repository ->
                        coroutineScope.launch {
                            selectedRegularPlaylistId = repository.saveStaticPlaylist(name, trackIds)
                            selectedSmartPlaylistId = null
                        }
                    }
                },
                onPlayTrackList = { trackIds, startTrackId, sourceType, sourceId,
                    sourceLabel, insertedBy, shuffle ->
                    appGraph.playbackController?.playFromTrackList(
                        trackIds = trackIds,
                        startTrackId = startTrackId,
                        sourceType = sourceType,
                        sourceId = sourceId,
                        sourceLabel = sourceLabel,
                        insertedBy = insertedBy,
                        shuffle = shuffle
                    )
                },
                onPlayNext = { trackId ->
                    appGraph.playbackController?.playNext(trackId)
                },
                onAddToQueue = { trackId ->
                    appGraph.playbackController?.addToQueue(trackId)
                },
                onToggleFavourite = { trackId, isFavourite ->
                    appGraph.trackRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.setFavourite(trackId = trackId, isFavourite = isFavourite)
                        }
                    }
                },
                onAddToPlaylist = onAddToPlaylist
            )
        }
        composable(FenlzerRoute.Import.route) {
            ImportScreen(
                state = localImportState,
                youtubeState = youtubeImportState,
                onImportFromDevice = ::launchLocalImportPicker,
                onYoutubeQueryChanged = youtubeImportViewModel::onQueryChanged,
                onSearchYoutube = youtubeImportViewModel::search,
                onImportYoutubeResult = youtubeImportViewModel::importResult,
                onYoutubePlaylistUrlChanged = youtubeImportViewModel::onPlaylistUrlChanged,
                onPreviewYoutubePlaylist = youtubeImportViewModel::previewPlaylist,
                onToggleYoutubePlaylistItem = youtubeImportViewModel::togglePlaylistItem,
                onSelectAllYoutubePlaylistItems = youtubeImportViewModel::selectAllPlaylistItems,
                onImportSelectedYoutubePlaylistItems =
                    youtubeImportViewModel::importSelectedPlaylistItems,
                onImportWholeYoutubePlaylist = youtubeImportViewModel::importWholePlaylist,
                onCancelYoutubeImport = youtubeImportViewModel::cancelImport,
                onRetryYoutubeImport = youtubeImportViewModel::retryImport,
                onMoveYoutubeImport = youtubeImportViewModel::moveImport,
                onHistoryFilterChanged = youtubeImportViewModel::setHistoryFilter,
                onClearYoutubeHistory = youtubeImportViewModel::clearHistory,
                onRetryYoutubeHistoryItem = youtubeImportViewModel::retryHistoryItem,
                onRetryFailed = localImportViewModel::importUris,
                onViewLibrary = {
                    navController.navigate(FenlzerRoute.Home.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = tab != FenlzerRoute.Home
                        }
                        launchSingleTop = true
                        restoreState = tab != FenlzerRoute.Home
                    }
                },
                onOpenSongDetails = onOpenSongDetails,
                onClearResult = localImportViewModel::clearResult,
                onClearYoutubeResult = youtubeImportViewModel::clearLastImportResult
            )
        }
        composable(FenlzerRoute.Queue.route) {
            QueueScreen(
                playbackState = playbackState,
                onBack = onCloseQueue,
                onRemoveItem = { queueItemId ->
                    appGraph.playbackController?.removeQueueItem(queueItemId)
                },
                onJumpToItem = { queueItemId ->
                    appGraph.playbackController?.jumpToQueueItem(queueItemId)
                },
                onClearUpcoming = {
                    appGraph.playbackController?.clearUpcoming()
                }
            ,
                onMoveItem = { queueItemId, offset ->
                    coroutineScope.launch { appGraph.queueRepository?.moveQueueItem(queueItemId, offset) }
                },
                onShuffleQueue = {
                    coroutineScope.launch { appGraph.queueRepository?.shuffleQueue() }
                },
                onShuffleUpcoming = {
                    coroutineScope.launch { appGraph.queueRepository?.shuffleUpcoming() }
                },
                onSaveQueueAsPlaylist = { name ->
                    coroutineScope.launch {
                        val trackIds = playbackState.queueItems
                            .filterNot { it.isRemote }
                            .mapNotNull { it.localTrackId ?: it.trackId }
                            .distinct()
                        if (trackIds.isNotEmpty()) {
                            appGraph.playlistRepository?.saveStaticPlaylist(name, trackIds)
                        }
                    }
                })
        }

        composable(FenlzerRoute.Diagnostics.route) {
            ApiDiagnosticsScreen(
                entries = apiDiagnostics,
                onBack = { navController.popBackStack() },
                onClearDiagnostics = {
                    appGraph.database?.let { database ->
                        coroutineScope.launch {
                            database.apiDiagnosticDao().clearAll()
                        }
                    }
                }
            )
        }

        composable(FenlzerRoute.Statistics.route) {
            val statsFlow = remember(appGraph.statsRepository) {
                appGraph.statsRepository?.observeStatisticsSummary()
            }
            val summary by statsFlow
                ?.collectAsStateWithLifecycle(initialValue = StatisticsSummary())
                ?: remember { mutableStateOf(StatisticsSummary()) }
            StatisticsScreen(summary = summary)
        }
        composable(FenlzerRoute.Player.route) {
            FullscreenPlayer(
                playbackState = playbackState,
                privateModeEnabled = appSettings.privateModeEnabledForSession,
                sleepTimerDefaultMinutes = appSettings.sleepTimerDefaultMinutes,
                onMinimize = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FenlzerRoute.Home.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onToggleFavourite = {
                    val currentItem = playbackState.currentItem
                    val localTrackId = currentItem?.localTrackId
                    if (localTrackId != null) {
                        appGraph.trackRepository?.let { repository ->
                            coroutineScope.launch {
                                repository.setFavourite(
                                    trackId = localTrackId,
                                    isFavourite = !currentItem.isFavourite
                                )
                            }
                        }
                    }
                },
                onPlayPause = { appGraph.playbackController?.togglePlayPause() },
                onPrevious = { appGraph.playbackController?.previous() },
                onNext = { appGraph.playbackController?.skipNext() },
                onSeekTo = { positionMs -> appGraph.playbackController?.seekTo(positionMs) },
                onToggleRepeat = { appGraph.playbackController?.cycleRepeatMode() },
                onToggleShuffle = { appGraph.playbackController?.toggleShuffle() },
                onAddToPlaylist = {
                    playbackState.currentItem?.let { item ->
                        item.localTrackId?.let { trackId ->
                            onAddToPlaylist(trackId, item.displayTitle)
                        }
                    }
                },
                onOpenQueue = onOpenQueue,
                onOpenSongDetails = {
                    playbackState.currentItem?.let { item ->
                        item.localTrackId?.let(onOpenSongDetails)
                    }
                },
                onEditMetadata = {
                    playbackState.currentItem?.let { item ->
                        item.localTrackId?.let(onEditMetadata)
                    }
                },
                onDeleteFromFenlzer = {
                    playbackState.currentItem?.localTrackId?.let { trackId ->
                        libraryTracks.firstOrNull { it.trackId == trackId }?.let { track ->
                            requestDeleteTracks(listOf(track))
                        }
                    }
                },
                onStartSleepTimerDuration = { durationMs ->
                    appGraph.playbackController?.startSleepTimerDuration(durationMs)
                },
                onStartSleepTimerEndOfSong = {
                    appGraph.playbackController?.startSleepTimerEndOfSong()
                },
                onStartSleepTimerEndOfQueue = {
                    appGraph.playbackController?.startSleepTimerEndOfQueue()
                },
                onCancelSleepTimer = {
                    appGraph.playbackController?.cancelSleepTimer()
                }
            )
        }
        composable(FenlzerRoute.Settings.route) {
            SettingsScreen(
                settings = appSettings,
                initialApiToken = appGraph.apiRepository.savedToken(),
                onThemeModeChanged = appGraph.settingsRepository::setThemeMode,
                onDefaultRepeatModeChanged = { repeatMode ->
                    appGraph.settingsRepository.setDefaultRepeatMode(repeatMode)
                    appGraph.playbackController?.setRepeatMode(repeatMode.name)
                },
                onDefaultShuffleChanged = appGraph.settingsRepository::setDefaultShuffleEnabled,
                onDefaultHomeSortChanged = appGraph.settingsRepository::setDefaultHomeSort,
                onImportDuplicateBehaviorChanged =
                    appGraph.settingsRepository::setImportDuplicateBehavior,
                onDeleteConfirmationChanged =
                    appGraph.settingsRepository::setDeleteConfirmationEnabled,
                onSleepTimerDefaultMinutesChanged =
                    appGraph.settingsRepository::setSleepTimerDefaultMinutes,
                onPrivateModeChanged = appGraph.settingsRepository::setPrivateModeEnabledForSession,
                onClearListeningHistory = {
                    appGraph.statsRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.clearListeningHistory()
                        }
                    }
                },
                onResetStatistics = {
                    appGraph.statsRepository?.let { repository ->
                        coroutineScope.launch {
                            repository.resetStatistics()
                        }
                    }
                },
                storageUsage = storageUsage,
                onRefreshStorageUsage = ::refreshStorageUsage,
                onClearCache = {
                    appGraph.storageUsageRepository?.let { repository ->
                        coroutineScope.launch {
                            storageUsage = repository.clearCache()
                        }
                    }
                },
                onClearImportHistory = {
                    appGraph.youtubeImportCoordinator?.let { coordinator ->
                        coroutineScope.launch {
                            coordinator.clearImportHistory().await()
                        }
                    }
                },
                onDeleteAllSongs = {
                    appGraph.trackRepository?.let { repository ->
                        coroutineScope.launch {
                            runCatching {
                                appGraph.playbackController?.prepareForTrackDeletion(
                                    libraryTracks.map { it.trackId }
                                )
                            }
                            repository.deleteAllSongs()
                            appGraph.playbackController?.refreshQueueFromRepository()
                            storageUsage = appGraph.storageUsageRepository?.snapshot()
                        }
                    }
                },
                apiDiagnostics = apiDiagnostics,
                onApiSettingsSaved = appGraph.apiRepository::saveApiSettings,
                onTestApiConnection = appGraph.apiRepository::testHealth,
                onOpenDiagnostics = { navController.navigate(FenlzerRoute.Diagnostics.route) },
                appVersion = appVersion
            )
        }
    }
}

@Composable
private fun DeleteFromFenlzerDialog(
    trackCount: Int,
    singleTrackTitle: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val body = if (trackCount == 1 && singleTrackTitle != null) {
        "This permanently removes \"$singleTrackTitle\" from Fenlzer, deletes its copied audio file, and removes playlist and queue references."
    } else {
        "This permanently removes $trackCount songs from Fenlzer, deletes their copied audio files, and removes playlist and queue references."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete from Fenlzer") },
        text = { Text(text = body) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Delete from Fenlzer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FenlzerTopBar(
    currentRoute: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit
) {
    val isSettings = currentRoute == FenlzerRoute.Settings.route
    val isQueue = currentRoute == FenlzerRoute.Queue.route
    val isStatistics = currentRoute == FenlzerRoute.Statistics.route
    val isDiagnostics = currentRoute in setOf(FenlzerRoute.Diagnostics.route, FenlzerRoute.Queue.route)
    val title = when (currentRoute) {
        FenlzerRoute.Playlists.route -> FenlzerRoute.Playlists.label
        FenlzerRoute.Import.route -> FenlzerRoute.Import.label
        FenlzerRoute.Settings.route -> FenlzerRoute.Settings.label
        FenlzerRoute.Statistics.route -> FenlzerRoute.Statistics.label
        FenlzerRoute.Diagnostics.route -> FenlzerRoute.Diagnostics.label
        FenlzerRoute.Queue.route -> FenlzerRoute.Queue.label
        else -> "Fenlzer"
    }

    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            if (isSettings || isQueue || isStatistics || isDiagnostics) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = {
            if (currentRoute == FenlzerRoute.Home.route) {
                IconButton(onClick = onStats) {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = "Open statistics"
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Open settings"
                    )
                }
            }
        }
    )
}

@Composable
private fun FenlzerBottomNavigation(
    currentRoute: String,
    onTabSelected: (FenlzerRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        mainTabs.forEach { tab ->
            val selected = currentRoute == tab.route.route

            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.route.label
                    )
                },
                label = { Text(text = tab.route.label) },
                modifier = Modifier.testTag("tab_${tab.route.route}")
            )
        }
    }
}

@Composable
private fun FenlzerNavigationRail(
    currentRoute: String,
    onTabSelected: (FenlzerRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(modifier = modifier) {
        mainTabs.forEach { tab ->
            NavigationRailItem(
                selected = currentRoute == tab.route.route,
                onClick = { onTabSelected(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.route.label
                    )
                },
                label = { Text(text = tab.route.label) },
                modifier = Modifier.testTag("rail_${tab.route.route}")
            )
        }
    }
}

private data class MainTab(
    val route: FenlzerRoute,
    val icon: ImageVector
)

private data class PendingAddToPlaylist(
    val trackId: String,
    val title: String
)

private data class PendingAlbumThumbnail(
    val albumKey: String,
    val overwriteExistingCustom: Boolean
)

private data class PendingDeleteSong(
    val trackId: String,
    val title: String
)

private val mainTabs = listOf(
    MainTab(FenlzerRoute.Home, Icons.Rounded.Home),
    MainTab(FenlzerRoute.Playlists, Icons.AutoMirrored.Rounded.QueueMusic),
    MainTab(FenlzerRoute.Import, Icons.Rounded.FileDownload)
)
