package com.sinura.personaltrainer.domain

/**
 * The three numbers that sit under the exercise identity on the gym floor:
 * the last set, the best set, and the volume of this exercise so far today.
 *
 * All three come from the same saved rows every other surface reads. Nothing is
 * invented: a lift with no history says so, and a lift measured in reps gets a rep
 * record rather than a kilogram one.
 */
data class FloorStat(
    val label: String,
    /** The readout. [SetCopy.NOTHING_YET] when there is nothing to show. */
    val value: String,
    /** A quieter qualifier under or beside the label: `Last time`, `Est. 1RM`. */
    val detail: String? = null,
    val spoken: String,
    /**
     * The set a tap may copy into the entry. Only the last-time fallback carries one:
     * a set from today is already the entry's own history, and a record is not a plan.
     */
    val applies: ExerciseSetRecord? = null,
)

data class ExerciseFloorStats(
    val lastSet: FloorStat,
    val bestSet: FloorStat,
    /** Working volume of this exercise today, in both measures. Format with the unit at the call site. */
    val work: SetWork,
) {
    fun volumeColumn(unit: WeightUnit): WorkColumn = SetCopy.workColumn(work, unit)
}

/**
 * Compact set lines for the stats row and the set-history chips.
 *
 * `70 × 10 @ 9` on a loaded lift, `10 reps @ 9` on a bodyweight one, `10 reps +20 @ 8`
 * with a vest. The unit is not repeated on every chip: the weight column above the
 * numbers already names it, and the row is a comparison, not a receipt.
 */
object FloorStatCopy {
    const val LAST_SET = "Last set"
    const val LAST_TIME = "Last time"
    const val BEST_SET = "Best set"
    const val VOLUME = "Volume"
    const val VOLUME_DETAIL = "this exercise"
    const val FIRST_SET = "First set"
    const val WARMUP_MARK = "Warm-up"
    /** A last-time hold reaches the floor without its seconds (see [ExerciseSetRecord]); say what it was, not `× 0`. */
    const val HOLD = "Hold"
    const val DETAIL_JOIN = " · "

    fun rpeDetail(rpe: Int): String = "RPE $rpe"

    fun compactSet(
        weightKg: Double,
        reps: Int,
        loadClass: LoadClass,
        unit: WeightUnit,
        rpe: Int? = null,
        durationSeconds: Int? = null,
    ): String {
        val held = durationSeconds?.takeIf { it > 0 && reps.coerceAtLeast(0) < 1 }
        val number = weightKg.takeIf { it.isFinite() && it > 0.0 }
            ?.let { WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(it, unit)) }
        val work = if (held != null) HoldWork.formatRange(held) else reps.coerceAtLeast(0).toString()
        val body = when (loadClass) {
            LoadClass.LOADED -> if (number != null) "$number × $work" else if (held != null) work else "$work reps"
            LoadClass.BODYWEIGHT -> if (held != null) work else "$work reps"
            LoadClass.BODYWEIGHT_ADDED -> {
                val base = if (held != null) work else "$work reps"
                if (number != null) "$base +$number" else base
            }
            LoadClass.BODYWEIGHT_ASSISTED -> {
                val base = if (held != null) work else "$work reps"
                if (number != null) "$base −$number" else base
            }
        }
        return if (rpe != null) "$body @ $rpe" else body
    }

    /** The same set, spoken with its unit so TalkBack does not lose the kilograms. */
    fun spokenSet(
        weightKg: Double,
        reps: Int,
        loadClass: LoadClass,
        unit: WeightUnit,
        rpe: Int? = null,
        durationSeconds: Int? = null,
    ): String {
        val line = SetCopy.setLine(weightKg, reps, loadClass, unit, durationSeconds)
        return if (rpe != null) "$line, RPE $rpe" else line
    }
}

object ExerciseFloorStatsCalculator {
    /**
     * @param priorHistory every finished working set of this lift from other sessions,
     * so a record can be judged against the whole log, not the last session alone.
     */
    fun of(
        session: WorkoutSession,
        exerciseId: String,
        lastPerformance: ExerciseSessionSummary?,
        priorHistory: List<ExerciseSetRecord>,
        unit: WeightUnit,
    ): ExerciseFloorStats {
        val loadClass = session.loadClassOf(exerciseId)
        val today = session.setsFor(exerciseId)
        return ExerciseFloorStats(
            lastSet = lastSet(today, lastPerformance, loadClass, unit),
            bestSet = bestSet(today, priorHistory, loadClass, unit),
            work = work(today, loadClass),
        )
    }

