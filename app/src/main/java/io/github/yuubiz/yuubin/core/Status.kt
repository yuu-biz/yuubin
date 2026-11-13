package io.github.yuubiz.yuubin.core

/**
 * The newer-than check is only a hint for the list; the APK's versionCode is what decides at install time
 * (see [checkApk]).
 */
fun appStatus(installed: Installed, lookup: ReleaseLookup): AppStatus {
    if (installed is Installed.Yes && !installed.signerMatches) return AppStatus.SignerMismatch(installed)
    val release = when (lookup) {
        is ReleaseLookup.Unavailable -> return AppStatus.ReleaseUnavailable(installed, lookup.reason)
        is ReleaseLookup.Found -> lookup.release
    }
    return when (installed) {
        Installed.No -> AppStatus.NotInstalled(release)
        is Installed.Yes ->
            if (isNewer(release, installed)) AppStatus.UpdateAvailable(installed, release)
            else AppStatus.UpToDate(installed, release)
    }
}

private fun isNewer(release: Release, installed: Installed.Yes): Boolean {
    val installedVersion = Version.parse(installed.versionName)
    return if (release.version != null && installedVersion != null) {
        release.version > installedVersion
    } else {
        release.tag.removePrefix("v") != installed.versionName
    }
}
