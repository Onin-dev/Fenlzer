# Fenlzer Database Schema and Persistence Plan

Room-oriented implementation draft for the final Fenlzer product plan

## Status

Draft created during product planning. Decisions marked as open should be resolved before implementation.

## 1. Purpose and scope

This document defines the proposed local persistence model for Fenlzer. It is designed for a Kotlin + Jetpack Compose + Media3 + Room application with a MediaSession-first playback architecture, local app-private storage, YouTube/API-backed import and streaming, Discover remote items, Android Auto, persistent queue, detailed listening statistics, and durable import history.

The goal is not to write exact Kotlin entity code yet, but to fix the database structure enough that implementation can proceed without later architectural rewrites.

## 2. Core design rules

- Imported tracks are the durable local music objects. They have app-private audio files and full metadata.
- Remote Discover items are temporary playable recommendation objects. They can stream, appear in the queue, and later merge into imported tracks.
- Once a remote item becomes an imported track, all pending references must point to the imported track.
- Queue entries are independent rows, not just a list of track IDs, because queue order, previous/upcoming position, remote items, and source context must persist.
- Regular playlists do not allow duplicates. Queue also does not allow duplicates; adding an already queued item moves/removes the previous occurrence according to the final behavior rules.
- Artists and albums are editable through bulk metadata operations, but their browse pages are derived from track metadata rather than stored as independent manually-managed objects.
- Statistics are based on playback events and playback sessions. Private-mode playback is never stored as normal history and is never sent to the API.
- Import/download jobs are persistent database objects and are mirrored in Import History.
- API diagnostics are stored locally with sanitized details.

## 3. Entity overview

Main groups:

- Library: Track, TrackOriginalMetadata, ThumbnailAsset, BulkMetadataOperation.
- Remote/Discover: RemoteItem, DiscoverSnapshot, DiscoverSnapshotItem, DiscoverRefreshDiagnostics.
- Playlists: Playlist, PlaylistTrack.
- Queue: QueueState, QueueItem.
- Playback/statistics: PlaybackEvent, PlaybackSession, TrackStatsSnapshot.
- Imports/API: ImportJob, ImportHistoryEntry, ApiDiagnosticEntry.
- Settings: AppSettings, ApiSettings, PlayerSettings.

Some values can be stored in normalized tables or as JSON depending on implementation preference. The schema below favors clarity and robust querying.

## 4. Entity details

### Track

Durable imported/local track record.

| Field | Meaning |
|---|---|
| `trackId` | String/UUID primary key. |
| `title` | Editable display title. Empty allowed; UI falls back to original filename. |
| `titleSortKey` | Normalized internal sort/search key. |
| `artist` | Editable display artist. Empty allowed for local imports. |
| `artistSortKey` | Normalized internal sort/search key. |
| `album` | Editable album. |
| `albumSortKey` | Normalized internal sort/search key. |
| `albumArtist` | Editable album artist. |
| `albumArtistSortKey` | Normalized internal sort/search key. |
| `genre` | Editable genre. |
| `year` | Nullable integer/string year field. |
| `trackNumber` | Nullable integer. |
| `discNumber` | Nullable integer. |
| `durationMs` | Long duration in milliseconds. |
| `notes` | User notes; searchable only in Song Details. |
| `sourceType` | LOCAL_FILE, YOUTUBE_SEARCH, YOUTUBE_PLAYLIST, DISCOVER_MANUAL, DISCOVER_AUTO, OTHER. |
| `youtubeVideoId` | Nullable strong identifier for YouTube-sourced tracks. |
| `sourceUrl` | Nullable original YouTube URL or source URL. |
| `originalFilename` | Original imported filename for local imports. |
| `internalFilename` | App-private stored filename, usually hash.extension. |
| `audioHash` | Content hash used for duplicate detection. |
| `fileSizeBytes` | Stored audio file size. |
| `finalAudioFormat` | Actual stored format/container/codec summary. |
| `thumbnailAssetId` | Nullable reference to custom/permanent thumbnail asset. |
| `embeddedThumbnailAssetId` | Nullable asset extracted from local file. |
| `remoteThumbnailUrl` | Nullable YouTube/remote thumbnail URL. |
| `isFavourite` | Boolean. |
| `favouritedAt` | Nullable timestamp. |
| `importedAt` | Timestamp preserved even when stats reset. |
| `updatedAt` | Metadata update timestamp. |
| `deletedAt` | Optional nullable tombstone if soft delete is desired; otherwise omit. |

