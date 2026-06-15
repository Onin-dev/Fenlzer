# Fenlzer V1 Remaining Implementation Report

Status: source-to-specification audit and remaining implementation plan.

Audit date: 2026-06-15.

Authority order used for this report:

1. `Fenlzer_Final_Application_Plan.md`
2. `Fenlzer_Database_Schema.md`
3. `Fenlzer_API_Endpoint_Contract.md`
4. Android API contract files
5. API implementation/specification notes
6. `Fenlzer_Working_Plan.md`

The original `Fenlzer_Phases_Plan.md` ends at Phase 15. The phases in this report
continue from Phase 16 and replace any assumption that the current source already
meets every Phase 14 and Phase 15 acceptance criterion.

## Executive summary

Fenlzer already contains most of the phone application required for V1:

- Kotlin/Compose architecture, Room, DataStore, Retrofit, Coil, Media3, and the
  required persistent app shell are present.
- Home, local import, YouTube search/import, playlist import, Active Imports,
  Import History, playback, persistent queue, mini-player, fullscreen player,
  playlists, smart playlists, Discover, metadata editing, Song Details, Artists,
  Albums, statistics, settings, storage management, and deletion flows all have
  substantial working implementations.
- Core YouTube transfer behavior includes API job polling, file transfer,
  SHA-256 verification, local insertion, API confirmation, duplicate handling,
  cancellation endpoints, retry endpoints, reorder endpoints, and restart
  recovery records.
- Discover can persist recommendations, stream remote items, retry an expired or
  failed stream once, import a remote item, and convert queue/stat references to
  a local track.

Fenlzer is nevertheless not a complete V1 yet. The principal blockers are:

- Android Auto is not connected to the MediaLibrary session.
- Import execution is process-bound instead of WorkManager/foreground-backed.
- Several Active Imports and automatic pending-action requirements are missing.
- Audio output routing and Share remain disabled placeholders.
- Discover startup refresh and history chunking rules are incomplete.
- Import provenance, local import completion actions, permanent YouTube artwork,
  and two statistics calculations need correction.
- Instrumented tests currently fail to compile, and the required migration,
  worker, MediaLibrary, process-death, and final regression coverage is absent.

## Current implementation coverage

### Implemented or substantially complete

- Three-tab navigation with adaptive landscape navigation and persistent
  mini-player.
- API base URL/token settings, health check, local sanitized diagnostics, and
  cleartext network support for personal/LAN API deployments.
- Room database through schema version 3, exported schemas, app-private audio,
  thumbnail, cache, and temporary import directories.
- Local multi-file import for MP3, M4A, WAV, FLAC, and OGG, metadata extraction,
  embedded artwork, content-hash duplicate detection, progress, grouped results,
  retry, and history records.
- YouTube search with five results, single import, playlist preview and polling,
  selected/whole-playlist import, exact API job states, transfer verification,
  confirmation, duplicate merge, cancel, retry, reorder, and history filtering.
- Media3 playback service and controller, persistent queue, source and modified
  labels, duplicate prevention, Play Next, Add to Queue, reorder, swipe removal,
  current-song deletion handling, repeat, shuffle, previous/next, sleep timer,
  headset controls, lock-screen/notification playback controls, and noisy-device
  handling.
- Home search/sort/filter/selection flows, Artists and Albums browsing, regular
  playlists, smart playlists, generated/custom covers, playlist details, and
  adaptive playlist layouts.
- Metadata editing with save/discard, field reset, reset-all, custom thumbnails,
  bulk artist/album editing, merge warnings, overwrite behavior, and bulk audit
  records.
- Song Details, per-song statistics, global statistics, listening history,
  sessions, private mode, recovery progress, storage accounting, typed DELETE,
  and file/reference cleanup.
- Discover as a smart playlist, persistent snapshots, manual/broader refresh,
  remote streaming, current/next-two prefetch, stream retry/skip, remote queue
  badges, download/favourite actions, and remote-to-local queue/stat conversion.
- Dark and AMOLED presentation plus the principal portrait/landscape layouts.

### Partially implemented

#### Android Auto and MediaSession platform behavior

- `CarMediaLibraryTreeBuilder` implements the intended local tree and smart
  playlist allow-list, but `FenlzerMediaService` installs an empty
  `MediaLibrarySession.Callback`.
