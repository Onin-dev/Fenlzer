package com.fenl.fenlzer

import android.app.Application
import androidx.work.Configuration

class FenlzerApplication : Application(), Configuration.Provider {
    val appGraph: AppGraph by lazy {
        AppGraph.create(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