Notes: Indexes: audioHash unique, youtubeVideoId unique when not null, sourceUrl, titleSortKey, artistSortKey, albumSortKey, importedAt, isFavourite/favouritedAt.

### TrackOriginalMetadata

Stores extracted/default metadata so the editor can reset fields.

| Field | Meaning |
|---|---|
| `trackId` | Primary key and foreign key to Track. |
| `originalTitle` | Original extracted title or empty. |
| `originalArtist` | Original extracted artist or empty. |
| `originalAlbum` | Original extracted album or empty. |
| `originalAlbumArtist` | Original extracted album artist or empty. |
| `originalGenre` | Original extracted genre or empty. |
| `originalYear` | Original extracted year or null. |
| `originalTrackNumber` | Original track number or null. |
| `originalDiscNumber` | Original disc number or null. |
| `originalThumbnailKind` | YOUTUBE, EMBEDDED, PLACEHOLDER, NONE. |
| `rawMetadataJson` | Optional full extracted metadata payload. |

Notes: One row per Track.

### ThumbnailAsset

Stores permanent and cached thumbnails known to Fenlzer.

| Field | Meaning |
|---|---|
| `thumbnailAssetId` | String/UUID primary key. |
| `kind` | CUSTOM_SONG, CUSTOM_PLAYLIST, EMBEDDED, YOUTUBE_IMPORTED, REMOTE_CACHE, ALBUM_BULK. |
| `internalFilename` | App-private image filename. |
| `sourceUrl` | Nullable remote image URL. |
| `contentHash` | Nullable image hash. |
| `createdAt` | Timestamp. |
| `lastAccessedAt` | For cache cleanup. |
| `isPermanent` | Boolean. Remote/search thumbnails are temporary until promoted. |

Notes: Clean unused files removes unreferenced temporary assets, not referenced permanent ones.

### BulkMetadataOperation

Internal audit record for artist/album bulk edits.

| Field | Meaning |
|---|---|
| `operationId` | String/UUID primary key. |
| `operationType` | ARTIST_RENAME, ALBUM_EDIT, ALBUM_THUMBNAIL_EDIT, BULK_SELECTION_EDIT. |
| `oldValuesJson` | Old metadata values. |
| `newValuesJson` | New metadata values. |
| `affectedTrackIdsJson` | Track IDs affected by the operation. |
| `createdAt` | Timestamp. |

Notes: No full undo is required, but this preserves a useful audit/debug trail.

### RemoteItem

Temporary remote Discover/search item that can stream and later become a Track.

| Field | Meaning |
|---|---|
| `remoteItemId` | String/UUID primary key. |
| `youtubeVideoId` | Strong remote identifier when available. |
| `sourceUrl` | Original YouTube URL. |
| `title` | Remote display title. |
| `artistOrChannel` | Remote artist/channel. |
| `durationMs` | Nullable duration. |
| `thumbnailUrl` | Remote thumbnail. |
| `canStream` | Boolean. |
| `canDownload` | Boolean. |
| `streamState` | REMOTE_ONLY, GETTING_STREAM, READY, STREAMING, STREAM_FAILED, UNAVAILABLE. |
| `lastPlayableUrl` | Nullable cached URL if safe to store. |
| `playableUrlExpiresAt` | Nullable expiry. API still makes final validation decision. |
| `lastResolvedAt` | Timestamp. |
| `importState` | NONE, QUEUED, DOWNLOADING, IMPORTED, FAILED. |
| `importedTrackId` | Nullable FK to Track after successful import/merge. |
| `createdAt` | Timestamp. |
| `updatedAt` | Timestamp. |

Notes: Remote items do not appear in Android Auto browse. They may appear in phone queue.

### DiscoverSnapshot

Persistent Discover list stable until manual refresh or eligible startup refresh.

| Field | Meaning |
|---|---|
| `snapshotId` | String/UUID primary key. |
| `generatedAt` | Timestamp. |
| `lastOpenedAt` | Timestamp. |
| `refreshType` | NORMAL or BROADER. |
| `candidateRequestTarget` | Dynamic candidate count, 25-75. |
| `finalDisplayedCount` | Final non-imported item count. |
| `refreshDetailsVisible` | Diagnostics can be viewed under Refresh details. |

