# Fenlzer API Implementation Build Plan

## 1. Purpose

This document turns the Fenlzer API endpoint contract into an implementation-oriented build plan. It defines the recommended server architecture, modules, persistent data model, job lifecycle, file lifecycle, implementation order, and test strategy for building the API before the Android application.

The API is designed specifically for Fenlzer. It should expose high-level Fenlzer endpoints rather than forcing the Android app to orchestrate low-level YouTubeDL behavior.

## 2. Core implementation goals

- Provide a stable, authenticated API for Fenlzer.
- Support YouTube search, playlist preview, downloads, remote streaming, Discover, and diagnostics.
- Persist download jobs across API/server restarts.
- Support Android app restart recovery through stable job IDs and job status restoration.
- Enforce a maximum of three concurrent YouTube downloads.
- Return best-quality streams for remote Discover playback.
- Allow requested download formats, defaulting to M4A/AAC.
- Fall back to the best available audio format if M4A/AAC cannot be produced cleanly.
- Stream completed files back to Fenlzer, then delete the server temporary file after app confirmation or 60-minute expiry.
- Accept full Fenlzer listening history for Discover using Zstandard-compressed JSON chunks.
- Exclude private-mode playback history entirely.

## 3. Recommended API stack

The exact stack can be adapted to the existing API, but this plan assumes the current API can evolve into a Fenlzer-specific service.

Recommended default stack:

- Python 3.12+
- FastAPI
- Uvicorn
- yt-dlp as the YouTubeDL engine
- SQLite for a small personal deployment, or PostgreSQL if stronger durability/concurrency is desired
- Background worker loop inside the API process for a simple personal deployment
- Optional separate worker process later if downloads become heavy
- Zstandard support for compressed history uploads
- Structured JSON logging

The API should remain simple to deploy on a VPS, but should still have durable job metadata and predictable cleanup behavior.

## 4. High-level server modules

### 4.1 Core module

Responsibilities:

- Load configuration.
- Validate API token.
- Generate or propagate request IDs.
- Apply idempotency keys where supported.
- Return standardized error envelopes.
- Configure logging.
- Expose global exception handling.

Suggested files:

```text
app/core/config.py
app/core/auth.py
app/core/errors.py
app/core/request_context.py
app/core/logging.py
```

### 4.2 API routers

Suggested routers:

```text
app/api/health.py
app/api/search.py
app/api/playlists.py
app/api/jobs.py
app/api/streaming.py
app/api/discover.py
app/api/diagnostics.py
```

Endpoint group ownership:

| Router | Responsibility |
|---|---|
| `health.py` | API health, authentication, yt-dlp availability, feature status |
| `search.py` | YouTube search endpoint |
| `playlists.py` | YouTube playlist preview and preview caching |
| `jobs.py` | Download jobs, batch jobs, job status, restore, retry, cancel, reorder, file transfer, confirmation |
| `streaming.py` | Best-quality remote stream URL resolution and validation |
| `discover.py` | History upload sessions, chunk upload, Discover refresh, broader Discover refresh |
| `diagnostics.py` | Recent sanitized API diagnostics and server-side debug status |

### 4.3 Services

Suggested services:

```text
app/services/ytdlp_service.py
app/services/search_service.py
app/services/playlist_preview_service.py
app/services/download_job_service.py
app/services/download_worker.py
app/services/file_artifact_service.py
app/services/stream_resolver_service.py
app/services/discover_service.py
app/services/history_upload_service.py
app/services/thumbnail_service.py
app/services/diagnostics_service.py
```

Key rule: services should return Fenlzer-level models and error codes, not raw yt-dlp output.

### 4.4 Persistence repositories

Suggested repositories:

```text
app/repositories/job_repository.py
app/repositories/playlist_preview_repository.py
app/repositories/stream_cache_repository.py
app/repositories/history_upload_repository.py
app/repositories/discover_repository.py
app/repositories/diagnostic_repository.py
```

Repositories should hide the chosen database backend from the service layer.

### 4.5 Background worker

The worker handles:

