package com.sinura.personaltrainer.toolchain

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
}
