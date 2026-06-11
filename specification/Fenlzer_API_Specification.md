# Fenlzer API Specification

Status: living companion specification for Fenlzer.
Last updated: 2026-06-07.
Scope: this document describes what the Fenlzer YouTubeDL/API service must provide to support the final V1 application.
Relationship to app plan: the main application plan remains in `Fenlzer_Working_Plan.md`; this document focuses only on API behavior, contracts, and examples.

## 1. API purpose

Fenlzer uses a private API for all YouTube-related and recommendation-related operations. The Android app should not directly reconstruct complex YouTubeDL behavior. Instead, the API should expose high-level Fenlzer-specific endpoints.

The API must support:

- YouTube search.
- YouTube playlist preview with progressive loading.
- 24-hour playlist preview caching.
- Single YouTube item download jobs.
- Batch/playlist download jobs.
- Download job priority, ordering, cancellation, retry, and restoration.
- Remote stream URL resolution and validation.
- Discover recommendation refresh.
- Broader Discover refresh.
- API-side filtering of already-imported songs.
- Stable job IDs so Fenlzer can restore import/download state after app restart.
- A health/status check used by Settings and API-unreachable banners.

Local device imports are client-only and do not require this API.

## 2. General API rules

### Base URL and authentication

Fenlzer stores the API base URL and token in Settings. The user can test the connection from Settings.

Authentication uses a simple static token/API key.

Recommended request header:

```http
Authorization: Bearer <fenlzer_api_token>
```

### API unreachable behavior in the app

If the API is unreachable, Fenlzer keeps the local library and local playback fully usable. YouTube search, YouTube playlist import, Discover refresh, remote streaming, and remote downloads are disabled with a clear banner/message.

### Response envelope

All JSON endpoints should use a consistent response style.

Successful response:

```json
{
  "ok": true,
  "data": {}
}
```

Failed response:

```json
{
  "ok": false,
  "error": {
    "code": "API_TIMEOUT",
    "message": "Fenlzer could not reach the streaming API.",
    "retryable": true,
    "details": {}
  }
}
```

### Error codes

The API should return stable machine-readable error codes. The app maps them to friendly UI messages.

| Code | Meaning | Retryable |
|---|---|---|
| UNAUTHORIZED | Token is missing or invalid | No |
| BAD_REQUEST | Request is malformed | No |
| API_TIMEOUT | API operation timed out | Yes |
| API_RATE_LIMITED | API or upstream is rate-limited | Yes |
| NETWORK_ERROR | API cannot reach upstream resources | Yes |
| VIDEO_UNAVAILABLE | Video is private, deleted, region-blocked, or unavailable | No |
| STREAM_UNAVAILABLE | Stream URL cannot be resolved | Maybe |
| DOWNLOAD_UNAVAILABLE | Download cannot be created | Maybe |
| URL_EXPIRED | Existing stream URL expired | Yes |
| JOB_NOT_FOUND | Job ID is unknown | No |
| JOB_CANCELLED | Job was cancelled | No |
| UNKNOWN_ERROR | Unexpected failure | Maybe |

## 3. Core data objects

### YouTube item

Used for search results, playlist preview entries, and Discover candidates.

```json
{
  "remoteItemId": "yt:video:dQw4w9WgXcQ",
  "youtubeVideoId": "dQw4w9WgXcQ",
  "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "title": "Song title",
  "artistOrChannel": "Artist or channel",
  "durationMs": 213000,
  "thumbnailUrl": "https://...",
  "canStream": true,
  "canDownload": true,
  "availability": "AVAILABLE"
}
```

### Remote Discover item

Remote Discover items are first-class temporary song items. They can be streamed and queued before import. They become local tracks only after download/import.

```json
{
  "remoteItemId": "discover:2026-06-07:001",
  "youtubeVideoId": "dQw4w9WgXcQ",
  "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "title": "Song title",
  "artistOrChannel": "Artist or channel",
  "durationMs": 213000,
  "thumbnailUrl": "https://...",
  "recommendationReason": "Based on favourite artist",
  "canStream": true,
  "canDownload": true,
  "availability": "AVAILABLE"
}
```