- selecting queued jobs by priority
- enforcing three concurrent YouTube downloads
- running yt-dlp
- post-processing output
- computing hashes
- marking files ready for transfer
- cleaning cancelled/expired artifacts
- resuming or repairing interrupted jobs after API restart

The worker must not require Fenlzer to stay connected.

## 5. Configuration

Required configuration:

| Setting | Purpose |
|---|---|
| `API_TOKEN` | Static token/API key used by Fenlzer |
| `BASE_PUBLIC_URL` | Optional public URL used when constructing file/stream links |
| `DATABASE_URL` | SQLite/PostgreSQL connection |
| `TEMP_DOWNLOAD_DIR` | Directory for server-side temporary download artifacts |
| `MAX_CONCURRENT_DOWNLOADS` | Default `3` |
| `COMPLETED_FILE_EXPIRY_SECONDS` | Default `3600` |
| `PLAYLIST_PREVIEW_CACHE_SECONDS` | Default `86400` |
| `REQUEST_TIMEOUT_SECONDS` | General external request timeout |
| `YTDLP_BINARY` | Path/name of yt-dlp executable if needed |
| `LOG_LEVEL` | Runtime log level |

Optional configuration:

| Setting | Purpose |
|---|---|
| `MAX_DISCOVER_CANDIDATES` | Default `75` |
| `DEFAULT_DOWNLOAD_FORMAT` | Default `m4a/aac` |
| `STREAM_QUALITY` | Default `best` |
| `DIAGNOSTIC_RETENTION` | Default server-side retention policy |

## 6. Authentication and request conventions

Every endpoint except possibly a minimal unauthenticated liveness endpoint should require the static token.

Recommended header:

```http
Authorization: Bearer <token>
```

Every response should include or echo:

- `requestId`
- `serverTime`

Every mutating endpoint should accept:

- `Idempotency-Key`

Error responses must follow a consistent envelope:

```json
{
  "requestId": "req_01HX...",
  "error": {
    "code": "STREAM_UNAVAILABLE",
    "message": "This recommendation is no longer available.",
    "retryable": false,
    "details": {}
  }
}
```

## 7. Persistent API data model

### 7.1 DownloadJob

One row per Fenlzer download/import job.

Fields:

| Field | Notes |
|---|---|
| `jobId` | Stable API job ID |
| `clientRequestId` | Optional Fenlzer-side correlation ID |
| `idempotencyKey` | Prevents duplicate job creation |
| `sourceType` | `youtube_search`, `discover`, `youtube_playlist` |
| `sourceUrl` | Original URL when available |
| `youtubeVideoId` | Strong identity when available |
| `title` | Best known title |
| `artistOrChannel` | Best known artist/channel |
| `thumbnailUrl` | Remote thumbnail |
| `requestedFormat` | Format requested by Fenlzer |
| `actualFormat` | Format actually produced |
| `reason` | Manual/auto import reason |
| `priority` | Effective queue priority |
| `status` | Job state |
| `progressPercent` | Nullable |
| `errorCode` | Stable error code when failed |
| `errorMessage` | User-safe message |
| `technicalError` | Sanitized technical detail |
| `createdAt` | Timestamp |
| `updatedAt` | Timestamp |
| `startedAt` | Timestamp |
| `completedAt` | Timestamp |
| `expiresAt` | File expiry when ready |
| `confirmedAt` | Fenlzer confirmation timestamp |

### 7.2 FileArtifact

One row per server temporary file produced by a completed job.

Fields:

| Field | Notes |
|---|---|
| `artifactId` | Stable artifact ID |
| `jobId` | Parent job |
| `path` | Server-local temp file path |
| `sizeBytes` | File size |
| `sha256` | Required for Fenlzer verification |
| `mimeType` | Actual MIME type |
| `container` | Container/extension |
| `codec` | Audio codec if known |
| `bitrate` | Optional |
| `durationMs` | Optional but useful |
| `createdAt` | Timestamp |
| `expiresAt` | Timestamp |
| `deletedAt` | Timestamp |

### 7.3 PlaylistPreview

Cached YouTube playlist preview.

Fields:

