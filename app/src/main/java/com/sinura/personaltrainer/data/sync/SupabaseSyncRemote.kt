package com.sinura.personaltrainer.data.sync

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.sinura.personaltrainer.domain.SyncEntityType

class SupabaseSyncRemote(
    private val rest: SupabaseRestClient,
    private val accessToken: suspend () -> String?,
) : SyncRemotePort {
    override suspend fun upsert(type: SyncEntityType, payloadJson: String) {
        val token = accessToken() ?: error("Not signed in")
        rest.post(type.remoteTable, payloadJson, token)
    }

    override suspend fun tombstone(type: SyncEntityType, payloadJson: String) {
        upsert(type, payloadJson)
    }

    override suspend fun pullUpdatedSince(type: SyncEntityType, sinceUpdatedAtMs: Long): List<String> {
        val token = accessToken() ?: error("Not signed in")
        val body = rest.getUpdatedSince(
            table = type.remoteTable,
            updatedColumn = UPDATED_COLUMN,
            sinceMs = sinceUpdatedAtMs,
            accessToken = token,
        )
        if (body.isBlank()) return emptyList()
        val array = JsonParser.parseString(body)
        if (!array.isJsonArray) return emptyList()
        return (array as JsonArray).map { element -> element.toString() }
    }

    private companion object {
        const val UPDATED_COLUMN = "updated_at_ms"
    }
}
