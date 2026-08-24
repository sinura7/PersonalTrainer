package com.sinura.personaltrainer.data.mapper

import com.sinura.personaltrainer.data.backup.BackupActivity
import com.sinura.personaltrainer.data.backup.BackupActivityBlock
import com.sinura.personaltrainer.data.backup.BackupActivityMuscle
import com.sinura.personaltrainer.data.backup.BackupActivityTemplate
import com.sinura.personaltrainer.data.backup.BackupCapturedTime
import com.sinura.personaltrainer.data.backup.BackupCardioInterval
import com.sinura.personaltrainer.data.backup.BackupStrengthSet
import com.sinura.personaltrainer.data.local.entity.ActivityBlockEntity
import com.sinura.personaltrainer.data.local.entity.ActivityCardioIntervalEntity
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.local.entity.ActivityStrengthSetEntity
import com.sinura.personaltrainer.data.local.entity.ActivityTemplateEntity
import com.sinura.personaltrainer.data.local.relation.ActivityBlockGraph
import com.sinura.personaltrainer.data.local.relation.ActivitySessionGraph
import com.sinura.personaltrainer.data.local.relation.ActivityTemplateGraph
import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivitySource
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityTemplate
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioInterval
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet

object ActivityMuscleCodec {
    fun encode(muscles: List<MuscleCredit>): String =
        muscles.joinToString("\n") { "${it.muscleKey}\t${it.weight}" }

    fun decode(raw: String?): List<MuscleCredit> =
        raw.orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split('\t')
                MuscleCredit(
                    muscleKey = parts[0],
                    weight = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0,
                )
            }
            .toList()
}

fun ActivitySessionGraph.toDomain(): ActivitySession {
    val start = CapturedCivilTime(
        instantMillis = session.performedStartInstantMs,
        zoneId = session.performedStartZoneId,
        offsetSeconds = session.performedStartOffsetSeconds,
        localEpochDay = session.performedStartLocalEpochDay,
    )
    val end = if (session.performedEndInstantMs != null &&
        session.performedEndZoneId != null &&
        session.performedEndOffsetSeconds != null &&
        session.performedEndLocalEpochDay != null
    ) {
        CapturedCivilTime(
            instantMillis = session.performedEndInstantMs,
            zoneId = session.performedEndZoneId,
            offsetSeconds = session.performedEndOffsetSeconds,
            localEpochDay = session.performedEndLocalEpochDay,
        )
    } else {
        null
    }
    return ActivitySession(
        id = session.id,
        status = ActivityStatus.valueOf(session.status),
        origin = ActivityOrigin.valueOf(session.origin),
        source = ActivitySource.valueOf(session.source),
        title = session.title,
        notes = session.notes,
        performedStart = start,
        performedEnd = end,
        templateId = session.templateId,
        occurrenceId = session.occurrenceId,
        blocks = blocks.sortedBy { it.block.sortOrder }.map { it.toDomain() },
        createdAtMs = session.createdAtMs,
        updatedAtMs = session.updatedAtMs,
        revision = session.revision,
    )
}

fun ActivityBlockGraph.toDomain(): ActivityBlock = when (block.kind) {
    "STRENGTH" -> StrengthBlock(
        id = block.id,
        sortOrder = block.sortOrder,
        exerciseId = block.exerciseId.orEmpty(),
        exerciseName = block.exerciseName.orEmpty(),
        loadType = block.loadType?.let { LoadType.valueOf(it) } ?: LoadType.EXTERNAL,
        equipment = block.equipment?.let { EquipmentType.valueOf(it) } ?: EquipmentType.OTHER,
        muscles = ActivityMuscleCodec.decode(block.musclesEncoded),
        sets = strengthSets.sortedBy { it.setNumber }.map { it.toDomain() },
    )
    "CARDIO" -> CardioBlock(
        id = block.id,
        sortOrder = block.sortOrder,
        type = block.cardioType?.let { CardioType.valueOf(it) } ?: CardioType.OTHER,
        indoor = block.indoor ?: false,
        elapsedSeconds = block.elapsedSeconds ?: 0L,
        movingSeconds = block.movingSeconds,
        distanceMeters = block.distanceMeters,
        elevationMeters = block.elevationMeters,
        heartRateBpm = block.heartRateBpm,
        energyKj = block.energyKj,
        rpe = block.rpe,
        routeRef = block.routeRef,
        intervals = cardioIntervals.sortedBy { it.sortOrder }.map { it.toDomain() },
    )
    else -> error("Unknown activity block kind ${block.kind}")
}

