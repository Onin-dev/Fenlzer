# Fenlzer V1 Implementation Phase Plan

## Understanding

- Fenlzer V1 is the complete final product, not an MVP.
- It is an offline-first personal Android music app with local private app storage and API-backed YouTube import, streaming, and Discover.
- Local library/playback must work when the API is unavailable.
- Playback must be MediaSession-first from the start, with persistent queue/source/modified state.
- Remote Discover songs can stream in the phone app before import, but are excluded from Android Auto browse/queue.
- Remote songs must convert cleanly to local tracks after import, including queue references, pending playlist/favourite actions, and non-private stats.
- Private mode is session-only, set from Settings, and excludes playback from stats/history/Discover input.
- The Android app should follow the existing API contract rather than redesigning it.

## Phase 1 — Buildable Foundation and App Architecture

### Goal
Create a clean, buildable Android foundation with the required stack, app package structure, navigation shell, dark theme, and MediaSession service skeleton.

### Main work
- Fix the current build mismatch by aligning `core-ktx` with installed `compileSdk` 36.1.
- Add Gradle support for Room, KSP, DataStore, WorkManager, Media3, Coil, Retrofit, kotlinx serialization, Navigation Compose, lifecycle ViewModels, and test tools.
- Use a single `:app` module with clear packages: `data`, `domain`, `playback`, `importing`, `ui`, `settings`, `common`.
- Add manual dependency container/AppGraph; do not introduce Hilt unless later required.
- Replace template UI with Fenlzer app shell: Home, Playlists, Import tabs, mini-player area, Settings entry placeholder.
- Add `MediaSessionService`/`MediaLibraryService` manifest scaffolding without full playback logic yet.

### Key files/modules
- `gradle/libs.versions.toml`, `app/build.gradle.kts`
- `MainActivity.kt`, `FenlzerApp.kt`, `AppGraph.kt`
- `ui/navigation/*`, `ui/theme/*`
- `playback/FenlzerMediaService.kt`

### Acceptance criteria
- Debug app builds and launches.
- Bottom tabs render with persistent mini-player empty state.
- Dark-only theme and AMOLED setting hook exist.
- Media service is declared and can be created without crashing.

### Manual app tests
- Launch app on emulator/device.
- Switch Home, Playlists, and Import tabs.
- Tap empty mini-player main area and verify it routes to Import or no-song player state.
- Rotate device and confirm shell remains usable.

### Automated tests
- Basic Compose navigation smoke test.
- Unit test for initial app settings defaults.
- Build verification with `assembleDebug` and `testDebugUnitTest`.

### Risks / notes
- Current shell build fails because `androidx.core:core-ktx:1.19.0` requires compile SDK 37, while only `android-36` and `android-36.1` are installed. Use `core-ktx` 1.18.0 for now.

## Phase 2 — Room Schema, Settings, and Storage Primitives

### Goal
Create durable persistence and private file-storage foundations before feature work depends on them.

### Main work
- Implement Room entities/DAOs for Track, original metadata, thumbnails, playlists, queue, remote items, Discover snapshots, playback events/sessions/stats, import jobs/history, API diagnostics, and bulk metadata operations.
- Enable exported Room schemas and migration test infrastructure.
- Use DataStore for non-secret settings and encrypted storage for the API token.
- Add private storage helpers for audio, thumbnails, temp import files, and cache cleanup.
- Add normalized sort/search key helpers.

### Key files/modules
- `data/local/entity/*`, `data/local/dao/*`, `data/local/FenlzerDatabase.kt`
- `data/settings/*`
- `data/storage/FenlzerStorage.kt`
- `domain/model/*`

### Acceptance criteria
- App opens the database and settings stores on fresh install.
- Schema includes all V1 relationships needed by the spec.
- No API dependency is required for local settings/database startup.

### Manual app tests
- Launch app fresh.
- Toggle AMOLED setting and restart app.
- Confirm app restarts with same setting.

### Automated tests
- DAO tests for Track, PlaylistTrack duplicate prevention, QueueItem local/remote reference rules.
- Room migration baseline test.
- Unit tests for normalized search/sort keys and storage path generation.

### Risks / notes
- Use hard delete for tracks as allowed by the spec.
- Keep `TrackStatsSnapshot` as a real table for UI performance.

## Phase 3 — API Configuration, Health Check, and Diagnostics

### Goal
Wire the existing API contract into Settings without making local playback depend on API availability.