| Field | Notes |
|---|---|
| `previewId` | Stable preview ID |
| `playlistUrl` | Original URL |
| `playlistTitle` | Best known title |
| `playlistThumbnailUrl` | Optional |
| `totalCount` | Known/estimated count |
| `loadedCount` | Count currently loaded |
| `status` | `loading`, `complete`, `partial`, `failed` |
| `createdAt` | Timestamp |
| `expiresAt` | 24-hour expiry |

### 7.4 PlaylistPreviewItem

Fields:

| Field | Notes |
|---|---|
| `previewItemId` | Stable item ID |
| `previewId` | Parent preview |
| `index` | Playlist order |
| `youtubeVideoId` | Strong identity when available |
| `url` | Item URL |
| `title` | Best known title |
| `artistOrChannel` | Best known channel/artist |
| `durationMs` | Nullable |
| `thumbnailUrl` | Optional |
| `availability` | `available`, `unavailable`, `private`, `deleted`, `unknown` |

### 7.5 StreamCacheEntry

The API makes the final validity decision, but stream results can be cached server-side.

Fields:

| Field | Notes |
|---|---|
| `remoteItemId` | Fenlzer remote ID or server-side ID |
| `youtubeVideoId` | Strong identity |
| `playableUrl` | Cached playable URL if safe to store |
| `expiresAt` | Expiry if known |
| `quality` | `best` |
| `mimeType` | MIME type |
| `requiredHeadersJson` | Optional headers needed by player |
| `createdAt` | Timestamp |
| `updatedAt` | Timestamp |

### 7.6 HistoryUploadSession

Used for Discover full-history upload.

Fields:

| Field | Notes |
|---|---|
| `uploadId` | Stable upload session ID |
| `expectedChunks` | Total chunk count |
| `receivedChunks` | Received count |
| `compression` | `zstd` |
| `status` | `open`, `complete`, `failed`, `expired` |
| `createdAt` | Timestamp |
| `completedAt` | Timestamp |
| `expiresAt` | Timestamp |

### 7.7 HistoryUploadChunk

Fields:

| Field | Notes |
|---|---|
| `uploadId` | Parent upload |
| `chunkIndex` | Zero-based or one-based, but consistent |
| `sha256` | Compressed chunk hash |
| `sizeBytes` | Compressed size |
| `receivedAt` | Timestamp |

### 7.8 DiscoverRefreshRecord

Stores server-side Discover refresh diagnostics.

Fields:

| Field | Notes |
|---|---|
| `refreshId` | Stable refresh ID |
| `uploadId` | History upload used |
| `mode` | `normal` or `broader` |
| `requestedCandidateLimit` | 25-75 dynamic cap |
| `candidatesReceived` | Count |
| `alreadyImportedFiltered` | Count |
| `unavailableFiltered` | Count |
| `finalCount` | Count returned to Fenlzer |
| `createdAt` | Timestamp |

### 7.9 ApiDiagnosticEntry

Fields:

| Field | Notes |
|---|---|
| `diagnosticId` | Stable ID |
| `requestId` | Request correlation |
| `endpoint` | Logical endpoint |
| `statusCode` | HTTP status |
| `durationMs` | Request duration |
| `errorCode` | Optional |
| `message` | Sanitized summary |
| `createdAt` | Timestamp |

## 8. Download job lifecycle

### 8.1 States

Recommended job states:

| State | Meaning |
|---|---|
| `queued` | Job is waiting to run |
| `running` | yt-dlp/download process is active |
| `post_processing` | Format conversion, metadata extraction, hashing, cleanup |
| `ready_for_transfer` | File is ready for Fenlzer to retrieve |
| `transferring` | Optional transient state while file is being streamed |
| `completed_confirmed` | Fenlzer confirmed successful local import/hash verification |
| `failed` | Job failed |
| `cancelled` | User/API cancelled job |
| `expired` | Completed file expired before Fenlzer confirmed import |
| `needs_attention` | API cannot determine state after restart or partial failure |

### 8.2 State transitions

