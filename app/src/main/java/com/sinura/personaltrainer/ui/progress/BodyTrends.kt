package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.data.local.dao.WorkingSetCountRow
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.PrSummaryRow
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.QuietButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.heatColor
import com.sinura.personaltrainer.ui.components.LineTrend
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.PersonalRecords
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.units.LocalClockFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * How a card's read stands. A card never says "nothing yet" for a read that failed: FAILED
 * shows Retry, STALE keeps the last value with a marker.
 */
enum class CardRead { LOADING, OK, STALE, FAILED }

/** v4 V7: the three Body cards below the muscles, each with its own read health. */
data class BodyTrends(
    val bodyweight: List<BodyweightEntry> = emptyList(),
    val bodyweightRead: CardRead = CardRead.LOADING,
    val records: List<PrSummaryRow> = emptyList(),
    val recordsRead: CardRead = CardRead.LOADING,
    val weeklySets: List<MuscleWeeks> = emptyList(),
    val volumeRead: CardRead = CardRead.LOADING,
    /** Monday of the oldest of the [WEEKS] ISO weeks shown. */
    val firstWeekStartEpochDay: Long = 0L,
)

/** Working sets for one muscle in each of the last [WEEKS] ISO weeks, oldest first. */
data class MuscleWeeks(val muscle: CanonicalMuscle, val sets: List<Int>) {
    val total: Int get() = sets.sum()
    val thisWeek: Int get() = sets.lastOrNull() ?: 0
}

internal const val WEEKS = 8
internal const val TOP_RECORDS = 5
internal const val BODYWEIGHT_POINTS = 12

object BodyTrendsMath {
    /** Monday of the ISO week [WEEKS] - 1 weeks before the one holding [today]. */
    fun firstWeekStart(todayEpochDay: Long): Long =
        LocalDate.ofEpochDay(todayEpochDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks((WEEKS - 1).toLong()).toEpochDay()

    /** The last [BODYWEIGHT_POINTS] entries, oldest first. Not a date window: a sparse log still draws. */
    fun recentBodyweight(entries: List<BodyweightEntry>): List<BodyweightEntry> =
        entries.sortedBy { it.epochDay }.takeLast(BODYWEIGHT_POINTS)

    /**
     * The muscle a lift's working sets are counted for: its primary (weight 1.0) in the
     * exercise_muscles junction, else its muscleGroup text for a lift without credits.
     */
    fun primaryMuscle(exercise: Exercise): CanonicalMuscle =
        exercise.muscles.firstOrNull { it.weight >= 1.0 }?.let { MuscleNormalizer.resolveKey(it.muscleKey) }
            ?: MuscleNormalizer.primaryOf(exercise.muscleGroup)

    /**
     * D12: working sets only (the query already drops warm-ups), credited to the lift's primary
     * muscle, bucketed by the ISO week its session finished in (local day). Muscles with none are
     * left out. Sorted by this week, then the 8 week total, then body map order.
     */
    fun weeklySets(
        rows: List<WorkingSetCountRow>,
        catalog: List<Exercise>,
        firstWeekStartEpochDay: Long,
        localEpochDayOf: (Long) -> Long,
    ): List<MuscleWeeks> {
        val muscleOf = catalog.associate { it.id to primaryMuscle(it) }
        val buckets = mutableMapOf<CanonicalMuscle, IntArray>()
        val start = firstWeekStartEpochDay
        rows.forEach { row ->
            val muscle = muscleOf[row.exerciseId]?.takeIf { it != CanonicalMuscle.OTHER } ?: return@forEach
            val day = localEpochDayOf(row.finishedAt)
            if (day < start) return@forEach
            val week = ((day - start) / 7).toInt()
            if (week !in 0 until WEEKS) return@forEach
            buckets.getOrPut(muscle) { IntArray(WEEKS) }[week] += row.workingSets
        }
        return CanonicalMuscle.bodyMapOrder
            .mapNotNull { m -> buckets[m]?.let { MuscleWeeks(m, it.toList()) } }
            .sortedWith(compareByDescending<MuscleWeeks> { it.thisWeek }.thenByDescending { it.total })
    }
}

object BodyTrendTags {
    const val BODYWEIGHT = "body-trend-bodyweight"
    const val RECORDS = "body-trend-records"
    const val ALL_RECORDS = "body-trend-all-records"
    const val VOLUME = "body-trend-volume"
    const val LOG_WEIGHT = "body-trend-log-weight"
    fun failed(card: String) = "$card-failed"
    fun stale(card: String) = "$card-stale"
    fun retry(card: String) = "$card-retry"
    fun record(exerciseId: String) = "body-trend-record-$exerciseId"
    fun volume(muscle: CanonicalMuscle) = "body-trend-volume-${muscle.name}"
}

/**
 * The read line under a card's header. FAILED replaces the card's content with Retry; STALE
 * keeps the content and marks it. Returns true when the content should still draw.
 */
@Composable
private fun readState(card: String, read: CardRead, onRetry: () -> Unit): Boolean {
    when (read) {
        CardRead.FAILED -> Column(
            Modifier.padding(Metrics.space3).testTag(BodyTrendTags.failed(card)),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(stringResource(R.string.body_read_failed), style = InstrumentType.body, color = TextSecondary)
            QuietButton(text = stringResource(R.string.body_retry), onClick = onRetry, plain = true,
                modifier = Modifier.testTag(BodyTrendTags.retry(card)))
        }
        CardRead.STALE -> Row(
            Modifier.fillMaxWidth().padding(horizontal = Metrics.space3).padding(top = Metrics.space2)
                .testTag(BodyTrendTags.stale(card)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.body_read_stale), style = InstrumentType.caption, color = TextSecondary,
                modifier = Modifier.weight(1f))
            QuietButton(text = stringResource(R.string.body_retry), onClick = onRetry, plain = true,
                modifier = Modifier.testTag(BodyTrendTags.retry(card)))
        }
        else -> Unit
    }
    return read == CardRead.OK || read == CardRead.STALE
}

