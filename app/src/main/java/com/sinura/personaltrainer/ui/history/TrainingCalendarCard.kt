package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.CalendarDay
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.TrainingMonth
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.ui.theme.heatColor
import com.sinura.personaltrainer.util.toCivilDate
import com.sinura.personaltrainer.util.toJavaDayOfWeek
import com.sinura.personaltrainer.util.toYearMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/**
 * The training month at a glance.
 *
 * Days are shaded by how hard they were relative to the hardest day of the *same* month, so
 * the pattern of a month reads on its own terms rather than being flattened by one outlier
 * session from a year ago. That intensity is now drawn as a heat dot from the app's single
 * ramp — the one the body map and the Home dots read from — instead of this file's own
 * alpha scale, so "how hard I trained" is one concept across the product.
 */
@Composable
fun TrainingCalendarCard(
    month: TrainingMonth,
    weekStart: Weekday,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenDay: (CalendarDay) -> Unit,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    showMonth: Boolean = false,
    onToggleMonth: () -> Unit = {},
) {
    val locale = LocalLocale.current.platformLocale
    val weeksToShow = if (showMonth) {
        month.weeks
    } else {
        listOfNotNull(
            month.weekContaining(today.toCivilDate().epochDay) ?: month.weeks.lastOrNull(),
        )
    }
    GymCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMonth) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = TextSecondary,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (showMonth) {
                        monthFormatter.format(month.month.toYearMonth())
                    } else {
                        HistoryCopy.windowTitle(AnalyticsHorizon.WEEK)
                    },
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 1,
                )
            }
            if (showMonth) {
                // Nothing is ever logged in the future, so there is no forward month to look at.
                val canGoForward = month.month.toYearMonth() < java.time.YearMonth.from(today)
                IconButton(onClick = onNextMonth, enabled = canGoForward) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = if (canGoForward) TextSecondary else TextTertiary,
                    )
                }
            }
            InstrumentChip(
                label = HistoryCopy.CALENDAR_MONTH,
                selected = showMonth,
                onClick = onToggleMonth,
                modifier = Modifier.testTag(HistoryTags.CALENDAR_MONTH),
            )
        }

        // The grid owns its own rhythm: one gap value in both axes, or the columns and the
        // weeks disagree about how far apart a day is.
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                TrainingCalendarBuilder.weekdayOrder(weekStart).forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Kicker(
                            day.toJavaDayOfWeek().getDisplayName(TextStyle.NARROW, locale),
                            color = TextTertiary,
                        )
                    }
                }
            }

            weeksToShow.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            isToday = day.date == today.toCivilDate(),
                            onClick = { onOpenDay(day) },
                            unit = unit,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        HairlineDivider(startIndent = 0.dp)

        Text(
            HistoryCopy.CALENDAR_HEAT,
            style = InstrumentType.caption,
            color = TextTertiary,
        )

        if (showMonth) {
            if (month.trainedDays == 0) {
                Text(
                    "Nothing logged this month.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space6),
                ) {
                    MetricCluster(
                        value = month.trainedDays.toString(),
                        label = "days",
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                    MetricCluster(
                        value = month.workingSets.toString(),
                        label = "sets",
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                    val column = SetCopy.workColumn(month.work, unit)
                    MetricCluster(
                        value = column.value,
                        label = column.label,
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    isToday: Boolean,
    onClick: () -> Unit,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.xs)
    val label = buildString {
        append(day.date.dayOfMonth)
        if (day.trained) append(", trained, ${SetCopy.workLine(day.work, unit)}")
    }
    Box(
        modifier = modifier
            // Not a square: the cell's width comes from weight(1f) and its content grew a
            // heat dot, so a width-derived height can squeeze the numeral off centre.
            .heightIn(min = TrainingCalendarMetrics.cellMin)
            .clip(shape)
            // A trained day is a panel, not a wash of accent: the fill says "something
            // happened here" and the dot below says how much.
            .background(if (day.trained) Surface2 else Color.Transparent)
            .then(
                // Today gets its own token so the marker survives on top of any fill. It used
                // to be a ring in the accent, which vanished into a heavily trained day.
                if (isToday) Modifier.border(Metrics.hairline, HairlineStrong, shape) else Modifier,
            )
            .then(if (day.trained) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                day.date.dayOfMonth.toString(),
                style = InstrumentType.numeralSm,
                // Intensity is no longer carried by the numeral. It used to composite
                // onPrimary over a 35%-alpha fill — about 2:1 on exactly the light days the
                // alpha floor existed to keep visible.
                color = if (day.inMonth) TextPrimary else TextTertiary,
                maxLines = 1,
            )
            // Kept in the layout when there is nothing to show, so the numerals sit on one
            // baseline across the whole grid rather than hopping wherever a day was trained.
            Box(
                modifier = Modifier
                    .size(HEAT_DOT)
                    .clip(CircleShape)
                    .background(
                        if (day.trained) heatColor(trainedHeat(day.intensity)) else Color.Transparent,
                    ),
            )
        }
    }
}

/**
 * A trained day never fades to nothing: the floor keeps a light session on the ramp instead
 * of at its empty stop, which is the distinction the calendar exists to draw.
 *
 * The clamp is deliberate belt-and-braces. TrainingCalendarBuilder bounds intensity already,
 * but the grid's leading and trailing padding days keep their real volume from a neighbouring
 * month, so an out-of-range fraction arriving here is a caller bug this screen absorbs rather
 * than renders as a day off the end of the scale.
 */
private fun trainedHeat(intensity: Float): Float =
    (MIN_TRAINED_HEAT + intensity * HEAT_RANGE).coerceIn(0f, 1f)

private const val MIN_TRAINED_HEAT = 0.22f
private const val HEAT_RANGE = 0.78f
private val HEAT_DOT = 6.dp

internal object TrainingCalendarMetrics {
    val cellMin = Metrics.touchMin
}
