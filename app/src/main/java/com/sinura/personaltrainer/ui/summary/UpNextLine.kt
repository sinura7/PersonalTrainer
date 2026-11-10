package com.sinura.personaltrainer.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.TodaySheetStart
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.DateCopy
import java.time.LocalDate

/**
 * The next planned training day after today, read once with the receipt.
 * [epochDay] null means no plan names a later day this week: the line says "plan your week".
 */
data class SummaryUpNext(
    val epochDay: Long?,
    val routineName: String?,
    /** True when the day comes from next week's rules: Plan only shows the current week. */
    val nextWeek: Boolean = false,
    /** F1: after a standalone warm-up, today's planned strength day is still open. */
    val startToday: Boolean = false,
    val todayStart: TodaySheetStart? = null,
)

/** Blueprint 3.6: one row between the facts and Done. Tap opens Plan on that day, or on the current week with no scroll for a next-week day. */
@Composable
internal fun UpNextLine(
    upNext: SummaryUpNext,
    todayEpochDay: Long,
    onOpenPlanDay: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    onStartToday: (TodaySheetStart) -> Unit = {},
) {
    val day = upNext.epochDay
    val name = upNext.routineName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.summary_up_next_unnamed)
    val text = when {
        upNext.startToday && day == todayEpochDay ->
            stringResource(R.string.summary_warmup_next_today, name)
        day == null -> stringResource(R.string.summary_up_next_none)
        upNext.nextWeek -> stringResource(
            R.string.summary_up_next_next_week,
            DateCopy.weekdayShort(LocalDate.ofEpochDay(day)),
            name,
        )
        day == todayEpochDay + 1 -> stringResource(R.string.summary_up_next_tomorrow, name)
        else -> {
            val date = LocalDate.ofEpochDay(day)
            stringResource(R.string.summary_up_next, "${DateCopy.weekdayShort(date)} ${date.dayOfMonth}", name)
        }
    }
    val openPlan = stringResource(R.string.summary_up_next_open_plan)
    val start = upNext.todayStart
  if (upNext.startToday && start != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.rowMin)
                .background(Surface1)
                .padding(horizontal = Metrics.space3)
                .testTag(SummaryTags.UP_NEXT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(
                text,
                style = InstrumentType.body,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            PrimaryGymButton(
                text = stringResource(R.string.start_title),
                onClick = { onStartToday(start) },
                modifier = Modifier.testTag(SummaryTags.UP_NEXT_START),
            )
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .background(Surface1)
            .clickable(role = Role.Button, onClickLabel = openPlan) { onOpenPlanDay(if (upNext.nextWeek) null else day) }
            .padding(horizontal = Metrics.space3)
            .testTag(SummaryTags.UP_NEXT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = InstrumentType.body, color = TextPrimary, modifier = Modifier.weight(1f))
        Icon(TemperIcons.Chevron, contentDescription = null, tint = TextTertiary)
    }
}
