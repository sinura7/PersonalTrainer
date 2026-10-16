package com.sinura.personaltrainer.domain

/**
 * The frozen "Use this plan" command for one setup visit.
 *
 * Written before the block, the weigh-in, and the days. A retry reads this
 * instead of today's date or a blueprint that changed while the app was dead.
 */
data class PlanAcceptReceipt(
    val id: String,
    val epochDay: Long,
    val bodyweightKg: Double?,
    val blockStartEpochDay: Long,
    val pins: List<Pair<Weekday, String>>,
    val routines: List<FrozenRoutine>,
    val complete: Boolean,
) {
    fun encode(): String {
        val weight = bodyweightKg?.toString().orEmpty()
        val days = pins.joinToString(",") { (day, key) -> "${day.name}=$key" }
        val done = if (complete) "1" else "0"
        val sessions = routines.joinToString("&") { it.encode() }
        return listOf(id, epochDay.toString(), weight, blockStartEpochDay.toString(), done, days, sessions)
            .joinToString("|")
    }

    companion object {
        fun decode(raw: String?): PlanAcceptReceipt? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split("|", limit = 7)
            if (parts.size < 6 || parts[0].isBlank()) return null
            val epochDay = parts[1].toLongOrNull() ?: return null
            val blockStart = parts[3].toLongOrNull() ?: return null
            val pins = parts[5].split(",").mapNotNull { token ->
                if (token.isBlank()) return@mapNotNull null
                val bits = token.split("=", limit = 2)
                if (bits.size < 2 || bits[1].isBlank()) return@mapNotNull null
                val day = Weekday.entries.firstOrNull { it.name == bits[0] } ?: return@mapNotNull null
                day to bits[1]
            }
            val routines = parts.getOrNull(6)?.let { FrozenRoutine.decodeAll(it) }.orEmpty()
            return PlanAcceptReceipt(
                id = parts[0],
                epochDay = epochDay,
                bodyweightKg = parts[2].toDoubleOrNull(),
                blockStartEpochDay = blockStart,
                pins = pins,
                routines = routines,
                complete = parts[4] == "1",
            )
        }
    }
}

/** Enough of a generated routine to rebuild it if the preview changed before the retry. */
data class FrozenRoutine(
    val key: String,
    val name: String,
    val lifts: List<FrozenLift>,
) {
    fun encode(): String {
        val lifts = lifts.joinToString(";") { "${it.exerciseId}:${it.sets}:${it.reps}:${it.restSeconds}" }
        return "$key^$name^$lifts"
    }

    fun toBlueprint(): BlueprintRoutine = BlueprintRoutine(
        key = key,
        name = name,
        focusKind = SessionFocusKind.FULL_BODY,
        lifts = lifts.map { lift ->
            BlueprintLift(
                exerciseId = lift.exerciseId,
                name = lift.exerciseId,
                equipment = EquipmentType.OTHER,
                targets = TargetDefaults(
                    sets = lift.sets,
                    reps = lift.reps,
                    restSeconds = lift.restSeconds,
                ),
            )
        },
    )

    companion object {
        fun decodeAll(raw: String): List<FrozenRoutine> = raw.split("&").mapNotNull { token ->
            if (token.isBlank()) return@mapNotNull null
            val bits = token.split("^", limit = 3)
            if (bits.size < 3 || bits[0].isBlank()) return@mapNotNull null
            val lifts = bits[2].split(";").mapNotNull { lift ->
                if (lift.isBlank()) return@mapNotNull null
                val fields = lift.split(":")
                if (fields.size < 4) return@mapNotNull null
                FrozenLift(
                    exerciseId = fields[0],
                    sets = fields[1].toIntOrNull() ?: return@mapNotNull null,
                    reps = fields[2].toIntOrNull() ?: return@mapNotNull null,
                    restSeconds = fields[3].toIntOrNull() ?: return@mapNotNull null,
                )
            }
            FrozenRoutine(key = bits[0], name = bits[1], lifts = lifts)
        }
    }
}

data class FrozenLift(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
)
