package com.sinura.personaltrainer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sinura.personaltrainer.domain.ReminderCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog

/**
 * Reminder taps land on Home first. Start then starts; a body tap only reviews. Settings (or
 * any other tab) must not swallow the id.
 *
 * Not while a session is live: the tap used to pop whatever was open, the workout itself
 * included, to reach a start that would be refused (audit UI-1). Every start is refused while
 * a workout or cardio is live, so the lifter stays where they are and is told why.
 */
internal object ReminderHandoff {
    fun homeTab(openStartId: String?, openReviewId: String?, sessionLive: Boolean): String? =
        if (tapped(openStartId, openReviewId) && !sessionLive) Route.Home.path else null

    fun heldForLiveSession(openStartId: String?, openReviewId: String?, sessionLive: Boolean): Boolean =
        tapped(openStartId, openReviewId) && sessionLive

    /**
     * What Home is handed. Nothing while a session is live, when the tap is held instead: on the
     * Home tab, Home would otherwise start it too, and show its own dialog over the held one.
     */
    fun forHome(id: String?, sessionLive: Boolean): String? = id.takeUnless { sessionLive }

    private fun tapped(openStartId: String?, openReviewId: String?): Boolean =
        openStartId != null || openReviewId != null
}

/**
 * Where a reminder tap goes. With no session live it switches to Home, which consumes the ids.
 * With one live the ids are consumed here, nothing moves, and a dialog says why. A Start held
 * this way never reaches Home, so its reminder is not used up and stays in the shade.
 *
 * Keyed on [sessionLive] too: on a cold start it reads false until the database has answered,
 * and a tap Home has not taken by then is held once it does, rather than dropped.
 */
@Composable
internal fun ReminderHandoffHost(
    openStartId: String?,
    openReviewId: String?,
    sessionLive: Boolean,
    goToTab: (String) -> Unit,
    onStartConsumed: () -> Unit,
    onReviewConsumed: () -> Unit,
) {
    var held by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openStartId, openReviewId, sessionLive) {
        if (ReminderHandoff.heldForLiveSession(openStartId, openReviewId, sessionLive)) {
            held = if (openStartId != null) ReminderCopy.LIVE_START_BODY else ReminderCopy.LIVE_REVIEW_BODY
            if (openStartId != null) onStartConsumed()
            if (openReviewId != null) onReviewConsumed()
            return@LaunchedEffect
        }
        ReminderHandoff.homeTab(openStartId, openReviewId, sessionLive)?.let(goToTab)
    }
    held?.let { body ->
        ConfirmActionDialog(
            title = ReminderCopy.LIVE_TITLE,
            body = body,
            confirmLabel = ReminderCopy.LIVE_OK,
            onConfirm = { held = null },
            onDismiss = { held = null },
            dismissLabel = null,
        )
    }
}
