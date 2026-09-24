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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * One flick column, used three times by the Settings reminder wheel (the gym floor's
 * weight / reps / hold wheels that shared it are gone; W2a removed their dead wrapper).
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
