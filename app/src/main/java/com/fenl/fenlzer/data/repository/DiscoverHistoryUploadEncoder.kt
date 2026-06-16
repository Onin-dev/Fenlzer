package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.remote.FenlzerApiFactory
import com.github.luben.zstd.Zstd
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class DiscoverHistoryUploadEncoder(
    private val json: Json = FenlzerApiFactory.json,
    private val compressor: (ByteArray) -> ByteArray = { bytes -> Zstd.compress(bytes) }
) {
    fun encode(
        events: List<JsonObject>,
        targetCompressedChunkSizeBytes: Int = DEFAULT_TARGET_CHUNK_SIZE_BYTES
    ): DiscoverHistoryUploadPayload {
        val targetSize = targetCompressedChunkSizeBytes.coerceAtLeast(1)
        val eventGroups = partitionEvents(events, targetSize)
        val chunkCount = eventGroups.size
        val chunks = eventGroups.mapIndexed { index, group ->
            encodeChunk(
                events = group,
                chunkIndex = index,
                chunkCount = chunkCount
            )
        }
        val overallDigest = MessageDigest.getInstance("SHA-256")
        chunks.forEach { chunk -> overallDigest.update(chunk.compressedBytes) }

        return DiscoverHistoryUploadPayload(
            chunks = chunks,
            overallSha256 = overallDigest.digest().toHex()
        )
    }

    private fun partitionEvents(
        events: List<JsonObject>,
        targetSize: Int
    ): List<List<JsonObject>> {
        if (events.isEmpty()) return listOf(emptyList())

        val groups = mutableListOf<List<JsonObject>>()
        var startIndex = 0
        while (startIndex < events.size) {
            var low = 1
            var high = events.size - startIndex
            var bestCount = 1

            while (low <= high) {
                val candidateCount = (low + high) / 2
                val candidate = encodeChunk(
                    events = events.subList(startIndex, startIndex + candidateCount),
                    chunkIndex = events.size,
                    chunkCount = events.size
                )
                if (candidate.compressedBytes.size <= targetSize || candidateCount == 1) {
                    bestCount = candidateCount
                    low = candidateCount + 1
                } else {
                    high = candidateCount - 1
                }
            }

            groups += events.subList(startIndex, startIndex + bestCount).toList()
            startIndex += bestCount
        }
        return groups
    }

    private fun encodeChunk(
        events: List<JsonObject>,
        chunkIndex: Int,
        chunkCount: Int
    ): DiscoverHistoryChunk {
        val payload = buildJsonObject {
            put("schemaVersion", JsonPrimitive(SCHEMA_VERSION))
            put("chunkIndex", JsonPrimitive(chunkIndex))
            put("chunkCount", JsonPrimitive(chunkCount))
            put("events", buildJsonArray { events.forEach(::add) })
        }
        val uncompressedBytes = json.encodeToString(JsonObject.serializer(), payload).toByteArray()
        val compressedBytes = compressor(uncompressedBytes)
        return DiscoverHistoryChunk(
            index = chunkIndex,
            count = chunkCount,
            eventCount = events.size,
            uncompressedBytes = uncompressedBytes,
            compressedBytes = compressedBytes,
            sha256 = compressedBytes.sha256()
        )
    }

    companion object {
        const val DEFAULT_TARGET_CHUNK_SIZE_BYTES = 1_048_576
        const val SCHEMA_VERSION = 1
    }
}

data class DiscoverHistoryUploadPayload(
    val chunks: List<DiscoverHistoryChunk>,
    val overallSha256: String
)

data class DiscoverHistoryChunk(
    val index: Int,
    val count: Int,
    val eventCount: Int,
    val uncompressedBytes: ByteArray,
    val compressedBytes: ByteArray,
    val sha256: String
)

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).toHex()

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