### Main work
- Copy/adapt Android API contract v2 DTOs and Retrofit service into app source.
- Add API settings screen fields for base URL and token.
- Add Test API Connection action using `GET /v1/health`.
- Store sanitized request diagnostics locally, capped at 500 entries.
- Map API errors into stable user-facing states without exposing tokens.

### Key files/modules
- `data/remote/FenlzerApiService.kt`, `FenlzerApiModels.kt`, `FenlzerApiFactory.kt`
- `data/remote/ApiRepository.kt`
- `settings/ApiSettingsScreen.kt`
- `data/local/dao/ApiDiagnosticDao.kt`

### Acceptance criteria
- User can configure base URL/token and test connection.
- Health response displays feature availability and tool status.
- API unavailable state disables API-backed features but not local library UI.
- All requests include bearer token and idempotency keys for mutating calls.

### Manual app tests
- Enter invalid URL and test connection: clear failure shown.
- Enter valid API/token and verify health details.
- Turn off API server and confirm app remains usable.

### Automated tests
- MockWebServer tests for health success, unauthorized, timeout, malformed response.
- Unit tests for token redaction and diagnostics retention.

### Risks / notes
- Treat `TRANSFER_CONFIRMED` and `COMPLETED` as terminal successful import states per Android contract v2.

## Phase 4 — Home Library and Local File Import

### Goal
Make Fenlzer useful offline by importing local files into private app storage and showing a real library.

### Main work
- Add Storage Access Framework picker for MP3, M4A, WAV, FLAC, OGG.
- Copy files into app-private audio storage as `sha256.extension`.
- Extract metadata, duration, embedded artwork where available, and original metadata rows.
- Detect duplicates by SHA-256; do not merge by title/artist/duration.
- Build Home list with search, sort/filter sheet, row actions, empty state, and import result screen.
- Add Home selection mode and bulk queue/playlist/edit/delete entry points where dependencies exist.

### Key files/modules
- `importing/local/LocalImportWorker.kt`
- `data/repository/TrackRepository.kt`
- `ui/home/*`, `ui/import/LocalImportScreen.kt`, `ui/import/ImportResultScreen.kt`

### Acceptance criteria
- Local files import successfully while API is unavailable.
- Duplicate local imports are rejected with Import History entries.
- Imported songs appear in Home with thumbnail/title/artist/duration.
- Missing metadata follows fallback rules.

### Manual app tests
- Import one valid file and play no-op row actions where playback is not ready yet.
- Import multiple files including a duplicate and unsupported file.
- Restart app and verify imported tracks persist.
- Delete app data and verify private files are gone.

### Automated tests
- Unit tests for SHA-256 duplicate detection.
- Instrumented import test using test asset files.
- DAO/repository tests for successful, duplicate, failed import history rows.

### Risks / notes
- Metadata extraction can gracefully fall back to empty editable fields.

## Phase 5 — Media3 Playback, Persistent Queue, and Mini-Player

### Goal
Enable robust local playback with a MediaSession-first architecture and persistent queue behavior.

### Main work
- Implement Media3 ExoPlayer inside the media service and connect app UI through a MediaController.
- Persist queue state, source label, modified flag, current item, previous/upcoming items, repeat/shuffle state, and playback position.
- Implement Home tap behavior exactly: tapped song plays immediately, old queue remains after it; search tap is an immediate one-song override.
- Implement Play Next, Add to Queue, duplicate handling, clear upcoming, save queue as playlist hook.
- Implement mini-player normal/empty states, seek, favourite, play/pause, next, and more menu.

### Key files/modules
- `playback/FenlzerMediaService.kt`, `PlaybackController.kt`, `QueueManager.kt`
- `data/repository/QueueRepository.kt`
- `ui/player/MiniPlayer.kt`, `ui/queue/QueueScreen.kt`

### Acceptance criteria
- Local tracks play from Home.
- Queue survives process death/app restart without autoplaying unless previously active.
- Queue source label and modified state persist.
- Duplicate queue rules match spec.

### Manual app tests
- Import three songs, play one, add another to queue, restart app.
- Test Play Next/Add to Queue duplicate behavior.
- Seek from mini-player.
- Remove current song from queue and confirm skip/stop behavior.

### Automated tests
- Unit tests for queue insertion, duplicate removal, repeat, shuffle, previous-button rules.
- Instrumented Media3 smoke test with local test audio.
- Persistence test for queue restore.

### Risks / notes
- Remote queue items are not added yet, but queue schema already supports them.

