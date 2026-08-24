package com.sinura.personaltrainer.data.mapper

import com.sinura.personaltrainer.data.local.entity.MeasurableGoalEntity
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.GoalKind
import com.sinura.personaltrainer.domain.GoalPeriod
import com.sinura.personaltrainer.domain.MeasurableGoal

fun MeasurableGoalEntity.toDomain(): MeasurableGoal = MeasurableGoal(
    id = id,
    kind = GoalKind.entries.firstOrNull { it.name == kind } ?: GoalKind.SESSION_COUNT,
    targetValue = targetValue,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    period = GoalPeriod.entries.firstOrNull { it.name == period } ?: GoalPeriod.WEEK,
    captured = CapturedCivilTime(
        instantMillis = instantMs,
        zoneId = zoneId,
        offsetSeconds = offsetSeconds,
        localEpochDay = localEpochDay,
    ),
    paused = paused,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

fun MeasurableGoal.toEntity(): MeasurableGoalEntity = MeasurableGoalEntity(
    id = id,
    kind = kind.name,
    targetValue = targetValue,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    period = period.name,
    instantMs = captured.instantMillis,
    zoneId = captured.zoneId,
    offsetSeconds = captured.offsetSeconds,
    localEpochDay = captured.localEpochDay,
    paused = paused,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)