    private fun lastSet(
        today: List<SetLog>,
        lastPerformance: ExerciseSessionSummary?,
        loadClass: LoadClass,
        unit: WeightUnit,
    ): FloorStat {
        val latest = today.maxWithOrNull(compareBy<SetLog> { it.completedAt }.thenBy { it.setNumber })
        if (latest != null) {
            // The effort rides the detail line, so the value stays one short line in its cell.
            val value = FloorStatCopy.compactSet(
                latest.weightKg, latest.reps, loadClass, unit, null, latest.durationSeconds,
            )
            val spokenSet = FloorStatCopy.spokenSet(
                latest.weightKg, latest.reps, loadClass, unit, latest.rpe, latest.durationSeconds,
            )
            val detail = listOfNotNull(
                FloorStatCopy.WARMUP_MARK.takeIf { latest.isWarmup },
                latest.rpe?.let { FloorStatCopy.rpeDetail(it) },
            ).joinToString(FloorStatCopy.DETAIL_JOIN).ifEmpty { null }
            return FloorStat(
                label = FloorStatCopy.LAST_SET,
                value = value,
                detail = detail,
                spoken = if (latest.isWarmup) "Last set, warm-up, $spokenSet" else "Last set, $spokenSet",
            )
        }
        val previous = lastPerformance?.sets?.lastOrNull()
        if (previous != null) {
            // History rows carry no duration, so a hold arrives as reps 0: it is a hold,
            // not "no weight × 0", and it is not a set the entry can copy.
            val hold = previous.reps < 1
            val value = if (hold) FloorStatCopy.HOLD else {
                FloorStatCopy.compactSet(previous.weightKg, previous.reps, loadClass, unit)
            }
            val spoken = if (hold) "a hold" else {
                FloorStatCopy.spokenSet(previous.weightKg, previous.reps, loadClass, unit, previous.rpe)
            }
            // The cell says what it is showing. Once its qualifier sits on the label line
            // rather than under the number, `Last set · Last time` would be two names for
            // one thing; last session's set is simply Last time.
            return FloorStat(
                label = FloorStatCopy.LAST_TIME,
                value = value,
                detail = previous.rpe?.takeUnless { hold }?.let { FloorStatCopy.rpeDetail(it) },
                spoken = "Last set, last time, $spoken",
                applies = previous.takeUnless { hold },
            )
        }
        return FloorStat(
            label = FloorStatCopy.LAST_SET,
            value = FloorStatCopy.FIRST_SET,
            spoken = "Last set, none yet. This is the first set.",
        )
    }

    /**
     * The record this lift can actually hold, judged the way History's standing bests are
     * ([standingRecords]): most reps where reps are the measure, else the estimated
     * one-rep max, else the heaviest set. Today's working sets are in the running, so a
     * record broken a minute ago is the best set now.
     */
    private fun bestSet(
        today: List<SetLog>,
        priorHistory: List<ExerciseSetRecord>,
        loadClass: LoadClass,
        unit: WeightUnit,
    ): FloorStat {
        val todayRecords = today.asSequence()
            .filterNot { it.isWarmup }
            .filterNot { (it.durationSeconds ?: 0) > 0 && it.reps < 1 }
            .map { set ->
                ExerciseSetRecord(
                    setId = set.id,
                    sessionId = set.sessionId,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    completedAt = set.completedAt,
                    rpe = set.rpe,
                )
            }
            .toList()
        // History rows carry no duration, so a finished hold arrives as reps 0: not a record.
        val records = PersonalRecords.bests(priorHistory.filter { it.reps >= 1 } + todayRecords, loadClass)
        val best = records[PersonalRecordKind.REPS]
            ?: records[PersonalRecordKind.ESTIMATED_ONE_REP_MAX]
            ?: records[PersonalRecordKind.WEIGHT]
        if (best == null) {
            return FloorStat(
                label = FloorStatCopy.BEST_SET,
                value = SetCopy.NOTHING_YET,
                spoken = "Best set, none yet",
            )
        }
        val value = FloorStatCopy.compactSet(best.weightKg, best.reps, loadClass, unit)
        // "Today" means a standing record was beaten this session; with nothing to beat,
        // the kind of record is the more useful word.
        val today = priorHistory.isNotEmpty() && todayRecords.any { it.setId == best.setId }
        return FloorStat(
            label = FloorStatCopy.BEST_SET,
            value = value,
            detail = if (today) "Today" else best.kind.label,
            spoken = "Best set, ${best.kind.label.lowercase()}, " +
                FloorStatCopy.spokenSet(best.weightKg, best.reps, loadClass, unit) +
                if (today) ", set today" else "",
        )
    }

    /** Same rule as [WorkoutSession.work]: working sets only, holds are time not tonnage. */
    private fun work(today: List<SetLog>, loadClass: LoadClass): SetWork {
        var total = SetWork.NONE
        today.forEach { set ->
            if (set.isWarmup) return@forEach
            val duration = set.durationSeconds
            if (duration != null && duration > 0 && set.reps < 1) return@forEach
            total += SetWork.of(weightKg = set.weightKg, reps = set.reps, loadClass = loadClass)
        }
        return total
    }
}
