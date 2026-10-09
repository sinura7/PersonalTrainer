package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect as CellLayoutRect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.DayFill
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.WeekBoardCell
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.ui.units.DateCopy
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** Primitive geometry only: activity content must never recenter a user's exploration. */
internal data class WeekStripGeometry(
    val viewportPx: Int,
    val density: Float,
    val fontScale: Float,
    val rtl: Boolean,
)

/** Owned before a caller's loading return, so a temporarily absent strip retains its position. */
class WeekStripState internal constructor(
    val scrollState: ScrollState,
    lastHandledSelected: Long? = null,
    lastHandledGeometry: WeekStripGeometry? = null,
) {
    internal var lastHandledSelected: Long? = lastHandledSelected
        private set
    internal var lastHandledGeometry: WeekStripGeometry? = lastHandledGeometry
        private set

    internal fun markHandled(selected: Long, geometry: WeekStripGeometry) {
        lastHandledSelected = selected
        lastHandledGeometry = geometry
    }

    internal companion object {
        val Saver = listSaver<WeekStripState, Any>(
            save = { stripState ->
                val geometry = stripState.lastHandledGeometry
                listOf(
                    stripState.scrollState.value,
                    stripState.lastHandledSelected != null && geometry != null,
                    stripState.lastHandledSelected ?: 0L,
                    geometry?.viewportPx ?: 0,
                    geometry?.density ?: 0f,
                    geometry?.fontScale ?: 0f,
                    geometry?.rtl ?: false,
                )
            },
            restore = { values ->
                val handled = values[1] as Boolean
                WeekStripState(
                    scrollState = ScrollState(initial = values[0] as Int),
                    lastHandledSelected = if (handled) values[2] as Long else null,
                    lastHandledGeometry = if (handled) {
                        WeekStripGeometry(
                            viewportPx = values[3] as Int,
                            density = values[4] as Float,
                            fontScale = values[5] as Float,
                            rtl = values[6] as Boolean,
                        )
                    } else null,
                )
            },
        )
    }
}

@Composable
fun rememberWeekStripState(): WeekStripState = rememberSaveable(saver = WeekStripState.Saver) {
    WeekStripState(scrollState = ScrollState(initial = 0))
}

/**
 * Seven dated selectable targets in one horizontal viewport (ADR-026).
 * Captions come from occurrences, with Plan proposals explicitly Suggested.
 * Today is the sole Volt marker; selection and resolution use neutral cues.
 */
