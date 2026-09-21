package com.sinura.personaltrainer.data.sync

import com.google.gson.annotations.SerializedName
import com.sinura.personaltrainer.data.local.entity.ActivityBlockEntity
import com.sinura.personaltrainer.data.local.entity.ActivityCardioIntervalEntity
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.local.entity.ActivityStrengthSetEntity
import com.sinura.personaltrainer.data.local.entity.ActivityTemplateEntity
import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity

/** Supabase row shapes (snake_case). Room stays camelCase locally. */
data class RemoteActivitySessionRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val status: String,
    val origin: String,
    val source: String,
    val title: String,
    val notes: String,
    @SerializedName("performed_start_instant_ms") val performedStartInstantMs: Long,
    @SerializedName("performed_start_zone_id") val performedStartZoneId: String,
    @SerializedName("performed_start_offset_seconds") val performedStartOffsetSeconds: Int,
    @SerializedName("performed_start_local_epoch_day") val performedStartLocalEpochDay: Long,
    @SerializedName("performed_end_instant_ms") val performedEndInstantMs: Long?,
    @SerializedName("performed_end_zone_id") val performedEndZoneId: String?,
    @SerializedName("performed_end_offset_seconds") val performedEndOffsetSeconds: Int?,
    @SerializedName("performed_end_local_epoch_day") val performedEndLocalEpochDay: Long?,
    @SerializedName("template_id") val templateId: String?,
    @SerializedName("occurrence_id") val occurrenceId: String?,
    @SerializedName("created_at_ms") val createdAtMs: Long,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    val revision: Long,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteActivityBlockRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("template_id") val templateId: String?,
    @SerializedName("sort_order") val sortOrder: Int,
    val kind: String,
    @SerializedName("exercise_id") val exerciseId: String?,
    @SerializedName("exercise_name") val exerciseName: String?,
    @SerializedName("load_type") val loadType: String?,
    val equipment: String?,
    @SerializedName("muscles_encoded") val musclesEncoded: String?,
    @SerializedName("cardio_type") val cardioType: String?,
    val indoor: Boolean?,
    @SerializedName("elapsed_seconds") val elapsedSeconds: Long?,
    @SerializedName("moving_seconds") val movingSeconds: Long?,
    @SerializedName("distance_meters") val distanceMeters: Double?,
    @SerializedName("elevation_meters") val elevationMeters: Double?,
    @SerializedName("heart_rate_bpm") val heartRateBpm: Int?,
    @SerializedName("energy_kj") val energyKj: Double?,
    val rpe: Int?,
    @SerializedName("route_ref") val routeRef: String?,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteStrengthSetRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("block_id") val blockId: String,
    @SerializedName("set_number") val setNumber: Int,
    @SerializedName("weight_kg") val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    @SerializedName("is_warmup") val isWarmup: Boolean,
    @SerializedName("completed_at_ms") val completedAtMs: Long,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteCardioIntervalRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("block_id") val blockId: String,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("elapsed_seconds") val elapsedSeconds: Long,
    @SerializedName("distance_meters") val distanceMeters: Double?,
    val rpe: Int?,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteScheduleRuleRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val weekday: Int,
    val hour: Int,
    val minute: Int,
    val modality: String,
    @SerializedName("zone_policy") val zonePolicy: String,
    @SerializedName("fixed_zone_id") val fixedZoneId: String?,
    @SerializedName("routine_id") val routineId: String?,
    @SerializedName("template_id") val templateId: String?,
    @SerializedName("focus_kind") val focusKind: String?,
    @SerializedName("reminder_offset_minutes") val reminderOffsetMinutes: Int,
    val enabled: Int,
    @SerializedName("created_at_ms") val createdAtMs: Long,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    val revision: Long = 0L,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteRoutineRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val name: String,
    val notes: String,
    @SerializedName("created_at_ms") val createdAtMs: Long,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    val revision: Long = 0L,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteRoutineExerciseRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("routine_id") val routineId: String,
    @SerializedName("exercise_id") val exerciseId: String,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("target_sets") val targetSets: Int,
    @SerializedName("target_reps") val targetReps: Int,
    @SerializedName("target_weight_kg") val targetWeightKg: Double?,
    @SerializedName("rest_seconds") val restSeconds: Int,
    @SerializedName("target_seconds") val targetSeconds: Int?,
    @SerializedName("target_seconds_max") val targetSecondsMax: Int?,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteActivityTemplateRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val title: String,
    val notes: String,
    @SerializedName("created_at_ms") val createdAtMs: Long,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    val revision: Long = 0L,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteScheduleOccurrenceRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("rule_id") val ruleId: String,
    val status: String,
    @SerializedName("instant_ms") val instantMs: Long,
    @SerializedName("zone_id") val zoneId: String,
    @SerializedName("offset_seconds") val offsetSeconds: Int,
    @SerializedName("local_epoch_day") val localEpochDay: Long,
    val hour: Int,
    val minute: Int,
    @SerializedName("completed_activity_id") val completedActivityId: String?,
    @SerializedName("created_at_ms") val createdAtMs: Long,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    val revision: Long = 0L,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