### Download job

All API-backed downloads use stable job IDs. Fenlzer mirrors these jobs in its local database.

```json
{
  "jobId": "job_01HXZ5",
  "status": "QUEUED",
  "sourceType": "YOUTUBE_VIDEO",
  "reason": "MANUAL_DISCOVER_DOWNLOAD",
  "youtubeVideoId": "dQw4w9WgXcQ",
  "queuePosition": 4,
  "priority": 50,
  "progress": {
    "percent": 0,
    "downloadedBytes": 0,
    "totalBytes": null,
    "stage": "QUEUED"
  },
  "createdAt": "2026-06-07T13:00:00Z",
  "updatedAt": "2026-06-07T13:00:00Z"
}
```

Job statuses:

| Status | Meaning |
|---|---|
| QUEUED | Waiting to download |
| DOWNLOADING | Audio/video data is being downloaded |
| EXTRACTING | Metadata or audio is being extracted/prepared |
| PACKAGING | Final file/result is being prepared for Fenlzer |
| COMPLETED | Download completed successfully |
| FAILED | Job failed |
| CANCELLED | User/API cancelled the job |
| NEEDS_ATTENTION | API cannot determine job state after restart |

Job reasons:

| Reason | Meaning |
|---|---|
| MANUAL_YOUTUBE_SEARCH | User downloaded from YouTube search |
| MANUAL_DISCOVER_DOWNLOAD | User downloaded from Discover |
| AUTO_FAVOURITE | Remote item was favourited and must be imported |
| AUTO_PLAYLIST_ADD | Remote item was added to a playlist and must be imported |
| YOUTUBE_PLAYLIST_BATCH | Item came from a YouTube playlist import |
| CURRENTLY_STREAMING_DOWNLOAD | Currently streaming item is being downloaded in parallel |

Manual imports/downloads always stay ahead of automatic imports. Already-running downloads are not interrupted; the next available slot goes to the highest-priority waiting job.

## 4. Health and configuration endpoints

### GET /v1/health

Used by Settings > Test API connection and by API-unreachable handling.

Response example:

```json
{
  "ok": true,
  "data": {
    "service": "fenlzer-api",
    "status": "READY",
    "version": "1.0.0",
    "serverTime": "2026-06-07T13:00:00Z",
    "features": {
      "youtubeSearch": true,
      "playlistPreview": true,
      "downloads": true,
      "streamResolve": true,
      "discover": true
    },
    "limits": {
      "maxConcurrentDownloads": 3,
      "maxDiscoverCandidates": 75,
      "playlistPreviewCacheHours": 24
    }
  }
}
```

## 5. YouTube search

### POST /v1/youtube/search

Searches YouTube and returns the closest results. Fenlzer displays the top 5 closest videos in the Import screen.

Request example:

```json
{
  "query": "song name artist",
  "limit": 5,
  "exclude": {
    "youtubeVideoIds": ["abc123"],
    "youtubeUrls": [],
    "audioHashes": []
  }
}
```

Response example:

```json
{
  "ok": true,
  "data": {
    "query": "song name artist",
    "results": [
      {
        "remoteItemId": "yt:video:dQw4w9WgXcQ",
        "youtubeVideoId": "dQw4w9WgXcQ",
        "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "title": "Song title",
        "artistOrChannel": "Artist or channel",
        "durationMs": 213000,
        "thumbnailUrl": "https://...",
        "canStream": true,
        "canDownload": true,
        "availability": "AVAILABLE"
      }
    ]
  }
}
```

## 6. YouTube playlist preview

### POST /v1/youtube/playlists/preview

Creates or retrieves a cached playlist preview. Playlist previews are cached for 24 hours. The preview should support progressive loading for large playlists.

Request example:

