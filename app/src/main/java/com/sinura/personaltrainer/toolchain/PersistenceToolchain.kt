package com.sinura.personaltrainer.toolchain

/**
 * The P4.4 persistence matrix. Sign-In stays on the version P4.5 owns.
 * Room stops at 2.7.2 because 2.8's compiler needs a newer
 * kotlinx.serialization than the P4.2 ceiling (1.8.1). Schema v2 and the
 * v1→v2 migration are unchanged.
 *
 * Lives outside `domain/` so the plain-JVM domain lane stays free of
 * Android libraries. Gson encodes the pin.
 */
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

        fun encode(value: PersistenceToolchain = SIGNED): String =
            ToolchainJson.gson.toJson(value)

        fun decode(raw: String): PersistenceToolchain =
            ToolchainJson.gson.fromJson(raw, PersistenceToolchain::class.java)
    }
}
