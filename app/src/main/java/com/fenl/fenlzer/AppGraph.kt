package com.fenl.fenlzer

import android.content.Context
import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.local.FenlzerDatabase
import com.fenl.fenlzer.data.remote.ApiRepository
import com.fenl.fenlzer.data.remote.NoOpApiDiagnosticRecorder
import com.fenl.fenlzer.data.remote.RoomApiDiagnosticRecorder
import com.fenl.fenlzer.data.repository.MetadataRepository
import com.fenl.fenlzer.data.repository.ApiDiagnosticsRepository
import com.fenl.fenlzer.data.repository.PlaylistRepository
import com.fenl.fenlzer.data.repository.QueueRepository
import com.fenl.fenlzer.data.repository.DiscoverRepository
import com.fenl.fenlzer.data.repository.SmartPlaylistRepository
import com.fenl.fenlzer.data.repository.StatsRepository
import com.fenl.fenlzer.data.repository.TrackRepository
import com.fenl.fenlzer.data.settings.AndroidKeystoreApiTokenStore
import com.fenl.fenlzer.data.settings.ApiTokenStore
import com.fenl.fenlzer.data.settings.AppSettingsRepository
import com.fenl.fenlzer.data.settings.DataStoreAppSettingsRepository
import com.fenl.fenlzer.data.settings.InMemoryApiTokenStore
import com.fenl.fenlzer.data.storage.FenlzerStorage
import com.fenl.fenlzer.data.storage.StorageUsageRepository
import com.fenl.fenlzer.domain.delete.DeleteFromFenlzerUseCase
import com.fenl.fenlzer.importing.local.AndroidLocalAudioMetadataExtractor
import com.fenl.fenlzer.importing.ImportQueueCoordinator
import com.fenl.fenlzer.importing.local.LocalImportRepository
import com.fenl.fenlzer.importing.youtube.YoutubeImportCoordinator
import com.fenl.fenlzer.importing.youtube.YoutubeImportRepository
import com.fenl.fenlzer.playback.PlaybackController
import com.fenl.fenlzer.playback.PlaybackStatsTracker
import com.fenl.fenlzer.playback.RemoteStreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppGraph(
    val settingsRepository: AppSettingsRepository,
    val apiTokenStore: ApiTokenStore = InMemoryApiTokenStore(),
    val database: FenlzerDatabase? = null,
    val storage: FenlzerStorage? = null,
    val storageUsageRepository: StorageUsageRepository? = null,
    val applicationContext: Context? = null,
    val dispatchers: FenlzerDispatchers = FenlzerDispatchers(),
    val trackRepository: TrackRepository? = null,
    val deleteFromFenlzerUseCase: DeleteFromFenlzerUseCase? = null,
    val queueRepository: QueueRepository? = null,
    val statsRepository: StatsRepository? = null,
    val playlistRepository: PlaylistRepository? = null,
    val smartPlaylistRepository: SmartPlaylistRepository? = null,
    val metadataRepository: MetadataRepository? = null,
    val discoverRepository: DiscoverRepository? = null,
    val playbackController: PlaybackController? = null,
    val localImportRepository: LocalImportRepository? = null,
    val youtubeImportRepository: YoutubeImportRepository? = null,
    val importQueueCoordinator: ImportQueueCoordinator? = null,
    val youtubeImportCoordinator: YoutubeImportCoordinator? = null,
    val apiDiagnosticsRepository: ApiDiagnosticsRepository? = null,
    val apiRepository: ApiRepository = ApiRepository(
        settingsRepository = settingsRepository,
        tokenStore = apiTokenStore,
        diagnosticRecorder = NoOpApiDiagnosticRecorder(),
        dispatchers = dispatchers
    ),
    private val appScope: CoroutineScope? = null
) {
    fun warmUpPersistence() {
        val scope = appScope ?: return
        val database = database ?: return
        val storage = storage

        scope.launch(dispatchers.io) {
            storage?.ensureDirectories()
            database.openHelper.readableDatabase.query("SELECT 1").use { cursor ->
                cursor.moveToFirst()
            }
            youtubeImportCoordinator?.startRecovery()
        }
        scope.launch(dispatchers.io) {
            discoverRepository?.refreshAtStartupIfEligible()
        }
    }

    companion object {
        fun create(context: Context): AppGraph {
            val appContext = context.applicationContext
            val dispatchers = FenlzerDispatchers()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val database = FenlzerDatabase.create(appContext)
            val settingsRepository = DataStoreAppSettingsRepository(appContext, scope)
            val apiTokenStore = AndroidKeystoreApiTokenStore(appContext)
            val storage = FenlzerStorage(appContext)
            val deleteFromFenlzerUseCase = DeleteFromFenlzerUseCase(
                trackDao = database.trackDao(),
                queueDao = database.queueDao(),
                storage = storage,
                dispatchers = dispatchers
            )
            val trackRepository = TrackRepository(
                trackDao = database.trackDao(),
                playbackDao = database.playbackDao(),
                storage = storage,
                deleteFromFenlzerUseCase = deleteFromFenlzerUseCase,
                dispatchers = dispatchers
            )
            val storageUsageRepository = StorageUsageRepository(
                storage = storage,
                dispatchers = dispatchers
            )
            val queueRepository = QueueRepository(
                queueDao = database.queueDao(),
                trackDao = database.trackDao(),
                remoteDiscoverDao = database.remoteDiscoverDao(),
                storage = storage,
                settingsRepository = settingsRepository,
                dispatchers = dispatchers
            )
            val statsRepository = StatsRepository(
                playbackDao = database.playbackDao(),
                trackDao = database.trackDao(),
                playlistDao = database.playlistDao(),
                dispatchers = dispatchers
            )
            val playlistRepository = PlaylistRepository(
                playlistDao = database.playlistDao(),
                trackDao = database.trackDao(),
                storage = storage,
                dispatchers = dispatchers
            )
            val smartPlaylistRepository = SmartPlaylistRepository(
                trackDao = database.trackDao(),
                playbackDao = database.playbackDao(),
                storage = storage
            )
            val metadataRepository = MetadataRepository(
                trackDao = database.trackDao(),
                playlistDao = database.playlistDao(),
                playbackDao = database.playbackDao(),
                storage = storage,
                dispatchers = dispatchers
            )
            val playbackStatsTracker = PlaybackStatsTracker(
                statsRepository = statsRepository,
                settingsRepository = settingsRepository,
                scope = scope
            )
            val localImportRepository = LocalImportRepository(
                context = appContext,
                trackDao = database.trackDao(),
                importDao = database.importDao(),
                storage = storage,
                metadataExtractor = AndroidLocalAudioMetadataExtractor(),
                dispatchers = dispatchers
            )
            val apiRepository = ApiRepository(
                settingsRepository = settingsRepository,
                tokenStore = apiTokenStore,
                diagnosticRecorder = RoomApiDiagnosticRecorder(database.apiDiagnosticDao()),
                dispatchers = dispatchers
            )
            val apiDiagnosticsRepository = ApiDiagnosticsRepository(
                localDao = database.apiDiagnosticDao(),
                remoteSource = apiRepository
            )
            val remoteStreamResolver = RemoteStreamResolver(
                apiRepository = apiRepository,
                remoteDiscoverDao = database.remoteDiscoverDao()
            )
            val playbackController = PlaybackController(
                context = appContext,
                queueRepository = queueRepository,
                scope = scope,
                statsTracker = playbackStatsTracker,
                remoteStreamResolver = remoteStreamResolver
            )
            val youtubeImportRepository = YoutubeImportRepository(
                apiRepository = apiRepository,
                trackDao = database.trackDao(),
                importDao = database.importDao(),
                remoteDiscoverDao = database.remoteDiscoverDao(),
                playlistDao = database.playlistDao(),
                queueDao = database.queueDao(),
                statsRepository = statsRepository,
                database = database,
                storage = storage,
                dispatchers = dispatchers
            )
            val importQueueCoordinator = ImportQueueCoordinator(
                context = appContext,
                importDao = database.importDao(),
                localRepository = localImportRepository,
                youtubeRepository = youtubeImportRepository
            )
            val youtubeImportCoordinator = YoutubeImportCoordinator(
                repository = youtubeImportRepository,
                importQueueCoordinator = importQueueCoordinator,
                scope = scope
            )
            val discoverRepository = DiscoverRepository(
                apiRepository = apiRepository,
                trackDao = database.trackDao(),
                playbackDao = database.playbackDao(),
                remoteDiscoverDao = database.remoteDiscoverDao(),
                queueRepository = queueRepository,
                streamResolver = remoteStreamResolver,
                youtubeImportCoordinator = youtubeImportCoordinator,
                dispatchers = dispatchers
            )

            return AppGraph(
                settingsRepository = settingsRepository,
                apiTokenStore = apiTokenStore,
                database = database,
                storage = storage,
                storageUsageRepository = storageUsageRepository,
                applicationContext = appContext,
                dispatchers = dispatchers,
                trackRepository = trackRepository,
                deleteFromFenlzerUseCase = deleteFromFenlzerUseCase,
                queueRepository = queueRepository,
                statsRepository = statsRepository,
                playlistRepository = playlistRepository,
                smartPlaylistRepository = smartPlaylistRepository,
                metadataRepository = metadataRepository,
                discoverRepository = discoverRepository,
                playbackController = playbackController,
                localImportRepository = localImportRepository,
                youtubeImportRepository = youtubeImportRepository,
                importQueueCoordinator = importQueueCoordinator,
                youtubeImportCoordinator = youtubeImportCoordinator,
                apiDiagnosticsRepository = apiDiagnosticsRepository,
                apiRepository = apiRepository,
                appScope = scope
            ).also { graph ->
                graph.warmUpPersistence()
            }
        }
    }
}
