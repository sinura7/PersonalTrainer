package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenSkeleton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * The six Plan week states from blueprint 4.2 (A28), each a small stateless piece so a render
 * test can draw it without a view model. Process death needs no piece of its own: PlanViewModel
 * reads the week back from the database.
 */
internal object PlanWeekState {
    /** No stored week, no pinned rule and nothing proposed: there is no week to show yet. */
    fun isEmpty(state: PlanUiState): Boolean =
        !state.isLoading && state.week == null && state.rules.isEmpty() &&
            state.occurrences.isEmpty() && state.proposals.isEmpty()
}

/** Loading: the header stays, rows are skeletons. */
@Composable
internal fun PlanWeekLoading(onOpenLibrary: () -> Unit, onOpenStartSheet: () -> Unit) {
    Column(modifier = Modifier.testTag(PlanWeekTags.LOADING)) {
        PlanHeader(onOpenLibrary = onOpenLibrary, onOpenStartSheet = onOpenStartSheet)
        ScreenSkeleton(cards = 4)
    }
}

/** Error: a panel with the message and Try again. */
@Composable
internal fun PlanWeekError(message: String, onRetry: () -> Unit) {
    GymCard(modifier = Modifier.fillMaxWidth().testTag(PlanWeekTags.ERROR)) {
        Column(
            modifier = Modifier.padding(Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Text(message, style = InstrumentType.body, color = TextPrimary)
            val retry = stringResource(R.string.plan_error_retry)
            SecondaryGymButton(
                text = retry,
                onClick = onRetry,
                modifier = Modifier.testTag(PlanWeekTags.RETRY),
            )
        }
    }
}

/** Empty: no week yet, and the one way to build it. */
@Composable
internal fun PlanWeekEmpty(onBuildWeek: () -> Unit) {
    GymCard(modifier = Modifier.fillMaxWidth().testTag(PlanWeekTags.EMPTY)) {
        Column(
            modifier = Modifier.padding(Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Text(stringResource(R.string.plan_empty_title), style = InstrumentType.title, color = TextPrimary)
            PrimaryGymButton(
                text = stringResource(R.string.plan_empty_cta),
                onClick = onBuildWeek,
                height = Metrics.commit,
                modifier = Modifier.testTag(PlanWeekTags.BUILD_WEEK),
            )
        }
    }
}

/** First time: the suggested week is labelled as a suggestion. */
@Composable
internal fun PlanSuggestedCaption() {
    Text(
        stringResource(R.string.plan_suggested_caption),
        style = InstrumentType.caption,
        color = TextSecondary,
        modifier = Modifier.testTag(PlanWeekTags.SUGGESTED),
    )
}

/** Resume: a live session is one tap away. */
@Composable
internal fun PlanLiveRow(onOpen: () -> Unit) {
    val label = stringResource(R.string.plan_live_row)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.touchMin)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = label }
            .testTag(PlanWeekTags.LIVE_ROW),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = InstrumentType.bodyStrong, color = Volt)
    }
}

object PlanWeekTags {
    const val LOADING = "plan-week-loading"
    const val ERROR = "plan-week-error"
    const val RETRY = "plan-week-retry"
    const val EMPTY = "plan-week-empty"
    const val BUILD_WEEK = "plan-week-build"
    const val SUGGESTED = "plan-week-suggested"
    const val LIVE_ROW = "plan-week-live"
}
