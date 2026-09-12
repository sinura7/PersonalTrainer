package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.DayBlockCopy
import com.sinura.personaltrainer.domain.FreeStartRank
import com.sinura.personaltrainer.domain.GetStartedCopy
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.OneFilledVolt
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.featuredSession
import com.sinura.personaltrainer.domain.sessionLifts
import com.sinura.personaltrainer.domain.sessionMinutes
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Empty-agenda leftover on Home: what the slot week says when no occurrence
 * was generated. When today has an agenda, [DailyAgendaCard] is the only Start.
 *
 * Quiet empty plus a normal Start. Generating a week lives in Settings.
 */
@Composable
fun ThisWeekCard(
    day: SuggestedTrainingDay?,
    nextDay: SuggestedTrainingDay?,
    loggedToday: Boolean,
    lifts: List<String>,
    reason: String?,
    sessionLive: Boolean,
    hasRoutines: Boolean,
    onPrimary: () -> Unit,
    onStartFree: () -> Unit,
    onOpenPlan: () -> Unit = {},
    routines: List<Routine> = emptyList(),
    quietStart: Boolean = false,
    setupComplete: Boolean = true,
) {
    val trainingToday = day?.takeUnless { it.isRest }
    var startPending by rememberSaveable { mutableStateOf(false) }
    val confirmDay = trainingToday.takeIf { startPending && !sessionLive && !loggedToday }
    LaunchedEffect(trainingToday, sessionLive, loggedToday) {
        if (trainingToday == null || sessionLive || loggedToday) startPending = false
    }
    if (confirmDay != null) {
        val confirm = HomeToday.fallbackStartConfirm(confirmDay, routines)
        ConfirmActionDialog(
            title = confirm.heading,
            body = confirm.body,
            confirmLabel = confirm.confirmLabel,
            onConfirm = {
                startPending = false
                onPrimary()
            },
            onDismiss = { startPending = false },
        )
    }
    val hasPlan = trainingToday != null || nextDay != null
    val kicker = when {
        trainingToday != null -> "Today"
        nextDay != null -> "Next · ${nextDay.dayOfWeek.shortLabel()}"
        else -> "Today"
    }
    val headline = when {
        trainingToday != null -> trainingToday.routineName ?: trainingToday.focusTitle
        nextDay != null -> nextDay.routineName ?: nextDay.focusTitle
        else -> "No plan yet"
    }

    val featuredRoutineId = featuredSession(trainingToday, nextDay)?.routineId

    GymCard {
        Kicker(kicker, color = TextSecondary)
        if (hasPlan) {
            DayBlockHead(
                title = headline,
                lines = DayBlockCopy.preview(
                    names = lifts,
                    minutes = sessionMinutes(featuredRoutineId, routines),
                ),
                exercises = sessionLifts(featuredRoutineId, routines),
            )
        } else {
            Text(
                headline,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (hasRoutines) {
                    "Nothing planned today. Start a workout, or open Plan."
                } else {
                    GetStartedCopy.EMPTY_CAPTION
                },
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        if (hasPlan && reason != null) {
            Text(
                reason,
                style = InstrumentType.caption,
                color = TextTertiary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!hasPlan) {
            LeftoverFreeStart(
                rank = OneFilledVolt.leftoverFreeStart(
                    sessionLive = sessionLive,
                    setupComplete = setupComplete,
                    hasRecoveryVolt = false,
                    quietStart = quietStart,
                ),
                onStartFree = onStartFree,
            )
            TextButton(
                onClick = onOpenPlan,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.touchMin),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    "Plan",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (!sessionLive && trainingToday != null && !loggedToday) {
            val sessionModifier = Modifier
                .padding(top = Metrics.space1)
                .testTag(HomeTags.SESSION)
                .semantics { contentDescription = "Start today's planned session" }
            if (quietStart) {
                TextButton(
                    onClick = { startPending = true },
                    modifier = sessionModifier.fillMaxWidth().heightIn(min = Metrics.touchMin),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        "Start this session",
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                SecondaryGymButton(
                    text = "Start this session",
                    onClick = { startPending = true },
                    modifier = sessionModifier,
                    height = Metrics.touchMin,
                )
            }
            val startModifier = Modifier
                .testTag(HomeTags.START)
                .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT }
            if (quietStart) {
                SecondaryGymButton(
                    text = SessionOrderCopy.FREE_WORKOUT,
                    onClick = onStartFree,
                    modifier = startModifier,
                    height = Metrics.touchMin,
                )
            } else {
                PrimaryGymButton(
                    text = SessionOrderCopy.FREE_WORKOUT,
                    onClick = onStartFree,
                    modifier = startModifier,
                    height = Metrics.touchMin,
                )
            }
        } else if (!sessionLive) {
            TextButton(
                onClick = onStartFree,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.touchMin)
                    .padding(top = Metrics.space1)
                    .testTag(HomeTags.START)
                    .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    SessionOrderCopy.FREE_WORKOUT,
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LeftoverFreeStart(
    rank: FreeStartRank,
    onStartFree: () -> Unit,
) {
    val startModifier = Modifier
        .padding(top = Metrics.space1)
        .testTag(HomeTags.START)
        .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT }
    when (rank) {
        FreeStartRank.HIDDEN -> Unit
        FreeStartRank.PRIMARY -> PrimaryGymButton(
            text = SessionOrderCopy.FREE_WORKOUT,
            onClick = onStartFree,
            modifier = startModifier,
            height = Metrics.touchMin,
        )
        FreeStartRank.SECONDARY -> SecondaryGymButton(
            text = SessionOrderCopy.FREE_WORKOUT,
            onClick = onStartFree,
            modifier = startModifier,
            height = Metrics.touchMin,
        )
        FreeStartRank.TEXT -> TextButton(
            onClick = onStartFree,
            modifier = startModifier
                .fillMaxWidth()
                .heightIn(min = Metrics.touchMin),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                SessionOrderCopy.FREE_WORKOUT,
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
