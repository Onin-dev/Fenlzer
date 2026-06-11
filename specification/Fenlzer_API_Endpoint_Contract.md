# Fenlzer API Endpoint Contract

Implementation-ready HTTP contract draft for the Fenlzer API

## Status

Companion implementation document for the final Fenlzer application plan and the broader Fenlzer API specification. This document defines concrete endpoint behavior, request/response shapes, status codes, job states, and JSON examples. It is intentionally practical rather than theoretical.

## 1. Global conventions

### Base URL

The Android app stores the API base URL in Settings. All examples use:

```text
https://api.example.com/v1
```

### Authentication

Every endpoint except a possible unauthenticated liveness probe uses a static API token.

```http
Authorization: Bearer <api_token>
```

The token is configured in Fenlzer Settings and stored on Android using secure/encrypted storage, not plain Room.

### Content types

Normal JSON endpoints:

```http
Content-Type: application/json
Accept: application/json
```

Compressed listening-history chunk upload:

```http
Content-Type: application/octet-stream
Content-Encoding: zstd
X-Fenlzer-Upload-Id: upl_...
X-Fenlzer-Chunk-Index: 0
X-Fenlzer-Chunk-Count: 8
```

Completed file transfer:

```http
Accept: application/octet-stream
```

### Timestamps

All timestamps are ISO-8601 UTC strings.

```json
"2026-06-07T14:22:30Z"
```

### Request IDs

The API returns a stable request ID on every response.

```http
X-Request-Id: req_20260607_000001
```

The same value is also present in JSON responses where useful.

### Idempotency

Fenlzer should send an idempotency key for mutating operations that may be retried after network failure.

```http
Idempotency-Key: fenlzer_7f38d7b2-1a4b-42ea-a6df-9f58a7e09e72
```

The API should safely return the original result if the same idempotency key is submitted again for the same endpoint and same authenticated app.

### Standard success envelope

Endpoints may return plain binary data for file transfer. JSON endpoints should use this envelope.

```json
{
  "success": true,
  "requestId": "req_20260607_000001",
  "data": {}
}
```

### Standard error envelope

```json
{
  "success": false,
  "requestId": "req_20260607_000002",
  "error": {
    "code": "API_TIMEOUT",
    "message": "The Fenlzer API could not complete the request in time.",
    "retryable": true,
    "recommendedRetryAfterMs": 5000,
    "details": {
      "safeTechnicalMessage": "yt-dlp process timed out while resolving metadata."
    }
  }
}
```

### Standard error codes

| Code | Meaning |
|---|---|
| `UNAUTHORIZED` | Missing or invalid API token. |
| `BAD_REQUEST` | Invalid JSON, missing field, invalid value. |
| `NOT_FOUND` | Resource does not exist or is no longer available. |
| `CONFLICT` | Duplicate or incompatible state transition. |
| `API_TIMEOUT` | Server-side process timed out. |
| `API_RATE_LIMITED` | API or upstream service rate-limited the request. |
| `NETWORK_ERROR` | API could not reach upstream resource. |
| `YTDLP_ERROR` | yt-dlp failed with a known error. |
| `VIDEO_UNAVAILABLE` | YouTube item cannot be accessed. |
| `STREAM_UNAVAILABLE` | Stream URL cannot be resolved or played. |
| `DOWNLOAD_UNAVAILABLE` | Download cannot be produced. |
| `FORMAT_UNAVAILABLE` | Requested format cannot be produced and fallback is disabled. |
| `JOB_NOT_READY` | File/result requested before job completion. |
| `JOB_EXPIRED` | Temporary completed file expired before retrieval. |
| `JOB_CANCELLED` | Job was cancelled. |
| `UNKNOWN_ERROR` | Unexpected server error. |

## 2. Shared data models

### YouTubeItem