- No browse-root, children, item, search, or playback preparation callbacks use
  the tree.
- Android media resumption is not implemented in the service callback.
- The favourite command resolver exists, but no MediaSession custom command or
  command button invokes it from Android Auto or notification controls.
- Remote-current Now Playing behavior and remote exclusion from Auto queue/browse
  are therefore not implemented end to end.

Key evidence:

- `app/src/main/java/com/fenl/fenlzer/playback/FenlzerMediaService.kt`
- `app/src/main/java/com/fenl/fenlzer/playback/CarMediaLibraryTreeBuilder.kt`
- `app/src/main/java/com/fenl/fenlzer/playback/CarFavouriteCommandResolver.kt`

#### Import durability and Active Imports

- YouTube imports run in an application coroutine protected by one global mutex.
  This serializes client-side import completion and is not durable background
  execution.
- Recovery starts only when `AppGraph` is initialized. It restores records after
  reopening the app but does not provide WorkManager/foreground continuation
  after ordinary process death.
- No import worker, foreground import service, `ForegroundInfo`, or global import
  notification exists despite WorkManager being included as a dependency.
- Active Imports queries only non-terminal statuses. Completed jobs disappear
  immediately instead of remaining until the user leaves the screen, and failed
  terminal jobs do not remain until dismissed.
- Active Imports is not split into Downloading Now and Upcoming sections.
- There is no app-wide running-import chip/banner outside the Import screen.
- Local import jobs have no UI cancellation path tied to cancellation of the
  running copy coroutine.
- Automatic retry of failed YouTube imports up to three attempts is not managed
  by the application executor.

Key evidence:

- `app/src/main/java/com/fenl/fenlzer/importing/youtube/YoutubeImportCoordinator.kt`
- `app/src/main/java/com/fenl/fenlzer/importing/youtube/YoutubeImportRepository.kt`
- `app/src/main/java/com/fenl/fenlzer/data/local/dao/ImportDao.kt`
- `app/src/main/java/com/fenl/fenlzer/AppGraph.kt`

#### Pending actions, provenance, and import completion UX

- Discover's explicit Favourite action imports and favourites the result, but
  favouriting a remote current item from the mini/fullscreen player does nothing.
- Adding a remote Discover item to a regular playlist is unavailable; no durable
  `targetPlaylistId` pending action is completed after import.
- Every API import uses manual priority even though the contract defines automatic
  favourite and playlist-add priorities.
- Import History labels every YouTube import as `Manual YouTube search import`,
  including playlist and Discover jobs.
- Imported tracks use generic source type `YOUTUBE` instead of the required source
  distinctions such as search, playlist, Discover manual, and Discover automatic.
- Local import results group success/duplicate/failure and support retry/details,
  but do not offer Play Imported Songs, Add Imported Songs to Playlist, or Home
  navigation with the imported tracks highlighted.
- YouTube artwork remains a remote URL after import. It is not promoted to
  permanent app-private thumbnail storage, so offline artwork is not guaranteed.

Key evidence:

- `app/src/main/java/com/fenl/fenlzer/ui/FenlzerApp.kt`
- `app/src/main/java/com/fenl/fenlzer/ui/importing/ImportScreen.kt`
- `app/src/main/java/com/fenl/fenlzer/data/local/entity/ImportApiEntities.kt`
- `app/src/main/java/com/fenl/fenlzer/importing/youtube/YoutubeImportRepository.kt`

#### Discover lifecycle and history upload

- Opening Discover updates `lastOpenedAt`, but startup never evaluates the rule:
  refresh only when the snapshot is older than eight hours and Discover has been
  opened since the previous refresh.
- Listening history is always encoded as one Zstandard chunk rather than chunks
  targeting roughly 1 MB.
- Pending playlist conversion is absent, although queue and non-private playback
  event conversion are implemented.

Key evidence:

- `app/src/main/java/com/fenl/fenlzer/data/repository/DiscoverRepository.kt`
- `app/src/main/java/com/fenl/fenlzer/data/local/dao/RemoteDiscoverDao.kt`

#### Fullscreen player and external controls

- Audio Output is visibly disabled and has no route picker implementation.
- Share is visibly disabled and has no Android share intent implementation.
- Share must use the original YouTube URL for YouTube/Discover items and title/
  artist text for local imports.
