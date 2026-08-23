package com.sinura.personaltrainer.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import com.sinura.personaltrainer.domain.BodyweightSteps
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A vertical number wheel with a live readout.
 *
 * The old chips were ten-kilo jumps of bare numbers. This is the instrument version: one
 * huge numeral, a unit toggle, and a column you flick. The stored value is always kilograms;
 * the wheel speaks the unit the lifter is looking at.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BodyweightWheel(
    kg: Double?,
    unit: WeightUnit,
    onKgChange: (Double) -> Unit,
    onUnitChange: (WeightUnit) -> Unit,
) {
    val values = remember(unit) { BodyweightSteps.displayValues(unit) }
    val selected = (kg?.let { BodyweightSteps.displayOf(it, unit) } ?: BodyweightSteps.defaultDisplay(unit))
        .coerceIn(values.first(), values.last())
    val startPage = values.indexOf(selected).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { values.size })
    val view = LocalView.current

    LaunchedEffect(unit, values, startPage) {
        if (pagerState.currentPage != startPage) {
            pagerState.scrollToPage(startPage)
        }
    }
    LaunchedEffect(pagerState, values, unit) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val display = values.getOrNull(page) ?: return@collect
                Haptics.tick(view)
                onKgChange(BodyweightSteps.toKg(display, unit))
            }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                values.getOrElse(pagerState.currentPage) { selected }.toString(),
                style = InstrumentType.numeralHero,
                color = TextPrimary,
            )
            Text(
                unit.suffix,
                style = InstrumentType.unit,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = Metrics.space3),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            WeightUnit.entries.forEach { option ->
                InstrumentChip(
                    label = option.suffix,
                    selected = option == unit,
                    onClick = { onUnitChange(option) },
                )
            }
        }
        WheelColumn(pagerState = pagerState, values = values)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(pagerState: PagerState, values: List<Int>) {
    val shape = RoundedCornerShape(Radius.md)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Metrics.touchMin * 3)
            .clip(shape)
            .background(Surface1)
            .border(Metrics.hairline, Hairline, shape),
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
            modifier = Modifier.fillMaxWidth().height(Metrics.touchMin * 3),
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
                    values[page].toString(),
                    style = InstrumentType.numeralMd,
                    color = if (focused) TextPrimary else TextTertiary,
                )
            }
        }
    }
}
