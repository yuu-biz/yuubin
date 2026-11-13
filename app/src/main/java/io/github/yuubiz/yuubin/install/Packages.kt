package io.github.yuubiz.yuubin.install

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import io.github.yuubiz.yuubin.core.ApkFacts
import io.github.yuubiz.yuubin.core.CatalogApp
import io.github.yuubiz.yuubin.core.InstallFailure
import io.github.yuubiz.yuubin.core.Installed
import io.github.yuubiz.yuubin.core.Sha256
import io.github.yuubiz.yuubin.core.Signers
import io.github.yuubiz.yuubin.core.checkApk
import java.io.File
import java.security.MessageDigest

/** PackageManager reads: installed catalog apps and downloaded APKs. Blocking; call off the main thread. */
class Packages(private val pm: PackageManager) {
    fun canRequestInstalls(): Boolean = pm.canRequestPackageInstalls()

    fun installed(app: CatalogApp): Installed {
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return Installed.No
        }
        return Installed.Yes(
            versionName = info.versionName.orEmpty(),
            versionCode = info.longVersionCode,
            signerMatches = info.signingInfo?.signers()?.areOnly(app.signer) == true,
        )
    }

    /** Null means the APK may be installed; see [checkApk] for the rules. */
    fun verify(apk: File, expectedPackage: String, pin: Sha256, installedVersionCode: Long?): InstallFailure? =
        checkApk(archive(apk), expectedPackage, pin, installedVersionCode)

    private fun archive(apk: File): ApkFacts? {
        // API 28 and 29 collect an archive's certificates only when GET_SIGNATURES is set; with GET_SIGNING_CERTIFICATES
        // alone signingInfo stays null there (AOSP PackageManager.getPackageArchiveInfo). Only signingInfo is read.
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.path, flags)
        } ?: return null
        return ApkFacts(info.packageName, info.longVersionCode, info.signingInfo?.signers())
    }

    private fun SigningInfo.signers(): Signers = Signers(
        current = apkContentsSigners.map { Sha256.of(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) },
        multiple = hasMultipleSigners(),
    )
}