- Notification and Android Auto favourite controls are absent.

Key evidence:

- `app/src/main/java/com/fenl/fenlzer/ui/player/FullscreenPlayer.kt`
- `app/src/main/java/com/fenl/fenlzer/playback/FenlzerMediaService.kt`

#### Statistics correctness

- The global statistics flow observes only the latest 500 events, so listening by
  day/hour, streak, rediscovery, and recent-history-derived calculations can become
  incomplete for a long-lived library.
- Average completion uses `playCount + skipCount` as the previous sample count,
  which excludes non-valid, non-skip partial playback events even though their
  completion percentages are included when updating the average.
- These calculations need a durable event count or database aggregate queries.

Key evidence:

- `app/src/main/java/com/fenl/fenlzer/data/repository/StatsRepository.kt`
- `app/src/main/java/com/fenl/fenlzer/data/local/entity/PlaybackEntities.kt`

#### Diagnostics, backup, and release hygiene

- The API service declares `/v1/diagnostics/recent`, but the settings diagnostics
  UI displays only local Room records.
- `android:allowBackup` is true and both backup XML files are untouched templates.
  This conflicts with the settled V1 boundary that imported files are lost when
  the app is uninstalled and that backup/export is not part of V1.
- Two obsolete `PlaylistsScreen.kt.*backup` files remain in the source tree.
- Exact purple production color remains unspecified, as already noted in the
  original phase plan.

Key evidence:

