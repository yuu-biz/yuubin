package io.github.yuubiz.yuubin

import android.content.Context
import io.github.yuubiz.yuubin.core.ApkDownloader
import io.github.yuubiz.yuubin.core.Catalog
import io.github.yuubiz.yuubin.core.GitHubReleases
import io.github.yuubiz.yuubin.install.Distributor
import io.github.yuubiz.yuubin.install.Packages
import io.github.yuubiz.yuubin.install.SessionInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File

/** Hand-built dependencies, one instance per process. */
class AppContainer(context: Context) {
    private val http = OkHttpClient()

    val distributor = Distributor(
        catalog = Catalog.apps,
        releases = GitHubReleases(http),
        downloader = ApkDownloader(http),
        packages = Packages(context.packageManager),
        sessions = SessionInstaller(context),
        apkDir = File(context.cacheDir, "apk"),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
