package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.GetStartedCopy
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.WeekTwoCopy
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Empty-agenda leftover on Home: what the slot week says when no occurrence
 * was generated. When today has an agenda, [DailyAgendaCard] is the only Start.
 *
 * Home used to stack a filled Start/Resume above this card and a second Start below it, all
 * three offering to begin the same session. The card owns the decision now, so the screen has
 * exactly one filled control — and, since the live session bar became the only thing that
 * offers to resume, this card carries no live-session state at all.
 *
 * **The empty state is the important one.** The plan is stored now rather than generated, so
 * the first thing anyone sees after updating is a week with nothing in it. That is honest, and
 * it is also a cliff: recovery from it has to be one tap from here. When routines already
 * exist, that tap is replay — the same volt as Plan. Suggest stays, quiet, as the heat path.
 * When there are no routines, Suggest is the volt: there is nothing to replay.
 *
 * **It absorbed the "Next session" card.** Home used to render this one and then, past the week
 * strip, a second card naming the same session, with the same routine name in it and a tap
 * handler byte-identical to this card's button — two answers to one question, the second of
 * which was only reachable by scrolling past the first. What that card actually contributed was
 * the lift list and the reason line, so those moved here, where they sit under the headline
 * they belong to. One card, one tap, more on it than either had alone.
 *
 * @param lifts lift names of the session named above, in session order. Empty
 * when the day has no routine attached — a proposed focus rather than a pinned session.
 * [SessionOrderCopy.numberedPreview] truncates the line.
 * @param reason one line on why it is worth doing, from [com.sinura.personaltrainer.domain
 * .nextSessionReason]. Null when there is nothing worth saying, which is not the same as "".
 * @param sessionLive when a workout is already running. The card still names the plan; it
 * does not offer to start or return. The live bar is the only way back — a Start button
 * here would either lie (it cannot start) or become a second Resume.
 * @param onStartFree empty session the lifter fills as they go. Quiet on purpose so it
 * does not compete with following today's Plan routine.
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
    onSuggestWeek: () -> Unit,
    onReplayAnswers: () -> Unit,
    onPrimary: () -> Unit,
    onStartFree: () -> Unit,
    setupComplete: Boolean = true,
    offerSetupActions: Boolean = true,
    onGenerateSchedule: () -> Unit = {},
    onBuildWeek: () -> Unit = {},
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

    // No onClick. A whole-card tap that navigated, with a filled Start button inside it, was a
    // mis-tap trap on the most-pressed control in the app; Phase 6b removed the argument and
    // left the parameter behind, which is the compile break this deletes.
    GymCard {
        Kicker(kicker, color = TextSecondary)
        Text(
            headline,
            style = InstrumentType.title,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (hasPlan && lifts.isNotEmpty()) {
            Text(
                SessionOrderCopy.numberedPreview(lifts),
                style = InstrumentType.body,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hasPlan && reason != null) {
            Text(reason, style = InstrumentType.caption, color = TextTertiary)
        }
        if (!hasPlan) {
            if (!setupComplete) {
                Text(
                    GetStartedCopy.EMPTY_CAPTION,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
                if (offerSetupActions) {
                    PrimaryGymButton(
                        text = GetStartedCopy.GENERATE,
                        onClick = onGenerateSchedule,
                        modifier = Modifier
                            .padding(top = Metrics.space1)
                            .testTag(HomeTags.GENERATE)
                            .semantics { contentDescription = GetStartedCopy.GENERATE },
                    )
                    TextButton(onClick = onBuildWeek) {
                        Text(
                            GetStartedCopy.BUILD,
                            style = InstrumentType.bodyStrong,
                            color = TextSecondary,
                        )
                    }
                    if (!sessionLive) {
                        TextButton(
                            onClick = onStartFree,
                            modifier = Modifier
                                .testTag(HomeTags.FREE)
                                .semantics { contentDescription = GetStartedCopy.WORKOUT },
                        ) {
                            Text(
                                GetStartedCopy.WORKOUT,
                                style = InstrumentType.bodyStrong,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            } else if (hasRoutines) {
                Text(
                    WeekTwoCopy.CAPTION,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
                PrimaryGymButton(
                    text = WeekTwoCopy.VOLT,
                    onClick = onReplayAnswers,
                    modifier = Modifier
                        .padding(top = Metrics.space1)
                        .testTag(HomeTags.REPLAY)
                        .semantics { contentDescription = WeekTwoCopy.VOLT },
                )
                TextButton(onClick = onSuggestWeek) {
                    Text("Suggest a week", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
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
            }
            if (setupComplete && !sessionLive) {
                TextButton(
                    onClick = onStartFree,
                    modifier = Modifier
                        .testTag(HomeTags.FREE)
                        .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT },
                ) {
                    Text(SessionOrderCopy.FREE_WORKOUT, style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
        } else if (!sessionLive && trainingToday != null && !loggedToday) {
            // The only volt on Home: follow the routine Plan already designed for today.
            PrimaryGymButton(
                text = "Start this session",
                onClick = onPrimary,
                modifier = Modifier
                    .padding(top = Metrics.space1)
                    .testTag(HomeTags.START)
                    .semantics { contentDescription = "Start today's planned session" },
            )
            TextButton(
                onClick = onStartFree,
                modifier = Modifier
                    .testTag(HomeTags.FREE)
                    .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(SessionOrderCopy.FREE_WORKOUT, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        } else if (!sessionLive) {
            // Rest day, or already trained: the plan Volt would lie. Free logging is still
            // the honest second path.
            TextButton(
                onClick = onStartFree,
                modifier = Modifier
                    .padding(top = Metrics.space1)
                    .testTag(HomeTags.FREE)
                    .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(SessionOrderCopy.FREE_WORKOUT, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
    }
}
