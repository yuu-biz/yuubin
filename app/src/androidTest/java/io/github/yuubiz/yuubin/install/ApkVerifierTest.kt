package io.github.yuubiz.yuubin.install

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuubiz.yuubin.core.Catalog
import io.github.yuubiz.yuubin.core.InstallFailure
import io.github.yuubiz.yuubin.core.Sha256
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs PackageManager's archive parsing on a real APK. Push one and pass its path, plus its package and signer when it
 * is not Folder Player Fork:
 *
 * ```
 * adb push FolderPlayerFork-1.10.2.apk /data/local/tmp/
 * adb shell am instrument -w -e class io.github.yuubiz.yuubin.install.ApkVerifierTest \
 *   -e apkPath /data/local/tmp/FolderPlayerFork-1.10.2.apk \
 *   [-e packageName <package> -e signer <sha256>] \
 *   io.github.yuubiz.yuubin.debug.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ApkVerifierTest {
    private val packages = Packages(InstrumentationRegistry.getInstrumentation().targetContext.packageManager)
    private val wrongPin = requireNotNull(Sha256.parse("00".repeat(32)))

    @Test
    fun the_pinned_signer_and_package_pass() {
        assertEquals(null, packages.verify(apk, expectedPackage, pin, installedVersionCode = null))
    }

    @Test
    fun a_wrong_pin_is_a_signer_mismatch() {
        assertEquals(InstallFailure.SIGNER_MISMATCH, packages.verify(apk, expectedPackage, wrongPin, installedVersionCode = null))
    }

    @Test
    fun a_wrong_package_is_a_package_mismatch() {
        assertEquals(InstallFailure.PACKAGE_MISMATCH, packages.verify(apk, "$expectedPackage.other", pin, installedVersionCode = null))
    }

    @Test
    fun an_installed_copy_at_or_above_the_apk_version_is_not_newer() {
        assertEquals(InstallFailure.NOT_NEWER, packages.verify(apk, expectedPackage, pin, installedVersionCode = Long.MAX_VALUE))
    }

    private companion object {
        val args: Bundle get() = InstrumentationRegistry.getArguments()

        val expectedPackage: String by lazy { args.getString("packageName") ?: Catalog.folderPlayerFork.packageName }

        val pin: Sha256 by lazy {
            requireNotNull(Sha256.parse(args.getString("signer") ?: Catalog.folderPlayerFork.signer.hex)) { "bad -e signer" }
        }

        val apk: File by lazy {
            val path = requireNotNull(args.getString("apkPath")) { "pass -e apkPath <APK path on the device>" }
            // SELinux may keep the app from reading the pushed file, so the shell copies it into the app's cache.
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val copy = File(instrumentation.targetContext.cacheDir, "verifier-test.apk")
            val pipe = instrumentation.uiAutomation.executeShellCommand("cat $path")
            ParcelFileDescriptor.AutoCloseInputStream(pipe).use { input -> copy.outputStream().use { input.copyTo(it) } }
            check(copy.length() > 0) { "could not read $path through the shell" }
            copy
        }
    }
}
