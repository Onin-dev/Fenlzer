package com.fenl.fenlzer.playback

import android.content.Context
import android.media.MediaRouter2

object AndroidAudioOutputSwitcher {
    fun show(context: Context): Boolean =
        runCatching {
            MediaRouter2.getInstance(context).showSystemOutputSwitcher()
        }.getOrDefault(false)
}