```json
{
  "playlistUrl": "https://www.youtube.com/playlist?list=PL...",
  "pageSize": 50,
  "useCache": true
}
```

Response example:

```json
{
  "ok": true,
  "data": {
    "previewId": "preview_01HXZ6",
    "playlistTitle": "Playlist name",
    "playlistUrl": "https://www.youtube.com/playlist?list=PL...",
    "thumbnailUrl": "https://...",
    "totalCount": 214,
    "loadedCount": 47,
    "isComplete": false,
    "expiresAt": "2026-06-08T13:00:00Z",
    "items": [
      {
        "index": 1,
        "remoteItemId": "yt:video:abc123",
        "youtubeVideoId": "abc123",
        "title": "Song title",
        "artistOrChannel": "Channel",
        "durationMs": 180000,
        "thumbnailUrl": "https://...",
        "availability": "AVAILABLE",
        "alreadyImported": false,
        "canDownload": true,
        "unavailableReason": null
      }
    ]
  }
}
```

### GET /v1/youtube/playlists/preview/{previewId}

Returns the current cached/progressively loaded preview state.

Query parameters:

- `offset`: first item index to return.
- `limit`: number of items to return.

Response uses the same `data` shape as the create endpoint.

## 7. Download job endpoints

### POST /v1/downloads

Creates a single YouTube-backed download job.

Request example:

```json
{
  "remoteItemId": "discover:2026-06-07:001",
  "youtubeVideoId": "dQw4w9WgXcQ",
  "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "reason": "AUTO_FAVOURITE",
  "priority": 80,
  "metadataHint": {
    "title": "Song title",
    "artistOrChannel": "Artist or channel",
    "durationMs": 213000,
    "thumbnailUrl": "https://..."
  },
  "pendingAction": {
    "type": "ADD_TO_FAVOURITES"
  }
}
```

Response example:

```json
{
  "ok": true,
  "data": {
    "jobId": "job_01HXZ5",
    "status": "QUEUED",
    "queuePosition": 2,
    "priority": 80
  }
}
```

### POST /v1/downloads/batch

Creates multiple download jobs, mostly for selected YouTube playlist items. The API may enqueue them while respecting the max concurrent download limit of 3.

Request example:

```json
{
  "reason": "YOUTUBE_PLAYLIST_BATCH",
  "items": [
    {
      "remoteItemId": "yt:video:abc123",
      "youtubeVideoId": "abc123",
      "youtubeUrl": "https://www.youtube.com/watch?v=abc123",
      "metadataHint": {
        "title": "Song A",
        "artistOrChannel": "Channel A",
        "durationMs": 180000,
        "thumbnailUrl": "https://..."
      }
    }
  ]
}
```

Response example:

```json
{
  "ok": true,
  "data": {
    "jobs": [
      {
        "jobId": "job_01",
        "youtubeVideoId": "abc123",
        "status": "QUEUED",
        "queuePosition": 1
      }
    ]
  }
}
```

### GET /v1/jobs/{jobId}

Returns job status. Used by Active Imports and app-restart restoration.

Completed response example:

```json
{
  "ok": true,
  "data": {
    "jobId": "job_01HXZ5",
    "status": "COMPLETED",
    "sourceType": "YOUTUBE_VIDEO",
    "reason": "AUTO_FAVOURITE",
    "progress": {
      "percent": 100,
      "stage": "COMPLETED"
    },
    "result": {
      "audioFileUrl": "https://api.example/files/job_01HXZ5/audio",
      "audioHash": "sha256:...",
      "fileExtension": "m4a",
      "mimeType": "audio/mp4",
      "sizeBytes": 7340032,
      "metadata": {
        "title": "Song title",
        "artist": "Artist or channel",
        "album": "",
        "genre": "",
        "year": null,
        "durationMs": 213000,
        "thumbnailUrl": "https://...",
        "youtubeVideoId": "dQw4w9WgXcQ",
        "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
      },
      "technicalDetails": {
        "container": "m4a",
        "codec": "aac",
        "bitrateKbps": 128,
        "sampleRateHz": 44100,
        "channels": 2
      }
    }
  }
}
```

