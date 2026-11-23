package com.cream.translator

import android.app.Application
import timber.log.Timber

class CreamTranslatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
