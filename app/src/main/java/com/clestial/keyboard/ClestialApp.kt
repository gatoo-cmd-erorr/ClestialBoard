package com.clestial.keyboard

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class ClestialApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Prefs(this).nightMode())
    }
}