@Composable
internal fun BodyweightTrendCard(
    entries: List<BodyweightEntry>,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    read: CardRead = CardRead.OK,
    onRetry: () -> Unit = {},
    onLogWeight: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().testTag(BodyTrendTags.BODYWEIGHT), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        GymSectionHeader(stringResource(R.string.body_trend_bodyweight))
        GroupedList {
            if (!readState(BodyTrendTags.BODYWEIGHT, read, onRetry)) return@GroupedList
            Column(Modifier.padding(Metrics.space3), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                val recent = BodyTrendsMath.recentBodyweight(entries)
                val latest = recent.lastOrNull()
                if (latest == null) {
                    Text(stringResource(R.string.body_no_bodyweight), style = InstrumentType.body, color = TextSecondary)
                    QuietButton(text = stringResource(R.string.body_log_weight), onClick = onLogWeight, plain = true,
                        modifier = Modifier.testTag(BodyTrendTags.LOG_WEIGHT))
                    return@Column
                }
                val shown = { kg: Double -> WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(kg, unit)) }
                Text(
                    stringResource(R.string.body_bodyweight_latest, shown(latest.kg), unit.suffix, DateCopy.civilDate(latest.epochDay)),
                    style = InstrumentType.bodyStrong, color = TextPrimary,
                )
                val first = recent.first()
                if (recent.size > 1) {
                    val delta = latest.kg - first.kg
                    val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
                    Text(
                        stringResource(R.string.body_bodyweight_change, sign + shown(kotlin.math.abs(delta)), unit.suffix, DateCopy.civilDate(first.epochDay)),
                        style = InstrumentType.caption, color = TextSecondary,
                    )
                    LineTrend(values = recent.map { WeightConverter.toDisplayValue(it.kg, unit) }, height = Metrics.space8)
                }
            }
        }
    }
}

