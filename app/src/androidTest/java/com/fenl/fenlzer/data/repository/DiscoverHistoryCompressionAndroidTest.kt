package com.fenl.fenlzer.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fenl.fenlzer.data.remote.FenlzerApiFactory
import com.github.luben.zstd.Zstd
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoverHistoryCompressionAndroidTest {
    @Test
    fun zstdCompressedHistoryChunkRoundTripsOnAndroid() {
        val payload = buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("chunkIndex", JsonPrimitive(0))
            put("chunkCount", JsonPrimitive(1))
            put(
                "events",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("eventId", JsonPrimitive("event-1"))
                            put("trackId", JsonPrimitive("track-1"))
                            put("youtubeVideoId", JsonPrimitive("video-1"))
                            put("title", JsonPrimitive("Song"))
                            put("artist", JsonPrimitive("Artist"))
                            put("startedAt", JsonPrimitive("2026-06-10T12:00:00Z"))
                            put("listenedMs", JsonPrimitive(120_000L))
                            put("durationMsAtPlayback", JsonPrimitive(180_000L))
                            put("validListen", JsonPrimitive(true))
                            put("skip", JsonPrimitive(false))
                            put("completion", JsonPrimitive(false))
                            put("completionPercent", JsonPrimitive(0.66f))
                            put("sourceContext", JsonPrimitive("DISCOVER"))
                        }
                    )
                }
            )
        }
        val rawJson = FenlzerApiFactory.json.encodeToString(JsonObject.serializer(), payload)
        val rawBytes = rawJson.toByteArray()

        val compressed = Zstd.compress(rawBytes)
        val restored = Zstd.decompress(compressed, rawBytes.size)

        assertTrue(compressed.isNotEmpty())
        assertEquals(rawJson, restored.decodeToString())
    }
}
