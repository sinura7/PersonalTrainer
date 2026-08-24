package com.sinura.personaltrainer.toolchain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The P4.4 persistence matrix. Sign-In stays on the version P4.5 owns.
 * Room stops at 2.7.2 because 2.8's compiler needs a newer
 * kotlinx.serialization than the P4.2 pin (1.8.1). Schema v2 and the
 * v1→v2 migration are unchanged.
 *
 * Lives outside `domain/` so the plain-JVM domain lane stays free of
 * serialization and Android libraries.
 */
@Serializable
data class PersistenceToolchain(
    val room: String,
    val datastore: String,
    val schemaV1: String,
    val schemaV2: String,
) {
    companion object {
        val SIGNED = PersistenceToolchain(
            room = "2.7.2",
            datastore = "1.2.1",
            schemaV1 = "6d58ad40d5c03785ab29aaf61157f369",
            schemaV2 = "3eedd5301f0344b7802f5d0da2f68b3e",
        )

        private val json = Json { ignoreUnknownKeys = false }

        fun encode(value: PersistenceToolchain = SIGNED): String =
            json.encodeToString(serializer(), value)

        fun decode(raw: String): PersistenceToolchain =
            json.decodeFromString(serializer(), raw)
    }
}
