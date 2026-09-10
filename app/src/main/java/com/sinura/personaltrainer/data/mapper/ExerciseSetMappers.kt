package com.sinura.personaltrainer.data.mapper

import com.sinura.personaltrainer.data.local.dao.ExerciseSetRow
import com.sinura.personaltrainer.domain.ExerciseSetEntry
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.HistoryKind

fun ExerciseSetRow.toExerciseSetEntry(kind: HistoryKind): ExerciseSetEntry = ExerciseSetEntry(
    record = ExerciseSetRecord(
        setId = setId,
        sessionId = sessionId,
        weightKg = weightKg,
        reps = reps,
        completedAt = completedAt,
    ),
    sessionName = sessionName,
    sessionPerformedAtMs = sessionDate,
    kind = kind,
)
