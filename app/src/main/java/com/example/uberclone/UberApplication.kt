package com.example.uberclone

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UberApplication: Application() {
    override fun onCreate() {
        super.onCreate()

    }
}