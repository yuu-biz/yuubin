package io.github.yuubiz.yuubin

import android.app.Application

class YuubinApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