## Phase 6 — Fullscreen Player, Audio Integration, and Sleep Timer

### Goal
Complete the phone playback experience around the MediaSession core.

### Main work
- Build fullscreen player expansion, portrait and landscape layouts.
- Add large artwork, controls, source context, next song, repeat/shuffle state, stats/details entry, add-to-playlist, queue, output route, and more menu.
- Add audio focus, becoming-noisy handling, notification controls, lock-screen controls, headset/Bluetooth controls, and Android media resume.
- Implement sleep timer options, custom duration, end-of-song/end-of-queue, and 10-second fade-out.

### Key files/modules
- `ui/player/FullscreenPlayer.kt`
- `playback/AudioFocusHandler.kt`, `SleepTimerController.kt`
- `playback/MediaNotificationProvider.kt`

### Acceptance criteria
- Fullscreen player controls match queue/repeat/shuffle behavior.
- Notification and lock-screen controls control the same session.
- Sleep timer pauses playback at the selected endpoint.
- Landscape fullscreen uses artwork left and controls right.

### Manual app tests
- Play music, lock screen, use notification controls.
- Disconnect headphones/Bluetooth and verify pause.
- Set 15-minute and end-of-song timers with short test tracks.
- Swipe down/left/right in fullscreen player.

### Automated tests
- Unit tests for sleep timer state machine.
- Unit tests for audio focus command mapping.
- Compose tests for fullscreen control visibility.

### Risks / notes
- Exact animation polish can continue in final polish, but all controls must function now.

## Phase 7 — Statistics, Listening History, and Private Mode

### Goal
Record accurate playback history and stats while preserving private-mode exclusions.

### Main work
- Track playback events on song end, song change, pause, and app close.
- Persist temporary progress every few seconds for crash recovery.
- Implement valid listen, skip, completion, repeated playback, sessions, and stats snapshot updates.
- Add Settings private mode toggle for current app session only.
- Add global Statistics screen and per-song stats data source.
- Implement Clear Listening History and Reset Statistics behavior.

### Key files/modules
- `playback/PlaybackStatsTracker.kt`
- `data/repository/StatsRepository.kt`
- `ui/stats/*`, `settings/PrivacySettings.kt`

### Acceptance criteria
- Play count, skip count, completion count, total listened time, first/last played are correct.
- Private-mode playback creates no normal history/stats and is never Discover input.
- Clear Listening History keeps aggregate song stats; Reset Statistics clears both.
- Playback sessions follow the 5-minute gap rule.

### Manual app tests
- Play a short song past valid threshold and verify stats.
- Skip before threshold and verify skip count.
- Enable private mode, play a song, verify no stats changed.
- Clear history and reset stats separately.

### Automated tests
- Unit tests for valid listen, skip, completion, sessions, repeat-one loops.
- Repository tests for clear history vs reset statistics.
- Crash-recovery temp progress test.

### Risks / notes
- Private events should normally be omitted, not stored with a flag.

## Phase 8 — Regular Playlists and Smart Playlists

### Goal
Deliver playlist management and local smart playlists using imported tracks and stats.

### Main work
- Implement regular playlist creation, rename, delete, thumbnail override, generated collage, add/remove songs, manual reorder, temporary sort/search.
- Implement Add to Playlist toggle dialog including Favourites.
- Implement playlist playback: tap, Play All, Shuffle Play replace queue.
- Implement smart playlists: Favourites, Most Listened, Recently Played, Never Played, Missing Metadata, Morning/Afternoon/Evening/Night Mixes.
- Support saving a smart playlist as a static regular playlist.

### Key files/modules
- `ui/playlists/*`
- `data/repository/PlaylistRepository.kt`, `SmartPlaylistRepository.kt`
- `ui/components/AddToPlaylistDialog.kt`

### Acceptance criteria
- Regular playlists do not allow duplicate tracks.
- Playlist manual order is preserved and not overwritten by search/sort.
- Smart playlists update from stats/metadata rules.
- Queue source labels reflect playlist/smart playlist playback.

### Manual app tests
- Create playlist, add songs, remove one, reorder, restart.
- Toggle favourite through mini/fullscreen and verify Favourites.
- Play All and Shuffle Play from a playlist.
- Save Most Listened as regular playlist.

### Automated tests
- DAO tests for unique playlist membership and reorder.
- Smart playlist query tests.
- Queue creation tests from playlist and smart playlist.

