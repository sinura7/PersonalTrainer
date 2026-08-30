package com.sinura.personaltrainer.toolchain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceToolchainTest {
    @Test
    fun signedMatrixRoundTripsThroughGson() {
        val encoded = PersistenceToolchain.encode()
        val decoded = PersistenceToolchain.decode(encoded)
        assertEquals(PersistenceToolchain.SIGNED, decoded)
        assertTrue(encoded.contains("\"room\":\"2.7.2\""))
        assertTrue(encoded.contains("\"datastore\":\"1.2.1\""))
    }

    @Test
    fun catalogAndSchemasMatchTheSignedMatrix() {
        val catalog = source("gradle/libs.versions.toml").readText()
        val signed = PersistenceToolchain.SIGNED
        assertEquals(signed.room, catalogVersion(catalog, "room"))
        assertEquals(signed.datastore, catalogVersion(catalog, "datastore"))
        assertEquals(signed.schemaV1, schemaHash("1.json"))
        assertEquals(signed.schemaV2, schemaHash("2.json"))
    }

    private fun catalogVersion(catalog: String, key: String): String {
        val match = Regex("""^$key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(catalog)
        return checkNotNull(match).groupValues[1]
    }

    private fun schemaHash(fileName: String): String {
        val schema = source(
            "app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/$fileName",
        ).readText()
        val match = Regex(""""identityHash"\s*:\s*"([^"]+)"""").find(schema)
        return checkNotNull(match).groupValues[1]
    }

    private fun source(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File(relative.removePrefix("app/")),
        )
        return candidates.first { it.isFile }
    }
}
