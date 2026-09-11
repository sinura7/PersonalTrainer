package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.sinura.personaltrainer.data.local.entity.MissedWorkDecisionEntity
import com.sinura.personaltrainer.data.local.entity.ReminderDeliveryEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerDao {
    @Query("SELECT COUNT(*) FROM schedule_rules")
    suspend fun ruleCount(): Int

    @Query("SELECT * FROM schedule_rules ORDER BY weekday ASC, hour ASC, minute ASC, id ASC")
    fun observeRules(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules ORDER BY weekday ASC, hour ASC, minute ASC, id ASC")
    suspend fun getRules(): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE id = :id")
    suspend fun getRule(id: String): ScheduleRuleEntity?

    @Upsert
    suspend fun upsertRules(rows: List<ScheduleRuleEntity>)

    @Upsert
    suspend fun upsertRule(row: ScheduleRuleEntity)

    @Query("DELETE FROM schedule_rules WHERE id = :id")
    suspend fun deleteRule(id: String)

    @Query("DELETE FROM schedule_rules")
    suspend fun deleteAllRules()

    @Query(
        "SELECT * FROM schedule_occurrences ORDER BY localEpochDay ASC, hour ASC, minute ASC, id ASC",
    )
    fun observeOccurrences(): Flow<List<ScheduleOccurrenceEntity>>

    @Query(
        "SELECT * FROM schedule_occurrences WHERE localEpochDay BETWEEN :start AND :end " +
            "ORDER BY localEpochDay ASC, hour ASC, minute ASC, id ASC",
    )
    suspend fun getOccurrencesBetween(start: Long, end: Long): List<ScheduleOccurrenceEntity>

    @Query("SELECT * FROM schedule_occurrences WHERE id = :id")
    suspend fun getOccurrence(id: String): ScheduleOccurrenceEntity?

    @Query("SELECT * FROM schedule_occurrences WHERE ruleId = :ruleId")
    suspend fun getOccurrencesForRule(ruleId: String): List<ScheduleOccurrenceEntity>

    @Upsert
    suspend fun upsertOccurrences(rows: List<ScheduleOccurrenceEntity>)

    @Upsert
    suspend fun upsertOccurrence(row: ScheduleOccurrenceEntity)

    @Query("DELETE FROM schedule_occurrences WHERE id = :id")
    suspend fun deleteOccurrence(id: String)

    @Query("DELETE FROM schedule_occurrences")
    suspend fun deleteAllOccurrences()

    @Query("SELECT * FROM missed_work_decisions WHERE weekStartEpochDay = :weekStart")
    suspend fun getDecision(weekStart: Long): MissedWorkDecisionEntity?

    @Query("SELECT * FROM missed_work_decisions")
    fun observeDecisions(): Flow<List<MissedWorkDecisionEntity>>

    @Query("SELECT * FROM missed_work_decisions")
    suspend fun getDecisions(): List<MissedWorkDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDecision(row: MissedWorkDecisionEntity)

    @Query("DELETE FROM missed_work_decisions")
    suspend fun deleteAllDecisions()

    @Query("SELECT * FROM reminder_deliveries ORDER BY scheduledAtMs ASC, id ASC")
    suspend fun getDeliveries(): List<ReminderDeliveryEntity>

    @Query("SELECT * FROM reminder_deliveries WHERE id = :id")
    suspend fun getDelivery(id: String): ReminderDeliveryEntity?

    @Query("SELECT * FROM reminder_deliveries WHERE occurrenceId = :occurrenceId")
    suspend fun getDeliveriesForOccurrence(occurrenceId: String): List<ReminderDeliveryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDelivery(row: ReminderDeliveryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeliveries(rows: List<ReminderDeliveryEntity>)

    @Query("DELETE FROM reminder_deliveries")
    suspend fun deleteAllDeliveries()
}