### Risks / notes
- Discover smart playlist is implemented later because it requires API recommendation flow.

## Phase 9 — Metadata Editing, Song Details, Artists, and Albums

### Goal
Complete metadata management and library browsing beyond the Home song list.

### Main work
- Add metadata editor with Save, discard warning, per-field reset, reset all, edited indicators, notes, and thumbnail replacement.
- Add Song Details screen/sheet with stats, playlists, original metadata, technical details, and source information.
- Add Artists and Albums browsers/details derived from Track metadata.
- Implement artist rename and album bulk edits with affected-song preview, merge warnings, overwrite/fill-empty choices, and BulkMetadataOperation audit records.
- Apply thumbnail priority: custom, YouTube, embedded, placeholder.

### Key files/modules
- `ui/metadata/*`, `ui/songdetails/*`
- `ui/library/artists/*`, `ui/library/albums/*`
- `data/repository/MetadataRepository.kt`

### Acceptance criteria
- Edits update Home, playlists, smart playlists, artists, albums, and search immediately.
- Original metadata reset works.
- Artist/album bulk edits require confirmation and record audit rows.
- Song Details contains only basic Play, not full playback controls.

### Manual app tests
- Edit title/artist/album/genre/year/notes and verify search/sort.
- Rename an artist into an existing artist and confirm merge warning.
- Bulk-edit album thumbnail/year/genre with overwrite options.
- Open Song Details from Home, player, and import result.

### Automated tests
- Unit tests for album identity and sorting rules.
- Repository tests for bulk edit operation records.
- Compose tests for discard confirmation and reset controls.

### Risks / notes
- V1 stores multiple artists as one exact text field.

## Phase 10 — YouTube Search, Single Download, Transfer, and Confirmation

### Goal
Import YouTube search results through the existing API into local Fenlzer tracks.

### Main work
- Build YouTube search UI returning five result cards.
- Create manual download jobs with `POST /v1/downloads`.
- Poll/restore job status with `GET /v1/jobs/{jobId}` and `POST /v1/jobs/status`.
- Stream completed files via `GET /v1/jobs/{jobId}/file`, support Range resume, verify SHA-256, copy to private storage, and call confirm endpoint.
- Create Track, original metadata, thumbnail, ImportJob, and ImportHistory rows.
- Retry failed downloads up to 3 times before exposing Retry.

### Key files/modules
- `ui/import/YoutubeSearchScreen.kt`
- `importing/api/ApiImportWorker.kt`, `FileTransferWorker.kt`
- `data/repository/ImportRepository.kt`

### Acceptance criteria
- Search result downloads become local editable tracks.
- SHA mismatch fails safely and does not confirm the API file.
- Confirm endpoint is called only after local hash verification.
- Same YouTube video ID/source URL/audio hash is treated as duplicate.

### Manual app tests
- Configure API, search, download one song, verify it appears in Home.
- Kill app during download, reopen, verify Active Imports restores.
- Simulate API offline after job creation and verify retry/failure state.
- Re-import same video and verify duplicate result.

### Automated tests
- MockWebServer tests for search, create job, polling, file download headers, confirm.
- Unit tests for job state mapping including `TRANSFER_CONFIRMED`.
- File-transfer tests for SHA success, SHA mismatch, Range resume.

### Risks / notes
- Do not rely exclusively on stream `mimeType`; let Media3 infer where API samples are inconsistent.

## Phase 11 — YouTube Playlist Import, Active Imports, and Import History

### Goal
Complete the import system for batch/manual workflows and durable history.

### Main work
- Implement playlist URL preview, progressive polling, whole-playlist download, and selected-item batch download.
- Complete Active Imports screen with downloading/upcoming sections, progress, cancel, retry, reorder, queue position, failures, and global foreground notification.
- Complete Import History with success/failed/duplicate/cancelled filters, clear history, and retry where available.
- Enforce manual job priority over automatic jobs; allow manual reorder of waiting jobs.

### Key files/modules
- `ui/import/PlaylistPreviewScreen.kt`, `ActiveImportsScreen.kt`, `ImportHistoryScreen.kt`
- `importing/ImportNotificationController.kt`
- `importing/ImportQueueCoordinator.kt`

### Acceptance criteria
- Playlist preview handles loading, complete, failed, and expired states.
- Batch imports produce individual job/history records.
- Cancel/retry/reorder use the exact API endpoints.
- Completed/failed imports stay visible in Active Imports until user leaves/dismisses and are written to Import History immediately.

