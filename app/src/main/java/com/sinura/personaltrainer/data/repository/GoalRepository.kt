package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.dao.GoalDao
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.GoalKind
import com.sinura.personaltrainer.domain.GoalPeriod
import com.sinura.personaltrainer.domain.MeasurableGoal
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalRepository(
    private val dao: GoalDao,
) {
    fun observeAll(): Flow<List<MeasurableGoal>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun all(): List<MeasurableGoal> = dao.getAll().map { it.toDomain() }

    suspend fun upsert(goal: MeasurableGoal) {
        dao.upsert(goal.toEntity())
    }

    suspend fun add(
        kind: GoalKind,
        targetValue: Double,
        period: GoalPeriod,
        exerciseId: String? = null,
        exerciseName: String? = null,
        nowMs: Long = JvmTime.nowMillis(),
    ): MeasurableGoal {
        val now = JvmTime.captureNow()
        val goal = MeasurableGoal(
            id = "goal-${kind.name.lowercase()}-${IdFactory.Uuid.newId()}",
            kind = kind,
            targetValue = targetValue,
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            period = period,
            captured = now,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        dao.upsert(goal.toEntity())
        return goal
    }

    suspend fun setPaused(id: String, paused: Boolean) {
        val current = dao.getAll().firstOrNull { it.id == id } ?: return
        dao.upsert(current.copy(paused = paused, updatedAtMs = JvmTime.nowMillis()))
    }

    suspend fun delete(id: String) {
        dao.delete(id)
    }

    suspend fun replaceAll(goals: List<MeasurableGoal>) {
        dao.deleteAll()
        if (goals.isNotEmpty()) dao.upsertAll(goals.map { it.toEntity() })
    }
}