```json
{
  "remoteItemId": "rem_ytd_9b0c4a",
  "youtubeVideoId": "dQw4w9WgXcQ",
  "sourceUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "title": "Example Song",
  "artistOrChannel": "Example Channel",
  "durationMs": 213000,
  "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
  "canStream": true,
  "canDownload": true,
  "isLive": false,
  "isUnavailable": false
}
```

### ApiJob

```json
{
  "apiJobId": "job_20260607_000123",
  "clientJobId": "local_job_3c0e4c",
  "jobType": "DISCOVER_AUTO_FAVOURITE",
  "status": "DOWNLOADING",
  "priorityClass": "AUTOMATIC",
  "queuePosition": 2,
  "progressPercent": 42,
  "youtubeVideoId": "dQw4w9WgXcQ",
  "sourceUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "displayTitle": "Example Song",
  "preferredFormat": "M4A_AAC",
  "actualFormat": null,
  "createdAt": "2026-06-07T14:00:00Z",
  "updatedAt": "2026-06-07T14:01:20Z",
  "completedAt": null,
  "file": null,
  "error": null
}
```

### Completed file descriptor

```json
{
  "available": true,
  "expiresAt": "2026-06-07T15:01:20Z",
  "sizeBytes": 7351132,
  "sha256": "c2f5a6d3...",
  "contentType": "audio/mp4",
  "filename": "dQw4w9WgXcQ.m4a"
}
```

### Audio format values

| Value | Meaning |
|---|---|
| `M4A_AAC` | Preferred default. Produce M4A/AAC if cleanly possible. |
| `BEST_AVAILABLE` | Best audio format available from source, no strict conversion requirement. |
| `SOURCE_NATIVE` | Preserve source-native audio container/codec if possible. |
| `MP3` | Optional compatibility format if supported by API. |
| `OPUS` | Optional efficient format if supported by API. |

Fenlzer default is `M4A_AAC`. If the API cannot produce it cleanly, it returns best available audio instead and reports the actual final format.

## 3. Health and capability endpoints

### GET /v1/health

Purpose: validate API reachability, authentication, yt-dlp availability, and feature support for Settings -> Test API connection.

Request:

```http
GET /v1/health HTTP/1.1
Authorization: Bearer <api_token>
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000010",
  "data": {
    "status": "OK",
    "apiVersion": "1.0.0",
    "serverTime": "2026-06-07T14:10:00Z",
    "authenticated": true,
    "features": {
      "youtubeSearch": true,
      "playlistPreview": true,
      "downloadJobs": true,
      "persistentJobs": true,
      "jobRestore": true,
      "jobCancel": true,
      "jobRetry": true,
      "jobReorder": true,
      "streamResolve": true,
      "discover": true,
      "diagnostics": true,
      "zstdHistoryUpload": true
    },
    "tools": {
      "ytDlpAvailable": true,
      "ytDlpVersion": "2026.05.22",
      "ffmpegAvailable": true,
      "ffmpegVersion": "7.1"
    },
    "limits": {
      "maxParallelDownloads": 3,
      "completedFileExpiryMinutes": 60,
      "maxDiscoverCandidates": 75,
      "targetHistoryChunkSizeBytes": 1048576
    }
  }
}
```

## 4. YouTube search

### POST /v1/youtube/search

Purpose: search YouTube from the Import tab and return video cards ready for download.

Request:

```json
{
  "query": "daft punk around the world",
  "limit": 5,
  "includeUnavailable": false,
  "clientContext": {
    "fenlzerVersion": "1.0.0",
    "locale": "en",
    "timezone": "Europe/Paris"
  }
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000020",
  "data": {
    "query": "daft punk around the world",
    "results": [
      {
        "remoteItemId": "rem_search_001",
        "youtubeVideoId": "LKYPYj2XX80",
        "sourceUrl": "https://www.youtube.com/watch?v=LKYPYj2XX80",
        "title": "Daft Punk - Around The World",
        "artistOrChannel": "Daft Punk",
        "durationMs": 249000,
        "thumbnailUrl": "https://i.ytimg.com/vi/LKYPYj2XX80/hqdefault.jpg",
        "canStream": true,
        "canDownload": true,
        "isLive": false,
        "isUnavailable": false
      }
    ]
  }
}
```