### Manual app tests
- Preview a playlist, select several songs, batch import.
- Cancel queued and running jobs.
- Reorder upcoming jobs and verify order persists/restores.
- Clear Import History and verify songs remain.

### Automated tests
- Mock API tests for preview create/get, batch creation, cancel/retry/reorder.
- Worker tests for manual priority over automatic jobs.
- UI tests for Active Imports sections and Import History filters.

### Risks / notes
- API supports three parallel downloads; app should display but not try to exceed server rules.

## Phase 12 — Discover, Remote Streaming, and Remote-to-Local Conversion

### Goal
Implement API-backed Discover as a stable smart playlist with remote streaming before import.

### Main work
- Upload non-private listening history using Zstandard-compressed JSON chunks.
- Refresh Discover and broader Discover, storing DiscoverSnapshot, RemoteItem, ordered items, and diagnostics.
- Stream remote songs via `/v1/stream/resolve`; prefetch current and next two remote queue items.
- Allow remote queue rows with Remote/Ready/Downloading/Unavailable badges.
- Import remote songs manually or through pending favourite/add-to-playlist actions.
- Convert remote references to Track on import or duplicate merge: queue, pending playlist/favourite, and non-private remote stats.

### Key files/modules
- `ui/discover/*`
- `data/repository/DiscoverRepository.kt`, `RemoteItemRepository.kt`
- `playback/RemoteStreamResolver.kt`
- `importing/api/RemoteImportCoordinator.kt`

### Acceptance criteria
- Discover persists until manual refresh or eligible startup refresh.
- Remote songs stream in phone app before import.
- Stream load failure refreshes URL once, retries once, then skips with message.
- Imported remote songs become normal local tracks and pending references convert cleanly.
- Remote songs are marked ineligible for Android Auto browse/queue.

### Manual app tests
- Generate Discover from a library with history.
- Play a remote Discover item, then import it while queued.
- Favourite a remote item and verify automatic import completes favourite.
- Refresh broader when normal results are too few.
- Enable private mode and verify those plays are absent from Discover upload.

### Automated tests
- Mock API tests for history upload/chunks/complete and refresh/broader refresh.
- Unit tests for remote-to-track merge and pending action completion.
- Playback tests for stream resolve retry/skip behavior.
- Serialization/compression tests for history chunk payloads.

### Risks / notes
- Discover should strictly exclude already-imported items using video IDs, source URLs, and audio hashes.

## Phase 13 — Settings, Storage Management, and Delete Flows

### Goal
Finish app controls, destructive operations, and storage transparency.

### Main work
- Complete Settings: repeat, shuffle, Home sort, duplicate behavior, sleep default, private mode, AMOLED, API config, diagnostics, app version, About.
- Implement Storage Management: audio size, thumbnail size, cache size, database size, total usage.
- Implement clear cache, clear import history, delete song, bulk delete, and delete all songs with typed `DELETE`.
- Remove audio files, thumbnails, playlist refs, queue refs, remote refs where applicable.

### Key files/modules
- `settings/*`
- `data/storage/StorageUsageRepository.kt`
- `domain/delete/DeleteFromFenlzerUseCase.kt`

### Acceptance criteria
- Settings persist correctly across restart except session-only private mode.
- Delete confirmations use required “Delete from Fenlzer” phrasing.
- Delete all songs requires typing `DELETE`.
- Clearing cache does not remove permanent thumbnails.

### Manual app tests
- Change every setting and restart app.
- Delete one song and verify file, queue, playlist references are gone.
- Bulk delete selected songs.
- Delete all songs and verify app returns to empty states.

### Automated tests
- Unit tests for storage size accounting.
- Repository tests for delete cascade behavior.
- UI tests for typed delete confirmation.

### Risks / notes
- No undo/trash for deletes.

## Phase 14 — Android Auto

### Goal
Deliver V1 Android Auto support through the existing MediaSession/MediaLibrary architecture.

### Main work
- Implement car-safe MediaLibrary browse tree for downloaded library, playlists, and allowed smart playlists.
- Expose Favourites, Recently Played, Most Listened, Morning, Afternoon, Evening, and Night Mix.
- Hide Discover, Never Played, and Missing Metadata from Android Auto.
- Exclude remote items from Android Auto browse and queue.
- Allow Android Auto Now Playing controls for a remote item already streaming on the phone without abruptly stopping it.
- Allow favouriting current downloaded song from Auto/notification controls.

