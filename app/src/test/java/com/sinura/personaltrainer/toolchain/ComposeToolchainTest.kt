package com.sinura.personaltrainer.toolchain

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
}
