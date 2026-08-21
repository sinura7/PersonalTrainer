package com.sinura.personaltrainer.domain

/**
 * What you weighed, and when.
 *
 * The guided setup asks for a bodyweight and, until this existed, nothing read it. Once
 * bodyweight lifts became their own class and reps became their measure, no calculation needed
 * a figure for the body doing the lifting — so the question collected a number, stored it,
 * carried it through every backup, and fed nothing. A question that changes nothing is the
 * clearest form of the thing this app keeps trying not to be.
 *
 * The job it is actually for is this: over a block, "did I get stronger" has a companion
 * question, and the companion question is "at what weight". Twelve weeks of added reps means
 * one thing at a steady bodyweight and something else entirely at plus four kilos. The block
 * review is the one place in the app where both halves can be read at once.
 *
 * A series, not a value, because a single number cannot answer it — and stored the way
 * [BlockArchive] is, for the same reasons: the shape is a pair of numbers, the volume is
 * bounded, and a schema change is the one category of mistake that cannot be undone on
 * someone's phone.
 */
data class BodyweightEntry(
    val epochDay: Long,
    val kg: Double,
)

object BodyweightLog {
    /** About four years of weekly weigh-ins. Past that the oldest entries stop being context. */
    const val MAX_ENTRIES = 200
    private const val RECORD = ","
    private const val FIELD = ":"

    fun encode(entries: List<BodyweightEntry>): String = entries
        .sortedBy { it.epochDay }
        .takeLast(MAX_ENTRIES)
        .joinToString(RECORD) { "${it.epochDay}$FIELD${WeightConverter.formatDisplayNumber(it.kg)}" }

    /** Tolerant: a malformed entry is dropped. A bad log must not cost the app its history. */
    fun decode(raw: String?): List<BodyweightEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD)
            if (parts.size != 2) return@mapNotNull null
            val day = parts[0].trim().toLongOrNull() ?: return@mapNotNull null
            val kg = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            if (!kg.isFinite() || kg < OnboardingAnswers.MIN_BODYWEIGHT_KG ||
                kg > OnboardingAnswers.MAX_BODYWEIGHT_KG
            ) {
                return@mapNotNull null
            }
            BodyweightEntry(epochDay = day, kg = kg)
        }
            .sortedBy { it.epochDay }
            .distinctBy { it.epochDay }
    }

    /**
     * Record a weigh-in, replacing any earlier one from the same day.
     *
     * One entry per day on purpose. Bodyweight swings a kilo or two between morning and evening
     * on water alone, so several readings from one day are noise dressed as a trend — and the
     * last one typed is the one the owner meant.
     */
    fun record(existing: List<BodyweightEntry>, entry: BodyweightEntry): List<BodyweightEntry> =
        (existing.filterNot { it.epochDay == entry.epochDay } + entry)
            .sortedBy { it.epochDay }
            .takeLast(MAX_ENTRIES)

    fun latest(entries: List<BodyweightEntry>): BodyweightEntry? = entries.maxByOrNull { it.epochDay }

    /**
     * The weigh-in that best describes [epochDay].
     *
     * The most recent one on or before it, because a weight is true until it is measured again.
     * Falls forward to the earliest entry when the date predates every weigh-in: a block that
     * started before anyone stepped on a scale is better described by the first reading than by
     * nothing at all, and saying so is what makes the block review's opening figure honest.
     */
    fun nearest(entries: List<BodyweightEntry>, epochDay: Long): BodyweightEntry? =
        entries.filter { it.epochDay <= epochDay }.maxByOrNull { it.epochDay }
            ?: entries.minByOrNull { it.epochDay }
}

/** How bodyweight moved across a span, when there is enough logged to say. */
data class BodyweightChange(
    val fromKg: Double,
    val toKg: Double,
) {
    val deltaKg: Double get() = toKg - fromKg

    /** True when the two readings are the same day's, so the span says nothing about a trend. */
    val isFlat: Boolean get() = kotlin.math.abs(deltaKg) < FLAT_KG

    companion object {
        /** Below this it is water, not a trend. */
        const val FLAT_KG = 0.5
    }
}
