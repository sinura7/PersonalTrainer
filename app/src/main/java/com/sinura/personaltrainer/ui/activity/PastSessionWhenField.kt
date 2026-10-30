package com.sinura.personaltrainer.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.units.DateCopy
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

/**
 * Blueprint 4.16 (A23): one When field replaces the Earlier / date / Later row. Tapping it opens
 * a date picker that refuses days after today; the time stays the existing noon rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PastSessionWhenField(
    epochDay: Long,
    todayEpochDay: Long,
    enabled: Boolean,
    onPick: (Long) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.past_when)
    // Within the last week the weekday reads best; further back, the plain civil date.
    val value = if (todayEpochDay - epochDay > RECENT_DAYS) {
        DateCopy.civilDate(epochDay)
    } else {
        DateCopy.weekdayShort(LocalDate.ofEpochDay(epochDay))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.touchMin)
            .clickable(enabled = enabled, role = Role.Button) { picking = true }
            .semantics { contentDescription = "$label, $value" }
            .testTag(ComposerTags.WHEN)
            .padding(vertical = Metrics.space2),
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Kicker(label)
        Text(value, style = InstrumentType.bodyStrong, color = TextPrimary)
    }
    if (picking) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = epochDay * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis / DAY_MS <= todayEpochDay
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let { onPick(it / DAY_MS) }
                        picking = false
                    },
                    modifier = Modifier.testTag(ComposerTags.WHEN_CONFIRM),
                ) { Text(stringResource(R.string.past_when_done)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.past_when_cancel)) }
            },
        ) {
            DatePicker(state = picker)
        }
    }
}

private const val RECENT_DAYS = 6L