@Composable
internal fun RecordsCard(
    records: List<PrSummaryRow>,
    unit: WeightUnit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    read: CardRead = CardRead.OK,
    onRetry: () -> Unit = {},
) {
    var all by rememberSaveable { mutableStateOf(false) }
    val clock = LocalClockFormat.current
    Column(modifier.fillMaxWidth().testTag(BodyTrendTags.RECORDS), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        GymSectionHeader(stringResource(R.string.body_trend_records))
        val hasMore = read != CardRead.FAILED && read != CardRead.LOADING && records.size > TOP_RECORDS
        GroupedList {
            if (!readState(BodyTrendTags.RECORDS, read, onRetry)) return@GroupedList
            if (records.isEmpty()) {
                Text(stringResource(R.string.body_no_records), style = InstrumentType.body, color = TextSecondary,
                    modifier = Modifier.padding(Metrics.space3))
            }
            val shown = if (all) records else records.take(TOP_RECORDS)
            shown.forEachIndexed { index, record ->
                if (index > 0) HairlineDivider()
                Column(
                    Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin)
                        .clickable(role = Role.Button) { onOpenExercise(record.exerciseId) }
                        .padding(horizontal = Metrics.space3, vertical = Metrics.space2)
                        .testTag(BodyTrendTags.record(record.exerciseId)),
                ) {
                    Text(record.exerciseName, style = InstrumentType.bodyStrong, color = TextPrimary)
                    Text(recordLine(record, unit, DateCopy.dateTime(record.achievedAt, clock)),
                        style = InstrumentType.caption, color = TextSecondary)
                }
            }
        }
        if (hasMore && !all) {
            QuietButton(text = stringResource(R.string.body_all_records, records.size), onClick = { all = true }, plain = true,
                modifier = Modifier.testTag(BodyTrendTags.ALL_RECORDS))
        }
    }
}

/** An estimated 1RM says it is derived and from which set; other records read as the set itself. */
@Composable
private fun recordLine(record: PrSummaryRow, unit: WeightUnit, whenText: String): String {
    val shown = { kg: Double -> WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(kg, unit)) }
    val estimate = if (record.kind == PersonalRecordKind.ESTIMATED_ONE_REP_MAX) {
        PersonalRecords.estimatedOneRepMaxKg(record.valueKg, record.reps)
    } else null
    return if (estimate != null) {
        stringResource(R.string.body_record_e1rm, shown(estimate), unit.suffix, shown(record.valueKg), record.reps, whenText)
    } else {
        stringResource(R.string.body_record_line, shown(record.valueKg), unit.suffix, record.reps, record.kind.label, whenText)
    }
}

@Composable
internal fun WeeklyVolumeCard(
    weeks: List<MuscleWeeks>,
    modifier: Modifier = Modifier,
    read: CardRead = CardRead.OK,
    onRetry: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().testTag(BodyTrendTags.VOLUME), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        GymSectionHeader(stringResource(R.string.body_trend_volume))
        GroupedList {
            if (!readState(BodyTrendTags.VOLUME, read, onRetry)) return@GroupedList
            if (weeks.isEmpty()) {
                Text(stringResource(R.string.body_no_volume), style = InstrumentType.body, color = TextSecondary,
                    modifier = Modifier.padding(Metrics.space3))
            }
            // One scale across every muscle, so a hot bar means the same in each row.
            val peak = weeks.maxOfOrNull { row -> row.sets.maxOrNull() ?: 0 }?.coerceAtLeast(1) ?: 1
            weeks.forEachIndexed { index, row ->
                if (index > 0) HairlineDivider()
                val spoken = stringResource(R.string.body_volume_spoken, row.muscle.displayName, row.thisWeek, row.total)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Metrics.rowMin).padding(horizontal = Metrics.space3)
                        .testTag(BodyTrendTags.volume(row.muscle)).semantics { contentDescription = spoken },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    Text(row.muscle.displayName, style = InstrumentType.body, color = TextPrimary, modifier = Modifier.weight(1f))
                    HeatBars(row.sets, peak)
                    Text(row.thisWeek.toString(), style = InstrumentType.bodyStrong, color = TextPrimary)
                }
            }
        }
    }
}

/** Weekly bars in the muscle heat colours, newest on the right. The numbers carry the meaning. */
@Composable
private fun HeatBars(sets: List<Int>, peak: Int) {
    Row(Modifier.height(Metrics.space6), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        sets.forEach { v ->
            val frac = v.toFloat() / peak
            Box(Modifier.width(Metrics.space2).height(Metrics.space6 * (frac * 0.85f + 0.15f)).background(heatColor(frac)))
        }
    }
}
