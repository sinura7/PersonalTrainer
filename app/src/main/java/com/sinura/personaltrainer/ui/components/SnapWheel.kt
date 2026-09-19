package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.domain.FloorEntryWheels
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
 * One flick column. Shared by the Settings reminder wheel and the gym-floor
 * weight / reps / hold wheels so a thumb learns the motion once.
 *
 * Three rows tall, middle row framed in Volt. The pager snaps to a page;
 * that page is the value.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SnapWheelColumn(
    pagerState: PagerState,
    values: List<String>,
    modifier: Modifier = Modifier,
    tag: String? = null,
    rowHeight: Dp = Metrics.touchMin,
    userScrollEnabled: Boolean = true,
) {
    val shape = RoundedCornerShape(Radius.md)
    val columnHeight = rowHeight * 3
    Box(
        modifier = modifier
            .height(columnHeight)
            .clip(shape)
            .background(Surface1)
            .border(Metrics.hairline, Hairline, shape)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .border(Metrics.hairline, Volt, RoundedCornerShape(Radius.xs)),
        )
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(columnHeight),
            pageSize = PageSize.Fixed(rowHeight),
            contentPadding = PaddingValues(vertical = rowHeight),
            userScrollEnabled = userScrollEnabled,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { page ->
            val focused = page == pagerState.currentPage
            Box(
                modifier = Modifier.height(rowHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    values.getOrElse(page) { "" },
                    style = InstrumentType.numeralMd,
                    color = if (focused) TextPrimary else TextTertiary,
                    modifier = Modifier.padding(horizontal = Metrics.space2),
                )
            }
        }
    }
}

/**
 * A single snap column that writes when the flick settles.
 *
 * The first settle after open is not a write (pager noise, recovered draft).
 * After the thumb has left the parked page, settling back on it is a write.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SnapValueWheel(
    values: List<String>,
    selectedIndex: Int,
    onSettledIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    rowHeight: Dp = Metrics.touchMin,
    userScrollEnabled: Boolean = true,
    parkKey: Any? = Unit,
) {
    key(parkKey) {
        SnapValueWheelBody(
            values = values,
            selectedIndex = selectedIndex,
            onSettledIndex = onSettledIndex,
            modifier = modifier,
            tag = tag,
            rowHeight = rowHeight,
            userScrollEnabled = userScrollEnabled,
            parkKey = parkKey,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnapValueWheelBody(
    values: List<String>,
    selectedIndex: Int,
    onSettledIndex: (Int) -> Unit,
    modifier: Modifier,
    tag: String?,
    rowHeight: Dp,
    userScrollEnabled: Boolean,
    parkKey: Any?,
) {
    val last = (values.size - 1).coerceAtLeast(0)
    val startPage = selectedIndex.coerceIn(0, last)
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { values.size.coerceAtLeast(1) },
    )
    val parkedPage = rememberParkedPage(parkKey, startPage)
    var settleMemory by remember(parkKey) {
        mutableStateOf(FloorEntryWheels.WheelSettleMemory())
    }
    val view = LocalView.current

    LaunchedEffect(startPage, values.size) {
        if (pagerState.currentPage != startPage) {
            pagerState.scrollToPage(startPage)
        }
    }
    LaunchedEffect(pagerState, selectedIndex, userScrollEnabled, parkedPage) {
        snapshotFlow { pagerState.settledPage to selectedIndex }
            .distinctUntilChanged()
            .collect { (page, selected) ->
                if (!userScrollEnabled) return@collect
                val memory = settleMemory
                val commit = FloorEntryWheels.shouldCommitSettledPage(
                    settledPage = page,
                    initialPage = parkedPage,
                    memory = memory,
                    selectedIndex = selected,
                )
                settleMemory = FloorEntryWheels.afterWheelSettle(
                    settledPage = page,
                    initialPage = parkedPage,
                    memory = memory,
                )
                if (!commit) return@collect
                Haptics.tick(view)
                onSettledIndex(page)
            }
    }

    SnapWheelColumn(
        pagerState = pagerState,
        values = values,
        modifier = modifier,
        tag = tag,
        rowHeight = rowHeight,
        userScrollEnabled = userScrollEnabled,
    )
}

@Composable
private fun rememberParkedPage(parkKey: Any?, startPage: Int): Int =
    remember(parkKey) { startPage }
