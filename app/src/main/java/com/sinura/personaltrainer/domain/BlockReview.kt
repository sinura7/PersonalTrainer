package com.sinura.personaltrainer.domain

/**
 * What twelve weeks actually came to.
 *
 * The whole reason a block has an end. Without this the completed state is a label and a
 * button — the app noticing a date passed and asking whether you would like another one — and
 * a lifter who trained hard for three months gets no more acknowledgement than one who did
 * nothing. The numbers all already exist; nothing here is a new measurement, only the first
 * time the app adds them up over a span it chose.
 *
 * Deliberately about what MOVED rather than what is biggest. "Most volume" would name whatever
 * lift happens to be a squat; "moved most" names the lift you actually got better at, which is
 * the question twelve weeks was asked to answer.
 */
data class BlockReview(
    val weeks: Int,
    val sessions: Int,
    val workingSets: Int,
    val work: SetWork,
    /** Distinct days trained, not sessions: two workouts in one day is one day of training. */
    val daysTrained: Int,
    val recordsBroken: Int,
    /** The lifts that improved most, best first. Empty when nothing had two comparable points. */
    val movers: List<BlockMover>,
    /**
     * How bodyweight moved across the block, when enough was logged to say.
     *
     * The companion question to every other number here. Twelve weeks of added reps means one
     * thing at a steady bodyweight and something else entirely at plus four kilos, and this is
     * the only place in the app where both halves can be read at once. Null when there are
     * fewer than two weigh-ins, because one reading is a weight, not a direction.
     */
    val bodyweight: BodyweightChange? = null,
) {
    val isEmpty: Boolean get() = sessions == 0
}

/**
 * Horizon readout: how many records this window broke, and which lift moved most.
 *
 * Not a second [BlockReview]. Weeks, bodyweight, and session totals already live
 * on [HorizonTotals]; this is the progress sentence those counts never said.
 */
data class HorizonProgress(
    val recordsBroken: Int,
    val movers: List<BlockMover>,
) {
    /** Best mover, or null when the range is too short or nothing had two comparable points. */
    val movedMost: BlockMover? get() = movers.firstOrNull()
}

/**
 * One lift's progress across the block, in whichever unit that lift is measured in.
 *
 * [fromLabel] and [toLabel] are pre-rendered rather than raw numbers because a loaded lift's
 * pair reads "100 kg → 110 kg" and a bodyweight lift's reads "8 reps → 15 reps", and the
 * caller that knows the unit is the only one that can say which.
 */
data class BlockMover(
    val exerciseId: String,
    val exerciseName: String,
    val fromLabel: String,
    val toLabel: String,
    /** Fractional improvement, for ordering. 0.25 is a quarter better than where it started. */
    val gain: Double,
)

object BlockReviewBuilder {
    /** Below this a "gain" is noise — a rounding difference, or one better rep on one day. */
    const val MIN_GAIN = 0.01
    const val MOVERS_SHOWN = 3
    private const val DAYS_IN_WEEK = 7L

    /**
     * How many weeks at each end of the block a lift is judged on.
     *
     * Not the first session against the last. One session is one day, and one bad day at either
     * end decides the whole answer — a lifter who was ill in week one gets a flattering result,
     * and one who was ill in week twelve gets told they went backwards. A fortnight at each end
     * is two to four sessions of most lifts, which is enough for the worst day not to be the
     * only day. Shorter blocks narrow it rather than letting the windows meet in the middle.
     */
    fun comparisonWeeks(blockWeeks: Int): Int = maxOf(1, minOf(2, blockWeeks / 4))

