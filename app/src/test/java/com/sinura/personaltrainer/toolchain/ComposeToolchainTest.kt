package com.sinura.personaltrainer.toolchain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeToolchainTest {
    @Test
    fun signedMatrixRoundTripsThroughGson() {
        val encoded = ComposeToolchain.encode()
        val decoded = ComposeToolchain.decode(encoded)
        assertEquals(ComposeToolchain.SIGNED, decoded)
        assertTrue(encoded.contains("\"composeBom\":\"2026.06.01\""))
        assertTrue(encoded.contains("\"navigation\":\"2.9.8\""))
        assertTrue(encoded.contains("\"compiler\":\"2.0.21\""))
    }

    @Test
    fun catalogMatchesTheSignedMatrix() {
        val catalog = source("gradle/libs.versions.toml").readText()
        val signed = ComposeToolchain.SIGNED
        assertEquals(signed.composeBom, catalogVersion(catalog, "composeBom"))
        assertEquals(signed.navigation, catalogVersion(catalog, "navigationCompose"))
        assertEquals(signed.compiler, catalogVersion(catalog, "kotlin"))
    }

    private fun catalogVersion(catalog: String, key: String): String {
        val match = Regex("""^$key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(catalog)
        return checkNotNull(match).groupValues[1]
    }

    private fun source(relative: String): File {
        val candidates = listOf(File(relative), File("../$relative"))
        return candidates.first { it.isFile }
    }
}
