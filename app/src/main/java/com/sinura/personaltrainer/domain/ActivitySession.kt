package com.sinura.personaltrainer.domain

/**
 * The durable fitness record (ADR-007).
 *
 * One envelope holds strength-only, cardio-only, or mixed typed blocks.
 * Cardio is never a fake catalog exercise. A cardio-only day needs zero
 * [StrengthSet] rows.
 */
data class ActivitySession(
    val id: String,
    val status: ActivityStatus,
    val origin: ActivityOrigin,
    val source: ActivitySource,
    val title: String,
    val notes: String,
    val performedStart: CapturedCivilTime,
    val performedEnd: CapturedCivilTime?,
    val templateId: String?,
    val occurrenceId: String?,
    val blocks: List<ActivityBlock>,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val revision: Long,
) {
    val localEpochDay: Long get() = performedStart.localEpochDay

    val strengthBlocks: List<StrengthBlock> get() = blocks.filterIsInstance<StrengthBlock>()
    val cardioBlocks: List<CardioBlock> get() = blocks.filterIsInstance<CardioBlock>()

    val isLive: Boolean get() = status == ActivityStatus.ACTIVE && origin == ActivityOrigin.LIVE
    val isCompleted: Boolean get() = status == ActivityStatus.COMPLETED
    val isStrengthOnly: Boolean get() = strengthBlocks.isNotEmpty() && cardioBlocks.isEmpty()
    val isCardioOnly: Boolean get() = cardioBlocks.isNotEmpty() && strengthBlocks.isEmpty()
    val isMixed: Boolean get() = strengthBlocks.isNotEmpty() && cardioBlocks.isNotEmpty()

    fun strengthSetCount(): Int = strengthBlocks.sumOf { it.sets.size }
}

enum class ActivityStatus { ACTIVE, COMPLETED }

enum class ActivityOrigin { LIVE, BACKDATED, IMPORTED }

enum class ActivitySource { TEMPER }

sealed class ActivityBlock {
    abstract val id: String
    abstract val sortOrder: Int
}

data class StrengthBlock(
    override val id: String,
    override val sortOrder: Int,
    val exerciseId: String,
    val exerciseName: String,
    val loadType: LoadType,
    val equipment: EquipmentType,
    val muscles: List<MuscleCredit>,
    val sets: List<StrengthSet>,
) : ActivityBlock()

data class StrengthSet(
    val id: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
    val completedAtMs: Long,
)

enum class CardioType {
    RUN,
    RIDE,
    ROW,
    SWIM,
    WALK,
    HIKE,
    SKI,
    OTHER,
}

data class CardioBlock(
    override val id: String,
    override val sortOrder: Int,
    val type: CardioType,
    val indoor: Boolean,
    val elapsedSeconds: Long,
    val movingSeconds: Long?,
    val distanceMeters: Double?,
    val elevationMeters: Double?,
    val heartRateBpm: Int?,
    val energyKj: Double?,
    val rpe: Int?,
    val routeRef: String?,
    val intervals: List<CardioInterval> = emptyList(),
) : ActivityBlock()

data class CardioInterval(
    val id: String,
    val sortOrder: Int,
    val elapsedSeconds: Long,
    val distanceMeters: Double?,
    val rpe: Int?,
)

/**
 * A session template. Editing one never rewrites history: a session may
 * snapshot a template id, but the logged blocks are their own rows.
 */
data class ActivityTemplate(
    val id: String,
    val title: String,
    val notes: String,
    val blocks: List<ActivityBlock>,
)

/**
 * Derived pace (seconds per metre) and speed (metres per second).
 *
 * Independently persisted pace/speed that contradict distance/time are
 * rejected at the authoring edge. These numbers are never stored.
 */
data class DerivedCardioRate(
    val secondsPerMeter: Double?,
    val metersPerSecond: Double?,
)