```text
queued -> running
running -> post_processing
post_processing -> ready_for_transfer
ready_for_transfer -> completed_confirmed
ready_for_transfer -> expired
queued -> cancelled
running -> cancelled
post_processing -> cancelled
running -> failed
post_processing -> failed
unknown_after_restart -> needs_attention
failed -> queued          (retry)
expired -> queued         (retry)
needs_attention -> queued (retry)
```

### 8.3 Concurrency rule

The worker may run at most three YouTube downloads at once.

Running jobs should not be preempted automatically. If a new higher-priority manual job is created while three jobs are running, it waits until the next slot is available, then runs before lower-priority queued jobs.

### 8.4 Priority rule

Manual imports/downloads always outrank automatic imports.

Recommended default priority order:

1. User-manually prioritized jobs in Active Imports.
2. Manual single-song download/import.
3. Manual currently-streaming song download.
4. Manual YouTube playlist selected/batch imports.
5. Auto-import for Favourite.
6. Auto-import for Add to Playlist.

The upcoming queue is manually reorderable, and manual reorder should override the computed priority within the waiting queue.

## 9. Server temporary file lifecycle

### 9.1 Normal lifecycle

1. Fenlzer creates a download job.
2. API downloads/prepares the file on the server.
3. API marks job `ready_for_transfer`.
4. Fenlzer streams the completed file from the API.
5. Fenlzer stores it in private app storage.
6. Fenlzer verifies SHA-256/hash.
7. Fenlzer calls file confirmation endpoint.
8. API marks job `completed_confirmed`.
9. API deletes the server temporary file.

### 9.2 Expiry lifecycle

If Fenlzer does not confirm the file within 60 minutes:

1. API marks job `expired`.
2. API deletes the server temporary file.
3. Fenlzer may retry the job if needed.

### 9.3 Failed transfer

If the HTTP transfer fails before Fenlzer confirms import:

- Keep the artifact until expiry.
- Fenlzer may retry retrieval.
- Do not delete solely because a transfer was attempted.

## 10. yt-dlp integration responsibilities

The `ytdlp_service` should provide stable operations:

- Search YouTube.
- Extract playlist preview progressively.
- Resolve best-quality audio stream URL.
- Download best/preferred audio.
- Convert/extract to requested format when possible.
- Return best available format when requested M4A/AAC cannot be produced cleanly.
- Extract metadata: title, channel/artist, duration, thumbnail, video ID, original URL.
- Surface stable Fenlzer error codes.

The rest of the API should not parse raw yt-dlp command output directly.

## 11. Streaming URL resolution

Remote Discover streaming uses best available quality.

Resolution behavior:

1. Fenlzer requests stream resolution with remote item/video identity.
2. API decides whether any cached stream URL is still valid.
3. If valid, return it.
4. If expired/missing, ask yt-dlp for a fresh URL.
5. Return playable URL, expiry if known, duration, MIME type, and required headers.

Fenlzer continuously prefetches the current remote item and the next two remote queue items. The API should therefore optimize stream URL resolution to be fast and idempotent.

## 12. Discover implementation

### 12.1 Input

Fenlzer uploads full local listening history, excluding all private-mode events, using Zstandard-compressed JSON chunks of roughly 1 MB.

The upload flow should be:

1. Create upload session.
2. Upload chunks with hashes.
3. Finalize upload.
4. API validates all chunks are present.
5. API reconstructs history and uses it for Discover.

### 12.2 Candidate generation

Discover should request 25-75 candidates dynamically depending on:

- number of songs in the app
- previous refreshes' filtering losses
- need to return 25 final non-imported songs

Hard cap: 75 candidates.

### 12.3 Filtering

API should filter out:

- already imported YouTube video IDs
- known source URLs already in library
- unavailable videos
- candidates rejected by strong identity checks

Title + artist + similar duration is not enough to reject a candidate as already imported.

### 12.4 Result behavior

Normal Discover target result count: 25.

If fewer than 5 valid non-imported songs remain, Fenlzer should show the available results and offer `Refresh broader`.

## 13. Playlist preview implementation

Playlist preview should support progressive loading.

Behavior:

