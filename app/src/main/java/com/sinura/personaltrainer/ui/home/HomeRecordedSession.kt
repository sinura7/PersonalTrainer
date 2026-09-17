package com.sinura.personaltrainer.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.HomeRecords
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeRecordedSession(
    session: SessionSummary,
    onOpen: (SessionSummary) -> Unit,
    modifier: Modifier = Modifier,
    scheduledTitle: String? = null,
) {
    GymCard(
        modifier = modifier.testTag(HomeTags.record(session.id)),
        onClick = { onOpen(session) },
    ) {
        val date = LocalDate.ofEpochDay(session.localEpochDay).format(RECORD_DATE)
        Kicker(if (scheduledTitle == null) "Completed · $date" else "Scheduled activity")
        Text(scheduledTitle ?: HomeRecords.title(session), style = InstrumentType.title, color = TextPrimary)
        if (scheduledTitle != null) {
            Text("Recorded on $date · ${HomeRecords.title(session)}", style = InstrumentType.body, color = TextSecondary)
        }
        Text(HomeRecords.metrics(session), style = InstrumentType.body, color = TextSecondary)
        Text("View session", style = InstrumentType.bodyStrong, color = Volt)
    }
}

private val RECORD_DATE = DateTimeFormatter.ofPattern("d MMM")