Failed response example:

```json
{
  "ok": true,
  "data": {
    "jobId": "job_01HXZ5",
    "status": "FAILED",
    "reason": "AUTO_PLAYLIST_ADD",
    "error": {
      "code": "DOWNLOAD_UNAVAILABLE",
      "message": "This song could not be downloaded.",
      "retryable": true,
      "details": {}
    }
  }
}
```

### POST /v1/jobs/status

Batch status endpoint used after app restart.

Request example:

```json
{
  "jobIds": ["job_01", "job_02", "job_03"]
}
```

Response example:

```json
{
  "ok": true,
  "data": {
    "jobs": [
      {
        "jobId": "job_01",
        "status": "DOWNLOADING",
        "progress": {
          "percent": 42,
          "stage": "DOWNLOADING"
        }
      }
    ],
    "unknownJobIds": []
  }
}
```

If the API cannot determine a job state after restart, it should return `NEEDS_ATTENTION` where possible. If it cannot answer at all, the app marks the job as Needs attention.

### POST /v1/jobs/{jobId}/cancel

Cancels queued or running jobs. Partial files should be cleaned up by the API.

Response example:

```json
{
  "ok": true,
  "data": {
    "jobId": "job_01HXZ5",
    "status": "CANCELLED"
  }
}
```

### POST /v1/jobs/{jobId}/retry

Retries a failed job. If the original job was created for a pending action, Fenlzer keeps the pending action locally and completes it when retry succeeds.

Response example:

```json
{
  "ok": true,
  "data": {
    "jobId": "job_01HXZ5_retry1",
    "status": "QUEUED",
    "replacesJobId": "job_01HXZ5"
  }
}
```

### POST /v1/jobs/reorder

Reorders upcoming jobs. Active/running downloads do not need to be interrupted. The API should apply the new ordering to queued jobs.

Request example:

```json
{
  "orderedJobIds": ["job_05", "job_03", "job_04"]
}
```

Response example:

```json
{
  "ok": true,
  "data": {
    "jobs": [
      { "jobId": "job_05", "queuePosition": 1 },
      { "jobId": "job_03", "queuePosition": 2 },
      { "jobId": "job_04", "queuePosition": 3 }
    ]
  }
}
```

## 8. Stream resolution endpoints

### POST /v1/stream/resolve

Returns a playable URL for a remote Discover item. Fenlzer may pass an existing URL and expiry information, but the API decides whether it is still valid.

Request example:

```json
{
  "remoteItemId": "discover:2026-06-07:001",
  "youtubeVideoId": "dQw4w9WgXcQ",
  "knownStream": {
    "playableUrl": "https://...",
    "expiresAt": "2026-06-07T14:00:00Z"
  },
  "purpose": "PLAYBACK"
}
```

Response example:

```json
{
  "ok": true,
  "data": {
    "remoteItemId": "discover:2026-06-07:001",
    "youtubeVideoId": "dQw4w9WgXcQ",
    "playableUrl": "https://...",
    "expiresAt": "2026-06-07T14:00:00Z",
    "durationMs": 213000,
    "title": "Song title",
    "artistOrChannel": "Artist or channel",
    "thumbnailUrl": "https://...",
    "canStream": true,
    "canDownload": true,
    "mimeType": "audio/webm",
    "bitrateKbps": 160,
    "requiresHeaders": false,
    "httpHeaders": {},
    "isUrlExpired": false,
    "recommendedRetryAfterMs": null
  }
}
```

Fenlzer uses this endpoint for:

- The current remote item.
- The next 2 remote items in the queue.
- Revalidating URLs before playback/prefetch when needed.

Failure behavior in Fenlzer:

1. Media load fails.
2. Fenlzer asks the API for a fresh URL once.
3. Fenlzer tries playback again once.
4. If playback still fails, Fenlzer skips the item and shows a message.