internal fun ActivitySessionEntity.toRemote(userId: String, deletedAtMs: Long? = null): RemoteActivitySessionRow =
    RemoteActivitySessionRow(
        id = id,
        userId = userId,
        status = status,
        origin = origin,
        source = source,
        title = title,
        notes = notes,
        performedStartInstantMs = performedStartInstantMs,
        performedStartZoneId = performedStartZoneId,
        performedStartOffsetSeconds = performedStartOffsetSeconds,
        performedStartLocalEpochDay = performedStartLocalEpochDay,
        performedEndInstantMs = performedEndInstantMs,
        performedEndZoneId = performedEndZoneId,
        performedEndOffsetSeconds = performedEndOffsetSeconds,
        performedEndLocalEpochDay = performedEndLocalEpochDay,
        templateId = templateId,
        occurrenceId = occurrenceId,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        revision = revision,
        deletedAtMs = deletedAtMs,
    )

internal fun ActivityBlockEntity.toRemote(userId: String, updatedAtMs: Long, deletedAtMs: Long? = null): RemoteActivityBlockRow =
    RemoteActivityBlockRow(
        id = id,
        userId = userId,
        sessionId = sessionId,
        templateId = templateId,
        sortOrder = sortOrder,
        kind = kind,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        loadType = loadType,
        equipment = equipment,
        musclesEncoded = musclesEncoded,
        cardioType = cardioType,
        indoor = indoor,
        elapsedSeconds = elapsedSeconds,
        movingSeconds = movingSeconds,
        distanceMeters = distanceMeters,
        elevationMeters = elevationMeters,
        heartRateBpm = heartRateBpm,
        energyKj = energyKj,
        rpe = rpe,
        routeRef = routeRef,
        updatedAtMs = updatedAtMs,
        deletedAtMs = deletedAtMs,
    )

internal fun ActivityStrengthSetEntity.toRemote(
    userId: String,
    updatedAtMs: Long,
    deletedAtMs: Long? = null,
): RemoteStrengthSetRow = RemoteStrengthSetRow(
    id = id,
    userId = userId,
    blockId = blockId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isWarmup = isWarmup,
    completedAtMs = completedAtMs,
    updatedAtMs = updatedAtMs,
    deletedAtMs = deletedAtMs,
)

internal fun ActivityCardioIntervalEntity.toRemote(
    userId: String,
    updatedAtMs: Long,
    deletedAtMs: Long? = null,
): RemoteCardioIntervalRow = RemoteCardioIntervalRow(
    id = id,
    userId = userId,
    blockId = blockId,
    sortOrder = sortOrder,
    elapsedSeconds = elapsedSeconds,
    distanceMeters = distanceMeters,
    rpe = rpe,
    updatedAtMs = updatedAtMs,
    deletedAtMs = deletedAtMs,
)

internal fun ScheduleRuleEntity.toRemote(userId: String, deletedAtMs: Long? = null): RemoteScheduleRuleRow =
    RemoteScheduleRuleRow(
        id = id,
        userId = userId,
        weekday = weekday,
        hour = hour,
        minute = minute,
        modality = modality,
        zonePolicy = zonePolicy,
        fixedZoneId = fixedZoneId,
        routineId = routineId,
        templateId = templateId,
        focusKind = focusKind,
        reminderOffsetMinutes = reminderOffsetMinutes,
        enabled = enabled,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        revision = 0L,
        deletedAtMs = deletedAtMs,
    )

internal fun ScheduleOccurrenceEntity.toRemote(userId: String, deletedAtMs: Long? = null): RemoteScheduleOccurrenceRow =
    RemoteScheduleOccurrenceRow(
        id = id,
        userId = userId,
        ruleId = ruleId,
        status = status,
        instantMs = instantMs,
        zoneId = zoneId,
        offsetSeconds = offsetSeconds,
        localEpochDay = localEpochDay,
        hour = hour,
        minute = minute,
        completedActivityId = completedActivityId,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        revision = 0L,
        deletedAtMs = deletedAtMs,
    )

