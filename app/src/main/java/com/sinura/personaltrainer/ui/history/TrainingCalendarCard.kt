package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect as CalendarPlacementRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.CalendarDay
import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.TrainingMonth
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.WeekStripGeometry
import com.sinura.personaltrainer.ui.components.WeekStripState
import com.sinura.personaltrainer.ui.components.rememberWeekStripState
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.heatColor
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.util.toCivilDate
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** One aligned scroll canvas; selected-period membership and monthly heat come from the VM. */
@Composable
fun TrainingCalendarCard(
    month: TrainingMonth,
    weekStart: Weekday,
    today: LocalDate,
    selectedEpochDay: Long,
    horizon: AnalyticsHorizon,
    onSelectDay: (Long) -> Unit,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    stripState: WeekStripState = rememberWeekStripState(),
) {
    val weeks = if (horizon == AnalyticsHorizon.MONTH) month.weeks
        else listOfNotNull(month.weekContaining(selectedEpochDay))
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val reducedMotion = LocalReducedMotion.current
    val measurer = rememberTextMeasurer()
    val baseWidthPx = remember(density.density, density.fontScale, direction, measurer) {
        val dateWidth = (1..31).maxOf {
            measurer.measure(it.toString(), style = InstrumentType.numeralSm, softWrap = false).size.width
        }
        val weekdayWidth = Weekday.entries.maxOf {
            measurer.measure(it.shortLabel().uppercase(Locale.ENGLISH),
                style = InstrumentType.kicker, softWrap = false).size.width
        }
        maxOf(
            with(density) { ceil(Metrics.touchMin.toPx()).toInt() },
            maxOf(dateWidth, weekdayWidth) + with(density) { (Metrics.space2 * 2).roundToPx() },
        )
    }
    val gapPx = with(density) { Metrics.space1.roundToPx() }
    GymCard(modifier = modifier.fillMaxWidth().testTag(HistoryTags.CALENDAR)) {
        BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds()) {
            val viewportPx = constraints.maxWidth
            val geometry = WeekStripGeometry(
                viewportPx, density.density, density.fontScale,
                direction == LayoutDirection.Rtl,
                weeks.firstOrNull()?.firstOrNull()?.date?.epochDay ?: selectedEpochDay,
            )
            val cellWidthPx = maxOf(baseWidthPx,
                (viewportPx - gapPx * (Weekday.DAYS_IN_WEEK - 1)) / Weekday.DAYS_IN_WEEK)
            val canvasWidth = cellWidthPx * Weekday.DAYS_IN_WEEK + gapPx * (Weekday.DAYS_IN_WEEK - 1)
            val positions = remember(geometry) { mutableStateMapOf<Long, CalendarPlacementRect>() }
            var contentWidthPx by remember(geometry) { mutableIntStateOf(0) }

            LaunchedEffect(selectedEpochDay, geometry) {
                if (viewportPx <= 0 || (stripState.lastHandledSelected == selectedEpochDay &&
                    stripState.lastHandledGeometry == geometry)) return@LaunchedEffect
                val bounds = snapshotFlow {
                    positions[selectedEpochDay]?.takeIf {
                        contentWidthPx > 0 && stripState.scrollState.viewportSize == viewportPx &&
                            stripState.scrollState.maxValue != Int.MAX_VALUE
                    }
                }.filterNotNull().first()
                val logicalStart = if (geometry.rtl) contentWidthPx - bounds.right else bounds.left
                val logicalEnd = logicalStart + bounds.width
                val offset = stripState.scrollState.value
                val target = when {
                    logicalStart < offset -> logicalStart.roundToInt()
                    logicalEnd > offset + viewportPx -> (logicalEnd - viewportPx).roundToInt()
                    else -> offset
                }.coerceIn(0, stripState.scrollState.maxValue)
                val sameGeometry = stripState.lastHandledGeometry == geometry
                stripState.markHandled(selectedEpochDay, geometry)
                if (sameGeometry && !reducedMotion) stripState.scrollState.animateScrollTo(target)
                else stripState.scrollState.scrollTo(target)
            }

            Column(
                Modifier.fillMaxWidth().horizontalScroll(stripState.scrollState)
                    .width(with(density) { canvasWidth.toDp() })
                    .testTag(HistoryTags.CALENDAR_SCROLL)
                    .onSizeChanged { contentWidthPx = it.width },
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                    TrainingCalendarBuilder.weekdayOrder(weekStart).forEachIndexed { index, weekday ->
                        Box(Modifier.width(with(density) { cellWidthPx.toDp() }),
                            contentAlignment = Alignment.Center) {
                            Kicker(text = weekday.shortLabel().uppercase(Locale.ENGLISH), color = TextTertiary,
                                modifier = Modifier.testTag(HistoryTags.weekday(index)))
                        }
                    }
                }
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                        week.forEach { day ->
                            DayCell(
                                day, day.date == today.toCivilDate(), day.date.epochDay == selectedEpochDay,
                                day.date.epochDay <= today.toEpochDay(), { onSelectDay(day.date.epochDay) }, unit,
                                Modifier.width(with(density) { cellWidthPx.toDp() })
                                    .onGloballyPositioned { coordinates ->
                                        val origin = coordinates.positionInParent()
                                        positions[day.date.epochDay] = CalendarPlacementRect(
                                            origin.x, origin.y,
                                            origin.x + coordinates.size.width, origin.y + coordinates.size.height,
                                        )
                                    },
                            )
                        }
                    }
                }
            }
        }
        Text(HistoryCopy.CALENDAR_HEAT, style = InstrumentType.caption, color = TextTertiary)
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    isToday: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    if (day.date.epochDay < LocalDate.MIN.toEpochDay() || day.date.epochDay > LocalDate.MAX.toEpochDay()) {
        Box(modifier.heightIn(min = TrainingCalendarMetrics.cellMin))
        return
    }
    val shape = RoundedCornerShape(Radius.xs)
    val label = buildList {
        add(DateCopy.weekdayFullDate(LocalDate.ofEpochDay(day.date.epochDay)))
        if (day.trained) add("trained, ${SetCopy.workLine(day.work, unit)}")
        if (isToday) add("today")
        if (selected) add("selected")
        if (!enabled) add("future date")
    }.joinToString(", ")
    Column(
        modifier.heightIn(min = TrainingCalendarMetrics.cellMin).clip(shape)
            .background(if (selected) SurfacePressed else if (day.trained) Surface2 else Color.Transparent)
            .then(if (selected) Modifier.border(Metrics.emphasisBorder, HairlineStrong, shape) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Metrics.space2, vertical = Metrics.space1)
            .testTag(HistoryTags.day(day.date.epochDay))
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Box(Modifier.size(Metrics.space4, Metrics.hairline * 2)
            .background(if (isToday) Volt else Color.Transparent))
        Text(
            day.date.dayOfMonth.toString(), style = InstrumentType.numeralSm,
            color = if (!enabled || !day.inMonth) TextTertiary else TextPrimary,
            modifier = Modifier.testTag(HistoryTags.date(day.date.epochDay)), maxLines = 1,
        )
        Box(Modifier.size(HEAT_DOT).clip(Radius.full)
            .background(if (day.trained) heatColor(trainedHeat(day.intensity)) else Color.Transparent))
    }
}

private fun trainedHeat(intensity: Float): Float = (MIN_TRAINED_HEAT + intensity * HEAT_RANGE).coerceIn(0f, 1f)
private const val MIN_TRAINED_HEAT = 0.22f
private const val HEAT_RANGE = 0.78f
private val HEAT_DOT = 6.dp

internal object TrainingCalendarMetrics {
    val cellMin = Metrics.touchMin
}
