package com.fcastellanos.voxlibris

import android.app.Application
import com.fcastellanos.voxlibris.di.AppContainer

class VoxLibrisApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