internal fun RemoteActivitySessionRow.toEntity(): ActivitySessionEntity = ActivitySessionEntity(
    id = id,
    status = status,
    origin = origin,
    source = source,
    title = title,
    notes = notes,
    performedStartInstantMs = performedStartInstantMs,
    performedStartZoneId = performedStartZoneId,
    performedStartOffsetSeconds = performedStartOffsetSeconds,
    performedStartLocalEpochDay = performedStartLocalEpochDay,
    performedEndInstantMs = performedEndInstantMs,
    performedEndZoneId = performedEndZoneId,
    performedEndOffsetSeconds = performedEndOffsetSeconds,
    performedEndLocalEpochDay = performedEndLocalEpochDay,
    templateId = templateId,
    occurrenceId = occurrenceId,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    revision = revision,
    liveToken = null,
)

internal fun RemoteScheduleRuleRow.toEntity(): ScheduleRuleEntity = ScheduleRuleEntity(
    id = id,
    weekday = weekday,
    hour = hour,
    minute = minute,
    modality = modality,
    zonePolicy = zonePolicy,
    fixedZoneId = fixedZoneId,
    routineId = routineId,
    templateId = templateId,
    focusKind = focusKind,
    reminderOffsetMinutes = reminderOffsetMinutes,
    enabled = enabled,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

internal fun RemoteActivityBlockRow.toEntity(): ActivityBlockEntity = ActivityBlockEntity(
    id = id,
    sessionId = sessionId,
    templateId = templateId,
    sortOrder = sortOrder,
    kind = kind,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    loadType = loadType,
    equipment = equipment,
    musclesEncoded = musclesEncoded,
    cardioType = cardioType,
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

internal fun RemoteStrengthSetRow.toEntity(): ActivityStrengthSetEntity = ActivityStrengthSetEntity(
    id = id,
    blockId = blockId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isWarmup = isWarmup,
    completedAtMs = completedAtMs,
)

internal fun RemoteCardioIntervalRow.toEntity(): ActivityCardioIntervalEntity = ActivityCardioIntervalEntity(
    id = id,
    blockId = blockId,
    sortOrder = sortOrder,
    elapsedSeconds = elapsedSeconds,
    distanceMeters = distanceMeters,
    rpe = rpe,
)

internal fun RemoteScheduleOccurrenceRow.toEntity(): ScheduleOccurrenceEntity = ScheduleOccurrenceEntity(
    id = id,
    ruleId = ruleId,
    status = status,
    instantMs = instantMs,
    zoneId = zoneId,
    offsetSeconds = offsetSeconds,
    localEpochDay = localEpochDay,
    hour = hour,
    minute = minute,
    completedActivityId = completedActivityId,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

internal fun RoutineEntity.toRemote(userId: String, deletedAtMs: Long? = null): RemoteRoutineRow =
    RemoteRoutineRow(
        id = id,
        userId = userId,
        name = name,
        notes = notes,
        createdAtMs = createdAt,
        updatedAtMs = updatedAt,
        revision = 0L,
        deletedAtMs = deletedAtMs,
    )

internal fun RoutineExerciseEntity.toRemote(
    userId: String,
    updatedAtMs: Long,
    deletedAtMs: Long? = null,
): RemoteRoutineExerciseRow = RemoteRoutineExerciseRow(
    id = id,
    userId = userId,
    routineId = routineId,
    exerciseId = exerciseId,
    sortOrder = sortOrder,
    targetSets = targetSets,
    targetReps = targetReps,
    targetWeightKg = targetWeightKg,
    restSeconds = restSeconds,
    targetSeconds = targetSeconds,
    targetSecondsMax = targetSecondsMax,
    updatedAtMs = updatedAtMs,
    deletedAtMs = deletedAtMs,
)

internal fun ActivityTemplateEntity.toRemote(userId: String, deletedAtMs: Long? = null): RemoteActivityTemplateRow =
    RemoteActivityTemplateRow(
        id = id,
        userId = userId,
        title = title,
        notes = notes,
        createdAtMs = updatedAtMs,
        updatedAtMs = updatedAtMs,
        revision = 0L,
        deletedAtMs = deletedAtMs,
    )

internal fun RemoteRoutineRow.toEntity(): RoutineEntity = RoutineEntity(
    id = id,
    name = name,
    notes = notes,
    createdAt = createdAtMs,
    updatedAt = updatedAtMs,
)

internal fun RemoteRoutineExerciseRow.toEntity(): RoutineExerciseEntity = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    sortOrder = sortOrder,
    targetSets = targetSets,
    targetReps = targetReps,
    targetWeightKg = targetWeightKg,
    restSeconds = restSeconds,
    targetSeconds = targetSeconds,
    targetSecondsMax = targetSecondsMax,
)

internal fun RemoteActivityTemplateRow.toEntity(): ActivityTemplateEntity = ActivityTemplateEntity(
    id = id,
    title = title,
    notes = notes,
    updatedAtMs = updatedAtMs,
)

data class RemoteCustomExerciseRow(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val name: String,
    @SerializedName("muscle_group") val muscleGroup: String,
    val notes: String,
    val equipment: String,
    @SerializedName("load_type") val loadType: String,
    @SerializedName("movement_key") val movementKey: String?,
    @SerializedName("image_key") val imageKey: String?,
    @SerializedName("name_key") val nameKey: String,
    @SerializedName("created_at_ms") val createdAtMs: Long,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    val revision: Long = 0L,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteExerciseMuscleRow(
    @SerializedName("exercise_id") val exerciseId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("muscle_key") val muscleKey: String,
    val weight: Double,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

data class RemoteBodyweightEntryRow(
    @SerializedName("epoch_day") val epochDay: Long,
    @SerializedName("user_id") val userId: String,
    val kg: Double,
    @SerializedName("recorded_at_ms") val recordedAtMs: Long,
    @SerializedName("zone_id") val zoneId: String,
    @SerializedName("offset_seconds") val offsetSeconds: Int,
    @SerializedName("updated_at_ms") val updatedAtMs: Long,
    @SerializedName("deleted_at_ms") val deletedAtMs: Long? = null,
)

internal fun ExerciseEntity.toCustomRemote(
    userId: String,
    createdAtMs: Long,
    deletedAtMs: Long? = null,
): RemoteCustomExerciseRow = RemoteCustomExerciseRow(
    id = id,
    userId = userId,
    name = name,
    muscleGroup = muscleGroup,
    notes = notes,
    equipment = equipment,
    loadType = loadType,
    movementKey = movementKey,
    imageKey = imageKey,
    nameKey = nameKey,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    revision = 0L,
    deletedAtMs = deletedAtMs,
)

internal fun ExerciseMuscleEntity.toRemote(
    userId: String,
    updatedAtMs: Long,
    deletedAtMs: Long? = null,
): RemoteExerciseMuscleRow = RemoteExerciseMuscleRow(
    exerciseId = exerciseId,
    userId = userId,
    muscleKey = muscleKey,
    weight = weight,
    updatedAtMs = updatedAtMs,
    deletedAtMs = deletedAtMs,
)

internal fun BodyweightEntryEntity.toRemote(
    userId: String,
    deletedAtMs: Long? = null,
): RemoteBodyweightEntryRow = RemoteBodyweightEntryRow(
    epochDay = epochDay,
    userId = userId,
    kg = kg,
    recordedAtMs = recordedAtMs,
    zoneId = zoneId,
    offsetSeconds = offsetSeconds,
    updatedAtMs = recordedAtMs,
    deletedAtMs = deletedAtMs,
)

internal fun RemoteCustomExerciseRow.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    muscleGroup = muscleGroup,
    notes = notes,
    isCustom = true,
    equipment = equipment,
    loadType = loadType,
    movementKey = movementKey,
    imageKey = imageKey,
    nameKey = nameKey,
    updatedAtMs = updatedAtMs,
)

internal fun RemoteExerciseMuscleRow.toEntity(): ExerciseMuscleEntity = ExerciseMuscleEntity(
    exerciseId = exerciseId,
    muscleKey = muscleKey,
    weight = weight,
)

internal fun RemoteBodyweightEntryRow.toEntity(): BodyweightEntryEntity = BodyweightEntryEntity(
    epochDay = epochDay,
    kg = kg,
    recordedAtMs = recordedAtMs,
    zoneId = zoneId,
    offsetSeconds = offsetSeconds,
)

fun syncExerciseMuscleEntityId(exerciseId: String, muscleKey: String): String =
    "$exerciseId|$muscleKey"

fun syncBodyweightEntityId(epochDay: Long): String = epochDay.toString()
