package com.sinura.personaltrainer.domain

/** Which history a lift reads and writes at a gym (D1, D2). */
sealed interface HistoryScope {
    /** Free weights: every session, any gym, including Unassigned. */
    data object Shared : HistoryScope

    /** Machines: only sessions whose gymId IS this value. Null means Unassigned. */
    data class AtGym(val gymId: String?) : HistoryScope
}

object GymScope {
    val GYM_SPECIFIC: Set<EquipmentType> = setOf(
        EquipmentType.MACHINE,
        EquipmentType.CABLE,
        EquipmentType.SMITH,
        EquipmentType.HYPER_PRO,
    )

    fun isGymSpecific(equipment: EquipmentType): Boolean = equipment in GYM_SPECIFIC

    fun of(equipment: EquipmentType, sessionGymId: String?): HistoryScope =
        if (isGymSpecific(equipment)) HistoryScope.AtGym(sessionGymId) else HistoryScope.Shared

    /**
     * Bound parameters for aggregate SQL scope clauses. Use `ws.gymId IS :scopeGymId`, never `=`.
     * [scopeShared] true skips the gym filter; false applies [scopeGymId] with SQLite `IS`.
     */
    fun HistoryScope.sqlArgs(): Pair<Boolean, String?> = when (this) {
        HistoryScope.Shared -> true to null
        is HistoryScope.AtGym -> false to gymId
    }

    /** Routine kg for free weights; null for gym-specific lifts unless repeat at the same gym. */
    fun targetWeightFor(
        equipment: EquipmentType,
        routineKg: Double?,
        sameGymAsSource: Boolean,
        sourceKg: Double?,
    ): Double? = when {
        !isGymSpecific(equipment) -> routineKg
        sameGymAsSource -> sourceKg
        else -> null
    }

    /**
     * Record standing-bests bucket: machines per gym (null = Unassigned), free weights overall.
     */
    fun recordBucket(equipment: EquipmentType?, sessionGymId: String?): String? =
        if (equipment != null && isGymSpecific(equipment)) sessionGymId else RECORD_BUCKET_SHARED

    fun recordKey(exerciseId: String, equipment: EquipmentType?, sessionGymId: String?): RecordKey =
        RecordKey(exerciseId, recordBucket(equipment, sessionGymId))

    fun stallScopeKey(sessionGymId: String?): String =
        sessionGymId ?: STALL_SCOPE_UNASSIGNED

    const val RECORD_BUCKET_SHARED = "shared"
    private const val STALL_SCOPE_UNASSIGNED = "unassigned"
}

data class RecordKey(
    val exerciseId: String,
    val bucket: String?,
)

data class OtherGymHint(
    val gymShortName: String,
    val weightKg: Double,
    val reps: Int,
    val dateMs: Long,
)
