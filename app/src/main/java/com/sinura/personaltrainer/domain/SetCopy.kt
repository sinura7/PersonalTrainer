package com.sinura.personaltrainer.domain

/**
 * How a set reads back, in the terms its lift is actually measured in.
 *
 * Everything here used to be `"$weight × $reps"`, which is a sentence about a barbell. Said of
 * a push-up it produced "0 kg × 12" — the app reporting that nothing was lifted twelve times.
 */
object SetCopy {
    /**
     * One set, as a line.
     *
     * Loaded: `100 kg × 5`. Bodyweight: `12 reps`. Weighted: `8 reps +20 kg` — the sign matters
     * and is not decoration, because the same field on an assisted lift reads `8 reps −20 kg`
     * and means the opposite thing about how hard the set was.
     */
    fun setLine(
        weightKg: Double,
        reps: Int,
        loadClass: LoadClass,
        unit: WeightUnit,
        durationSeconds: Int? = null,
        entryPrecision: Boolean = false,
    ): String {
        fun Double.loadLabel(): String = if (entryPrecision) WorkoutWeightCopy.label(this, unit) else toWeightLabel(unit)
        val held = durationSeconds?.takeIf { it > 0 }
        val safeReps = reps.coerceAtLeast(0)
        if (held != null && safeReps < 1) {
            val clock = HoldWork.formatRange(held)
            val load = weightKg.takeIf { it.isFinite() && it > 0.0 }
            return when (loadClass) {
                LoadClass.LOADED -> "${load?.loadLabel() ?: NO_WEIGHT} × $clock"
                LoadClass.BODYWEIGHT -> clock
                LoadClass.BODYWEIGHT_ADDED ->
                    if (load == null) clock else "$clock +${load.loadLabel()}"
                LoadClass.BODYWEIGHT_ASSISTED ->
                    if (load == null) clock else "$clock −${load.loadLabel()}"
            }
        }
        val load = weightKg.takeIf { it.isFinite() && it > 0.0 }
        val repsLine = when (loadClass) {
            LoadClass.LOADED -> "${load?.loadLabel() ?: NO_WEIGHT} × $safeReps"
            LoadClass.BODYWEIGHT -> repsLabel(safeReps)
            LoadClass.BODYWEIGHT_ADDED ->
                if (load == null) repsLabel(safeReps)
                else "${repsLabel(safeReps)} +${load.loadLabel()}"
            LoadClass.BODYWEIGHT_ASSISTED ->
                if (load == null) repsLabel(safeReps)
                else "${repsLabel(safeReps)} −${load.loadLabel()}"
        }
        return if (held == null) repsLine else "$repsLine · ${HoldWork.formatRange(held)}"
    }

    /**
     * A session, a week, a month — however much of it there is.
     *
     * Both halves appear only when both are non-zero, so a pure barbell day still reads as one
     * clean tonnage and a pure calisthenics day as one clean rep count. A mixed day gets both,
     * which is the honest answer and the reason [SetWork] carries two numbers.
     */
    fun workLine(work: SetWork, unit: WeightUnit): String {
        val parts = buildList {
            if (work.volumeKg > 0.0) add(work.volumeKg.toVolumeLabel(unit))
            if (work.bodyweightReps > 0) add("${work.bodyweightReps} bodyweight reps")
        }
        return if (parts.isEmpty()) NOTHING_YET else parts.joinToString("  ·  ")
    }

    /**
     * The prompt under the weight field while logging.
     *
     * Bodyweight lifts get no field at all, so this is null for them rather than an empty
     * string: a labelled empty box is an invitation to put a number in it, and the number
     * someone would put there is their bodyweight, which is not what the column means.
     */
    fun weightFieldHint(loadClass: LoadClass): String? = when (loadClass.weightMeaning) {
        WeightMeaning.LIFTED -> null
        WeightMeaning.NONE -> null
        WeightMeaning.ADDED -> "Vest, belt or plate. Leave empty for bodyweight only."
        WeightMeaning.ASSISTANCE -> "How much the machine took off. More assist is an easier set."
    }