Notes: Auto-refresh on startup only if older than 8 hours and Discover was opened since last refresh.

### DiscoverSnapshotItem

Ordered items within a Discover snapshot.

| Field | Meaning |
|---|---|
| `snapshotId` | FK to DiscoverSnapshot. |
| `remoteItemId` | FK to RemoteItem. |
| `position` | Integer order. |
| `recommendationReason` | Optional internal reason/debug text. |

Notes: Primary key can be snapshotId + position or snapshotId + remoteItemId.

### DiscoverRefreshDiagnostics

Filtering/debug metrics for Discover refresh.

| Field | Meaning |
|---|---|
| `snapshotId` | FK to DiscoverSnapshot. |
| `candidatesRequested` | Integer. |
| `candidatesReceived` | Integer. |
| `alreadyImportedFiltered` | Integer. |
| `invalidOrUnavailableFiltered` | Integer. |
| `finalDisplayedCount` | Integer. |
| `refreshBroaderShown` | Boolean. |
| `apiRequestIdsJson` | Related API request IDs. |

Notes: Visible only under Refresh details.

### Playlist

Regular playlist plus special stored Favourites wrapper if desired.

| Field | Meaning |
|---|---|
| `playlistId` | String/UUID primary key. |
| `name` | Playlist name. |
| `playlistType` | REGULAR or FAVOURITES_WRAPPER if implemented. |
| `customThumbnailAssetId` | Nullable FK to ThumbnailAsset. |
| `createdAt` | Timestamp. |
| `modifiedAt` | Timestamp. |

Notes: Smart playlists are generated views, not stored as editable playlist rows except when saved as static regular playlists.

### PlaylistTrack

Songs inside regular playlists; duplicates forbidden.

| Field | Meaning |
|---|---|
| `playlistId` | FK to Playlist. |
| `trackId` | FK to Track. |
| `position` | Manual order position. |
| `addedAt` | Timestamp. |

Notes: Unique index on playlistId + trackId. Reorder disabled while search/sort is active.

### QueueState

Single persistent queue state and source context.

| Field | Meaning |
|---|---|
| `queueStateId` | Usually a singleton ID. |
| `sourceType` | HOME, HOME_SEARCH, PLAYLIST, SMART_PLAYLIST, DISCOVER, QUEUE, SONG_DETAILS, CUSTOM. |
| `sourceId` | Nullable playlist/smart/snapshot ID. |
| `sourceLabel` | Example: Queue from Playlist: Night Drive. |
| `isModified` | Boolean; shown as "Modified". |
| `currentQueueItemId` | Nullable current QueueItem. |
| `repeatMode` | OFF, ALL, ONE. |
| `shuffleEnabled` | Boolean. |
| `createdAt` | Timestamp. |
| `updatedAt` | Timestamp. |

Notes: Queue source and modified state persist after app restart.

### QueueItem

A row in the persistent queue. Can point to local Track or RemoteItem.

| Field | Meaning |
|---|---|
| `queueItemId` | String/UUID primary key. |
| `queueStateId` | FK to QueueState. |
| `trackId` | Nullable FK to Track. |
| `remoteItemId` | Nullable FK to RemoteItem. |
| `position` | Integer order over previous/current/upcoming. |
| `state` | PREVIOUS, CURRENT, UPCOMING, UNAVAILABLE. |
| `insertedBy` | TAP, PLAY_NEXT, ADD_TO_QUEUE, PLAYLIST_START, DISCOVER_START, RESTORE, MANUAL_REORDER. |
| `addedAt` | Timestamp. |

Notes: Exactly one of trackId or remoteItemId should be non-null. When remote imports, convert silently to trackId.

### PlaybackEvent

Atomic playback history event used for stats.

