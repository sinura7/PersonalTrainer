package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.DayFill
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.WeekBoardCell
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.DateCopy
import java.time.LocalDate

/** Equivalent full-size date actions for the existing seven-day strip. */
@Composable
internal fun HomeDayPicker(
    cells: List<WeekBoardCell>,
    selected: Long,
    today: Long,
    enabled: Boolean,
    canSelect: () -> Boolean,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    recovery: (@Composable () -> Unit)? = null,
) {
    val maySelect by rememberUpdatedState(canSelect)
    AlertDialog(
        modifier = Modifier.testTag(HomeDayTags.PICKER),
        onDismissRequest = onDismiss,
        title = { Text("Choose a day", style = InstrumentType.title, color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                recovery?.invoke()
                cells.forEach { cell ->
                    val date = DateCopy.weekdayFullDate(LocalDate.ofEpochDay(cell.epochDay))
                    val status = if (cell.fill == DayFill.EMPTY) null else WeekBoard.status(cell)
                    val details = listOfNotNull(cell.caption, status, "Today".takeIf { cell.epochDay == today })
                        .joinToString(" · ")
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = Metrics.touchMin)
                            .selectable(
                                selected = cell.epochDay == selected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { if (maySelect()) onSelect(cell.epochDay) },
                            )
                            .padding(vertical = Metrics.space2)
                            .testTag(HomeDayTags.day(cell.epochDay))
                            .semantics { contentDescription = "$date, $details" },
                    ) {
                        Text(date, style = InstrumentType.bodyStrong, color = TextPrimary)
                        Text(details, style = InstrumentType.caption,
                            color = if (cell.epochDay == today) Volt else TextSecondary)
                        if (cell.epochDay == selected) {
                            Text("Selected", style = InstrumentType.caption, color = TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Metrics.touchMin)) {
                Text("Close", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        },
    )
}

internal object HomeDayTags {
    const val PICKER = "home-day-picker"
    const val CHOOSE = "home-choose-day"
    const val SAVING = "home-day-saving"
    const val ERROR = "home-day-memory-error"
    const val RETRY = "home-day-memory-retry"
    const val RESET = "home-day-memory-reset"
    fun day(epochDay: Long): String = "home-day-choice-$epochDay"
}