    /**
     * TalkBack for the weight well. 0 is a chosen value, not a missing 5 lb plate.
     */
    fun weightWellSpoken(
        meaning: WeightMeaning,
        weightKg: Double,
        unit: WeightUnit,
        entryPrecision: Boolean = false,
    ): String {
        if (!weightKg.isFinite() || weightKg <= 0.0) {
            return when (meaning) {
                WeightMeaning.ASSISTANCE -> "${meaning.fieldLabel}, no assistance"
                WeightMeaning.LIFTED -> "${meaning.fieldLabel}, $NO_WEIGHT"
                WeightMeaning.ADDED, WeightMeaning.NONE -> "${meaning.fieldLabel}, $NO_WEIGHT, $BODYWEIGHT_LOAD"
            }
        }
        val shown = if (entryPrecision) WorkoutWeightCopy.number(weightKg, unit) else WeightConverter.formatDisplayNumber(
            WeightConverter.toDisplayValue(weightKg, unit),
        )
        return "${meaning.fieldLabel} $shown ${unit.suffix}"
    }

    fun weightKeypadHelper(loadClass: LoadClass, allowsZero: Boolean): String {
        val base = weightFieldHint(loadClass)
            ?: "No negatives; up to two decimals. 87.5 or 87,5."
        if (!allowsZero) return base
        val zero = when (loadClass.weightMeaning) {
            WeightMeaning.LIFTED -> "0 means no external load."
            WeightMeaning.ASSISTANCE -> "0 means no assistance."
            WeightMeaning.ADDED, WeightMeaning.NONE -> "0 is $NO_WEIGHT ($BODYWEIGHT_LOAD)."
        }
        return "$zero $base"
    }

    /**
     * The middle column of a session row, which is a fixed-width slot that has to hold one
     * number and one label.
     *
     * Kilograms when the session moved any, its rep count when it did not. A calisthenics
     * session would otherwise sit in a column of barbell sessions reading "0 kg", which is the
     * old stand-in's failure inverted: instead of inventing work that did not happen, erasing
     * work that did. A mixed session shows kilograms, and its reps are on the session itself —
     * the column is a comparison across rows, not a full account of one.
     */
    fun workColumn(work: SetWork, unit: WeightUnit): WorkColumn = when {
        work.volumeKg > 0.0 -> WorkColumn(
            value = WeightConverter.formatVolumeNumber(work.volumeKg, unit),
            label = unit.suffix,
        )
        work.bodyweightReps > 0 -> WorkColumn(
            value = work.bodyweightReps.toString(),
            label = "reps",
        )
        else -> WorkColumn(value = NOTHING_YET, label = unit.suffix)
    }

    /**
     * How bodyweight moved, as a line. Null when it did not move enough to be a trend.
     *
     * Signed, because the direction is the whole content: the same four kilos is the point of a
     * bulk and the failure of a cut, and the app has no business deciding which one this was.
     */
    fun bodyweightLine(change: BodyweightChange?, unit: WeightUnit): String? {
        if (change == null) return null
        val from = change.fromKg.toWeightLabel(unit)
        val to = change.toKg.toWeightLabel(unit)
        if (change.isFlat) return "$from → $to · held"
        val delta = kotlin.math.abs(change.deltaKg).toWeightLabel(unit)
        val sign = if (change.deltaKg > 0) "+" else "−"
        return "$from → $to · $sign$delta"
    }

    /**
     * The quiet line under a set in [the one set table] — number, then RPE
     * when it was logged. Warm-up is a mark on the row, not a second word
     * here, so the workout log and a finished session say the same thing.
     */
    fun tableExtras(setNumber: Int, rpe: Int?): String =
        tableExtras(ordinal = "Set $setNumber", rpe = rpe)

    /**
     * Floor rows pass a derived ordinal (`WU 1`, `Set 2 of 4`, `Extra 1`).
     * History still names the stored number through [tableExtras] `(setNumber)`.
     */
    fun tableExtras(ordinal: String, rpe: Int?): String = buildList {
        add(ordinal)
        rpe?.let { add("RPE $it") }
    }.joinToString(" · ")

    private fun repsLabel(reps: Int): String = if (reps == 1) "1 rep" else "$reps reps"

    const val NOTHING_YET = "—"
    const val NO_WEIGHT = "no weight"
    const val BODYWEIGHT_LOAD = "bodyweight"
}

/** One number and its unit, for a fixed-width readout. */
data class WorkColumn(val value: String, val label: String)
