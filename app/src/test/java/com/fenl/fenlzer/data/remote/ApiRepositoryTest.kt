package com.fenl.fenlzer.data.remote

import com.fenl.fenlzer.common.FenlzerDispatchers
import com.fenl.fenlzer.data.settings.InMemoryApiTokenStore
import com.fenl.fenlzer.data.settings.InMemoryAppSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testHealthSendsBearerTokenAndRecordsSuccess() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(successfulHealthBody)
        )
        val diagnostics = InMemoryApiDiagnosticRecorder()
        val repository = repository(diagnostics)

        val result = repository.testHealth(
            baseUrl = server.url("/").toString(),
            token = "secret-token"
        )

        val request = server.takeRequest()
        assertEquals("/v1/health", request.path)
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
        assertTrue(result is ApiHealthCheckResult.Success)
        assertEquals(1, diagnostics.entries.size)
        assertTrue(diagnostics.entries.single().success)
    }

    @Test
    fun testHealthRedactsTokenInFailureDiagnostics() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": false,
                      "requestId": "req_error",
                      "error": {
                        "code": "UNAUTHORIZED",
                        "message": "Token secret-token is invalid.",
                        "retryable": false
                      }
                    }
                    """.trimIndent()
                )
        )
        val diagnostics = InMemoryApiDiagnosticRecorder()
        val repository = repository(diagnostics)

        val result = repository.testHealth(
            baseUrl = server.url("/").toString(),
            token = "secret-token"
        )

        assertTrue(result is ApiHealthCheckResult.Failure)
        val failure = result as ApiHealthCheckResult.Failure
        assertEquals("UNAUTHORIZED", failure.errorCode)
        assertFalse(failure.message.contains("secret-token"))
        assertEquals(1, diagnostics.entries.size)
        assertFalse(diagnostics.entries.single().sanitizedMessage!!.contains("secret-token"))
    }

    @Test
    fun searchYoutubeUsesSavedSettingsAndBearerToken() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_search",
                      "data": {
                        "query": "around the world",
                        "results": [
                          {
                            "remoteItemId": "rem_1",
                            "youtubeVideoId": "video_1",
                            "sourceUrl": "https://www.youtube.com/watch?v=video_1",
                            "title": "Around The World",
                            "artistOrChannel": "Daft Punk",
                            "durationMs": 249000,
                            "thumbnailUrl": "https://img.example/video_1.jpg",
                            "canStream": true,
                            "canDownload": true,
                            "isLive": false,
                            "isUnavailable": false
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )
        val diagnostics = InMemoryApiDiagnosticRecorder()
        val repository = repository(diagnostics)
        repository.saveApiSettings(server.url("/").toString(), "secret-token")

        val result = repository.searchYoutube("around the world")

        val request = server.takeRequest()
        assertEquals("/v1/youtube/search", request.path)
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
        assertEquals("Around The World", result.results.single().title)
        assertTrue(diagnostics.entries.single().success)
    }

    @Test
    fun recentDiagnosticsUsesContractEndpointAndParsesResponse() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_recent",
                      "data": {
                        "entries": [
                          {
                            "requestId": "req_server_1",
                            "endpoint": "/v1/health",
                            "method": "GET",
                            "statusCode": 200,
                            "durationMs": 15,
                            "success": true,
                            "createdAt": "2026-06-15T10:15:30Z"
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )
        val repository = repository(InMemoryApiDiagnosticRecorder())
        repository.saveApiSettings(server.url("/").toString(), "secret-token")

        val result = repository.recentDiagnostics(limit = 25)

        assertEquals("/v1/diagnostics/recent?limit=25", server.takeRequest().path)
        assertEquals("req_server_1", result.entries.single().requestId)
    }

    @Test
    fun malformedRecentDiagnosticsRecordsSanitizedFailure() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{ malformed secret-token")
        )
        val diagnostics = InMemoryApiDiagnosticRecorder()
        val repository = repository(diagnostics)
        repository.saveApiSettings(server.url("/").toString(), "secret-token")

        val failure = runCatching { repository.recentDiagnostics() }

        assertTrue(failure.isFailure)
        assertEquals("UNKNOWN_ERROR", diagnostics.entries.single().errorCode)
        assertFalse(diagnostics.entries.single().sanitizedMessage.orEmpty().contains("secret-token"))
    }

    @Test
    fun downloadJobFlowUsesContractEndpointsAndConfirmsFile() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_create",
                      "data": {
                        "job": {
                          "apiJobId": "job_1",
                          "clientJobId": "local_job",
                          "jobType": "YOUTUBE_SEARCH",
                          "status": "QUEUED",
                          "state": "QUEUED",
                          "source": {
                            "type": "YOUTUBE_VIDEO",
                            "youtubeVideoId": "video_1",
                            "sourceUrl": "https://www.youtube.com/watch?v=video_1",
                            "remoteItemId": "rem_1"
                          },
                          "progressPercent": 0,
                          "retryable": false
                        }
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_job",
                      "data": {
                        "job": {
                          "apiJobId": "job_1",
                          "clientJobId": "local_job",
                          "jobType": "YOUTUBE_SEARCH",
                          "status": "READY_FOR_TRANSFER",
                          "state": "READY_FOR_TRANSFER",
                          "source": {
                            "type": "YOUTUBE_VIDEO",
                            "youtubeVideoId": "video_1",
                            "sourceUrl": "https://www.youtube.com/watch?v=video_1",
                            "remoteItemId": "rem_1"
                          },
                          "progressPercent": 100,
                          "retryable": false,
                          "file": {
                            "available": true,
                            "sha256": "6101a990aa8ebe75424659370c16bb5337483a62be9f74504a86c73f2658147e",
                            "filename": "video_1.m4a"
                          }
                        }
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-Request-Id", "req_file")
                .setHeader(
                    "X-Fenlzer-SHA256",
                    "6101a990aa8ebe75424659370c16bb5337483a62be9f74504a86c73f2658147e"
                )
                .setHeader("Content-Disposition", "attachment; filename=\"video_1.m4a\"")
                .setBody("Fenlzer")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_confirm",
                      "data": {
                        "apiJobId": "job_1",
                        "fileConfirmed": true,
                        "serverTemporaryFileDeleted": true
                      }
                    }
                    """.trimIndent()
                )
        )
        val repository = repository(InMemoryApiDiagnosticRecorder())
        repository.saveApiSettings(server.url("/").toString(), "secret-token")

        val created = repository.createYoutubeDownload(
            CreateDownloadRequest(
                clientJobId = "local_job",
                source = DownloadSource(
                    youtubeVideoId = "video_1",
                    sourceUrl = "https://www.youtube.com/watch?v=video_1",
                    remoteItemId = "rem_1"
                )
            ),
            idempotencyKey = "idem_1"
        )
        val job = repository.getJob(created.job.apiJobId).job
        val file = repository.getJobFile(job.apiJobId)
        val fileText = file.body()!!.string()
        val confirmation = repository.confirmJobFile(
            jobId = job.apiJobId,
            request = ConfirmFileRequest(
                clientJobId = "local_job",
                receivedSha256 = "6101a990aa8ebe75424659370c16bb5337483a62be9f74504a86c73f2658147e",
                receivedSizeBytes = 7,
                localTrackId = "track_1",
                importedAt = "2026-06-09T12:00:00Z"
            )
        )

        assertEquals("job_1", created.job.apiJobId)
        assertEquals("READY_FOR_TRANSFER", job.status)
        assertEquals("Fenlzer", fileText)
        assertTrue(confirmation.fileConfirmed)
        assertEquals("/v1/downloads", server.takeRequest().path)
        assertEquals("/v1/jobs/job_1", server.takeRequest().path)
        assertEquals("/v1/jobs/job_1/file", server.takeRequest().path)
        val confirmRequest = server.takeRequest()
        assertEquals("/v1/jobs/job_1/file/confirm", confirmRequest.path)
        assertTrue(confirmRequest.body.readUtf8().contains("receivedSha256"))
    }

    @Test
    fun playlistAndActiveJobEndpointsUseContractPaths() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_preview_create",
                      "data": {
                        "previewId": "preview_1",
                        "status": "LOADING",
                        "playlistTitle": "Road Mix",
                        "loadedItemCount": 1,
                        "totalExpectedItems": 2,
                        "items": [
                          {
                            "position": 1,
                            "remoteItemId": "rem_1",
                            "youtubeVideoId": "video_1",
                            "sourceUrl": "https://www.youtube.com/watch?v=video_1",
                            "title": "Track One",
                            "canStream": true,
                            "canDownload": true,
                            "alreadyKnownByClient": false
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_preview_get",
                      "data": {
                        "previewId": "preview_1",
                        "status": "COMPLETE",
                        "playlistTitle": "Road Mix",
                        "loadedItemCount": 2,
                        "totalExpectedItems": 2,
                        "items": []
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_batch",
                      "data": {
                        "batchId": "batch_1",
                        "createdJobs": [
                          {
                            "clientJobId": "local_1",
                            "apiJobId": "job_1",
                            "status": "QUEUED"
                          }
                        ],
                        "rejectedItems": []
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_status",
                      "data": {
                        "jobs": [
                          {
                            "apiJobId": "job_1",
                            "status": "RUNNING",
                            "state": "DOWNLOADING_METADATA",
                            "queuePosition": 1,
                            "progressPercent": 25
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_cancel",
                      "data": {
                        "apiJobId": "job_1",
                        "status": "CANCELLED"
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_retry",
                      "data": {
                        "oldJobId": "job_1",
                        "newJob": {
                          "apiJobId": "job_2",
                          "status": "QUEUED",
                          "priorityClass": "MANUAL"
                        }
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_reorder",
                      "data": {
                        "updated": true,
                        "orderedApiJobIds": ["job_2", "job_3"]
                      }
                    }
                    """.trimIndent()
                )
        )
        val repository = repository(InMemoryApiDiagnosticRecorder())
        repository.saveApiSettings(server.url("/").toString(), "secret-token")

        val preview = repository.createPlaylistPreview(
            "https://www.youtube.com/playlist?list=playlist_1"
        )
        val refreshed = repository.getPlaylistPreview(preview.previewId)
        val batch = repository.createDownloadBatch(
            request = BatchDownloadRequest(
                batchId = "batch_1",
                reason = ApiJobReason.YOUTUBE_PLAYLIST,
                items = emptyList()
            ),
            idempotencyKey = "idem_batch"
        )
        val statuses = repository.getManyJobStatuses(listOf("job_1"))
        val cancelled = repository.cancelJob("job_1", "User cancelled")
        val retried = repository.retryJob("job_1", clientRetryId = "retry_1")
        val reordered = repository.reorderJobs(listOf("job_2", "job_3"))

        assertEquals("preview_1", preview.previewId)
        assertEquals("COMPLETE", refreshed.status)
        assertEquals("job_1", batch.createdJobs.single().apiJobId)
        assertEquals("DOWNLOADING_METADATA", statuses.jobs.single().state)
        assertEquals(1, statuses.jobs.single().queuePosition)
        assertEquals("CANCELLED", cancelled.status)
        assertEquals("job_2", retried.newJob.apiJobId)
        assertTrue(reordered.updated)

        val previewRequest = server.takeRequest()
        assertEquals("/v1/youtube/playlists/preview", previewRequest.path)
        assertTrue(previewRequest.body.readUtf8().contains("playlistUrl"))
        assertEquals("/v1/youtube/playlists/preview/preview_1", server.takeRequest().path)
        val batchRequest = server.takeRequest()
        assertEquals("/v1/downloads/batch", batchRequest.path)
        assertEquals("idem_batch", batchRequest.getHeader("Idempotency-Key"))
        assertEquals("/v1/jobs/status", server.takeRequest().path)
        assertEquals("/v1/jobs/job_1/cancel", server.takeRequest().path)
        val retryRequest = server.takeRequest()
        assertEquals("/v1/jobs/job_1/retry", retryRequest.path)
        assertEquals("retry_1", retryRequest.getHeader("Idempotency-Key"))
        assertEquals("/v1/jobs/reorder", server.takeRequest().path)
    }

    @Test
    fun streamResolveUsesContractEndpointAndSavedToken() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_stream",
                      "data": {
                        "remoteItemId": "rem_1",
                        "youtubeVideoId": "video_1",
                        "sourceUrl": "https://www.youtube.com/watch?v=video_1",
                        "playableUrl": "https://media.example/video_1",
                        "expiresAt": "2026-06-10T12:30:00Z",
                        "durationMs": 180000,
                        "title": "Remote Song",
                        "artistOrChannel": "Remote Artist",
                        "thumbnailUrl": "https://img.example/video_1.jpg",
                        "canStream": true,
                        "canDownload": true,
                        "urlMode": "DIRECT",
                        "reusable": true,
                        "reusableUntil": "2026-06-10T12:25:00Z",
                        "mimeType": "audio/webm",
                        "bitrate": 160000,
                        "requiresHeaders": false,
                        "httpHeaders": {},
                        "isUrlExpired": false,
                        "quality": "BEST"
                      }
                    }
                    """.trimIndent()
                )
        )
        val repository = repository(InMemoryApiDiagnosticRecorder())
        repository.saveApiSettings(server.url("/").toString(), "secret-token")

        val resolved = repository.resolveStream(
            StreamResolveRequest(
                remoteItemId = "rem_1",
                youtubeVideoId = "video_1",
                sourceUrl = "https://www.youtube.com/watch?v=video_1",
                knownPlayableUrl = buildJsonObject {
                    put("urlHash", JsonPrimitive("previous_hash"))
                    put("expiresAt", JsonPrimitive("2026-06-10T12:00:00Z"))
                    put("lastResolvedAt", JsonPrimitive("2026-06-10T11:30:00Z"))
                },
                reason = "PREFETCH_NEXT_TWO"
            )
        )

        val request = server.takeRequest()
        assertEquals("/v1/stream/resolve", request.path)
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("knownPlayableUrl"))
        assertTrue(body.contains("PREFETCH_NEXT_TWO"))
        assertEquals("https://media.example/video_1", resolved.playableUrl)
        assertTrue(resolved.canStream)
    }

    @Test
    fun discoverUploadAndRefreshUseContractPathsAndHeaders() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_upload_create",
                      "data": {
                        "uploadId": "upload_1",
                        "acceptedCompression": "zstd",
                        "targetChunkSizeBytes": 1048576,
                        "expiresAt": "2026-06-10T12:30:00Z"
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_chunk",
                      "data": {
                        "uploadId": "upload_1",
                        "chunkIndex": 0,
                        "accepted": true,
                        "receivedCompressedBytes": 3,
                        "chunkSha256": "abc123"
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "requestId": "req_complete",
                      "data": {
                        "uploadId": "upload_1",
                        "status": "COMPLETE",
                        "usableForDiscover": true
                      }
                    }
                    """.trimIndent()
                )
        )
        val discoverBody = """
            {
              "success": true,
              "requestId": "req_discover",
              "data": {
                "snapshotId": "disc_1",
                "generatedAt": "2026-06-10T12:00:00Z",
                "refreshType": "NORMAL",
                "candidateRequestTarget": 50,
                "finalDisplayedCount": 1,
                "refreshBroaderAvailable": true,
                "items": [
                  {
                    "position": 0,
                    "remoteItemId": "rem_1",
                    "youtubeVideoId": "video_1",
                    "sourceUrl": "https://www.youtube.com/watch?v=video_1",
                    "title": "Remote Song",
                    "artistOrChannel": "Remote Artist",
                    "durationMs": 180000,
                    "thumbnailUrl": "https://img.example/video_1.jpg",
                    "canStream": true,
                    "canDownload": true,
                    "recommendationReason": "Based on local listening",
                    "alreadyImported": false
                  }
                ],
                "diagnostics": {
                  "candidatesRequested": 50,
                  "candidatesReceived": 12,
                  "alreadyImportedFiltered": 3,
                  "invalidOrUnavailableFiltered": 8,
                  "finalDisplayedCount": 1,
                  "refreshBroaderShown": true
                }
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(discoverBody)
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(discoverBody.replace("\"refreshType\": \"NORMAL\"", "\"refreshType\": \"BROADER\""))
        )
        val repository = repository(InMemoryApiDiagnosticRecorder())
        repository.saveApiSettings(server.url("/").toString(), "secret-token")

        val upload = repository.createHistoryUpload(
            CreateHistoryUploadRequest(
                clientUploadId = "hist_1",
                estimatedEventCount = 1,
                estimatedCompressedChunkCount = 1
            ),
            idempotencyKey = "idem_upload"
        )
        val chunk = repository.uploadHistoryChunk(
            uploadId = upload.uploadId,
            chunkIndex = 0,
            chunkCount = 1,
            compressedBody = byteArrayOf(1, 2, 3)
        )
        val complete = repository.completeHistoryUpload(
            uploadId = upload.uploadId,
            request = CompleteUploadRequest(
                chunkCount = 1,
                overallSha256 = "abc123",
                totalEventCount = 1
            ),
            idempotencyKey = "idem_complete"
        )
        val refreshRequest = DiscoverRefreshRequest(
            historyUploadId = upload.uploadId,
            clientLibrary = buildJsonObject {
                put("trackCount", JsonPrimitive(1))
            }
        )
        val refresh = repository.refreshDiscover(refreshRequest, idempotencyKey = "idem_refresh")
        val broader = repository.refreshDiscoverBroader(
            refreshRequest.copy(
                broadenReason = "TOO_FEW_RESULTS",
                previousSnapshotId = refresh.snapshotId
            ),
            idempotencyKey = "idem_broader"
        )

        assertEquals("upload_1", upload.uploadId)
        assertTrue(chunk.accepted)
        assertTrue(complete.usableForDiscover)
        assertEquals("Remote Song", refresh.items.single().title)
        assertEquals("BROADER", broader.refreshType)

        val createRequest = server.takeRequest()
        assertEquals("/v1/discover/history/uploads", createRequest.path)
        assertEquals("idem_upload", createRequest.getHeader("Idempotency-Key"))
        assertTrue(createRequest.body.readUtf8().contains("hist_1"))

        val chunkRequest = server.takeRequest()
        assertEquals("/v1/discover/history/uploads/upload_1/chunks", chunkRequest.path)
        assertEquals("zstd", chunkRequest.getHeader("Content-Encoding"))
        assertEquals("upload_1", chunkRequest.getHeader("X-Fenlzer-Upload-Id"))
        assertEquals("0", chunkRequest.getHeader("X-Fenlzer-Chunk-Index"))
        assertEquals("1", chunkRequest.getHeader("X-Fenlzer-Chunk-Count"))
        assertEquals(3L, chunkRequest.bodySize)

        val completeRequest = server.takeRequest()
        assertEquals("/v1/discover/history/uploads/upload_1/complete", completeRequest.path)
        assertEquals("idem_complete", completeRequest.getHeader("Idempotency-Key"))
        assertTrue(completeRequest.body.readUtf8().contains("overallSha256"))

        val refreshHttpRequest = server.takeRequest()
        assertEquals("/v1/discover/refresh", refreshHttpRequest.path)
        assertEquals("idem_refresh", refreshHttpRequest.getHeader("Idempotency-Key"))
        assertTrue(refreshHttpRequest.body.readUtf8().contains("strictlyExcludeImported"))

        val broaderRequest = server.takeRequest()
        assertEquals("/v1/discover/refresh-broader", broaderRequest.path)
        assertEquals("idem_broader", broaderRequest.getHeader("Idempotency-Key"))
        assertTrue(broaderRequest.body.readUtf8().contains("TOO_FEW_RESULTS"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun repository(
        diagnostics: InMemoryApiDiagnosticRecorder
    ): ApiRepository {
        val dispatcher = UnconfinedTestDispatcher()
        return ApiRepository(
            settingsRepository = InMemoryAppSettingsRepository(),
            tokenStore = InMemoryApiTokenStore(),
            diagnosticRecorder = diagnostics,
            dispatchers = FenlzerDispatchers(
                main = dispatcher,
                io = dispatcher,
                default = dispatcher
            )
        )
    }

    private val successfulHealthBody = """
        {
          "success": true,
          "requestId": "req_123",
          "data": {
            "status": "OK",
            "apiVersion": "1.0.0",
            "serverTime": "2026-06-07T14:10:00Z",
            "apiReachable": true,
            "authenticated": true,
            "databaseOk": true,
            "workerRunning": true,
            "activeJobsSupported": true,
            "streamSupported": true,
            "features": {
              "youtubeSearch": true,
              "downloadJobs": true,
              "streamResolve": true
            }
          }
        }
    """.trimIndent()
}