Notes:

- Fenlzer displays thumbnail, title, channel/artist, duration, and Download button.
- Duplicate detection against the local library remains app-side using strong identifiers; the API may also accept exclusion IDs in future.

## 5. YouTube playlist preview

### POST /v1/youtube/playlists/preview

Purpose: create or reuse a cached progressive playlist preview. Fenlzer caches previews for 24 hours.

Request:

```json
{
  "playlistUrl": "https://www.youtube.com/playlist?list=PL_example",
  "cacheTtlHours": 24,
  "progressive": true,
  "clientKnownVideoIds": ["LKYPYj2XX80", "abc123"],
  "includeUnavailable": true
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000030",
  "data": {
    "previewId": "prev_20260607_000001",
    "status": "LOADING",
    "playlistTitle": "Example Playlist",
    "playlistThumbnailUrl": "https://i.ytimg.com/vi/example/hqdefault.jpg",
    "totalExpectedItems": 214,
    "loadedItemCount": 47,
    "expiresAt": "2026-06-08T14:12:00Z",
    "items": [
      {
        "position": 0,
        "remoteItemId": "rem_pl_001",
        "youtubeVideoId": "video001",
        "sourceUrl": "https://www.youtube.com/watch?v=video001",
        "title": "Playlist Song 1",
        "artistOrChannel": "Channel 1",
        "durationMs": 181000,
        "thumbnailUrl": "https://i.ytimg.com/vi/video001/hqdefault.jpg",
        "canStream": true,
        "canDownload": true,
        "availability": "AVAILABLE",
        "alreadyKnownByClient": false
      }
    ]
  }
}
```

### GET /v1/youtube/playlists/preview/{previewId}

