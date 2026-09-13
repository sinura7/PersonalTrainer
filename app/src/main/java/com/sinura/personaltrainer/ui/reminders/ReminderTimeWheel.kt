package com.sinura.personaltrainer.ui.reminders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.ReminderCopy
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Hour, minute, AM/PM as three flick columns. Not 1–12 chips.
 *
 * Stored hours stay 0–23. The wheel always speaks twelve-hour time
 * with AM/PM, which is how you set an alarm with a thumb.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderTimeWheel(
    hour: Int,
    minute: Int,
    onTime: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = ReminderCopy.hours12
    val minutes = ReminderCopy.minutes
    val periods = ReminderCopy.periodLabels
    val startHour = hours.indexOf(ReminderCopy.twelveHour(hour)).coerceAtLeast(0)
    val startMinute = minutes.indexOf(minute.coerceIn(0, 59)).coerceAtLeast(0)
    val startPeriod = if (ReminderCopy.isPm(hour)) 1 else 0
    val hourPager = rememberPagerState(initialPage = startHour, pageCount = { hours.size })
    val minutePager = rememberPagerState(initialPage = startMinute, pageCount = { minutes.size })
    val periodPager = rememberPagerState(initialPage = startPeriod, pageCount = { periods.size })
    val view = LocalView.current

    LaunchedEffect(hourPager, minutePager, periodPager, startHour, startMinute, startPeriod) {
        snapshotFlow {
            Triple(hourPager.settledPage, minutePager.settledPage, periodPager.settledPage)
        }
            .distinctUntilChanged()
            .collect { (hourPage, minutePage, periodPage) ->
                val stillInitial =
                    !ReminderCopy.shouldCommitSettledPage(hourPage, startHour) &&
                        !ReminderCopy.shouldCommitSettledPage(minutePage, startMinute) &&
                        !ReminderCopy.shouldCommitSettledPage(periodPage, startPeriod)
                if (stillInitial) return@collect
                Haptics.tick(view)
                onTime(
                    ReminderCopy.toHour24(hours[hourPage], pm = periodPage == 1),
                    minutes[minutePage],
                )
            }
    }

    val spoken = ReminderCopy.timeLabel(hour, minute)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(REMINDER_TIME_WHEEL)
            .semantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        WheelColumn(
            pagerState = hourPager,
            values = hours.map { it.toString() },
            modifier = Modifier.weight(1f),
            tag = REMINDER_TIME_HOUR,
        )
        WheelColumn(
            pagerState = minutePager,
            values = minutes.map { "%02d".format(it) },
            modifier = Modifier.weight(1f),
            tag = REMINDER_TIME_MINUTE,
        )
        WheelColumn(
            pagerState = periodPager,
            values = periods,
            modifier = Modifier.weight(1f),
            tag = REMINDER_TIME_PERIOD,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    pagerState: PagerState,
    values: List<String>,
    modifier: Modifier = Modifier,
    tag: String,
) {
    val shape = RoundedCornerShape(Radius.md)
    Box(
        modifier = modifier
            .height(Metrics.touchMin * 3)
            .clip(shape)
            .background(Surface1)
            .border(Metrics.hairline, Hairline, shape)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Metrics.touchMin)
                .border(Metrics.hairline, Volt, RoundedCornerShape(Radius.xs)),
        )
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(Metrics.touchMin * 3),
            pageSize = PageSize.Fixed(Metrics.touchMin),
            contentPadding = PaddingValues(vertical = Metrics.touchMin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { page ->
            val focused = page == pagerState.currentPage
            Box(
                modifier = Modifier.height(Metrics.touchMin),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    values[page],
                    style = InstrumentType.numeralMd,
                    color = if (focused) TextPrimary else TextTertiary,
                    modifier = Modifier.padding(horizontal = Metrics.space2),
                )
            }
        }
    }
}