@Composable
fun WeekStrip(
    cells: List<WeekBoardCell>,
    today: Long,
    selected: Long,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
    proposals: Map<Long, SuggestedTrainingDay> = emptyMap(),
    stripState: WeekStripState = rememberWeekStripState(),
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val reducedMotion = LocalReducedMotion.current
    val measurer = rememberTextMeasurer()
    // Fixed references are measured once per text geometry, never on a board/count/title refresh.
    val baseWidthPx = remember(density.density, density.fontScale, direction, measurer) {
        val weekdayWidth = Weekday.entries.maxOf { weekday ->
            measurer.measure(
                text = weekday.shortLabel().uppercase(Locale.ENGLISH),
                style = InstrumentType.kicker,
                softWrap = false,
            ).size.width
        }
        val dateWidth = (1..31).maxOf { day ->
            measurer.measure(text = day.toString(), style = InstrumentType.numeralSm, softWrap = false).size.width
        }
        val captionWidth = WIDTH_REFERENCE.maxOf { reference ->
            measurer.measure(text = reference, style = InstrumentType.caption, softWrap = false).size.width
        }
        val paddingPx = with(density) { (Metrics.space2 * 2).roundToPx() }
        val minimumPx = with(density) { ceil(Metrics.touchMin.toPx()).toInt() }
        maxOf(minimumPx, maxOf(weekdayWidth, dateWidth, captionWidth) + paddingPx)
    }
    val gapPx = with(density) { Metrics.space1.roundToPx() }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().clipToBounds(),
    ) {
        val viewportPx = constraints.maxWidth
        val geometry = WeekStripGeometry(
            viewportPx = viewportPx,
            density = density.density,
            fontScale = density.fontScale,
            rtl = direction == LayoutDirection.Rtl,
        )
        val fittingWidthPx = ((viewportPx - gapPx * (Weekday.DAYS_IN_WEEK - 1)) / Weekday.DAYS_IN_WEEK)
        val cellWidthPx = maxOf(baseWidthPx, fittingWidthPx).coerceAtMost(viewportPx)
        // New geometry requires fresh actual placements, not stale coordinates from the previous width.
        val positions = remember(geometry) { mutableStateMapOf<Long, CellLayoutRect>() }
        var contentWidthPx by remember(geometry) { mutableIntStateOf(0) }

        LaunchedEffect(selected, geometry) {
            if (viewportPx <= 0 ||
                (stripState.lastHandledSelected == selected && stripState.lastHandledGeometry == geometry)
            ) return@LaunchedEffect
            val bounds = snapshotFlow {
                val positioned = positions[selected]
                if (positioned != null && contentWidthPx > 0 &&
                    stripState.scrollState.viewportSize == viewportPx && stripState.scrollState.maxValue != Int.MAX_VALUE
                ) positioned else null
            }.filterNotNull().first()
            val logicalStart = if (geometry.rtl) contentWidthPx - bounds.right else bounds.left
            val logicalEnd = logicalStart + bounds.width
            val offset = stripState.scrollState.value
            val target = when {
                logicalStart < offset -> logicalStart.roundToInt()
                logicalEnd > offset + viewportPx -> (logicalEnd - viewportPx).roundToInt()
                else -> offset
            }.coerceIn(0, stripState.scrollState.maxValue)
            // Admit before moving: a user's gesture cancels movement without resurrecting it later.
            val sameGeometry = stripState.lastHandledGeometry == geometry
            stripState.markHandled(selected = selected, geometry = geometry)
            if (sameGeometry && !reducedMotion) {
                stripState.scrollState.animateScrollTo(value = target)
            } else {
                stripState.scrollState.scrollTo(value = target)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WeekStripTags.STRIP)
                .selectableGroup()
                .horizontalScroll(stripState.scrollState)
                .height(IntrinsicSize.Min)
                .onSizeChanged { contentWidthPx = it.width },
            horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            cells.forEach { cell ->
                key(cell.epochDay) {
                    WeekCell(
                        cell = cell,
                        proposal = proposals[cell.epochDay],
                        isToday = cell.epochDay == today,
                        selected = cell.epochDay == selected,
                        onClick = { onSelectDay(cell.epochDay) },
                        modifier = Modifier
                            .width(with(density) { cellWidthPx.toDp() })
                            .fillMaxHeight()
                            .onGloballyPositioned { coordinates ->
                                val origin = coordinates.positionInParent()
                                positions[cell.epochDay] = CellLayoutRect(
                                    left = origin.x,
                                    top = origin.y,
                                    right = origin.x + coordinates.size.width,
                                    bottom = origin.y + coordinates.size.height,
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekCell(
    cell: WeekBoardCell,
    proposal: SuggestedTrainingDay?,
    isToday: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayOfMonth = remember(cell.epochDay) { CivilDate.fromEpochDay(cell.epochDay).dayOfMonth }
    val preview = cell.fill == DayFill.EMPTY && proposal != null
    val label = if (preview) {
        "Suggested ${if (proposal?.isRest == true) WeekBoard.REST else proposal?.focusTitle.orEmpty()}"
    } else cell.caption
    val status = if (cell.fill == DayFill.EMPTY) null else WeekBoard.status(cell)
    val spoken = buildList {
        add(DateCopy.weekdayFullDate(LocalDate.ofEpochDay(cell.epochDay)))
        add(label)
        if (status != null) add(status)
        if (selected) add("selected")
        if (isToday) add("today")
    }.joinToString(", ")
    val labelColor = when {
        selected -> TextPrimary
        preview -> TextTertiary
        cell.fill == DayFill.NONE -> Danger
        cell.fill == DayFill.PARTIAL -> Warn
        else -> TextSecondary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) SurfacePressed else Surface2)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = Metrics.touchMin)
            .padding(horizontal = Metrics.space2, vertical = Metrics.space1)
            .testTag(WeekStripTags.cell(cell.epochDay))
            .semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Box(
            modifier = Modifier
                .size(width = TODAY_MARKER_WIDTH, height = TODAY_MARKER_HEIGHT)
                .background(
                    when {
                        isToday -> Volt
                        selected -> HairlineStrong
                        else -> Color.Transparent
                    },
                ),
        )
        Text(
            text = cell.weekday.shortLabel().uppercase(Locale.ENGLISH),
            style = InstrumentType.kicker,
            color = when {
                isToday -> Volt
                selected -> TextPrimary
                else -> TextSecondary
            },
            modifier = Modifier.testTag(WeekStripTags.weekday(cell.epochDay)),
            textAlign = TextAlign.Center,
        )
        Text(
            text = dayOfMonth.toString(),
            style = InstrumentType.numeralSm,
            color = TextPrimary,
            modifier = Modifier.testTag(WeekStripTags.date(cell.epochDay)),
        )
        Text(
            text = label,
            style = InstrumentType.caption,
            color = labelColor,
            modifier = Modifier.testTag(WeekStripTags.caption(cell.epochDay)),
            textAlign = TextAlign.Center,
        )
        if (status != null) {
            Text(
                text = status,
                style = InstrumentType.caption,
                color = TextSecondary,
                modifier = Modifier.testTag(WeekStripTags.status(cell.epochDay)),
                textAlign = TextAlign.Center,
            )
        }
        if (cell.fill == DayFill.ALL) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(LOGGED_TICK),
            )
        } else {
            Box(modifier = Modifier.size(LOGGED_TICK))
        }
    }
}

object WeekStripTags {
    const val STRIP = "week-strip"
    fun cell(epochDay: Long): String = "week-cell-$epochDay"
    fun weekday(epochDay: Long): String = "week-weekday-$epochDay"
    fun date(epochDay: Long): String = "week-date-$epochDay"
    fun caption(epochDay: Long): String = "week-caption-$epochDay"
    fun status(epochDay: Long): String = "week-status-$epochDay"
}

private val WIDTH_REFERENCE = listOf(
    "Rest", "Workout", "Cardio", "Mixed", "Suggested", "Suggested Rest", "2 activities",
    "1 completed", "1 skipped", "1 missed", "1 planned",
) + CardioType.entries.map(CardioCopy::name)
private val TODAY_MARKER_WIDTH = 16.dp
private val TODAY_MARKER_HEIGHT = 3.dp
private val LOGGED_TICK = 12.dp