Purpose: poll/read progressive preview results.

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000031",
  "data": {
    "previewId": "prev_20260607_000001",
    "status": "COMPLETE",
    "playlistTitle": "Example Playlist",
    "totalExpectedItems": 214,
    "loadedItemCount": 214,
    "expiresAt": "2026-06-08T14:12:00Z",
    "items": []
  }
}
```

Preview statuses: `LOADING`, `COMPLETE`, `FAILED`, `EXPIRED`.

Item availability values: `AVAILABLE`, `PRIVATE`, `DELETED`, `REGION_BLOCKED`, `UNSUPPORTED`, `UNKNOWN_UNAVAILABLE`.

## 6. Downloads and jobs

### POST /v1/downloads

Purpose: create a persistent server-side download job. The server downloads/prepares the audio file, Fenlzer later streams it back, then the server deletes it after confirmation or 60-minute expiry.

Request:

```json
{
  "clientJobId": "local_job_3c0e4c",
  "source": {
    "type": "YOUTUBE_VIDEO",
    "youtubeVideoId": "LKYPYj2XX80",
    "sourceUrl": "https://www.youtube.com/watch?v=LKYPYj2XX80",
    "remoteItemId": "rem_search_001"
  },
  "jobType": "YOUTUBE_SEARCH",
  "priorityClass": "MANUAL",
  "preferredFormat": "M4A_AAC",
  "fallbackToBestAvailable": true,
  "reason": "Manual YouTube search import",
  "target": {
    "favouriteAfterImport": false,
    "playlistIdAfterImport": null
  }
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000040",
  "data": {
    "job": {
      "apiJobId": "job_20260607_000123",
      "clientJobId": "local_job_3c0e4c",
      "jobType": "YOUTUBE_SEARCH",
      "status": "QUEUED",
      "priorityClass": "MANUAL",
      "queuePosition": 1,
      "progressPercent": 0,
      "youtubeVideoId": "LKYPYj2XX80",
      "sourceUrl": "https://www.youtube.com/watch?v=LKYPYj2XX80",
      "displayTitle": "Daft Punk - Around The World",
      "preferredFormat": "M4A_AAC",
      "actualFormat": null,
      "createdAt": "2026-06-07T14:15:00Z",
      "updatedAt": "2026-06-07T14:15:00Z",
      "completedAt": null,
      "file": null,
      "error": null
    }
  }
}
```

Manual downloads always stay ahead of automatic imports. Already-running automatic downloads are not cancelled; manual jobs take the next available slot first.

### POST /v1/downloads/batch

Purpose: create multiple download jobs, especially from YouTube playlist preview selections.

Request:

```json
{
  "batchId": "batch_20260607_0001",
  "priorityClass": "MANUAL",
  "preferredFormat": "M4A_AAC",
  "fallbackToBestAvailable": true,
  "reason": "YouTube playlist import",
  "items": [
    {
      "clientJobId": "local_job_a",
      "youtubeVideoId": "video001",
      "sourceUrl": "https://www.youtube.com/watch?v=video001",
      "remoteItemId": "rem_pl_001"
    },
    {
      "clientJobId": "local_job_b",
      "youtubeVideoId": "video002",
      "sourceUrl": "https://www.youtube.com/watch?v=video002",
      "remoteItemId": "rem_pl_002"
    }
  ]
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000041",
  "data": {
    "batchId": "batch_20260607_0001",
    "createdJobs": [
      { "clientJobId": "local_job_a", "apiJobId": "job_001", "status": "QUEUED" },
      { "clientJobId": "local_job_b", "apiJobId": "job_002", "status": "QUEUED" }
    ],
    "rejectedItems": []
  }
}
```

### GET /v1/jobs/{jobId}

Purpose: get one persistent job status.

Response for completed job:

```json
{
  "success": true,
  "requestId": "req_20260607_000050",
  "data": {
    "job": {
      "apiJobId": "job_20260607_000123",
      "clientJobId": "local_job_3c0e4c",
      "jobType": "YOUTUBE_SEARCH",
      "status": "COMPLETED",
      "priorityClass": "MANUAL",
      "progressPercent": 100,
      "youtubeVideoId": "LKYPYj2XX80",
      "sourceUrl": "https://www.youtube.com/watch?v=LKYPYj2XX80",
      "displayTitle": "Daft Punk - Around The World",
      "preferredFormat": "M4A_AAC",
      "actualFormat": "M4A_AAC",
      "createdAt": "2026-06-07T14:15:00Z",
      "updatedAt": "2026-06-07T14:17:30Z",
      "completedAt": "2026-06-07T14:17:30Z",
      "file": {
        "available": true,
        "expiresAt": "2026-06-07T15:17:30Z",
        "sizeBytes": 7351132,
        "sha256": "c2f5a6d3...",
        "contentType": "audio/mp4",
        "filename": "LKYPYj2XX80.m4a"
      },
      "metadata": {
        "title": "Daft Punk - Around The World",
        "artistOrChannel": "Daft Punk",
        "durationMs": 249000,
        "thumbnailUrl": "https://i.ytimg.com/vi/LKYPYj2XX80/hqdefault.jpg"
      },
      "error": null
    }
  }
}
```

Job statuses: `QUEUED`, `DOWNLOADING`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED`.

### POST /v1/jobs/status

Purpose: restore Active Imports after app restart by requesting many job states.

Request:

```json
{
  "apiJobIds": ["job_001", "job_002", "job_003"]
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000051",
  "data": {
    "jobs": [
      { "apiJobId": "job_001", "status": "DOWNLOADING", "progressPercent": 64 },
      { "apiJobId": "job_002", "status": "COMPLETED", "progressPercent": 100 },
      { "apiJobId": "job_003", "status": "UNKNOWN" }
    ]
  }
}
```

If the API cannot answer a job status, Fenlzer marks the local import job as `NEEDS_ATTENTION`.

