# Fenlzer Android API Contract v2

This package was generated from the corrected API outputs uploaded after the API began exposing typed success response models in OpenAPI.

## Status

The Android API contract is now usable as a stable integration baseline.

Confirmed from the latest samples:

- `GET /v1/health` returns health data, feature flags, tool versions, worker status, and configured limits.
- `POST /v1/downloads` now succeeds with a queued `JobObject`.
- `POST /v1/jobs/status` now uses the correct request field name: `apiJobIds`.
- OpenAPI now defines typed success envelopes for major endpoints.
- `POST /v1/stream/resolve` returns a direct playable media URL, expiry, metadata, headers, and reuse fields.
- Diagnostics show successful current calls and previous validation failures.

## Important Android rules

### Successful terminal import state

Treat these as successful terminal states:

```kotlin
ApiJobState.TRANSFER_CONFIRMED
ApiJobState.COMPLETED
```

The API currently uses `TRANSFER_CONFIRMED` after Android verifies the downloaded file and confirms it.

### Job status request

Use:

```json
{
  "apiJobIds": ["job_20260607_725b2088"]
}
```

Do not use `jobIds`.

### Create download request

Use either the flattened fields or the `source` object. The cleanest Android request is:

```json
{
  "source": {
    "type": "YOUTUBE_VIDEO",
    "youtubeVideoId": "5NV6Rdv1a3I",
    "sourceUrl": "https://www.youtube.com/watch?v=5NV6Rdv1a3I",
    "remoteItemId": "rem_search_fdf2e06620"
  },
  "jobType": "YOUTUBE_SEARCH",
  "priorityClass": "MANUAL",
  "preferredFormat": "M4A_AAC",
  "fallbackToBestAvailable": true,
  "reason": "MANUAL_SINGLE"
}
```

### File transfer

Use `getJobFile()` as a streaming Retrofit call.

Android should:

1. stream the response body into private app storage,
2. read file headers such as `X-Fenlzer-SHA256`,
3. verify SHA-256 locally,
4. call `confirmJobFile()`,
5. treat `TRANSFER_CONFIRMED` as imported.

### Range resume

To resume from byte offset `N`, call:

```kotlin
api.getJobFile(jobId, range = "bytes=$N-")
```

After reconstruction, verify the final file hash against `X-Fenlzer-SHA256`.

### Stream playback

When `requiresHeaders == true`, pass `httpHeaders` to Media3 for the direct media URL.

Current sample issue:

- `mimeType` currently appears as `"https"` even though the direct URL query contains `mime=audio%2Fwebm`.
- Android should not rely exclusively on `mimeType` yet.
- Prefer letting Media3 infer the content type from the URL/headers, or ask the API to fix this to return `audio/webm`.

## Suggested Gradle dependencies

```kotlin
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

Use the versions already compatible with the rest of the project if different.

## Still missing real samples

These models are based on OpenAPI schemas, but real samples would still help confirm edge cases for:

- playlist preview create/get
- history upload create/chunk/complete
- Discover refresh / refresh broader
- completed file response headers
- confirm file response after Android-style hash verification

These are not blockers for starting Android integration.