| Field | Meaning |
|---|---|
| `eventId` | String/UUID primary key. |
| `trackId` | Nullable FK to Track. |
| `remoteItemId` | Nullable FK to RemoteItem for pending remote stats. |
| `sessionId` | FK to PlaybackSession. |
| `startedAt` | Timestamp. |
| `endedAt` | Nullable timestamp. |
| `listenedMs` | Real elapsed listening time, excluding pause. |
| `durationMsAtPlayback` | Song duration at time of playback. |
| `validListen` | listenedMs >= min(15s, 50% duration). |
| `skip` | Manual move before valid listen threshold. |
| `completion` | Listened to at least 90% duration. |
| `completionPercent` | listened duration / song duration. |
| `stopPositionMs` | If not completed, timestamp where playback stopped. |
| `privateMode` | Boolean. Private events should normally not be stored; if temporary recovery exists, never merge. |
| `sourceContext` | Queue/source context at playback. |

Notes: Remote events merge into Track only if imported and not private.

### PlaybackSession

Continuous listening session where gaps under 5 minutes belong to same session.

| Field | Meaning |
|---|---|
| `sessionId` | String/UUID primary key. |
| `startedAt` | Timestamp. |
| `endedAt` | Nullable timestamp. |
| `totalListenedMs` | Accumulated listening time. |
| `eventCount` | Number of playback events. |
| `createdFromPrivateMode` | Boolean; normally false because private sessions are excluded. |

Notes: A new session starts when any playback starts after no playback for 5+ minutes.

### TrackStatsSnapshot

Optional denormalized stats for fast UI.

| Field | Meaning |
|---|---|
| `trackId` | Primary key FK to Track. |
| `playCount` | Valid listens. |
| `skipCount` | Skips. |
| `completionCount` | Completions. |
| `totalListenedMs` | Total real listening time. |
| `firstPlayedAt` | Nullable timestamp. |
| `lastPlayedAt` | Nullable timestamp. |
| `averageCompletionPercent` | Average over playback events. |

Notes: Can be recomputed from PlaybackEvent if needed, but snapshots improve UI speed.

### ImportJob

Persistent active/download job.

| Field | Meaning |
|---|---|
| `importJobId` | String/UUID primary key. |
| `apiJobId` | Stable server job ID for API-backed imports. |
| `jobType` | LOCAL_FILE, YOUTUBE_SEARCH, YOUTUBE_PLAYLIST_ITEM, DISCOVER_MANUAL, DISCOVER_AUTO_FAVOURITE, DISCOVER_AUTO_PLAYLIST. |
| `priority` | Manual jobs ahead of automatic jobs; user can reorder upcoming jobs. |
| `status` | QUEUED, DOWNLOADING, COPYING, EXTRACTING_METADATA, COMPLETED, FAILED, CANCELLED, NEEDS_ATTENTION. |
| `sourceUrl` | Nullable source URL. |
| `youtubeVideoId` | Nullable. |
| `remoteItemId` | Nullable FK to RemoteItem. |
| `targetPlaylistId` | Nullable for auto-import to playlist. |
| `targetFavourite` | Boolean for auto-import favourite. |
| `preferredFormat` | Requestable format; default M4A/AAC. |
| `actualFormat` | Final format if fallback occurs. |
| `progressPercent` | Nullable progress. |
| `errorCode` | Stable error code if failed. |
| `errorMessage` | Friendly/sanitized message. |
| `technicalDetailsJson` | Advanced details for history/debug. |
| `createdAt` | Timestamp. |
| `updatedAt` | Timestamp. |
| `completedAt` | Nullable timestamp. |

Notes: Active Imports has Downloading now and Upcoming to download sections.

### ImportHistoryEntry

Permanent history until manually cleared.

| Field | Meaning |
|---|---|
| `historyId` | String/UUID primary key. |
| `importJobId` | Nullable FK to ImportJob. |
| `result` | SUCCESS, DUPLICATE, FAILED, CANCELLED. |
| `reason` | Manual local import, Auto-import for Favourites, etc. |
| `trackId` | Nullable imported/merged Track. |
| `sourceUrl` | Nullable. |
| `youtubeVideoId` | Nullable. |
| `displayTitle` | Snapshot title. |
| `errorCode` | Nullable stable error code. |
| `friendlyMessage` | User-facing message. |
| `technicalDetailsJson` | Expandable advanced details. |
| `createdAt` | Timestamp. |

Notes: Search/filter by Success, Failed, Duplicate, Cancelled.

### ApiDiagnosticEntry

Local sanitized API log entry.