### GET /v1/jobs/{jobId}/file

Purpose: stream a completed temporary server file back to Fenlzer.

Request:

```http
GET /v1/jobs/job_20260607_000123/file HTTP/1.1
Authorization: Bearer <api_token>
Accept: application/octet-stream
```

Response headers:

```http
HTTP/1.1 200 OK
Content-Type: audio/mp4
Content-Length: 7351132
Content-Disposition: attachment; filename="LKYPYj2XX80.m4a"
X-Request-Id: req_20260607_000052
X-Fenlzer-Job-Id: job_20260607_000123
X-Fenlzer-SHA256: c2f5a6d3...
X-Fenlzer-Expires-At: 2026-06-07T15:17:30Z
```

Fenlzer verifies the hash after receiving and copying the file into app-private storage.

### POST /v1/jobs/{jobId}/file/confirm

Purpose: Fenlzer confirms successful local import/hash verification. The server may delete the temporary file immediately after this call. If this call never arrives, the server deletes the file after 60 minutes.

Request:

```json
{
  "clientJobId": "local_job_3c0e4c",
  "receivedSha256": "c2f5a6d3...",
  "receivedSizeBytes": 7351132,
  "localTrackId": "trk_20260607_000888",
  "importedAt": "2026-06-07T14:18:10Z"
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000053",
  "data": {
    "apiJobId": "job_20260607_000123",
    "fileConfirmed": true,
    "serverTemporaryFileDeleted": true
  }
}
```

### POST /v1/jobs/{jobId}/cancel

Purpose: cancel a queued or running job.

Request:

```json
{
  "reason": "User cancelled from Active Imports"
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000054",
  "data": {
    "apiJobId": "job_20260607_000123",
    "status": "CANCELLED"
  }
}
```

### POST /v1/jobs/{jobId}/retry

Purpose: retry a failed job, preserving the original intent such as auto-import for Favourites or target playlist.

Request:

```json
{
  "clientRetryId": "retry_20260607_0001",
  "preserveOriginalIntent": true
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000055",
  "data": {
    "oldJobId": "job_failed_001",
    "newJob": {
      "apiJobId": "job_retry_001",
      "status": "QUEUED",
      "priorityClass": "AUTOMATIC"
    }
  }
}
```

### POST /v1/jobs/reorder

Purpose: reorder queued upcoming jobs from Active Imports.

Request:

```json
{
  "orderedApiJobIds": ["job_003", "job_001", "job_002"],
  "scope": "UPCOMING_ONLY"
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000056",
  "data": {
    "updated": true,
    "orderedApiJobIds": ["job_003", "job_001", "job_002"]
  }
}
```

## 7. Streaming

### POST /v1/stream/resolve

Purpose: resolve or validate a best-quality playable URL for a remote Discover item. Fenlzer stores `expiresAt` if returned, but the API always makes the final validity decision.

Request:

```json
{
  "remoteItemId": "rem_disc_001",
  "youtubeVideoId": "video123",
  "sourceUrl": "https://www.youtube.com/watch?v=video123",
  "quality": "BEST",
  "knownPlayableUrl": {
    "urlHash": "sha256_of_previous_url",
    "expiresAt": "2026-06-07T14:55:00Z",
    "lastResolvedAt": "2026-06-07T14:10:00Z"
  },
  "reason": "PREFETCH_NEXT_TWO"
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000060",
  "data": {
    "remoteItemId": "rem_disc_001",
    "youtubeVideoId": "video123",
    "playableUrl": "https://rr3---sn.example.googlevideo.com/videoplayback?...",
    "expiresAt": "2026-06-07T15:05:00Z",
    "durationMs": 204000,
    "title": "Remote Song",
    "artistOrChannel": "Remote Artist",
    "thumbnailUrl": "https://i.ytimg.com/vi/video123/hqdefault.jpg",
    "canStream": true,
    "canDownload": true,
    "mimeType": "audio/webm",
    "bitrate": 160000,
    "requiresHeaders": false,
    "httpHeaders": {},
    "isUrlExpired": false
  }
}
```

