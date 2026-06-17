package com.fenl.fenlzer.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.FenlzerDatabase
import com.fenl.fenlzer.data.repository.QueueCommandResult
import com.fenl.fenlzer.data.repository.QueueListEditor
import com.fenl.fenlzer.data.repository.QueueRepository
import com.fenl.fenlzer.data.storage.FenlzerStorage
import kotlinx.coroutines.withContext
import java.time.ZoneId

class CarMediaLibraryRepository(
    private val database: FenlzerDatabase,
    private val storage: FenlzerStorage,
    private val queueRepository: QueueRepository,
    private val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val treeBuilder = CarMediaLibraryTreeBuilder(zoneId = zoneId)
    private val mediaItemMapper = CarMediaItemMapper(storage)

    suspend fun rootItem(): MediaItem = withContext(dispatchers.io) {
        mediaItemMapper.mediaItemFor(buildTree().root)
    }

    suspend fun item(mediaId: String): MediaItem? = withContext(dispatchers.io) {
        buildTree().node(mediaId)?.let(mediaItemMapper::mediaItemFor)
    }

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> =
        withContext(dispatchers.io) {
            buildTree()
                .children(parentId = parentId, page = page, pageSize = pageSize)
                .map(mediaItemMapper::mediaItemFor)
        }

    suspend fun childCount(parentId: String): Int = withContext(dispatchers.io) {
        buildTree().childCount(parentId)
    }

    suspend fun search(query: String, page: Int, pageSize: Int): List<MediaItem> =
        withContext(dispatchers.io) {
            buildTree()
                .search(query = query, page = page, pageSize = pageSize)
                .map(mediaItemMapper::mediaItemFor)
        }

    suspend fun searchCount(query: String): Int = withContext(dispatchers.io) {
        buildTree().searchCount(query)
    }

    suspend fun replaceQueueFromRequest(
        mediaItems: List<MediaItem>,
        requestedStartIndex: Int
    ): CarQueueResolution = withContext(dispatchers.io) {
        val tree = buildTree()
        val request = requestFor(tree, mediaItems, requestedStartIndex)
            ?: throw UnsupportedOperationException("No local Android Auto media item could be resolved.")
        val queueResult = queueRepository.replaceWithTrackList(
            trackIds = request.trackIds,
            startTrackId = request.startTrackId,
            sourceType = request.sourceType,
            sourceId = request.sourceId,
            sourceLabel = request.sourceLabel,
            insertedBy = request.insertedBy
        )
        queueResult.toCarQueueResolution()
    }

    suspend fun addQueueItemsFromRequest(mediaItems: List<MediaItem>): List<MediaItem> =
        withContext(dispatchers.io) {
            val tree = buildTree()
            val tracks = mediaItems
                .flatMap { item -> tree.playableTracksFor(listOf(item.mediaId)) }
                .distinctBy { track -> track.trackId }
            if (tracks.isEmpty()) return@withContext emptyList()

            var lastResult: QueueCommandResult? = null
            tracks.forEach { track ->
                lastResult = queueRepository.addToQueue(track.trackId)
            }
            val queueItems = lastResult?.queue?.items.orEmpty()
            tracks.mapNotNull { track ->
                queueItems.lastOrNull { item -> item.localTrackId == track.trackId }
            }.map(QueueMediaItemMapper::mediaItemFor)
        }

    private suspend fun buildTree(): CarMediaLibraryTree {
        val tracks = database.trackDao().getAllTracks()
        val thumbnailIdsByTrackId = tracks.associate { track ->
            track.trackId to (track.thumbnailAssetId ?: track.embeddedThumbnailAssetId)
        }
        val thumbnailIds = thumbnailIdsByTrackId.values
            .filterNotNull()
            .distinct()
        val thumbnailsById = if (thumbnailIds.isEmpty()) {
            emptyMap()
        } else {
            database.trackDao()
                .getThumbnailAssets(thumbnailIds)
                .associateBy { thumbnail -> thumbnail.thumbnailAssetId }
        }
        return treeBuilder.build(
            tracks = tracks,
            playlists = database.playlistDao().getPlaylists(),
            playlistTracks = database.playlistDao().getAllPlaylistTracks(),
            stats = database.playbackDao().getTrackStatsSnapshots(),
            events = database.playbackDao().getNonPrivatePlaybackEvents(),
            thumbnailInternalFilenamesByTrackId = thumbnailIdsByTrackId.mapValues { (_, thumbnailId) ->
                thumbnailId?.let(thumbnailsById::get)?.internalFilename
            }
        )
    }

    private fun requestFor(
        tree: CarMediaLibraryTree,
        mediaItems: List<MediaItem>,
        requestedStartIndex: Int
    ): CarPlaybackRequest? {
        val searchQuery = mediaItems.firstNotNullOfOrNull { item ->
            item.requestMetadata.searchQuery?.toString()?.takeIf { it.isNotBlank() }
        }
        if (searchQuery != null) {
            val trackIds = tree.searchTrackIds(searchQuery)
            if (trackIds.isEmpty()) return null
            return CarPlaybackRequest(
                trackIds = trackIds,
                startTrackId = trackIds.first(),
                sourceType = "ANDROID_AUTO_SEARCH",
                sourceId = null,
                sourceLabel = "Android Auto Search: $searchQuery",
                insertedBy = QueueListEditor.INSERTED_BY_TAP
            )
        }

        val mediaIds = mediaItems
            .mapNotNull { item -> item.mediaId.takeIf { it.isNotBlank() } }
        val tracks = tree.playableTracksFor(mediaIds)
            .distinctBy { track -> track.trackId }
        if (tracks.isEmpty()) return null

        val requestedMediaId = mediaIds.getOrNull(requestedStartIndex.coerceAtLeast(0))
            ?: mediaIds.first()
        val requestedTrackId = CarMediaIds.trackId(requestedMediaId)
            ?.takeIf { trackId -> tracks.any { it.trackId == trackId } }
        val requestedNode = tree.node(requestedMediaId)

        return CarPlaybackRequest(
            trackIds = tracks.map { track -> track.trackId },
            startTrackId = requestedTrackId ?: tracks.first().trackId,
            sourceType = sourceTypeFor(requestedMediaId),
            sourceId = sourceIdFor(requestedMediaId),
            sourceLabel = sourceLabelFor(requestedMediaId, requestedNode),
            insertedBy = insertedByFor(requestedMediaId)
        )
    }

    private fun QueueCommandResult.toCarQueueResolution(): CarQueueResolution {
        val items = queue.items
            .filter { item -> item.localTrackId != null && !item.isRemote }
            .map(QueueMediaItemMapper::mediaItemFor)
        val startIndex = queue.currentQueueItemId
            ?.let { currentId -> items.indexOfFirst { item -> item.mediaId == currentId } }
            ?.takeIf { index -> index >= 0 }
            ?: C.INDEX_UNSET
        return CarQueueResolution(
            mediaItems = items,
            startIndex = startIndex,
            startPositionMs = if (startIndex == C.INDEX_UNSET) C.TIME_UNSET else 0L
        )
    }

    private fun sourceTypeFor(mediaId: String): String =
        when {
            mediaId == CarMediaIds.SONGS -> "ANDROID_AUTO_SONGS"
            CarMediaIds.playlistId(mediaId) != null -> "ANDROID_AUTO_PLAYLIST"
            CarMediaIds.smartPlaylistId(mediaId) != null -> "ANDROID_AUTO_SMART_PLAYLIST"
            else -> "ANDROID_AUTO_TRACK"
        }

    private fun sourceIdFor(mediaId: String): String? =
        CarMediaIds.playlistId(mediaId)
            ?: CarMediaIds.smartPlaylistId(mediaId)
            ?: CarMediaIds.trackId(mediaId)

    private fun sourceLabelFor(mediaId: String, node: CarMediaNode?): String =
        when {
            mediaId == CarMediaIds.SONGS -> "Android Auto Songs"
            node != null -> "Android Auto: ${node.title}"
            else -> "Android Auto"
        }

    private fun insertedByFor(mediaId: String): String =
        when {
            CarMediaIds.playlistId(mediaId) != null -> QueueListEditor.INSERTED_BY_PLAYLIST_START
            CarMediaIds.smartPlaylistId(mediaId) != null -> QueueListEditor.INSERTED_BY_SMART_PLAYLIST_START
            else -> QueueListEditor.INSERTED_BY_TAP
        }
}

data class CarQueueResolution(
    val mediaItems: List<MediaItem>,
    val startIndex: Int,
    val startPositionMs: Long
)

private data class CarPlaybackRequest(
    val trackIds: List<String>,
    val startTrackId: String,
    val sourceType: String,
    val sourceId: String?,
    val sourceLabel: String,
    val insertedBy: String
)