fun ActivityStrengthSetEntity.toDomain() = StrengthSet(
    id = id,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isWarmup = isWarmup,
    completedAtMs = completedAtMs,
)

fun ActivityCardioIntervalEntity.toDomain() = CardioInterval(
    id = id,
    sortOrder = sortOrder,
    elapsedSeconds = elapsedSeconds,
    distanceMeters = distanceMeters,
    rpe = rpe,
)

fun ActivityTemplateGraph.toDomain() = ActivityTemplate(
    id = template.id,
    title = template.title,
    notes = template.notes,
    blocks = blocks.sortedBy { it.block.sortOrder }.map { it.toDomain() },
)

fun ActivitySession.toEntity() = ActivitySessionEntity(
    id = id,
    status = status.name,
    origin = origin.name,
    source = source.name,
    title = title,
    notes = notes,
    performedStartInstantMs = performedStart.instantMillis,
    performedStartZoneId = performedStart.zoneId,
    performedStartOffsetSeconds = performedStart.offsetSeconds,
    performedStartLocalEpochDay = performedStart.localEpochDay,
    performedEndInstantMs = performedEnd?.instantMillis,
    performedEndZoneId = performedEnd?.zoneId,
    performedEndOffsetSeconds = performedEnd?.offsetSeconds,
    performedEndLocalEpochDay = performedEnd?.localEpochDay,
    templateId = templateId,
    occurrenceId = occurrenceId,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    revision = revision,
    liveToken = if (status == ActivityStatus.ACTIVE) "LIVE" else null,
)

fun ActivityBlock.toEntity(sessionId: String?, templateId: String?): ActivityBlockEntity =
    when (this) {
        is StrengthBlock -> ActivityBlockEntity(
            id = id,
            sessionId = sessionId,
            templateId = templateId,
            sortOrder = sortOrder,
            kind = "STRENGTH",
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            loadType = loadType.name,
            equipment = equipment.name,
            musclesEncoded = ActivityMuscleCodec.encode(muscles),
            cardioType = null,
            indoor = null,
            elapsedSeconds = null,
            movingSeconds = null,
            distanceMeters = null,
            elevationMeters = null,
            heartRateBpm = null,
            energyKj = null,
            rpe = null,
            routeRef = null,
        )
        is CardioBlock -> ActivityBlockEntity(
            id = id,
            sessionId = sessionId,
            templateId = templateId,
            sortOrder = sortOrder,
            kind = "CARDIO",
            exerciseId = null,
            exerciseName = null,
            loadType = null,
            equipment = null,
            musclesEncoded = null,
            cardioType = type.name,
            indoor = indoor,
            elapsedSeconds = elapsedSeconds,
            movingSeconds = movingSeconds,
            distanceMeters = distanceMeters,
            elevationMeters = elevationMeters,
            heartRateBpm = heartRateBpm,
            energyKj = energyKj,
            rpe = rpe,
            routeRef = routeRef,
        )
    }

fun StrengthSet.toEntity(blockId: String) = ActivityStrengthSetEntity(
    id = id,
    blockId = blockId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isWarmup = isWarmup,
    completedAtMs = completedAtMs,
)

fun CardioInterval.toEntity(blockId: String) = ActivityCardioIntervalEntity(
    id = id,
    blockId = blockId,
    sortOrder = sortOrder,
    elapsedSeconds = elapsedSeconds,
    distanceMeters = distanceMeters,
    rpe = rpe,
)

fun ActivityTemplate.toEntity(updatedAtMs: Long) = ActivityTemplateEntity(
    id = id,
    title = title,
    notes = notes,
    updatedAtMs = updatedAtMs,
)

fun CapturedCivilTime.toBackup() = BackupCapturedTime(
    instantMs = instantMillis,
    zoneId = zoneId,
    offsetSeconds = offsetSeconds,
    localEpochDay = localEpochDay,
)

fun BackupCapturedTime.toDomain() = CapturedCivilTime(
    instantMillis = instantMs,
    zoneId = zoneId,
    offsetSeconds = offsetSeconds,
    localEpochDay = localEpochDay,
)

