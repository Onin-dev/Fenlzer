package com.fenl.fenlzer

import android.app.Application
import androidx.work.Configuration

class FenlzerApplication : Application(), Configuration.Provider {
    private val appGraphDelegate = lazy {
        AppGraph.create(this)
    }
    val appGraph: AppGraph by appGraphDelegate

    fun appGraphIfInitialized(): AppGraph? =
        if (appGraphDelegate.isInitialized()) appGraph else null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