## 9. Discover endpoints

### POST /v1/discover/refresh

Generates a stable Discover list. Discover should contain remote, not-yet-imported songs by default. Fenlzer strictly hides already-imported songs, and the API should also filter them out when possible.

Request example:

```json
{
  "targetCount": 25,
  "candidateLimit": 75,
  "mode": "NORMAL",
  "exclude": {
    "youtubeVideoIds": ["abc123", "def456"],
    "youtubeUrls": [],
    "audioHashes": []
  },
  "librarySummary": {
    "trackCount": 540,
    "favouriteArtists": ["Artist A"],
    "topArtists": ["Artist A", "Artist B"],
    "topGenres": ["Pop", "Electronic"],
    "recentYoutubeVideoIds": ["xyz987"]
  },
  "previousRefreshDiagnostics": {
    "lastRequestedCandidates": 50,
    "lastFilteredImportedCount": 18,
    "lastFinalCount": 25
  }
}
```

Response example:

```json
{
  "ok": true,
  "data": {
    "discoverId": "discover_2026_06_07_1300",
    "generatedAt": "2026-06-07T13:00:00Z",
    "expiresHintHours": 8,
    "items": [
      {
        "remoteItemId": "discover:2026-06-07:001",
        "youtubeVideoId": "dQw4w9WgXcQ",
        "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "title": "Song title",
        "artistOrChannel": "Artist or channel",
        "durationMs": 213000,
        "thumbnailUrl": "https://...",
        "recommendationReason": "Based on favourite artist",
        "canStream": true,
        "canDownload": true,
        "availability": "AVAILABLE"
      }
    ],
    "diagnostics": {
      "candidatesRequested": 75,
      "candidatesReceived": 72,
      "alreadyImportedFilteredCount": 22,
      "unavailableFilteredCount": 3,
      "finalDisplayedCount": 25,
      "refreshBroaderSuggested": false
    }
  }
}
```

Discover behavior required by the app:

- The app asks for 25 to 75 candidates dynamically.
- The cap is 75 candidates.
- The final displayed Discover target is 25 songs.
- Already-imported songs are strictly hidden.
- If fewer than 5 valid non-imported results remain, Fenlzer shows fewer results and offers Refresh broader.
- Discover contents persist until manual refresh or eligible startup refresh.
- Startup refresh occurs only if the user opened Discover since the last refresh and the last refresh is older than 8 hours.
- Android Auto never exposes Discover or remote Discover streams.

### POST /v1/discover/refresh-broader

Requests broader recommendations when normal Discover cannot find enough valid non-imported songs.

Request example:

```json
{
  "targetCount": 25,
  "candidateLimit": 75,
  "exclude": {
    "youtubeVideoIds": ["abc123"],
    "youtubeUrls": [],
    "audioHashes": []
  },
  "broadenFromDiscoverId": "discover_2026_06_07_1300"
}
```

Response uses the same shape as `/v1/discover/refresh`.

## 10. Duplicate detection and merging

Strong duplicate identifiers:

1. Same YouTube video ID.
2. Same audio hash after download.
3. Same source URL.

Title + artist + similar duration is not enough to automatically treat an item as a duplicate.

If a remote item downloads but Fenlzer detects that it already exists locally, Fenlzer uses the existing local track and completes the pending action. The API should return enough identifiers for the app to make this decision.

When a remote item is imported:

- Remote queue items convert silently to the local track identity.
- Pending favourite/add-to-playlist actions complete after import.
- Pending non-private remote stats merge into the imported track.
- Private-mode remote playback remains excluded permanently.

## 11. Thumbnail handling

The API should return thumbnail URLs for search, playlist preview, Discover, stream resolution, and completed downloads.

Fenlzer behavior:

- Remote thumbnails are cached temporarily before import.
- After import, the thumbnail is promoted into Fenlzer's permanent thumbnail storage.
- Custom user thumbnails still override YouTube thumbnails inside the app.