| Field | Meaning |
|---|---|
| `diagnosticId` | String/UUID primary key. |
| `requestId` | API request ID if provided. |
| `endpoint` | Endpoint path/name. |
| `method` | HTTP method. |
| `startedAt` | Timestamp. |
| `durationMs` | Long. |
| `statusCode` | Nullable HTTP status. |
| `success` | Boolean. |
| `errorCode` | Nullable stable API error code. |
| `sanitizedMessage` | No token/secrets. |
| `metadataJson` | Optional sanitized details. |

Notes: Keep last 500 entries.

### AppSettings / ApiSettings / PlayerSettings

Persistent settings. Can be one table with typed keys or separate singleton tables.

| Field | Meaning |
|---|---|
| `apiBaseUrl` | Configurable API base URL. |
| `apiTokenEncrypted` | Static token/API key stored securely. |
| `defaultRepeatMode` | Repeat all. |
| `defaultShuffleMode` | Off. |
| `defaultHomeSort` | Recently added. |
| `sleepTimerDefaultMinutes` | 30. |
| `privateModeEnabledForSession` | Manual private mode for current app session. |
| `themeMode` | Dark or AMOLED. |
| `accentColorHex` | Provided later by user. |

Notes: Tokens should be stored using Android secure storage rather than plain Room if possible.

## 5. Key relationships and constraints

- Track 1:1 TrackOriginalMetadata.
- Track many-to-many Playlist through PlaylistTrack, with unique playlistId + trackId.
- QueueItem points to either Track or RemoteItem, never both.
- RemoteItem can later point to importedTrackId after successful import or duplicate merge.
- DiscoverSnapshot contains ordered DiscoverSnapshotItem rows, each pointing to RemoteItem.
- PlaybackEvent points to Track for local/imported playback or RemoteItem for pending remote playback.
- ImportJob may point to RemoteItem and later to Track through ImportHistoryEntry.
- ThumbnailAsset can be referenced by tracks, playlists, and temporary cached remote/search results.

## 6. Recommended indexes

- Track: audioHash, youtubeVideoId, sourceUrl, importedAt, titleSortKey, artistSortKey, albumSortKey, albumArtistSortKey, isFavourite + favouritedAt.
- PlaylistTrack: unique playlistId + trackId, playlistId + position.
- QueueItem: queueStateId + position, trackId, remoteItemId.
- PlaybackEvent: trackId + startedAt, remoteItemId + startedAt, sessionId, startedAt.
- PlaybackSession: startedAt, endedAt.
- ImportJob: status + priority, apiJobId, remoteItemId, createdAt.
- ImportHistoryEntry: result + createdAt, trackId, youtubeVideoId.
- ApiDiagnosticEntry: startedAt, endpoint, success.

## 7. Smart playlist query model

Smart playlists should be generated from Track, TrackStatsSnapshot, PlaybackEvent, and DiscoverSnapshot rather than stored as regular playlists.

- Favourites: Track where isFavourite = true, sorted by favouritedAt descending.
- Most Listened: top 100 tracks by totalListenedMs, minimum listened time 30 seconds.
- Recently Played: latest 100 from PlaybackEvent/TrackStatsSnapshot; cleared by Reset Statistics and Clear Listening History.
- Never Played: tracks with no valid listens.
- Missing Metadata: any missing title, artist, album, genre, year, or thumbnail.
- Time-Based mixes: top 50 tracks by total listened duration in the matching time block.
- Discover: current DiscoverSnapshot, 25 non-imported remote items by default.

## 8. Open schema decisions

The core schema is largely settled. Remaining implementation choices:

1. Whether TrackStatsSnapshot is mandatory or derived on demand. Recommendation: use it for performance, recompute when needed.
2. Whether deleted tracks are hard-deleted only or optionally tombstoned during destructive operations. Product behavior says permanent delete after confirmation, so hard delete is acceptable.
3. Exact storage mechanism for API token. Recommendation: Android EncryptedSharedPreferences/DataStore or platform credential storage, not plain Room.
4. Exact JSON shape for rawMetadataJson, operation payloads, and API technical details.
5. Whether RemoteItem rows are pruned after Discover refreshes or retained for queue/history references. Recommendation: retain while referenced by queue/import/history, prune unreferenced stale rows.
