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
    fun setLine(weightKg: Double, reps: Int, loadClass: LoadClass, unit: WeightUnit): String {
        val safeReps = reps.coerceAtLeast(0)
        val load = weightKg.takeIf { it.isFinite() && it > 0.0 }
        return when (loadClass) {
            LoadClass.LOADED -> "${(load ?: 0.0).toWeightLabel(unit)} × $safeReps"
            LoadClass.BODYWEIGHT -> repsLabel(safeReps)
            LoadClass.BODYWEIGHT_ADDED ->
                if (load == null) repsLabel(safeReps)
                else "${repsLabel(safeReps)} +${load.toWeightLabel(unit)}"
            LoadClass.BODYWEIGHT_ASSISTED ->
                if (load == null) repsLabel(safeReps)
                else "${repsLabel(safeReps)} −${load.toWeightLabel(unit)}"
        }
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
            value = WeightConverter.formatGroupedNumber(WeightConverter.toDisplayValue(work.volumeKg, unit)),
            label = unit.suffix,
        )
        work.bodyweightReps > 0 -> WorkColumn(
            value = work.bodyweightReps.toString(),
            label = "reps",
        )
        else -> WorkColumn(value = NOTHING_YET, label = unit.suffix)
    }

    private fun repsLabel(reps: Int): String = if (reps == 1) "1 rep" else "$reps reps"

    const val NOTHING_YET = "—"
}

/** One number and its unit, for a fixed-width readout. */
data class WorkColumn(val value: String, val label: String)
