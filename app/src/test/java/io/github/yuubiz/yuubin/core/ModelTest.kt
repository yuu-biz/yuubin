package io.github.yuubiz.yuubin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    private fun v(text: String): Version = requireNotNull(Version.parse(text)) { "unparsable: $text" }

    @Test
    fun `numeric components compare as numbers`() {
        assertTrue(v("v1.10.2") > v("v1.9.0"))
        assertTrue(v("1.2.10") > v("1.2.9"))
        assertTrue(v("2.0.0") > v("1.99.99"))
    }

    @Test
    fun `a pre-release sorts before its release`() {
        assertTrue(v("1.0.0-rc.3") < v("1.0.0"))
        assertTrue(v("1.0.0") > v("1.0.0-rc.3"))
    }

    @Test
    fun `numeric pre-release identifiers compare as numbers`() {
        assertTrue(v("1.0.0-rc.2") < v("1.0.0-rc.10"))
    }

    @Test
    fun `the leading v is optional and ignored`() {
        assertEquals(0, v("v1.2.3").compareTo(v("1.2.3")))
        assertEquals(v("1.2.3"), v("v1.2.3"))
    }

    @Test
    fun `a fork suffix parses`() {
        val version = v("0.5.0-fork.1")
        assertEquals(0, version.major)
        assertEquals(5, version.minor)
        assertEquals(0, version.patch)
        assertEquals("fork.1", version.preRelease)
    }

    @Test
    fun `text that is not a version returns null`() {
        listOf("", "garbage", "1.2", "v1", "1.2.3.4", "1.x.3", "vv1.2.3", "1.2.3-", "99999999999.0.0").forEach {
            assertNull("expected null for \"$it\"", Version.parse(it))
        }
    }

    private val hex = "bd7e9920a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a36da"

    @Test
    fun `sha256 parses the apksigner form`() {
        assertEquals(hex, Sha256.parse(hex)?.hex)
    }

    @Test
    fun `sha256 parses the keytool colon form in upper case`() {
        val colon = hex.uppercase().chunked(2).joinToString(":")
        assertEquals(hex, Sha256.parse(colon)?.hex)
    }

    @Test
    fun `sha256 parses the GitHub prefix`() {
        assertEquals(hex, Sha256.parse("sha256:$hex")?.hex)
    }

    @Test
    fun `sha256 with the wrong length or characters returns null`() {
        assertNull(Sha256.parse(hex.dropLast(2)))
        assertNull(Sha256.parse(hex + "00"))
        assertNull(Sha256.parse("z".repeat(64)))
        assertNull(Sha256.parse(""))
    }

    @Test
    fun `sha256 of a digest matches the parsed hex`() {
        val digest = ByteArray(32) { it.toByte() }
        assertNotNull(Sha256.parse(Sha256.of(digest).hex))
        assertEquals(Sha256.of(digest), Sha256.parse(Sha256.of(digest).hex))
    }
}
