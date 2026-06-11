package com.fenl.fenlzer

import android.app.Application

class FenlzerApplication : Application() {
    val appGraph: AppGraph by lazy {
        AppGraph.create(this)
    }
}
