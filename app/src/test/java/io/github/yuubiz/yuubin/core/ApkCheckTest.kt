package io.github.yuubiz.yuubin.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkCheckTest {
    private val pin = requireNotNull(Sha256.parse("11".repeat(32)))
    private val other = requireNotNull(Sha256.parse("22".repeat(32)))
    private val pkg = "com.example.app"

    private fun apk(
        packageName: String = pkg,
        versionCode: Long = 10,
        signers: Signers? = Signers(listOf(pin), multiple = false),
    ) = ApkFacts(packageName, versionCode, signers)

    private fun check(apk: ApkFacts?, installedVersionCode: Long? = null) = checkApk(apk, pkg, pin, installedVersionCode)

    @Test
    fun `an APK signed only by the pin passes on a fresh install`() {
        assertEquals(null, check(apk()))
    }

    @Test
    fun `an APK with a higher versionCode passes as an update`() {
        assertEquals(null, check(apk(versionCode = 10), installedVersionCode = 9))
    }

    @Test
    fun `an unparsable APK or one without signing information fails closed`() {
        assertEquals(InstallFailure.UNREADABLE_APK, check(null))
        assertEquals(InstallFailure.UNREADABLE_APK, check(apk(signers = null)))
    }

    @Test
    fun `another package name is rejected even with the right signer`() {
        assertEquals(InstallFailure.PACKAGE_MISMATCH, check(apk(packageName = "com.example.other")))
    }

    @Test
    fun `another signer, extra signers or no signer are rejected`() {
        listOf(
            Signers(listOf(other), multiple = false),
            Signers(listOf(pin, other), multiple = true),
            Signers(listOf(pin), multiple = true),
            Signers(listOf(pin, pin), multiple = false),
            Signers(emptyList(), multiple = false),
        ).forEach { signers ->
            assertEquals(signers.toString(), InstallFailure.SIGNER_MISMATCH, check(apk(signers = signers)))
        }
    }

    @Test
    fun `an APK that is not newer than the installed copy is rejected`() {
        assertEquals(InstallFailure.NOT_NEWER, check(apk(versionCode = 10), installedVersionCode = 10))
        assertEquals(InstallFailure.NOT_NEWER, check(apk(versionCode = 9), installedVersionCode = 10))
    }

    @Test
    fun `the signer is checked before the version`() {
        val foreignAndOld = apk(versionCode = 1, signers = Signers(listOf(other), multiple = false))
        assertEquals(InstallFailure.SIGNER_MISMATCH, check(foreignAndOld, installedVersionCode = 10))
    }
}
