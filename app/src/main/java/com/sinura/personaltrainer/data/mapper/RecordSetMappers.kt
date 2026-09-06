package com.sinura.personaltrainer.data.mapper

import com.sinura.personaltrainer.data.local.dao.RecordSetRow
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.RecordSet

/**
 * Null for a block with no lift, which cannot hold a record. An unknown or missing load type
 * reads as loaded, the same stricter fallback [LoadClass.of] takes everywhere else.
 */
fun RecordSetRow.toRecordSet(): RecordSet? {
    val lift = exerciseId ?: return null
    return RecordSet(
        exerciseId = lift,
        exerciseName = exerciseName.orEmpty(),
        loadClass = LoadClass.of(LoadType.fromStorage(loadType)),
        set = ExerciseSetRecord(
            setId = setId,
            sessionId = sessionId,
            weightKg = weightKg,
            reps = reps,
            completedAt = completedAt,
        ),
    )
}
