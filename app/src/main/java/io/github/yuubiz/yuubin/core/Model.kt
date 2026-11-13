package io.github.yuubiz.yuubin.core

@JvmInline
value class Sha256 private constructor(val hex: String) {
    override fun toString(): String = hex

    companion object {
        private val HEX = Regex("[0-9a-f]{64}")

        /** Accepts `apksigner` form, `keytool` form (colons) and GitHub's `sha256:` prefix. */
        fun parse(text: String): Sha256? {
            val hex = text.trim().removePrefix("sha256:").replace(":", "").lowercase()
            return if (HEX.matches(hex)) Sha256(hex) else null
        }

        fun of(digest: ByteArray): Sha256 {
            require(digest.size == 32) { "SHA-256 digest must be 32 bytes" }
            return Sha256(digest.joinToString("") { "%02x".format(it) })
        }
    }
}

data class GitHubRepo(val owner: String, val name: String)

/** One app Yuubin distributes. The catalog is compiled in; adding an app means a Yuubin release. */
data class CatalogApp(
    val packageName: String,
    val displayName: String,
    val repo: GitHubRepo,
    /** Must match exactly one asset name of a release. */
    val apkAsset: Regex,
    /** SHA-256 of the only signing certificate Yuubin accepts for this package. */
    val signer: Sha256,
)

/**
 * `X.Y.Z` with an optional `-suffix`, as used by the tags (`v1.10.2`, `v1.0.0-rc.3`) and the
 * versionName of every catalog app. A version with a suffix sorts before the same version without one.
 */
data class Version(val major: Int, val minor: Int, val patch: Int, val preRelease: String?) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)
            .takeIf { it != 0 }
            ?: comparePreRelease(preRelease, other.preRelease)

    companion object {
        private val PATTERN = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?""")

        fun parse(text: String): Version? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = match.destructured.toList().take(3).map { it.toIntOrNull() ?: return null }
            return Version(major, minor, patch, match.groupValues[4].ifEmpty { null })
        }

        private fun comparePreRelease(a: String?, b: String?): Int = when {
            a == b -> 0
            a == null -> 1
            b == null -> -1
            else -> {
                val left = a.split('.')
                val right = b.split('.')
                left.zip(right).firstNotNullOfOrNull { (x, y) ->
                    val xn = x.toIntOrNull()
                    val yn = y.toIntOrNull()
                    val c = if (xn != null && yn != null) xn.compareTo(yn) else x.compareTo(y)
                    c.takeIf { it != 0 }
                } ?: left.size.compareTo(right.size)
            }
        }
    }
}

data class ApkAsset(val name: String, val url: String, val sizeBytes: Long, val sha256: Sha256)

data class Release(
    val tag: String,
    /** Null when the tag is not `vX.Y.Z`; the status then falls back to comparing names. */
    val version: Version?,
    val apk: ApkAsset,
    val notes: String,
    val pageUrl: String,
)

enum class LookupFailure {
    /** 404: no published release, or the repository is private. */
    NOT_FOUND,
    RATE_LIMITED,
    NETWORK,
    NO_APK_ASSET,
    AMBIGUOUS_APK_ASSET,
    /** The asset has no `digest`; Yuubin does not install what it cannot hash-check. */
    NO_DIGEST,
    MALFORMED,
}

sealed interface ReleaseLookup {
    data class Found(val release: Release) : ReleaseLookup
    data class Unavailable(val reason: LookupFailure) : ReleaseLookup
}

sealed interface Installed {
    data object No : Installed
    data class Yes(val versionName: String, val versionCode: Long, val signerMatches: Boolean) : Installed
}

/** What the list shows for one app. A pure function of [Installed] and [ReleaseLookup]; see `appStatus`. */
sealed interface AppStatus {
    data class NotInstalled(val release: Release) : AppStatus
    data class UpdateAvailable(val installed: Installed.Yes, val release: Release) : AppStatus
    data class UpToDate(val installed: Installed.Yes, val release: Release) : AppStatus

    /** The installed copy is signed with another key (a debug build, for example), so Android refuses an update. */
    data class SignerMismatch(val installed: Installed.Yes) : AppStatus
    data class ReleaseUnavailable(val installed: Installed, val reason: LookupFailure) : AppStatus
}

enum class InstallFailure {
    DOWNLOAD,
    HASH_MISMATCH,
    UNREADABLE_APK,
    PACKAGE_MISMATCH,
    SIGNER_MISMATCH,
    /** The APK's versionCode is not above the installed one. */
    NOT_NEWER,
    INSTALL_PERMISSION_MISSING,
    CANCELLED,
    CONFLICT,
    INCOMPATIBLE,
    STORAGE,
    BLOCKED,
    OTHER,
}

/** One state machine per package. Only the installer moves it; the UI reads it. */
sealed interface InstallProgress {
    data object Idle : InstallProgress
    data class Downloading(val bytes: Long, val total: Long) : InstallProgress
    data object Verifying : InstallProgress
    data object AwaitingConfirmation : InstallProgress
    data object Installing : InstallProgress
    data object Succeeded : InstallProgress
    data class Failed(val reason: InstallFailure, val detail: String? = null) : InstallProgress
}
