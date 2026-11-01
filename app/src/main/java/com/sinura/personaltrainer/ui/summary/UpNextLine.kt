package com.sinura.personaltrainer.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.sinura.personaltrainer.R
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
)

/** Blueprint 3.6: one row between the facts and Done. Tap opens Plan on that day. */
@Composable
internal fun UpNextLine(
    upNext: SummaryUpNext,
    todayEpochDay: Long,
    onOpenPlanDay: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = upNext.epochDay
    val name = upNext.routineName.orEmpty()
    val text = when {
        day == null -> stringResource(R.string.summary_up_next_none)
        day == todayEpochDay + 1 -> stringResource(R.string.summary_up_next_tomorrow, name)
        else -> {
            val date = LocalDate.ofEpochDay(day)
            stringResource(R.string.summary_up_next, "${DateCopy.weekdayShort(date)} ${date.dayOfMonth}", name)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .background(Surface1)
            .clickable { onOpenPlanDay(day) }
            .padding(horizontal = Metrics.space3)
            .testTag(SummaryTags.UP_NEXT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = InstrumentType.body, color = TextPrimary, modifier = Modifier.weight(1f))
        Icon(TemperIcons.Chevron, contentDescription = null, tint = TextTertiary)
    }
}
