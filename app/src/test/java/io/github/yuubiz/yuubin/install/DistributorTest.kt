package io.github.yuubiz.yuubin.install

import io.github.yuubiz.yuubin.core.ApkAsset
import io.github.yuubiz.yuubin.core.AppStatus
import io.github.yuubiz.yuubin.core.CatalogApp
import io.github.yuubiz.yuubin.core.Download
import io.github.yuubiz.yuubin.core.GitHubRepo
import io.github.yuubiz.yuubin.core.InstallProgress
import io.github.yuubiz.yuubin.core.Installed
import io.github.yuubiz.yuubin.core.Release
import io.github.yuubiz.yuubin.core.ReleaseLookup
import io.github.yuubiz.yuubin.core.Sha256
import io.github.yuubiz.yuubin.core.Version
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DistributorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val signer = requireNotNull(Sha256.parse("ab".repeat(32)))
    private val appA = CatalogApp("com.example.a", "A", GitHubRepo("o", "a"), Regex("a\\.apk"), signer)
    private val appB = CatalogApp("com.example.b", "B", GitHubRepo("o", "b"), Regex("b\\.apk"), signer)
    private val release = Release("v1.1.0", Version.parse("v1.1.0"), ApkAsset("a.apk", "https://x/a.apk", 10, signer), "", "https://x")
    private val v1 = Installed.Yes("1.0.0", versionCode = 1, signerMatches = true)
    private val v2 = Installed.Yes("1.1.0", versionCode = 2, signerMatches = true)

    private val packages = FakePackages()
    private val sessions = FakeSessions()
    private var lookupGate: CompletableDeferred<Unit>? = null
    private var downloadGate: CompletableDeferred<Unit>? = null
    private var downloads = 0

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelScopes() = scopes.forEach { it.cancel() }

    // Not backgroundScope: advanceUntilIdle stops once only background work is left, which would strand an install.
    private fun TestScope.distributor(): Distributor {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher).also { scopes += it }
        return Distributor(
            catalog = listOf(appA, appB),
            latestRelease = {
                lookupGate?.await()
                ReleaseLookup.Found(release)
            },
            downloadApk = { _, target, _ ->
                downloads++
                downloadGate?.await()
                Download.Done(target)
            },
            packages = packages,
            sessions = sessions,
            apkDir = tmp.root.resolve("apk"),
            scope = scope,
            io = dispatcher,
        )
    }

    private fun Distributor.row(app: CatalogApp): AppRow = rows.value.single { it.app == app }

    /** Refreshes, then runs an install of [app] up to its committed session and returns that session's id. */
    private suspend fun TestScope.committedInstall(distributor: Distributor, app: CatalogApp): Int {
        distributor.refresh()
        distributor.install(app)
        advanceUntilIdle()
        assertEquals(InstallProgress.Installing, distributor.row(app).progress)
        return sessions.live.filterValues { it.packageName == app.packageName && it.committed == true }.keys.single()
    }

    @Test
    fun `an install that succeeds while a refresh waits for its lookup keeps the new version`() = runTest {
        packages.installed[appA.packageName] = v1
        val distributor = distributor()
        val id = committedInstall(distributor, appA)

        lookupGate = CompletableDeferred()
        val refreshing = launch { distributor.refresh() }
        runCurrent()
        packages.installed[appA.packageName] = v2
        sessions.live.remove(id)
        distributor.onSessionStatus(id, SessionStatus.Succeeded)
        runCurrent()
        lookupGate?.complete(Unit)
        refreshing.join()
        advanceUntilIdle()

        assertEquals(AppRow(appA, AppStatus.UpToDate(v2, release), InstallProgress.Succeeded), distributor.row(appA))
    }

    @Test
    fun `a session gone with a newer version installed settles as Succeeded`() = runTest {
        packages.installed[appA.packageName] = v1
        val distributor = distributor()
        val id = committedInstall(distributor, appA)

        sessions.live.remove(id)
        packages.installed[appA.packageName] = v2
        distributor.reconcile()
        advanceUntilIdle()

        assertEquals(AppRow(appA, AppStatus.UpToDate(v2, release), InstallProgress.Succeeded), distributor.row(appA))
    }

    @Test
    fun `a session gone with the version unchanged settles as Idle`() = runTest {
        packages.installed[appA.packageName] = v1
        val distributor = distributor()
        val id = committedInstall(distributor, appA)

        sessions.live.remove(id)
        distributor.reconcile()
        advanceUntilIdle()

        assertEquals(AppRow(appA, AppStatus.UpdateAvailable(v1, release), InstallProgress.Idle), distributor.row(appA))
    }

    @Test
    fun `install while one is in flight for the package starts nothing`() = runTest {
        packages.installed[appA.packageName] = v1
        val distributor = distributor()
        distributor.refresh()
        downloadGate = CompletableDeferred()

        distributor.install(appA)
        runCurrent()
        distributor.install(appA)
        downloadGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, downloads)
        assertEquals(1, sessions.staged.size)
    }
}

private class FakePackages : AppPackages {
    val installed = mutableMapOf<String, Installed>()

    override fun canRequestInstalls() = true
    override fun installed(app: CatalogApp): Installed = installed[app.packageName] ?: Installed.No
    override fun verify(apk: java.io.File, expectedPackage: String, pin: Sha256, installedVersionCode: Long?) = null
}

private class FakeSessions : InstallSessions {
    data class Live(val packageName: String?, val committed: Boolean?)

    /** Sessions the system still holds, by id. Tests add sessions from an earlier process and remove finished ones. */
    val live = mutableMapOf<Int, Live>()
    val staged = mutableListOf<Int>()
    val abandoned = mutableListOf<Int>()
    private var nextId = 100

    override fun stage(apk: java.io.File, packageName: String): Int {
        val id = nextId++
        live[id] = Live(packageName, committed = false)
        staged += id
        return id
    }

    override fun commit(sessionId: Int) {
        live[sessionId] = live.getValue(sessionId).copy(committed = true)
    }

    override fun exists(sessionId: Int) = sessionId in live

    override fun abandon(sessionId: Int) {
        if (live.remove(sessionId) != null) abandoned += sessionId
    }

    override fun abandonAll() = live.keys.toList().forEach(::abandon)
}