Failure behavior:

1. Media load fails in Fenlzer.
2. Fenlzer asks API for a fresh URL once.
3. Fenlzer retries playback once.
4. If still failing, Fenlzer skips the item and shows the configured message/banner.

## 8. Discover listening-history upload

Discover can use full local listening history, excluding private-mode events. Upload uses Zstandard-compressed JSON chunks targeting about 1 MB compressed per chunk.

### POST /v1/discover/history/uploads

Purpose: create a history upload session.

Request:

```json
{
  "clientUploadId": "hist_20260607_0001",
  "compression": "zstd",
  "estimatedEventCount": 18042,
  "estimatedCompressedChunkCount": 8,
  "schemaVersion": 1,
  "excludedPrivateModeEvents": true
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000070",
  "data": {
    "uploadId": "upl_20260607_0001",
    "acceptedCompression": "zstd",
    "targetChunkSizeBytes": 1048576,
    "expiresAt": "2026-06-07T15:30:00Z"
  }
}
```

### POST /v1/discover/history/uploads/{uploadId}/chunks

Purpose: upload one compressed chunk.

Uncompressed JSON before compression:

```json
{
  "schemaVersion": 1,
  "chunkIndex": 0,
  "chunkCount": 8,
  "events": [
    {
      "eventId": "evt_001",
      "trackId": "trk_001",
      "youtubeVideoId": "video001",
      "title": "Song A",
      "artist": "Artist A",
      "album": "Album A",
      "genre": "Electronic",
      "startedAt": "2026-06-01T18:20:00Z",
      "listenedMs": 181000,
      "durationMsAtPlayback": 200000,
      "validListen": true,
      "skip": false,
      "completion": true,
      "completionPercent": 0.91,
      "sourceContext": "PLAYLIST"
    }
  ]
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000071",
  "data": {
    "uploadId": "upl_20260607_0001",
    "chunkIndex": 0,
    "accepted": true,
    "receivedCompressedBytes": 982144,
    "chunkSha256": "a3d91e..."
  }
}
```

### POST /v1/discover/history/uploads/{uploadId}/complete

Purpose: finalize upload and make it available for Discover generation.

Request:

```json
{
  "chunkCount": 8,
  "overallSha256": "71ab...",
  "totalEventCount": 18042
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000072",
  "data": {
    "uploadId": "upl_20260607_0001",
    "status": "COMPLETE",
    "usableForDiscover": true
  }
}
```

## 9. Discover refresh

### POST /v1/discover/refresh

Purpose: generate a stable Discover snapshot of non-imported recommendations. The API dynamically asks for 25-75 candidate songs depending on library size and prior filter losses. It returns up to 25 valid non-imported items.

Request:

```json
{
  "historyUploadId": "upl_20260607_0001",
  "targetDisplayCount": 25,
  "maxCandidateCount": 75,
  "strictlyExcludeImported": true,
  "clientLibrary": {
    "trackCount": 512,
    "youtubeVideoIds": ["video001", "video002"],
    "sourceUrls": ["https://www.youtube.com/watch?v=video001"],
    "audioHashes": ["c2f5a6d3..."]
  },
  "previousRefreshDiagnostics": {
    "lastCandidatesRequested": 50,
    "lastAlreadyImportedFiltered": 19,
    "lastFinalDisplayedCount": 25
  }
}
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000080",
  "data": {
    "snapshotId": "disc_20260607_0001",
    "generatedAt": "2026-06-07T14:35:00Z",
    "refreshType": "NORMAL",
    "candidateRequestTarget": 50,
    "finalDisplayedCount": 25,
    "refreshBroaderAvailable": false,
    "items": [
      {
        "position": 0,
        "remoteItemId": "rem_disc_001",
        "youtubeVideoId": "video123",
        "sourceUrl": "https://www.youtube.com/watch?v=video123",
        "title": "Recommended Song",
        "artistOrChannel": "Recommended Artist",
        "durationMs": 204000,
        "thumbnailUrl": "https://i.ytimg.com/vi/video123/hqdefault.jpg",
        "canStream": true,
        "canDownload": true,
        "recommendationReason": "Based on recently played songs",
        "alreadyImported": false
      }
    ],
    "diagnostics": {
      "candidatesRequested": 50,
      "candidatesReceived": 49,
      "alreadyImportedFiltered": 18,
      "invalidOrUnavailableFiltered": 6,
      "finalDisplayedCount": 25,
      "refreshBroaderShown": false
    }
  }
}
```