fun ActivitySession.toBackup() = BackupActivity(
    id = id,
    status = status.name,
    origin = origin.name,
    source = source.name,
    title = title,
    notes = notes,
    performedStart = performedStart.toBackup(),
    performedEnd = performedEnd?.toBackup(),
    templateId = templateId,
    occurrenceId = occurrenceId,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    revision = revision,
    blocks = blocks.map { it.toBackup() },
)

fun ActivityBlock.toBackup(): BackupActivityBlock = when (this) {
    is StrengthBlock -> BackupActivityBlock(
        id = id,
        sortOrder = sortOrder,
        kind = "STRENGTH",
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        loadType = loadType.name,
        equipment = equipment.name,
        muscles = muscles.map { BackupActivityMuscle(it.muscleKey, it.weight) },
        sets = sets.map {
            BackupStrengthSet(
                id = it.id,
                setNumber = it.setNumber,
                weightKg = it.weightKg,
                reps = it.reps,
                rpe = it.rpe,
                isWarmup = it.isWarmup,
                completedAtMs = it.completedAtMs,
            )
        },
    )
    is CardioBlock -> BackupActivityBlock(
        id = id,
        sortOrder = sortOrder,
        kind = "CARDIO",
        cardioType = type.name,
        indoor = indoor,
        elapsedSeconds = elapsedSeconds,
        movingSeconds = movingSeconds,
        distanceMeters = distanceMeters,
        elevationMeters = elevationMeters,
        heartRateBpm = heartRateBpm,
        energyKj = energyKj,
        rpe = rpe,
        routeRef = routeRef,
        intervals = intervals.map {
            BackupCardioInterval(
                id = it.id,
                sortOrder = it.sortOrder,
                elapsedSeconds = it.elapsedSeconds,
                distanceMeters = it.distanceMeters,
                rpe = it.rpe,
            )
        },
    )
}

fun ActivityTemplate.toBackup() = BackupActivityTemplate(
    id = id,
    title = title,
    notes = notes,
    blocks = blocks.map { it.toBackup() },
)

fun BackupActivity.toDomain() = ActivitySession(
    id = id,
    status = ActivityStatus.valueOf(status),
    origin = ActivityOrigin.valueOf(origin),
    source = ActivitySource.valueOf(source),
    title = title,
    notes = notes,
    performedStart = performedStart.toDomain(),
    performedEnd = performedEnd?.toDomain(),
    templateId = templateId,
    occurrenceId = occurrenceId,
    blocks = blocks.map { it.toDomain() },
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    revision = revision,
)

fun BackupActivityTemplate.toDomain() = ActivityTemplate(
    id = id,
    title = title,
    notes = notes,
    blocks = blocks.map { it.toDomain() },
)

fun BackupActivityBlock.toDomain(): ActivityBlock = when (kind) {
    "STRENGTH" -> StrengthBlock(
        id = id,
        sortOrder = sortOrder,
        exerciseId = exerciseId.orEmpty(),
        exerciseName = exerciseName.orEmpty(),
        loadType = loadType?.let { LoadType.valueOf(it) } ?: LoadType.EXTERNAL,
        equipment = equipment?.let { EquipmentType.valueOf(it) } ?: EquipmentType.OTHER,
        muscles = muscles.map { MuscleCredit(it.muscleKey, it.weight) },
        sets = sets.map {
            StrengthSet(
                id = it.id,
                setNumber = it.setNumber,
                weightKg = it.weightKg,
                reps = it.reps,
                rpe = it.rpe,
                isWarmup = it.isWarmup,
                completedAtMs = it.completedAtMs,
            )
        },
    )
    else -> CardioBlock(
        id = id,
        sortOrder = sortOrder,
        type = cardioType?.let { CardioType.valueOf(it) } ?: CardioType.OTHER,
        indoor = indoor ?: false,
        elapsedSeconds = elapsedSeconds ?: 0L,
        movingSeconds = movingSeconds,
        distanceMeters = distanceMeters,
        elevationMeters = elevationMeters,
        heartRateBpm = heartRateBpm,
        energyKj = energyKj,
        rpe = rpe,
        routeRef = routeRef,
        intervals = intervals.map {
            CardioInterval(
                id = it.id,
                sortOrder = it.sortOrder,
                elapsedSeconds = it.elapsedSeconds,
                distanceMeters = it.distanceMeters,
                rpe = it.rpe,
            )
        },
    )
}