    /**
     * @param items every finished piece of training; those outside the block are ignored
     * rather than trusted to have been filtered, because a caller that forgets turns a
     * block review into a career one. Strength sessions and completed activities both
     * count; cardio-only days add a session and a trained day, not PRs.
     * @param unit only for rendering the movers' labels.
     */
    fun build(
        block: TrainingBlock,
        items: List<CompletedTraining>,
        unit: WeightUnit,
        bodyweightLog: List<BodyweightEntry> = emptyList(),
    ): BlockReview {
        val inBlock = items.filter { item ->
            item.localEpochDay >= block.startEpochDay &&
                item.localEpochDay < block.endExclusiveEpochDay
        }
        // What [countRecords] compares each in-block set against. Everything the caller handed
        // over that finished before day one — the same list, differently filtered, so no extra
        // query and no chance of the two halves coming from different reads.
        val beforeBlock = items.filter { item -> item.localEpochDay < block.startEpochDay }
        return BlockReview(
            weeks = block.weeks,
            sessions = inBlock.size,
            workingSets = inBlock.sumOf { it.strength.size },
            work = SetWork.sum(inBlock.map { it.strengthWork() }),
            daysTrained = inBlock.map { it.localEpochDay }.distinct().size,
            recordsBroken = countRecords(inBlock = inBlock, beforeBlock = beforeBlock),
            movers = movers(
                startEpochDay = block.startEpochDay,
                endExclusiveEpochDay = block.endExclusiveEpochDay,
                inBlock = inBlock,
                unit = unit,
            ),
            bodyweight = bodyweightChange(block, bodyweightLog),
        )
    }

    /**
     * Bodyweight at the block's start against its end.
     *
     * Two distinct weigh-ins or nothing: one reading is a weight, not a direction, and showing
     * "78 kg → 78 kg" because the same entry answered both ends would be the app inventing a
     * result out of a single measurement.
     */
    private fun bodyweightChange(
        block: TrainingBlock,
        log: List<BodyweightEntry>,
    ): BodyweightChange? {
        if (log.size < 2) return null
        val opening = BodyweightLog.nearest(log, block.startEpochDay) ?: return null
        val closing = BodyweightLog.nearest(log, block.endExclusiveEpochDay - 1) ?: return null
        if (opening.epochDay == closing.epochDay) return null
        return BodyweightChange(fromKg = opening.kg, toKg = closing.kg)
    }

    /**
     * Records broken *within the block*, judged against everything logged before each set.
     *
     * Prior history includes sessions from before the block: a personal best set in week one is
     * only a best if it beat what came before, and starting the comparison at the block's first
     * day would hand a returning lifter a record for every lift they touched.
     *
     * @param beforeBlock every finished piece of training earlier than the block's first day, in
     * any order. Only its working sets are read.
     */
    private fun countRecords(
        inBlock: List<CompletedTraining>,
        beforeBlock: List<CompletedTraining>,
    ): Int = RecordsCalculator.countBrokenTraining(
        inBlock = inBlock,
        beforeBlock = beforeBlock,
    )

    /**
     * Records and movers over an inclusive-start, exclusive-end civil range.
     *
     * History's horizon readout reuses the block review's measure so "moved most"
     * means the same thing at four weeks as it does at twelve. Callers may pass
     * items from before [startEpochDay]; those seed record detection and are
     * ignored for movers.
     */
    fun overRange(
        startEpochDay: Long,
        endExclusiveEpochDay: Long,
        items: List<CompletedTraining>,
        unit: WeightUnit,
    ): HorizonProgress {
        val inRange = items.filter { item ->
            item.localEpochDay >= startEpochDay &&
                item.localEpochDay < endExclusiveEpochDay
        }
        val before = items.filter { item -> item.localEpochDay < startEpochDay }
        return HorizonProgress(
            recordsBroken = countRecords(inBlock = inRange, beforeBlock = before),
            movers = movers(
                startEpochDay = startEpochDay,
                endExclusiveEpochDay = endExclusiveEpochDay,
                inBlock = inRange,
                unit = unit,
            ),
        )
    }

    /**
     * Days at each end of a span used to judge a lift.
     *
     * Same rule as [comparisonWeeks], expressed in days so a week-long History
     * horizon can still name a direction: one to fourteen days at each end,
     * never more than half the span, never overlapping.
     */
    fun comparisonDays(spanDays: Long): Long {
        if (spanDays < 2L) return 0L
        val weeks = (spanDays / DAYS_IN_WEEK).toInt().coerceAtLeast(1)
        return minOf(comparisonWeeks(weeks) * DAYS_IN_WEEK, spanDays / 2)
    }

