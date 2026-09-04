package com.sinura.personaltrainer.toolchain

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
}
