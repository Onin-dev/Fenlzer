package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.remote.FenlzerApiFactory
import java.security.MessageDigest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverHistoryUploadEncoderTest {
    @Test
    fun largeHistoryIsCompressedIntoOrderedTargetSizedChunksWithOverallHash() {
        val events = (0 until 80).map { index ->
            buildJsonObject {
                put("eventId", JsonPrimitive("event-$index"))
                put("title", JsonPrimitive("song-$index-${uniqueText(index)}"))
            }
        }
        val targetSize = 320

        val payload = DiscoverHistoryUploadEncoder(
            compressor = { bytes -> bytes }
        ).encode(
            events = events,
            targetCompressedChunkSizeBytes = targetSize
        )

        assertTrue(payload.chunks.size > 1)
        val restoredIds = mutableListOf<String>()
        payload.chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.index)
            assertEquals(payload.chunks.size, chunk.count)
            assertTrue(chunk.eventCount == 1 || chunk.compressedBytes.size <= targetSize)

            val json = FenlzerApiFactory.json
                .parseToJsonElement(chunk.compressedBytes.decodeToString())
                .jsonObject
            assertEquals(index, json.getValue("chunkIndex").jsonPrimitive.content.toInt())
            assertEquals(payload.chunks.size, json.getValue("chunkCount").jsonPrimitive.content.toInt())
            restoredIds += json.getValue("events").jsonArray.map { event ->
                event.jsonObject.getValue("eventId").jsonPrimitive.content
            }
        }

        assertEquals(events.indices.map { "event-$it" }, restoredIds)
        val digest = MessageDigest.getInstance("SHA-256")
        payload.chunks.forEach { chunk -> digest.update(chunk.compressedBytes) }
        assertEquals(digest.digest().toHex(), payload.overallSha256)
    }

    @Test
    fun emptyHistoryStillProducesOneValidChunk() {
        val payload = DiscoverHistoryUploadEncoder(
            compressor = { bytes -> bytes }
        ).encode(emptyList(), 1_024)

        assertEquals(1, payload.chunks.size)
        assertEquals(0, payload.chunks.single().eventCount)
    }

    private fun uniqueText(seed: Int): String = buildString {
        repeat(20) { offset ->
            append(((seed * 37 + offset * 19) % 26 + 'a'.code).toChar())
            append(seed * 101 + offset)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
}
