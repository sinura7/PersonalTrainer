package com.sinura.personaltrainer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [Index("entityType", "entityId"), Index("createdAtMs")],
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    /** JSON payload for upsert/delete tombstone; null only for legacy rows. */
    val payloadJson: String?,
    val createdAtMs: Long,
    val attempts: Int,
    val lastError: String?,
)

@Entity(tableName = "sync_table_cursors")
data class SyncTableCursorEntity(
    @PrimaryKey val tableName: String,
    /** Pull watermark: rows with updatedAtMs strictly greater are fetched. */
    val lastPulledUpdatedAtMs: Long,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastSuccessAtMs: Long?,
    val lastError: String?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
