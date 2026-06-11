package com.fenl.fenlzer.playback

import com.fenl.fenlzer.data.local.dao.RemoteDiscoverDao
import com.fenl.fenlzer.data.remote.ApiRepository
import com.fenl.fenlzer.data.remote.StreamResolveRequest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.security.MessageDigest
import java.time.Instant

class RemoteStreamResolver(
    private val apiRepository: ApiRepository,
    private val remoteDiscoverDao: RemoteDiscoverDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun resolve(remoteItemId: String, reason: String): RemoteStreamResolution {
        val remoteItem = remoteDiscoverDao.getRemoteItem(remoteItemId)
        remoteDiscoverDao.updateStreamResolution(
            remoteItemId = remoteItemId,
            streamState = STREAM_GETTING,
            playableUrl = remoteItem?.lastPlayableUrl,
            expiresAt = remoteItem?.playableUrlExpiresAt,
            resolvedAt = remoteItem?.lastResolvedAt,
            updatedAt = now()
        )

        val response = apiRepository.resolveStream(
            StreamResolveRequest(
                remoteItemId = remoteItemId,
                youtubeVideoId = remoteItem?.youtubeVideoId,
                sourceUrl = remoteItem?.sourceUrl,
                knownPlayableUrl = remoteItem?.knownPlayableUrl(),
                reason = reason
            )
        )
        val expiresAt = runCatching { Instant.parse(response.expiresAt).toEpochMilli() }.getOrNull()
        val resolvedAt = now()
        remoteDiscoverDao.updateStreamResolution(
            remoteItemId = remoteItemId,
            streamState = if (response.canStream && !response.isUrlExpired) STREAM_READY else STREAM_UNAVAILABLE,
            playableUrl = response.playableUrl.takeIf { response.canStream && !response.isUrlExpired },
            expiresAt = expiresAt,
            resolvedAt = resolvedAt,
            updatedAt = resolvedAt
        )
        return RemoteStreamResolution(
            remoteItemId = response.remoteItemId,
            playableUrl = response.playableUrl,
            expiresAt = expiresAt,
            canStream = response.canStream && !response.isUrlExpired
        )
    }

    suspend fun markFailed(remoteItemId: String) {
        val current = remoteDiscoverDao.getRemoteItem(remoteItemId)
        remoteDiscoverDao.updateStreamResolution(
            remoteItemId = remoteItemId,
            streamState = STREAM_FAILED,
            playableUrl = current?.lastPlayableUrl,
            expiresAt = current?.playableUrlExpiresAt,
            resolvedAt = current?.lastResolvedAt,
            updatedAt = now()
        )
    }

    private fun com.fenl.fenlzer.data.local.entity.RemoteItemEntity.knownPlayableUrl() =
        lastPlayableUrl?.takeIf { it.isNotBlank() }?.let { url ->
            buildJsonObject {
                put("urlHash", JsonPrimitive(url.sha256()))
                playableUrlExpiresAt?.let { put("expiresAt", JsonPrimitive(Instant.ofEpochMilli(it).toString())) }
                lastResolvedAt?.let { put("lastResolvedAt", JsonPrimitive(Instant.ofEpochMilli(it).toString())) }
            }
        }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val STREAM_REMOTE_ONLY = "REMOTE_ONLY"
        const val STREAM_GETTING = "GETTING_STREAM"
        const val STREAM_READY = "READY"
        const val STREAM_FAILED = "STREAM_FAILED"
        const val STREAM_UNAVAILABLE = "UNAVAILABLE"
    }
}

data class RemoteStreamResolution(
    val remoteItemId: String,
    val playableUrl: String,
    val expiresAt: Long?,
    val canStream: Boolean
)
