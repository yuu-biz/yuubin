package io.github.yuubiz.yuubin.core

/** The current signing certificates of a package, as PackageManager's `SigningInfo` reports them. */
data class Signers(val current: List<Sha256>, val multiple: Boolean) {
    fun areOnly(pin: Sha256): Boolean = !multiple && current == listOf(pin)
}

/** What PackageManager reads from a downloaded APK. [signers] is null when it reported no signing information. */
data class ApkFacts(val packageName: String, val versionCode: Long, val signers: Signers?)

/**
 * The gate before an APK reaches the installer. [apk] is null when PackageManager could not parse the file;
 * [installedVersionCode] is null when the package is not installed.
 */
fun checkApk(apk: ApkFacts?, expectedPackage: String, pin: Sha256, installedVersionCode: Long?): InstallFailure? = when {
    apk?.signers == null -> InstallFailure.UNREADABLE_APK
    apk.packageName != expectedPackage -> InstallFailure.PACKAGE_MISMATCH
    !apk.signers.areOnly(pin) -> InstallFailure.SIGNER_MISMATCH
    installedVersionCode != null && apk.versionCode <= installedVersionCode -> InstallFailure.NOT_NEWER
    else -> null
}
