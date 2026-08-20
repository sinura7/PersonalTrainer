package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CalendarDay
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.TrainingMonth
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toVolumeLabel
import com.sinura.personaltrainer.ui.components.GymCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/**
 * The training month at a glance.
 *
 * Days are shaded by how hard they were relative to the hardest day of the *same* month, so
 * the pattern of a month reads on its own terms rather than being flattened by one outlier
 * session from a year ago.
 */
@Composable
fun TrainingCalendarCard(
    month: TrainingMonth,
    weekStart: DayOfWeek,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenDay: (CalendarDay) -> Unit,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    GymCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                monthFormatter.format(month.month),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            // Nothing is ever logged in the future, so there is no forward month to look at.
            val canGoForward = month.month < java.time.YearMonth.from(today)
            IconButton(onClick = onNextMonth, enabled = canGoForward) {
                Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            TrainingCalendarBuilder.weekdayOrder(weekStart).forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        month.weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        isToday = day.date == today,
                        onClick = { onOpenDay(day) },
                        unit = unit,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            if (month.trainedDays == 0) {
                "Nothing logged this month."
            } else {
                "${month.trainedDays} ${if (month.trainedDays == 1) "day" else "days"} · " +
                    "${month.workingSets} working ${if (month.workingSets == 1) "set" else "sets"} · " +
                    month.volumeKg.toVolumeLabel(unit)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    isToday: Boolean,
    onClick: () -> Unit,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // A trained day never fades to nothing: the floor keeps a light session visibly different
    // from a rest day, which is the distinction the calendar exists to draw.
    val fill = when {
        !day.trained -> Color.Transparent
        else -> scheme.primary.copy(alpha = MIN_TRAINED_ALPHA + day.intensity * ALPHA_RANGE)
    }
    val label = buildString {
        append(day.date.dayOfMonth)
        if (day.trained) append(", trained, ${day.volumeKg.toVolumeLabel(unit)}")
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .then(
                if (isToday) {
                    Modifier.border(1.5.dp, scheme.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .then(if (day.trained) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (day.trained) FontWeight.Bold else FontWeight.Normal,
            color = when {
                day.trained -> scheme.onPrimary
                day.inMonth -> scheme.onSurface
                // Padding days are context, not content; they should not read as trainable.
                else -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )
    }
}

private const val MIN_TRAINED_ALPHA = 0.35f
private const val ALPHA_RANGE = 0.65f
