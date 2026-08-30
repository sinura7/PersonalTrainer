package com.sinura.personaltrainer.toolchain

/**
 * The P4.3 Compose matrix. Room, DataStore, and Sign-In stay on the
 * versions P4.4–P4.5 own. The BOM stops at 2026.06.01 because
 * 2026.08.00 pulls Compose UI 1.12.0, which wants compileSdk 37 /
 * AGP 9.1. The compiler is the Kotlin Compose plugin and stays on
 * Kotlin 2.0.21.
 *
 * Lives outside `domain/` so the plain-JVM domain lane stays free of
 * Android libraries. Gson encodes the pin; kotlinx.serialization is
 * not a compile plugin on this module.
 */
data class ComposeToolchain(
    val composeBom: String,
    val navigation: String,
    val compiler: String,
    val composeUi: String,
    val material3: String,
) {
    companion object {
        val SIGNED = ComposeToolchain(
            composeBom = "2026.06.01",
            navigation = "2.9.8",
            compiler = "2.0.21",
            composeUi = "1.11.4",
            material3 = "1.4.0",
        )

        fun encode(value: ComposeToolchain = SIGNED): String =
            ToolchainJson.gson.toJson(value)

        fun decode(raw: String): ComposeToolchain =
            ToolchainJson.gson.fromJson(raw, ComposeToolchain::class.java)
    }
}