### Key files/modules
- `playback/CarMediaLibraryCallback.kt`
- `playback/MediaItemMapper.kt`
- `ui/automotive` test helpers if needed

### Acceptance criteria
- Android Auto browses local library/playlists only.
- Remote Discover streams never appear in Auto browse/queue.
- Current remote playback remains controllable if already active.
- Downloaded current song can be favourited through media custom command.

### Manual app tests
- Connect Android Auto emulator/head unit.
- Browse Library, Playlists, allowed smart playlists.
- Start local playback from Auto.
- Start remote Discover on phone, connect Auto, verify Now Playing controls but no remote browse exposure.

### Automated tests
- Unit tests for browse tree filtering.
- MediaLibrary callback tests for local vs remote item mapping.
- Custom command test for favourite downloaded song.

### Risks / notes
- Final Auto hierarchy can be tuned, but hidden/visible smart playlist rules are fixed.

## Phase 15 — Adaptive Layout, Polish, and V1 Regression

### Goal
Make the full app feel complete, resilient, and ready for personal daily use.

### Main work
- Finish landscape/adaptive behavior: compact Home top bar, queue side panel, playlist grids, player landscape layout.
- Polish row density, typography, menus, empty states, banners, error copy, thumbnails, and progress states.
- Add API diagnostics screen for local/server recent diagnostics.
- Harden offline behavior and process death restoration across import/playback.
- Run full V1 regression and fix discovered issues.

### Key files/modules
- `ui/adaptive/*`, shared UI components, all feature screens
- `data/remote/DiagnosticsRepository.kt`
- test suites under `app/src/test` and `app/src/androidTest`

### Acceptance criteria
- All V1 requirements from the final app plan are implemented.
- App builds cleanly, tests pass, and no screen is placeholder-only.
- Local playback/library remains usable with API unavailable.
- Rotation, process death, and restart preserve expected state.
- No token/secret appears in diagnostics or logs.

### Manual app tests
- Full smoke run: local import, playback, queue, playlists, stats, metadata, YouTube import, Discover, storage, Android Auto.
- Offline run with API disabled.
- Rotation run through Home, Queue, fullscreen player, playlists, Import, Discover.
- Process death during playback and during active import.
- AMOLED mode visual pass.

### Automated tests
- Full unit test suite.
- Room migration suite.
- Mock API integration suite.
- Compose UI smoke tests for core screens.
- Instrumented playback/import smoke tests.

### Risks / notes
- Exact purple accent HEX remains a visual-production detail; use a temporary dark purple token until provided.

## Recommended First Phase

Start with **Phase 1 — Buildable Foundation and App Architecture**. The current template app cannot build in this shell until the dependency/SDK mismatch is fixed, so the first phase should restore a reliable baseline before adding feature code.

## Exact First Coding Tasks

1. In `gradle/libs.versions.toml`, change `coreKtx` from `1.19.0` to `1.18.0` to match installed `android-36.1`.
2. Add version catalog entries and Gradle plugins/dependencies for Room + KSP, DataStore, WorkManager, Media3, Coil, Retrofit, OkHttp, kotlinx serialization, Navigation Compose, lifecycle ViewModel Compose, MockWebServer, coroutine tests, and Room testing.
3. Set Java/Kotlin compile target to 17 using the bundled Android Studio JBR.
4. Replace template `Greeting` UI with `FenlzerApp` shell: Home, Playlists, Import tabs, mini-player empty state, Settings route.
5. Add package skeleton and `AppGraph` manual dependency container.
6. Add manifest declarations and empty implementation for the Media3 media service.
7. Add first smoke tests for app launch/navigation and settings defaults.

## First Verification Commands

```bash
env JAVA_HOME=/home/fenl/android-studio/jbr ./gradlew :app:assembleDebug
env JAVA_HOME=/home/fenl/android-studio/jbr ./gradlew :app:testDebugUnitTest
```

Current baseline note: `assembleDebug` was attempted and failed at `:app:checkDebugAarMetadata` because `androidx.core:core/core-ktx:1.19.0` requires compile SDK 37, while this machine currently has Android SDK platforms `android-36` and `android-36.1`.

## Blocking Questions

None. The missing exact accent color is not blocking; implementation can use a temporary dark purple theme token and replace it later.

External reference used for the build-baseline decision: AndroidX Core release notes for 1.18.0 state its compile SDK changed to API 36.1: https://developer.android.com/jetpack/androidx/releases/core