- The API returns a preview ID quickly.
- Items can be loaded progressively.
- Fenlzer can poll or request more loaded items.
- Large playlists show progress such as `Loaded 47 / 214`.
- Preview cache lasts 24 hours.

Unavailable/private/deleted videos should appear as unavailable preview items rather than breaking the whole preview.

## 14. Diagnostics and logs

### 14.1 Server-side diagnostics

The API should store sanitized diagnostic entries for recent requests and jobs.

Never store secrets or full authentication tokens in diagnostics.

### 14.2 App-side diagnostics support

Every API response should include `requestId`, allowing Fenlzer to correlate local app logs with server diagnostics.

### 14.3 Health detail

The health endpoint should report:

- API reachable
- authentication valid
- yt-dlp available
- active jobs supported
- streaming supported
- playlist preview supported
- Discover supported
- server storage writable

## 15. Error code taxonomy

Stable error codes should include at least:

| Code | Meaning |
|---|---|
| `UNAUTHORIZED` | Missing/invalid token |
| `BAD_REQUEST` | Invalid request |
| `YTDLP_UNAVAILABLE` | yt-dlp missing or unusable |
| `VIDEO_UNAVAILABLE` | Video unavailable/private/deleted |
| `PLAYLIST_UNAVAILABLE` | Playlist unavailable |
| `STREAM_UNAVAILABLE` | Stream cannot be resolved |
| `DOWNLOAD_FAILED` | Download failed |
| `POST_PROCESSING_FAILED` | Conversion/extraction/hash failed |
| `FILE_EXPIRED` | Completed file expired before confirmation |
| `JOB_NOT_FOUND` | Unknown job ID |
| `JOB_NOT_READY` | File requested before ready |
| `JOB_NOT_CANCELLABLE` | Cancel not possible in current state |
| `HISTORY_UPLOAD_INCOMPLETE` | Missing history chunks |
| `DISCOVER_INSUFFICIENT_RESULTS` | Discover could not find enough valid recommendations |
| `API_TIMEOUT` | External/internal timeout |
| `API_RATE_LIMITED` | API throttled request |
| `INTERNAL_ERROR` | Unexpected server error |

## 16. Implementation order

### Phase 1: Project foundation

Build:

- FastAPI project skeleton
- configuration loading
- token authentication
- request ID middleware
- error envelope handling
- structured logging
- database setup and migrations
- base repository pattern

Acceptance:

- API starts reliably.
- Invalid token is rejected.
- Request IDs appear in success and error responses.
- Database can persist and read a simple record.

### Phase 2: Health and diagnostics

Build:

- health endpoint
- yt-dlp availability check
- writable temp storage check
- diagnostics storage
- diagnostics endpoint

Acceptance:

- Settings/Test API connection in Fenlzer can show detailed status.
- Server can report whether streaming/download/Discover features are supported.

### Phase 3: YouTube search

Build:

- YouTube search endpoint
- ytdlp search wrapper
- result metadata normalization
- stable error handling

Acceptance:

- Search returns thumbnail, title, channel/artist, duration, URL/video ID.
- Search errors map to stable Fenlzer error codes.

### Phase 4: Playlist preview

Build:

- playlist preview creation
- progressive loading
- preview cache persistence for 24 hours
- unavailable item handling

Acceptance:

- Large playlists can be previewed progressively.
- Fenlzer can select all or individual items.
- Unavailable items do not break the preview.

### Phase 5: Download jobs

Build:

- create job
- create batch jobs
- persistent queue
- worker loop
- max three concurrent downloads
- job priority and manual reorder
- cancel/retry
- restart restoration

Acceptance:

- Jobs survive API restart.
- Fenlzer can restore Active Imports.
- Manual jobs run ahead of automatic queued jobs.
- Running jobs are not preempted automatically.

### Phase 6: File artifact delivery

Build:

- file artifact table
- SHA-256 computation
- stream completed file endpoint
- confirm endpoint
- 60-minute expiry cleanup

Acceptance:

- API does not delete file until Fenlzer confirms or expiry occurs.
- Expired jobs can be retried.
- Fenlzer can verify hash before confirmation.

### Phase 7: Streaming resolution

Build:

- resolve stream endpoint
- stream URL cache/validation
- best-quality stream selection
- required headers response
- retryable error mapping

Acceptance:

- Fenlzer can stream remote Discover items quickly.
- Repeated prefetch calls are safe and efficient.
- Expired URLs are refreshed by the API.

### Phase 8: Discover history upload

Build:

- create upload session
- accept Zstandard-compressed JSON chunks
- validate chunk hashes
- finalize upload
- reconstruct full history

Acceptance:

- Full listening history can be uploaded in roughly 1 MB compressed chunks.
- Missing chunks prevent finalization with a clear error.
- Private-mode events are absent by design.

### Phase 9: Discover refresh

Build:

- normal Discover refresh
- broader Discover refresh
- candidate over-fetch up to 75
- already-imported filtering
- unavailable filtering
- diagnostics metrics

Acceptance:

- API returns up to 25 valid non-imported recommendations.
- If fewer than 5 valid results remain, Fenlzer can show Refresh broader.
- Refresh diagnostics are returned and stored.

### Phase 10: Hardening and acceptance tests

Build:

- integration test suite
- restart recovery tests
- cleanup tests
- load/concurrency tests
- error mapping tests
- contract example validation

Acceptance:

- API passes the V1 acceptance checklist.
- Fenlzer can be developed against the endpoint contract without guessing server behavior.

## 17. Test strategy

### 17.1 Unit tests

Test:

- config parsing
- auth validation
- error envelope generation
- priority calculation
- state transitions
- expiry calculation
- Zstandard chunk validation
- request ID propagation
- yt-dlp output normalization using fixtures

### 17.2 Integration tests

Test:

- health endpoint
- search endpoint
- playlist preview endpoint
- job creation/status/cancel/retry/reorder
- file transfer and confirmation
- stream resolution
- history upload session/chunks/finalization
- Discover refresh and broader refresh

### 17.3 Lifecycle tests

Test:

- API restart while jobs are queued
- API restart while jobs are running
- API restart when file is ready for transfer
- Fenlzer retrieves file but does not confirm
- file expires after 60 minutes
- cancelled job cleans partial files
- failed job can be retried

### 17.4 Concurrency tests

Test:

- no more than three downloads run at once
- manual jobs run before lower-priority automatic jobs when a slot opens
- manual reorder changes waiting job order
- batch jobs do not starve single manual downloads

### 17.5 Discover tests

Test:

- full history upload in chunks
- missing chunk handling
- duplicate filtering by YouTube video ID/source URL
- title/artist/duration does not cause duplicate rejection
- candidate cap of 75
- fewer-than-5 result behavior
- Refresh broader behavior

### 17.6 Security and privacy tests

Test:

- invalid token rejected
- diagnostics do not expose token
- private-mode events are never sent by app test fixtures and are not accepted as Discover history if marked private
- file endpoint requires authentication
- job IDs cannot leak unrelated files

## 18. Minimal V1 API acceptance checklist

The API is ready for Fenlzer V1 app development when:

- Health check returns detailed status.
- Static token authentication works.
- Search returns usable YouTube results.
- Playlist preview loads progressively and caches for 24 hours.
- Download jobs persist across server restarts.
- Three-download concurrency limit is enforced.
- Manual jobs outrank automatic queued jobs.
- Completed file transfer uses stream-back plus app confirmation.
- Unconfirmed files expire after 60 minutes.
- Stream resolution returns best-quality playable URLs and refreshes expired URLs.
- Full listening history upload supports Zstandard chunks.
- Discover returns non-imported recommendations with diagnostics.
- Diagnostics provide sanitized request/job traces.
- Error codes are stable and documented.

## 19. Open implementation details

The product behavior is settled, but these low-level implementation details can be finalized during coding:

- SQLite versus PostgreSQL for the deployed API.
- Exact yt-dlp command arguments for each operation.
- Exact background worker strategy: in-process worker versus separate process.
- Whether stream URLs can be safely cached server-side or should always be refreshed via yt-dlp.
- Exact retention period for old server-side diagnostics.
- Whether playlist preview progress is pushed, polled, or fetched on demand.

