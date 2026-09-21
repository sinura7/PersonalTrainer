package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.sinura.personaltrainer.data.local.entity.SyncMetadataEntity
import com.sinura.personaltrainer.data.local.entity.SyncOutboxEntity
import com.sinura.personaltrainer.data.local.entity.SyncTableCursorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("DELETE FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deletePendingForEntity(entityType: String, entityId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(row: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxRows(rows: List<SyncOutboxEntity>)

    @Query(
        "SELECT * FROM sync_outbox ORDER BY createdAtMs ASC, id ASC LIMIT :limit",
    )
    suspend fun peekOutbox(limit: Int): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteOutbox(id: String)

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Query("UPDATE sync_outbox SET attempts = :attempts, lastError = :lastError WHERE id = :id")
    suspend fun markOutboxAttempt(id: String, attempts: Int, lastError: String?)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Int

    @Upsert
    suspend fun upsertCursor(row: SyncTableCursorEntity)

    @Query("SELECT * FROM sync_table_cursors WHERE tableName = :tableName")
    suspend fun getCursor(tableName: String): SyncTableCursorEntity?

    @Upsert
    suspend fun upsertMetadata(row: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE id = 0 LIMIT 1")
    fun observeMetadata(): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE id = 0 LIMIT 1")
    suspend fun getMetadata(): SyncMetadataEntity?
}