    /**
     * The lifts that improved most, judged on a window at each end of the block.
     *
     * A lift has to appear in both windows to say anything: a lift trained only in week one is
     * a position, not a direction, and one picked up in week eleven has nothing to be compared
     * against. The measure is the lift's own — an estimated max for loaded work, the best rep
     * count for bodyweight — so a pull-up going eight to fifteen is a mover on the same list as
     * a squat going 100 to 120.
     */
    private fun movers(
        startEpochDay: Long,
        endExclusiveEpochDay: Long,
        inBlock: List<CompletedTraining>,
        unit: WeightUnit,
    ): List<BlockMover> {
        val window = comparisonDays(endExclusiveEpochDay - startEpochDay)
        if (window <= 0L) return emptyList()
        val openingEnds = startEpochDay + window
        val closingBegins = endExclusiveEpochDay - window

        val byExercise = inBlock.attempts().groupBy { it.exerciseId }

        return byExercise.mapNotNull { (exerciseId, pairs) ->
            val loadClass = pairs.first().loadClass
            val opening = pairs.filter { it.localEpochDay < openingEnds }
            val closing = pairs.filter { it.localEpochDay >= closingBegins }
            if (opening.isEmpty() || closing.isEmpty()) return@mapNotNull null

            val from = bestOf(opening, loadClass) ?: return@mapNotNull null
            val to = bestOf(closing, loadClass) ?: return@mapNotNull null
            if (from <= 0.0) return@mapNotNull null

            val gain = (to - from) / from
            if (gain < MIN_GAIN) return@mapNotNull null
            BlockMover(
                exerciseId = exerciseId,
                exerciseName = pairs.first().exerciseName,
                fromLabel = label(from, loadClass, unit),
                toLabel = label(to, loadClass, unit),
                gain = gain,
            )
        }
            .sortedWith(compareByDescending<BlockMover> { it.gain }.thenBy { it.exerciseName })
            .take(MOVERS_SHOWN)
    }

    /**
     * One session's best showing of a lift, in that lift's own unit.
     *
     * Reps for anything measured in reps; the top working set's estimated max for loaded work,
     * falling back to raw weight when the set is too long to estimate from. An estimate rather
     * than the bar weight because five at 100 and three at 105 are not ordered by the number on
     * the bar, and a block review that called the second one a regression would be wrong.
     */
    private fun bestOf(attempts: List<ReviewAttempt>, loadClass: LoadClass): Double? {
        if (attempts.isEmpty()) return null
        if (loadClass.repsAreTheMeasure) return attempts.maxOf { it.set.reps }.toDouble()
        val estimate = attempts.mapNotNull {
            PersonalRecords.estimatedOneRepMaxKg(it.set.weightKg, it.set.reps)
        }
            .maxOrNull()
        return estimate ?: attempts.maxOf { it.set.weightKg }.takeIf { it > 0.0 }
    }

    private fun label(value: Double, loadClass: LoadClass, unit: WeightUnit): String =
        if (loadClass.repsAreTheMeasure) {
            val reps = value.toInt()
            if (reps == 1) "1 rep" else "$reps reps"
        } else {
            value.toWeightLabel(unit)
        }

    private fun List<CompletedTraining>.attempts(): List<ReviewAttempt> =
        flatMap { item ->
            item.strength.map { row ->
                ReviewAttempt(
                    exerciseId = row.exerciseId,
                    exerciseName = row.exerciseName,
                    loadClass = row.loadClass,
                    set = row.set,
                    localEpochDay = item.localEpochDay,
                )
            }
        }

    private data class ReviewAttempt(
        val exerciseId: String,
        val exerciseName: String,
        val loadClass: LoadClass,
        val set: ExerciseSetRecord,
        val localEpochDay: Long,
    )
}
