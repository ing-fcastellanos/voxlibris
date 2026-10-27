package com.fcastellanos.readapp

import android.app.Application
import com.fcastellanos.readapp.di.AppContainer

class ReadApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
