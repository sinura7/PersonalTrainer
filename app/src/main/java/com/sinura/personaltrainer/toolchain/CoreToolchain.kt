package com.sinura.personaltrainer.toolchain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The P4.2 core-family matrix. Compose, Room, and Sign-In stay on the
 * versions P4.3–P4.5 own. Core KTX stops at 1.17.0 because 1.18+
 * wants compileSdk 37 / AGP 9. Lifecycle stops at 2.10.0 because
 * 2.11 requires AGP 9.2. Coroutines stay on 1.10.2 because 1.11 is
 * a Kotlin 2.2 companion.
 *
 * Lives outside `domain/` so the plain-JVM domain lane stays free of
 * serialization and Android libraries.
 */
@Serializable
data class CoreToolchain(
    val coreKtx: String,
    val lifecycle: String,
    val activity: String,
    val coroutines: String,
    val serialization: String,
    val robolectric: String,
    val androidxTestCore: String,
    val androidxTestRunner: String,
    val androidxTestRules: String,
    val androidxTestJunit: String,
) {
    companion object {
        val SIGNED = CoreToolchain(
            coreKtx = "1.17.0",
            lifecycle = "2.10.0",
            activity = "1.12.4",
            coroutines = "1.10.2",
            serialization = "1.8.1",
            robolectric = "4.16",
            androidxTestCore = "1.7.0",
            androidxTestRunner = "1.7.0",
            androidxTestRules = "1.7.0",
            androidxTestJunit = "1.3.0",
        )

        private val json = Json { ignoreUnknownKeys = false }

        fun encode(value: CoreToolchain = SIGNED): String =
            json.encodeToString(serializer(), value)

        fun decode(raw: String): CoreToolchain =
            json.decodeFromString(serializer(), raw)
    }
}