- `app/src/main/java/com/fenl/fenlzer/data/remote/FenlzerApiService.kt`
- `app/src/main/java/com/fenl/fenlzer/settings/ApiDiagnosticsScreen.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

## Verification state at audit time

- Main debug Kotlin compilation succeeds.
- JVM unit tests succeed.
- Instrumented test compilation fails before device execution:
  - `ApiDiagnosticsScreenTest` imports an unavailable `assertDoesNotExist` symbol.
  - `SettingsScreenTest` does not provide the required `onOpenDiagnostics`
    callback.
- No Room migration test suite exists for schema versions 1 -> 2 -> 3 or future
  migrations.
- No worker/import process-death tests exist.
- No MediaLibrary callback or Android Auto integration tests exist.
- No automated tests cover pending remote favourite/add-to-playlist completion.
- A full lint/assembly command did not complete during this audit and must be
  rerun as part of the release phases.

## Remaining implementation phases

## Phase 16 — V1 Baseline, Data Contracts, and Test Repair

### Goal

Establish a clean, testable baseline and add the persistent fields/contracts needed
by the remaining import, Discover, and platform work.

### Main work

- Repair all current Android instrumented-test compilation errors.
- Add source/import provenance capable of distinguishing local file, YouTube
  search, YouTube playlist, Discover manual, Discover auto-favourite, and Discover
  auto-playlist imports.
- Persist requested format, final format, import reason, and pending action intent
  wherever the existing job/track/history records cannot currently preserve them.
- Add the next Room migration and exported schema without destructive fallback.
- Add migration tests for every supported upgrade path.
- Connect `/v1/diagnostics/recent` to a combined local/server diagnostics screen,
  preserving token sanitization.
- Explicitly disable Android backup or add exhaustive exclusions consistent with
  the final product boundary.
- Remove obsolete backup source artifacts.

### Key files/modules

- `data/local/FenlzerDatabase.kt`, Room entities, DAOs, and exported schemas
- `data/remote/ApiRepository.kt`, `FenlzerApiService.kt`, diagnostics models
- `settings/ApiDiagnosticsScreen.kt`
- `AndroidManifest.xml`, `res/xml/backup_rules.xml`,
  `res/xml/data_extraction_rules.xml`
- `app/src/androidTest`, Room migration tests

### Acceptance criteria

- Debug app, JVM tests, and instrumented-test sources compile.
- Existing version-3 data migrates without loss.
- Track, job, and history records preserve exact import source and reason.
- Diagnostics can display local and server entries without exposing credentials.
- Backup behavior matches the V1 uninstall/data-loss boundary.
- The application remains buildable and all phone features from completed phases
  still launch.

### Manual app tests

1. Upgrade an installed database containing tracks, playlists, queue, stats,
   Discover, and import history.
2. Verify all data remains visible and playable after migration.
3. Open diagnostics with the API online and offline; verify local/server states and
   sanitized failures.
4. Confirm API settings and ordinary offline playback still work.

### Automated tests

- Room migrations from 1 -> latest, 2 -> latest, and 3 -> latest.
- API diagnostics repository tests for online, offline, malformed, and sanitized
  responses.
- Compose diagnostics filter/clear tests.
- Full debug and test-source compilation.

### Risks / notes

- Provenance fields must be added before durable import workers begin creating new
  records, otherwise another migration and compatibility path will be needed.

## Phase 17 — Durable Import Executor and Complete Active Imports

### Goal

Make local and API-backed imports durable, cancellable, observable, and compliant
with Active Imports behavior across process death and app navigation.

### Main work

- Replace process-bound import orchestration with unique WorkManager work and
  foreground execution for long transfers.
- Maintain one global expandable import notification with per-job details.
- Resume polling, transfer, verification, confirmation, and pending completion
  after ordinary process death without requiring playback to keep the process alive.
- Keep server-side concurrency authoritative while allowing up to three current
  API downloads and avoiding the existing whole-run client mutex bottleneck.
- Implement application retry policy up to three attempts for retryable failures,
  preserving exact server state and idempotency keys.
- Make queued and running local imports cancellable and clean partial files.
- Split Active Imports into Downloading Now and Upcoming sections.
- Keep completed jobs visible until the user leaves Active Imports and failed jobs
  visible until explicitly dismissed.
- Add a persistent running-import chip/banner accessible outside the Import screen.
- Preserve user reorder and manual-over-automatic priority through restart.

### Key files/modules

- `importing/ImportQueueCoordinator.kt`
- `importing/ImportWorker.kt`, foreground notification controller
- `importing/youtube/YoutubeImportRepository.kt`
- `importing/local/LocalImportRepository.kt`
- `data/local/dao/ImportDao.kt`
- `ui/importing/ImportScreen.kt`, `YoutubeImportViewModel.kt`,
  `LocalImportViewModel.kt`
- `FenlzerApplication.kt`, WorkManager configuration

### Acceptance criteria

- Imports continue or resume correctly after ordinary process death.
- Force-stop leaves durable jobs recoverable on the next user launch.
- Queued and running jobs can be cancelled and leave no partial file.
- Up to three server jobs can be represented as downloading concurrently.
- Manual jobs are ahead of automatic jobs when the next slot becomes available.
- Reorder persists and is restored from the API/local database.
- Active Imports terminal-item visibility exactly follows the specification.
- A foreground notification accurately represents global import progress.

### Manual app tests

1. Start three YouTube imports and one queued import; navigate throughout the app.
2. Kill the app process during metadata download, server download, local transfer,
   verification, and confirmation; relaunch and verify completion.
3. Force-stop during an import, relaunch manually, and verify recovery.
4. Cancel one queued, one server-running, one transferring, and one local import.
5. Reorder upcoming jobs, restart, and verify the same order.
6. Complete and fail jobs, verify terminal visibility and dismissal rules.
7. Verify the global notification and in-app banner from Home and Playlists.

### Automated tests

- WorkManager tests for scheduling, uniqueness, retry, cancellation, and recovery.
- Repository tests for terminal visibility and dismissal.
- MockWebServer tests for recovery through every API job state.
- File cleanup tests for cancellation and failed verification.
- Priority/reorder tests including mixed manual and automatic jobs.

### Risks / notes

- WorkManager cannot execute while Android has the package force-stopped. The V1
  guarantee is durable recovery on the next launch, not bypassing platform rules.

## Phase 18 — Pending Remote Actions and Import Completion Experience

### Goal

Complete every path by which remote or newly imported songs become normal local
Fenlzer tracks and finish the post-import user experience.

### Main work

- Support remote Favourite from Discover, mini-player, fullscreen player, and
  notification/Auto command paths by creating an automatic pending import.
- Support adding a remote Discover song to a regular playlist by persisting the
  target playlist and completing the add after import or duplicate merge.
- Use automatic job priority/reasons for pending actions while preserving manual
  import priority.
- Complete pending favourite, playlist, queue, and non-private statistics
  references atomically after import or duplicate detection.
- Record accurate source type and history reason for search, playlist, Discover
  manual, auto-favourite, auto-playlist, duplicate, retry, and cancellation paths.
- Add Play Imported Songs, Add Imported Songs to Playlist, and View in Library
  with imported-song highlighting to local import results.
- Download and promote YouTube/Discover artwork into permanent private thumbnail
  storage after successful import, while preserving custom-thumbnail priority.

### Key files/modules

- `DiscoverRepository.kt`, `YoutubeImportRepository.kt`
- `ImportJobEntity`, `ImportHistoryEntryEntity`, track provenance fields
- `PlaylistRepository.kt`, `QueueRepository.kt`, `StatsRepository.kt`
- `FenlzerApp.kt`, `ImportScreen.kt`, Home highlight/selection state
- thumbnail storage and Coil/network helpers

### Acceptance criteria

- Favouriting any remote current item imports it and leaves the resulting local
  track favourited.
- Adding a remote item to a playlist imports or merges it and then adds exactly one
  local track to the chosen playlist.
- Pending actions survive process death and retry.
- Queue and non-private stats references convert without duplication or loss.
- Import History and Song Details show the correct origin and reason.
- Local import result actions work for one and many successful songs.
- Imported YouTube artwork remains available offline after cache clearing.

### Manual app tests

1. Favourite a remote Discover song from Discover and while it is playing.
2. Add a remote Discover song to a playlist, kill the process, and verify eventual
   completion after relaunch.
3. Repeat both actions for a song already present locally and verify duplicate
   merge behavior.
4. Import several local songs; play them, add them to a playlist, and open Home
   with exactly those tracks highlighted.
5. Clear cache and disable the network; verify imported YouTube artwork remains.
6. Inspect history/source information for every import origin.

### Automated tests

- Pending favourite/add-to-playlist persistence and completion tests.
- Duplicate merge tests for all pending actions.
- Queue and stats remote-to-local conversion tests.
- History/provenance mapping tests.
- Thumbnail promotion and orphan cleanup tests.
- Compose tests for local import result actions and Home highlighting.

### Risks / notes

- Pending action completion should be transactionally idempotent so retries cannot
  duplicate playlist membership or history records.

## Phase 19 — Discover Lifecycle and Statistics Correctness

### Goal

Finish Discover’s persistence contract and make long-term listening statistics
mathematically correct for a daily-use V1 library.

### Main work

- Implement eligible startup Discover refresh: snapshot older than eight hours and
  opened since the last refresh.
- Preserve the current snapshot when the API is unavailable or refresh fails.
- Split listening-history uploads into Zstandard-compressed chunks targeting
  roughly 1 MB and calculate the contract-defined overall hash/counts.
- Keep private-mode playback permanently absent from events and Discover input,
  including crash-recovery paths.
- Replace the latest-500-event dependency for global calculations with complete
  aggregate queries or complete event streams.
- Store or derive the correct completion-average sample count.
- Add deterministic tests for streak, rediscovery, sessions, time buckets, repeat
  loops, private mode, and remote-event conversion.

### Key files/modules

- `data/repository/DiscoverRepository.kt`
- `data/local/dao/RemoteDiscoverDao.kt`, snapshot fields
- `data/repository/StatsRepository.kt`, `PlaybackStatsTracker.kt`
- `data/local/dao/PlaybackDao.kt`, playback entities and migrations
- application startup orchestration

### Acceptance criteria

- Discover refreshes automatically only under the exact eligibility rule.
- Failed startup refresh never destroys or empties the previous snapshot.
- Large history uploads use multiple valid compressed chunks.
- Private-mode playback produces no recoverable event, statistic, session, or
  Discover input.
- Global statistics remain correct after more than 500 events.
- Average completion equals the average across all applicable playback events.

### Manual app tests

1. Exercise fresh, younger-than-eight-hour, unopened, eligible, offline, and failed
   startup refresh cases.
2. Generate enough history for a multi-chunk upload and refresh Discover.
3. Kill the app while playing in private mode and verify no history appears.
4. Seed/use a library with more than 500 events and inspect global statistics.

### Automated tests

- Startup eligibility truth-table tests.
- Multi-chunk compression, ordering, size, and overall-hash tests.
- Private-mode crash-recovery tests.
- Database aggregate and long-history statistics tests.
- Completion-average regression tests.

### Risks / notes

- Startup refresh must not delay app launch or compromise offline-first behavior.

## Phase 20 — Player Platform Actions and Media Resumption

### Goal

Complete the required Android player integrations before attaching Android Auto.

### Main work

- Implement Audio Output using the appropriate Android/Media3 output-switching
  surface for supported devices.
- Implement Share using the original YouTube URL for YouTube/Discover items and
  title/artist text for local imports.
- Implement Media3 playback resumption from Android system media controls after
  process recreation.
- Move enough queue/current-item restoration into the service/session layer that
  external controllers do not depend on opening the activity first.
- Add a favourite custom command/button for downloaded current songs in the media
  notification/session; keep it unavailable for remote-only items.
- Verify audio focus, calls, navigation ducking, competing media, noisy-device,
  headset, Bluetooth, lock-screen, and notification behavior after the service
  changes.

### Key files/modules

- `playback/FenlzerMediaService.kt`
- MediaSession callback and media-item mapper
- `playback/PlaybackController.kt`, `QueueRepository.kt`
- `ui/player/FullscreenPlayer.kt`, `FenlzerApp.kt`
- Android intent/output routing helpers

### Acceptance criteria

- Audio Output opens a working route chooser where supported.
- Share emits the correct content for local and YouTube-backed items.
- Android media resume restores the last playable local queue and position.
- Notification favourite toggles the downloaded current track and updates UI.
- Remote current playback remains controllable but cannot be favourited without
  going through its automatic import action.
- Existing playback, queue persistence, and rotation behavior do not regress.

### Manual app tests

1. Switch between speaker, Bluetooth, and available cast/output routes.
2. Share a local song, a YouTube import, and a remote Discover song.
3. Remove the app process and resume from Android media controls.
4. Toggle favourite from the notification and verify Favourites immediately.
5. Repeat headset, Bluetooth, call, navigation, and noisy-device tests.

### Automated tests

- Share payload unit tests.
- Playback-resumption callback tests.
- Media custom-command tests for local and remote items.
- Persistent queue/media-item mapping tests.
- Instrumented notification command smoke tests where platform APIs permit.

### Risks / notes

- Output-switcher availability varies by API level/device; unsupported cases need
  a graceful disabled or explanatory state rather than a dead control.

## Phase 21 — Android Auto V1

### Goal

Complete the required car-safe local library, playlist, and playback experience
using the now-complete MediaLibrary session.

### Main work

- Implement `MediaLibrarySession.Callback` browse root, children, item retrieval,
  search, playback preparation, and subscription notifications.
- Map local tracks and permanent artwork to valid Media3 browse/playable items.
- Expose Songs, regular playlists, Favourites, Recently Played, Most Listened,
  Morning, Afternoon, Evening, and Night Mix.
- Hide Discover, Never Played, and Missing Metadata.
- Exclude remote items from Auto browse and Auto-created queue.
- Permit Now Playing controls for a remote item already streaming on the phone
  without stopping or replacing it when Auto connects.
- Expose favourite for downloaded current songs through the custom command.
- Keep Auto queue/current media state synchronized with the phone queue.

### Key files/modules

- `playback/FenlzerMediaService.kt`
- `playback/CarMediaLibraryCallback.kt`
- `playback/MediaItemMapper.kt`
- `playback/CarMediaLibraryTreeBuilder.kt`
- queue, playlist, smart-playlist, stats, and thumbnail repositories
- Android Auto test configuration/helpers

### Acceptance criteria

- Android Auto browses and plays the complete downloaded library.
- Regular and allowed smart playlists display correct local contents and artwork.
- Hidden smart playlists and remote items never appear in browse or Auto queues.
- Connecting Auto during remote playback preserves and controls current playback.
- Favourite works for downloaded current songs and not for remote-only items.
- Phone and Auto queue/current state stay synchronized.

### Manual app tests

1. Connect the Desktop Head Unit or an Android Auto compatible head unit.
2. Browse Songs, regular playlists, and every allowed smart playlist.
3. Start playback from track, playlist, smart playlist, and search results.
4. Verify queue, next/previous, repeat, and current metadata/artwork.
5. Start remote Discover playback on the phone, connect Auto, and verify Now
   Playing without remote browse/queue exposure.
6. Toggle favourite for a downloaded song from Auto.

### Automated tests

- MediaLibrary callback tests for every root and child collection.
- Local/remote media-item filtering tests.
- Paging, search, artwork, and empty-library tests.
- Auto queue construction and synchronization tests.
- Favourite custom-command tests.

### Risks / notes

- This phase is a V1 release blocker, not an optional enhancement.

## Phase 22 — Adaptive Polish and V1 Release Qualification

### Goal

Prove that the complete application is stable, accessible, buildable, and faithful
to the final specification across phone, landscape, background, offline, and car
usage.

### Main work

- Review every final-plan requirement against the implemented source and close any
  remaining discrepancy.
- Complete portrait, landscape, large-width, keyboard, insets, scrolling, queue
  side-panel, and fullscreen-player visual passes.
- Verify touch targets, content descriptions, focus order, TalkBack labels,
  contrast, text scaling, and long-text handling.
- Finish empty/loading/error/offline states and ensure no feature is represented by
  a placeholder-only control.
- Run and fix full unit, migration, MockWebServer, WorkManager, Compose UI,
  instrumented playback/import, and MediaLibrary suites.
- Run lint, debug/release assembly, install/upgrade tests, and dependency/security
  review without embedding API credentials.
- Execute the complete manual V1 regression matrix and record results.

### Key files/modules

- All application modules
- `ui/components`, adaptive layouts, themes, and all feature screens
- `app/src/test`, `app/src/androidTest`, Room schemas
- Gradle build, lint, and release configuration
- final manual test report under `specification/`

### Acceptance criteria

- Every settled V1 requirement is implemented with no known blocker.
- Debug and release variants build cleanly.
- JVM, migration, worker, API, Compose, instrumented, playback, and MediaLibrary
  tests pass.
- Lint has no unresolved release-blocking findings.
- Local library/playback works with the API unavailable.
- Playback, queue, imports, and pending actions survive expected process death.
- All screens remain usable in portrait and landscape with keyboard and mini-player.
- Android Auto passes its full manual matrix.
- No secret/token appears in logs, diagnostics, backups, or packaged resources.

### Manual app tests

1. Fresh install and upgrade install with populated old database.
2. Full local import, YouTube search, playlist import, Active Imports, history,
   Discover, pending actions, and offline workflow.
3. Full playback, queue, notification, sleep timer, media resume, deletion, and
   storage workflow.
4. Full Home, playlists, metadata, Song Details, Artists, Albums, statistics, and
   settings workflow.
5. Rotation and landscape run through every screen, dialog, sheet, keyboard state,
   mini-player, fullscreen player, and queue side panel.
6. Process death during playback and every import stage.
7. AMOLED, text scaling, TalkBack, long-title, empty-library, low-storage, API-down,
   and network-loss tests.
8. Complete Android Auto workflow.

### Automated tests

- Entire test suite plus release assembly and lint.
- Database migration suite from every exported schema.
- Mock API contract suite for every endpoint used by the app.
- WorkManager and process-recovery tests.
- Compose smoke tests for all routes and core dialogs/sheets.
- MediaSession/MediaLibrary integration tests.

### Risks / notes

- Any regression found here is fixed within Phase 22; the phase is not complete
  while a required manual test remains blocked or unverified.

## Recommended implementation order

Implement the phases strictly in this order:

1. Phase 16 — establish schema and test foundations.
2. Phase 17 — make the import engine durable before adding more import intents.
3. Phase 18 — complete pending actions and import UX on the durable engine.
4. Phase 19 — finish Discover/statistics correctness.
5. Phase 20 — complete service-level player actions and media resumption.
6. Phase 21 — attach Android Auto to the finished MediaLibrary/session behavior.
7. Phase 22 — perform final adaptive, accessibility, build, and regression gates.

Each phase must stop at its manual app tests. The next phase should begin only
after the user reports the manual tests passed or provides issues to fix.

## Definition of Fenlzer V1 complete

Fenlzer V1 is complete only when all seven remaining phases pass their acceptance
criteria, all automated suites compile and pass, the complete manual regression is
recorded, and Android Auto works with the exact local/remote boundaries in the
final application plan.