If fewer than 5 valid results remain, Fenlzer shows fewer results and offers Refresh broader.

### POST /v1/discover/refresh-broader

Purpose: generate a broader Discover snapshot when the normal refresh returned too few usable recommendations.

Request is the same as `/v1/discover/refresh`, with:

```json
{
  "broadenReason": "TOO_FEW_RESULTS",
  "previousSnapshotId": "disc_20260607_0001"
}
```

Response uses the same structure as normal refresh with `refreshType: "BROADER"`.

## 10. Diagnostics

### GET /v1/diagnostics/recent

Purpose: return sanitized recent API diagnostics for the Settings advanced diagnostics screen. App-side diagnostics remain the main source of request history, but server diagnostics help debug API behavior.

Query parameters:

```text
limit=100&since=2026-06-07T00:00:00Z
```

Response:

```json
{
  "success": true,
  "requestId": "req_20260607_000090",
  "data": {
    "entries": [
      {
        "requestId": "req_20260607_000080",
        "endpoint": "/v1/discover/refresh",
        "method": "POST",
        "statusCode": 200,
        "durationMs": 1880,
        "success": true,
        "errorCode": null,
        "sanitizedMessage": null,
        "createdAt": "2026-06-07T14:35:00Z"
      }
    ]
  }
}
```

## 11. Endpoint order for implementation

Recommended implementation order:

1. `GET /v1/health`
2. `POST /v1/youtube/search`
3. `POST /v1/youtube/playlists/preview`
4. `GET /v1/youtube/playlists/preview/{previewId}`
5. `POST /v1/downloads`
6. `POST /v1/downloads/batch`
7. `GET /v1/jobs/{jobId}`
8. `POST /v1/jobs/status`
9. `GET /v1/jobs/{jobId}/file`
10. `POST /v1/jobs/{jobId}/file/confirm`
11. `POST /v1/jobs/{jobId}/cancel`
12. `POST /v1/jobs/{jobId}/retry`
13. `POST /v1/jobs/reorder`
14. `POST /v1/stream/resolve`
15. Discover history upload endpoints
16. `POST /v1/discover/refresh`
17. `POST /v1/discover/refresh-broader`
18. `GET /v1/diagnostics/recent`

## 12. Minimal V1 acceptance checklist

The API is ready for Fenlzer integration when:

- Health check reports all required features and detailed tool status.
- Search returns five usable YouTube results with thumbnails, duration, video ID, URL, stream/download capability.
- Playlist previews load progressively and are cached for 24 hours.
- Download jobs persist across server restarts and return stable job IDs.
- Server supports three parallel downloads.
- Manual jobs are prioritized ahead of automatic jobs without interrupting already-running jobs.
- Completed files stream back to Fenlzer and are deleted only after confirmation or 60-minute expiry.
- Job status restore works after Android app restart.
- Cancel, retry, and reorder work for queued/running jobs as defined.
- Stream resolution returns best available audio and enough expiry/header metadata for Media3 playback.
- Discover receives full Zstandard-compressed listening history chunks and excludes private-mode events.
- Discover returns non-imported recommendations only, with diagnostics and broader refresh support.
- All errors use stable error codes and sanitized messages.