## 12. Active Imports and notifications

The API should make it possible for Fenlzer to show Active Imports split into:

- Downloading now.
- Upcoming to download.

Fenlzer supports 3 parallel YouTube/API downloads. The API should expose current job states and queue positions.

The app shows one global import notification, expandable with per-song details.

Completed imports remain visible in Active Imports until the user leaves that screen, while also being written to Import History immediately.

Failed imports remain visible in Active Imports until dismissed and also appear in Import History.

## 13. Import History requirements

API-backed imports should provide enough information for Import History entries:

- Source: YouTube search, Discover, YouTube playlist.
- Reason: manual download, auto-import for Favourite, auto-import for playlist, batch import.
- Success / failed / duplicate / cancelled status.
- Friendly error message.
- Advanced technical error details.
- Retry availability.
- Original YouTube URL and video ID.
- Resulting local track identifiers when available.

Automatic Discover imports appear in Import History with the reason shown, such as `Auto-imported for Favourites`.

Failed auto-download retry must be reachable from:

- Snackbar/message.
- Active Imports.
- Import History.

## 14. Share behavior supported by API data

For YouTube-sourced songs and remote Discover songs, Fenlzer shares the original YouTube URL.

For local device imports, Fenlzer shares only text metadata such as title and artist. The API is not involved.

Therefore API responses must preserve the original YouTube URL and video ID for YouTube-backed items.

## 15. Privacy and statistics interaction

The API does not own listening statistics. Fenlzer owns stats locally.

However, the API must provide stable remote item identity so Fenlzer can:

- Store pending remote playback stats.
- Merge them after import if private mode was not active.
- Discard them permanently if private mode was active.

## 16. Endpoint summary

| Endpoint | Method | Purpose |
|---|---|---|
| /v1/health | GET | Check API availability and feature support |
| /v1/youtube/search | POST | Search YouTube for Import screen |
| /v1/youtube/playlists/preview | POST | Create/retrieve cached playlist preview |
| /v1/youtube/playlists/preview/{previewId} | GET | Poll/read progressive playlist preview |
| /v1/downloads | POST | Create one download job |
| /v1/downloads/batch | POST | Create multiple download jobs |
| /v1/jobs/{jobId} | GET | Get one job's status/result |
| /v1/jobs/status | POST | Get many jobs' statuses after restart |
| /v1/jobs/{jobId}/cancel | POST | Cancel queued/running job |
| /v1/jobs/{jobId}/retry | POST | Retry failed job |
| /v1/jobs/reorder | POST | Reorder queued jobs |
| /v1/stream/resolve | POST | Resolve or validate playable stream URL |
| /v1/discover/refresh | POST | Generate normal Discover recommendations |
| /v1/discover/refresh-broader | POST | Generate broader Discover recommendations |

## 17. Open API details to refine later

| Area | Detail still to decide | Suggested direction |
|---|---|---|
| Exact API paths | Endpoint names may change during implementation | Keep semantic names close to this document |
| Audio format | Preferred download output format | Choose based on quality/compatibility and Media3 support |
| Metadata mapping | Exact yt-dlp metadata fields to app fields | Normalize title, artist/channel, duration, thumbnail, video ID, URL |
| Stream URL lifetime | How accurate `expiresAt` can be | API decides final validity each time |
| File delivery | Whether completed files are fetched via temporary URL or streamed from API | Temporary authenticated file URL is cleanest |
| Broader Discover | Exact algorithm for broadening | Use less strict taste filters while still excluding imported items |
| API logs | Whether to expose logs in advanced details | Useful for personal debugging, but sanitize token/URLs if needed |

## 18. Finalization status

This API document is not final yet. It is the working source of truth for what the Fenlzer API must eventually support. The next API planning step should be to refine each endpoint into concrete implementation-ready request/response examples and align them with the current server's actual YouTubeDL capabilities.
