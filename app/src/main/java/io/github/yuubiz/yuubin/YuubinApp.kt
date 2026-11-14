package io.github.yuubiz.yuubin

import android.app.Application
import io.github.yuubiz.yuubin.updates.UpdateCheckWorker

class YuubinApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UpdateCheckWorker.schedule(this)
    }
}
