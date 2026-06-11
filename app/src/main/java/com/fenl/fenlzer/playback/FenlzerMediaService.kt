package com.fenl.fenlzer.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession

class FenlzerMediaService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exoPlayer = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
            }
        player = exoPlayer
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            object : MediaLibrarySession.Callback {}
        ).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player?.release()
        player = null

        super.onDestroy()
    }
}
