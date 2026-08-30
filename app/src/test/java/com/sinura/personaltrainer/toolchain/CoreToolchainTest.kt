package com.sinura.personaltrainer.toolchain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreToolchainTest {
    @Test
    fun signedMatrixRoundTripsThroughGson() {
        val encoded = CoreToolchain.encode()
        val decoded = CoreToolchain.decode(encoded)
        assertEquals(CoreToolchain.SIGNED, decoded)
        assertTrue(encoded.contains("\"coreKtx\":\"1.17.0\""))
        assertTrue(encoded.contains("\"lifecycle\":\"2.10.0\""))
    }

    @Test
    fun catalogMatchesTheSignedMatrix() {
        val catalog = source("gradle/libs.versions.toml").readText()
        val signed = CoreToolchain.SIGNED
        assertEquals(signed.coreKtx, catalogVersion(catalog, "coreKtx"))
        assertEquals(signed.lifecycle, catalogVersion(catalog, "lifecycleRuntimeKtx"))
        assertEquals(signed.activity, catalogVersion(catalog, "activityCompose"))
        assertEquals(signed.coroutines, catalogVersion(catalog, "coroutines"))
        assertEquals("1.8.1", signed.serialization)
        assertTrue(!catalog.contains("kotlinx-serialization-json"))
        assertTrue(!catalog.contains("kotlin-serialization"))
        assertEquals(signed.robolectric, catalogVersion(catalog, "robolectric"))
        assertEquals(signed.androidxTestCore, catalogVersion(catalog, "androidxTestCore"))
        assertEquals(signed.androidxTestRunner, catalogVersion(catalog, "androidxTestRunner"))
        assertEquals(signed.androidxTestRules, catalogVersion(catalog, "androidxTestRules"))
        assertEquals(signed.androidxTestJunit, catalogVersion(catalog, "androidxTestExtJunit"))
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
