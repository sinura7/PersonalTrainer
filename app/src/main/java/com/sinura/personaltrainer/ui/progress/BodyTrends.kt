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
import com.sinura.personaltrainer.ui.theme.HeatEmpty
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.units.LocalClockFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** v4 V7: the three Body cards below the muscles. Each list is empty until its read lands. */
data class BodyTrends(
    val bodyweight: List<BodyweightEntry> = emptyList(),
    val records: List<PrSummaryRow> = emptyList(),
    val weeklySets: List<MuscleWeeks> = emptyList(),
    /** Monday of the oldest of the [WEEKS] ISO weeks shown. */
    val firstWeekStartEpochDay: Long = 0L,
)

/** Working sets for one muscle in each of the last [WEEKS] ISO weeks, oldest first. */
data class MuscleWeeks(val muscle: CanonicalMuscle, val sets: List<Int>) {
    val total: Int get() = sets.sum()
}

internal const val WEEKS = 8
internal const val TOP_RECORDS = 5

object BodyTrendsMath {
    /** Monday of the ISO week [WEEKS] - 1 weeks before the one holding [today]. */
    fun firstWeekStart(todayEpochDay: Long): Long =
        LocalDate.ofEpochDay(todayEpochDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks((WEEKS - 1).toLong()).toEpochDay()

    /**
     * D12: working sets only (the query already drops warm-ups), credited to the lift's primary
     * muscle, bucketed by the ISO week its session finished in. Muscles with none are left out.
     */
    fun weeklySets(
        rows: List<WorkingSetCountRow>,
        catalog: List<Exercise>,
        firstWeekStartEpochDay: Long,
        localEpochDayOf: (Long) -> Long,
    ): List<MuscleWeeks> {
        val muscleOf = catalog.associate { it.id to MuscleNormalizer.primaryOf(it.muscleGroup) }
        val buckets = mutableMapOf<CanonicalMuscle, IntArray>()
        val start = firstWeekStartEpochDay
        rows.forEach { row ->
            val muscle = muscleOf[row.exerciseId]?.takeIf { it != CanonicalMuscle.OTHER } ?: return@forEach
            val day = localEpochDayOf(row.finishedAt)
            val week = ((day - start) / 7).toInt()
            if (day < start || week !in 0 until WEEKS) return@forEach
            buckets.getOrPut(muscle) { IntArray(WEEKS) }[week] += row.workingSets
        }
        return CanonicalMuscle.bodyMapOrder
            .mapNotNull { m -> buckets[m]?.let { MuscleWeeks(m, it.toList()) } }
            .sortedByDescending { it.total }
    }
}

object BodyTrendTags {
    const val BODYWEIGHT = "body-trend-bodyweight"
    const val RECORDS = "body-trend-records"
    const val ALL_RECORDS = "body-trend-all-records"
    const val VOLUME = "body-trend-volume"
    fun record(exerciseId: String) = "body-trend-record-$exerciseId"
    fun volume(muscle: CanonicalMuscle) = "body-trend-volume-${muscle.name}"
}

@Composable
internal fun BodyweightTrendCard(entries: List<BodyweightEntry>, unit: WeightUnit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(BodyTrendTags.BODYWEIGHT), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        GymSectionHeader(stringResource(R.string.body_trend_bodyweight))
        GroupedList {
            Column(Modifier.padding(Metrics.space3), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                val sorted = entries.sortedBy { it.epochDay }
                val latest = sorted.lastOrNull()
                if (latest == null) {
                    Text(stringResource(R.string.body_no_bodyweight), style = InstrumentType.body, color = TextSecondary)
                    return@Column
                }
                val shown = { kg: Double -> WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(kg, unit)) }
                Text(
                    stringResource(R.string.body_bodyweight_latest, shown(latest.kg), unit.suffix, DateCopy.civilDate(latest.epochDay)),
                    style = InstrumentType.bodyStrong, color = TextPrimary,
                )
                val first = sorted.first()
                if (sorted.size > 1) {
                    val delta = latest.kg - first.kg
                    val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
                    Text(
                        stringResource(R.string.body_bodyweight_change, sign + shown(kotlin.math.abs(delta)), unit.suffix, DateCopy.civilDate(first.epochDay)),
                        style = InstrumentType.caption, color = TextSecondary,
                    )
                }
                Sparkbars(sorted.takeLast(12).map { it.kg })
            }
        }
    }
}

@Composable
internal fun RecordsCard(records: List<PrSummaryRow>, unit: WeightUnit, onOpenExercise: (String) -> Unit, modifier: Modifier = Modifier) {
    var all by rememberSaveable { mutableStateOf(false) }
    val clock = LocalClockFormat.current
    Column(modifier.fillMaxWidth().testTag(BodyTrendTags.RECORDS), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        GymSectionHeader(stringResource(R.string.body_trend_records))
        GroupedList {
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
                    val value = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(record.valueKg, unit))
                    Text(
                        stringResource(R.string.body_record_line, value, unit.suffix, record.reps, record.kind.label, DateCopy.dateTime(record.achievedAt, clock)),
                        style = InstrumentType.caption, color = TextSecondary,
                    )
                }
            }
        }
        if (records.size > TOP_RECORDS && !all) {
            QuietButton(text = stringResource(R.string.body_all_records, records.size), onClick = { all = true }, plain = true,
                modifier = Modifier.testTag(BodyTrendTags.ALL_RECORDS))
        }
    }
}

@Composable
internal fun WeeklyVolumeCard(weeks: List<MuscleWeeks>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(BodyTrendTags.VOLUME), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        GymSectionHeader(stringResource(R.string.body_trend_volume))
        GroupedList {
            if (weeks.isEmpty()) {
                Text(stringResource(R.string.body_no_volume), style = InstrumentType.body, color = TextSecondary,
                    modifier = Modifier.padding(Metrics.space3))
            }
            weeks.forEachIndexed { index, row ->
                if (index > 0) HairlineDivider()
                val spoken = stringResource(R.string.body_volume_spoken, row.muscle.displayName, row.sets.last(), row.total)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Metrics.rowMin).padding(horizontal = Metrics.space3)
                        .testTag(BodyTrendTags.volume(row.muscle)).semantics { contentDescription = spoken },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    Text(row.muscle.displayName, style = InstrumentType.body, color = TextPrimary, modifier = Modifier.weight(1f))
                    Sparkbars(values = row.sets.map(Int::toDouble), fromZero = true)
                    Text(row.sets.last().toString(), style = InstrumentType.bodyStrong, color = TextPrimary)
                }
            }
        }
    }
}

/** Small bars, newest on the right. Decorative: the numbers beside them carry the meaning. */
@Composable
private fun Sparkbars(values: List<Double>, fromZero: Boolean = false) {
    if (values.isEmpty()) return
    val max = values.max()
    val min = if (fromZero) 0.0 else values.min()
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    Row(Modifier.height(Metrics.space6), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        values.forEach { v ->
            val frac = if (max == min && !fromZero) 0.6 else ((v - min) / span) * 0.85 + 0.15
            Box(Modifier.width(Metrics.space2).height(Metrics.space6 * frac.toFloat()).background(if (v > 0) Volt else HeatEmpty))
        }
    }
}
