package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.shortLabel
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * Today's line on Home: what the plan says, and the one button that acts on it.
 *
 * Home used to stack a filled Start/Resume above this card and a second Start below it, all
 * three offering to begin the same session. The card owns the decision now, so the screen has
 * exactly one filled control — and, since the live session bar became the only thing that
 * offers to resume, this card carries no live-session state at all.
 *
 * **The empty state is the important one.** The plan is stored now rather than generated, so
 * the first thing anyone sees after updating is a week with nothing in it. That is honest, and
 * it is also a cliff: recovery from it has to be one tap from here, not a tab hunt followed by
 * a hunt for the right button. So "Suggest a week" is the primary action when there is no plan,
 * and starting an unplanned workout — which is still perfectly reasonable — steps down to the
 * quieter action beneath it.
 */
@Composable
fun ThisWeekCard(
    day: SuggestedTrainingDay?,
    nextDay: SuggestedTrainingDay?,
    loggedToday: Boolean,
    onOpenPlan: () -> Unit,
    onSuggestWeek: () -> Unit,
    onPrimary: () -> Unit,
) {
    val trainingToday = day?.takeUnless { it.isRest }
    val hasPlan = trainingToday != null || nextDay != null
    val kicker = when {
        trainingToday != null -> "Today"
        nextDay != null -> "Next · ${nextDay.dayOfWeek.shortLabel()}"
        else -> "This week"
    }
    val headline = when {
        trainingToday != null -> trainingToday.routineName ?: trainingToday.focusTitle
        nextDay != null -> nextDay.routineName ?: nextDay.focusTitle
        day != null -> "No plan yet"
        else -> "No plan yet"
    }

    GymCard(onClick = onOpenPlan) {
        Kicker(kicker, color = TextSecondary)
        Text(
            headline,
            style = InstrumentType.title,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (hasPlan) {
            PrimaryGymButton(
                text = when {
                    trainingToday != null && loggedToday -> "Train again"
                    trainingToday != null -> "Start this session"
                    else -> "Start a workout"
                },
                onClick = onPrimary,
                modifier = Modifier.padding(top = Metrics.space1),
            )
        } else {
            Text(
                "Pin your week in Plan, or let the app propose one.",
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            PrimaryGymButton(
                text = "Suggest a week",
                onClick = onSuggestWeek,
                modifier = Modifier.padding(top = Metrics.space1),
            )
            TextButton(onClick = onPrimary) {
                Text("Start a workout", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
    }
}
