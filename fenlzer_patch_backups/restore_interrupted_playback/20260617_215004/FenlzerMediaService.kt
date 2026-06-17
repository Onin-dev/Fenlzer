package com.fenl.fenlzer.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.fenl.fenlzer.R
import com.fenl.fenlzer.FenlzerApplication
import com.fenl.fenlzer.MainActivity
import com.fenl.fenlzer.data.local.FenlzerDatabase
import com.fenl.fenlzer.data.repository.QueueRepository
import com.fenl.fenlzer.data.repository.TrackRepository
import com.fenl.fenlzer.data.settings.DataStoreAppSettingsRepository
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class FenlzerMediaService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null
    private var carLibraryChangeJob: Job? = null

    private val serviceDatabaseDelegate = lazy { FenlzerDatabase.create(applicationContext) }
    private val serviceDatabase by serviceDatabaseDelegate
    private val serviceSettingsRepository by lazy {
        DataStoreAppSettingsRepository(applicationContext, serviceScope)
    }
    private val serviceStorage by lazy { FenlzerStorage(applicationContext) }
    private val serviceArtworkCache by lazy { PlaybackArtworkCache(applicationContext) }
    private val serviceQueueRepository by lazy {
        QueueRepository(
            queueDao = serviceDatabase.queueDao(),
            trackDao = serviceDatabase.trackDao(),
            remoteDiscoverDao = serviceDatabase.remoteDiscoverDao(),
            storage = serviceStorage,
            settingsRepository = serviceSettingsRepository
        )
    }
    private val serviceTrackRepository by lazy {
        TrackRepository(
            trackDao = serviceDatabase.trackDao(),
            playbackDao = serviceDatabase.playbackDao(),
            storage = serviceStorage
        )
    }

    private val existingAppGraph
        get() = (application as FenlzerApplication).appGraphIfInitialized()

    private val queueRepository: QueueRepository
        get() = existingAppGraph?.queueRepository ?: serviceQueueRepository

    private val trackRepository: TrackRepository
        get() = existingAppGraph?.trackRepository ?: serviceTrackRepository

    private fun activeCarLibraryRepository(): CarMediaLibraryRepository {
        val appGraph = existingAppGraph
        val appDatabase = appGraph?.database
        val appStorage = appGraph?.storage
        val appQueueRepository = appGraph?.queueRepository
        return if (appDatabase != null && appStorage != null && appQueueRepository != null) {
            CarMediaLibraryRepository(
                database = appDatabase,
                storage = appStorage,
                queueRepository = appQueueRepository,
                dispatchers = appGraph.dispatchers
            )
        } else {
            serviceCarLibraryRepository
        }
    }

    private val serviceCarLibraryRepository by lazy {
        CarMediaLibraryRepository(
            database = serviceDatabase,
            storage = serviceStorage,
            queueRepository = serviceQueueRepository
        )
    }

    private val carRootItem: MediaItem by lazy {
        CarMediaItemMapper(serviceStorage).mediaItemFor(
            CarMediaNode.browsable(
                mediaId = CarMediaIds.ROOT,
                title = "Fenlzer"
            )
        )
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exoPlayer = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
            }
        exoPlayer.addListener(playerListener)
        player = exoPlayer
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.mipmap.ic_launcher)
            }
        )
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            sessionCallback
        )
            .setMediaButtonPreferences(emptyList())
            .setSessionActivity(sessionActivity())
            .build()

        restorePersistedQueueForSession()
        startPositionTicker()
        startCarLibraryChangeObserver()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        positionJob?.cancel()
        positionJob = null
        carLibraryChangeJob?.cancel()
        carLibraryChangeJob = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        serviceScope.cancel()
        if (serviceDatabaseDelegate.isInitialized()) {
            serviceDatabase.close()
        }

        super.onDestroy()
    }

    private val sessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = SessionCommands.Builder()
                .add(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT)
                .add(FenlzerMediaSessionCommands.ToggleFavourite)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(emptyList())
                .setSessionActivity(sessionActivity())
                .build()
        }

        override fun onPostConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            refreshFavouriteButton()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != FenlzerMediaSessionCommands.ACTION_TOGGLE_FAVOURITE) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }

            val future = SettableFuture.create<SessionResult>()
            serviceScope.launch {
                runCatching {
                    val queue = queueRepository.snapshot()
                    val target = MediaFavouriteCommandTargetResolver.resolve(
                        currentMediaId = player?.currentMediaItem?.mediaId,
                        queueItems = queue.items
                    ) ?: return@runCatching SessionResult(SessionError.ERROR_INVALID_STATE)

                    trackRepository.setFavourite(
                        trackId = target.trackId,
                        isFavourite = !target.isFavourite
                    )
                    existingAppGraph?.playbackController?.refreshQueueFromRepository()
                    refreshFavouriteButton()
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
                    .onSuccess(future::set)
                    .onFailure(future::setException)
            }
            return future
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(carRootItem, params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            serviceFuture {
                activeCarLibraryRepository().item(mediaId)
                    ?.let { item -> LibraryResult.ofItem(item, null) }
                    ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            serviceFuture {
                LibraryResult.ofItemList(
                    activeCarLibraryRepository().children(
                        parentId = parentId,
                        page = page,
                        pageSize = pageSize
                    ),
                    params
                )
            }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> =
            serviceFuture {
                val repository = activeCarLibraryRepository()
                val item = repository.item(parentId)
                if (item?.mediaMetadata?.isBrowsable != true) {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE, params)
                } else {
                    session.notifyChildrenChanged(
                        browser,
                        parentId,
                        repository.childCount(parentId),
                        params
                    )
                    LibraryResult.ofVoid(params)
                }
            }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> =
            serviceFuture {
                session.notifySearchResultChanged(
                    browser,
                    query,
                    activeCarLibraryRepository().searchCount(query),
                    params
                )
                LibraryResult.ofVoid(params)
            }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            serviceFuture {
                LibraryResult.ofItemList(
                    activeCarLibraryRepository().search(
                        query = query,
                        page = page,
                        pageSize = pageSize
                    ),
                    params
                )
            }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> =
            serviceFuture {
                if (!CarMediaRequestClassifier.isCarLibraryRequest(mediaItems)) {
                    return@serviceFuture mediaItems
                }
                val items = activeCarLibraryRepository()
                    .addQueueItemsFromRequest(mediaItems)
                    .takeIf { items -> items.isNotEmpty() }
                    ?: throw UnsupportedOperationException("No local Android Auto media items could be added.")
                existingAppGraph?.playbackController?.refreshQueueFromRepository()
                items
            }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            serviceFuture {
                if (!CarMediaRequestClassifier.isCarLibraryRequest(mediaItems)) {
                    return@serviceFuture MediaSession.MediaItemsWithStartPosition(
                        mediaItems,
                        startIndex,
                        startPositionMs
                    )
                }
                val queue = activeCarLibraryRepository().replaceQueueFromRequest(
                    mediaItems = mediaItems,
                    requestedStartIndex = startIndex
                )
                existingAppGraph?.playbackController?.refreshQueueFromRepository()
                MediaSession.MediaItemsWithStartPosition(
                    queue.mediaItems,
                    queue.startIndex,
                    queue.startPositionMs
                )
            }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            serviceFuture { mediaItemsWithStartPositionForResume(metadataOnly = !isForPlayback) }
    }

    private fun <T> serviceFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            runCatching { block() }
                .onSuccess(future::set)
            .onFailure(future::setException)
        }
        return future
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val queueItemId = mediaItem?.mediaId
            if (queueItemId != null) {
                serviceScope.launch {
                    queueRepository.markCurrent(
                        queueItemId = queueItemId,
                        playbackPositionMs = 0L,
                        wasPlaying = player?.isPlaying
                    )
                    refreshFavouriteButton()
                }
            } else {
                refreshFavouriteButton()
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
            ) {
                persistPlaybackState(player)
            }
            if (
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
            ) {
                refreshFavouriteButton()
            }
        }
    }

    private fun restorePersistedQueueForSession() {
        serviceScope.launch {
            val queue = queueRepository.snapshot()
            val resumeItems = QueueMediaItemMapper.localResumeItems(queue) { item ->
                serviceArtworkCache.squareArtworkUriFor(
                    sourceUri = item.remoteThumbnailUri ?: item.thumbnailUri,
                    fallbackUri = item.thumbnailUri
                ) ?: item.thumbnailUri
            }
            if (resumeItems.mediaItems.isEmpty()) return@launch

            withContext(Dispatchers.Main.immediate) {
                val exoPlayer = player ?: return@withContext
                if (exoPlayer.mediaItemCount > 0) return@withContext
                exoPlayer.setMediaItems(
                    resumeItems.mediaItems,
                    resumeItems.startIndex,
                    resumeItems.startPositionMs
                )
                exoPlayer.repeatMode = when (queue.repeatMode) {
                    "ONE" -> Player.REPEAT_MODE_ONE
                    "OFF" -> Player.REPEAT_MODE_OFF
                    else -> Player.REPEAT_MODE_ALL
                }
                // The persisted queue is already physically ordered for shuffle.
                // Keep Media3 shuffle disabled so service resumption and car playback
                // follow the same order shown by the app Queue screen.
                exoPlayer.shuffleModeEnabled = false
                exoPlayer.prepare()
                exoPlayer.playWhenReady = false
                refreshFavouriteButton()
            }
        }
    }

    private suspend fun mediaItemsWithStartPositionForResume(
        metadataOnly: Boolean
    ): MediaSession.MediaItemsWithStartPosition {
        val queue = queueRepository.snapshot()
        val resumeItems = QueueMediaItemMapper.localResumeItems(queue) { item ->
            serviceArtworkCache.squareArtworkUriFor(
                sourceUri = item.remoteThumbnailUri ?: item.thumbnailUri,
                fallbackUri = item.thumbnailUri
            ) ?: item.thumbnailUri
        }
        if (resumeItems.mediaItems.isEmpty()) {
            throw UnsupportedOperationException("No local queue available for playback resumption.")
        }
        return if (metadataOnly) {
            val index = resumeItems.startIndex.takeIf { it != C.INDEX_UNSET } ?: 0
            MediaSession.MediaItemsWithStartPosition(
                listOf(resumeItems.mediaItems[index]),
                0,
                0L
            )
        } else {
            MediaSession.MediaItemsWithStartPosition(
                resumeItems.mediaItems,
                resumeItems.startIndex,
                resumeItems.startPositionMs
            )
        }
    }

    private fun refreshFavouriteButton() {
        val session = mediaLibrarySession ?: return
        serviceScope.launch {
            val queue = queueRepository.snapshot()
            val target = MediaFavouriteCommandTargetResolver.resolve(
                currentMediaId = player?.currentMediaItem?.mediaId,
                queueItems = queue.items
            )
            val buttons = target?.let {
                listOf(FenlzerMediaSessionCommands.favouriteButton(it.isFavourite))
            } ?: emptyList()
            withContext(Dispatchers.Main.immediate) {
                session.setMediaButtonPreferences(buttons)
            }
        }
    }

    private fun startPositionTicker() {
        if (positionJob != null) return
        positionJob = serviceScope.launch {
            while (true) {
                player?.let(::persistPlaybackState)
                delay(POSITION_PERSIST_INTERVAL_MS)
            }
        }
    }

    private fun startCarLibraryChangeObserver() {
        if (carLibraryChangeJob != null) return
        carLibraryChangeJob = serviceScope.launch {
            combine(
                serviceDatabase.trackDao().observeTracksByRecentlyAdded(),
                serviceDatabase.playlistDao().observePlaylists(),
                serviceDatabase.playlistDao().observePlaylistTracks(),
                serviceDatabase.playbackDao().observeTrackStatsSnapshots(),
                serviceDatabase.playbackDao().observePlaybackEvents()
            ) { tracks, playlists, playlistTracks, _, _ ->
                CarLibraryNotificationCounts(
                    songs = tracks.size,
                    regularPlaylists = playlists
                        .filter { playlist ->
                            playlist.playlistType == CarMediaLibraryTreeBuilder.REGULAR_PLAYLIST_TYPE
                        }
                        .associate { playlist ->
                            playlist.playlistId to playlistTracks.count { track ->
                                track.playlistId == playlist.playlistId
                            }
                        }
                )
            }.collect { counts ->
                val session = mediaLibrarySession ?: return@collect
                session.notifyChildrenChanged(CarMediaIds.ROOT, ROOT_CHILD_COUNT, null)
                session.notifyChildrenChanged(CarMediaIds.SONGS, counts.songs, null)
                session.notifyChildrenChanged(CarMediaIds.PLAYLISTS, counts.regularPlaylists.size, null)
                session.notifyChildrenChanged(
                    CarMediaIds.SMART_PLAYLISTS,
                    CarMediaLibraryTreeBuilder.ANDROID_AUTO_SMART_PLAYLIST_IDS.size,
                    null
                )
                counts.regularPlaylists.forEach { (playlistId, childCount) ->
                    session.notifyChildrenChanged(CarMediaIds.playlist(playlistId), childCount, null)
                }
            }
        }
    }

    private fun persistPlaybackState(player: Player) {
        val mediaId = player.currentMediaItem?.mediaId
        serviceScope.launch {
            if (mediaId != null) {
                queueRepository.markCurrent(
                    queueItemId = mediaId,
                    playbackPositionMs = player.currentPosition.coerceAtLeast(0L),
                    wasPlaying = player.isPlaying || player.playWhenReady
                )
            } else {
                queueRepository.persistPlaybackState(
                    playbackPositionMs = player.currentPosition.coerceAtLeast(0L),
                    wasPlaying = player.isPlaying || player.playWhenReady
                )
            }
        }
    }

    private fun sessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private companion object {
        const val POSITION_PERSIST_INTERVAL_MS = 1_000L
        const val ROOT_CHILD_COUNT = 3
    }
}

private data class CarLibraryNotificationCounts(
    val songs: Int,
    val regularPlaylists: Map<String, Int>
)
