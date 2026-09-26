package com.sinura.personaltrainer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.domain.ReminderCopy
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a reminder tap meets, read from the database as the tap arrives. */
internal sealed interface TapVerdict {
    data object NothingLive : TapVerdict

    /** A workout or cardio is live that is not the tapped planned day. */
    data object OtherSessionLive : TapVerdict

    /** The live session is the tapped planned day, started early or from Home or Plan. */
    data class ThisSessionLive(val sessionId: String, val cardio: Boolean) : TapVerdict
}

/** The tap Home may act on: handed over only once nothing was found live. */
@Immutable
internal data class HomeTap(val startId: String?, val deliveryId: String?, val reviewId: String?)

/**
 * Reminder taps land on Home, which starts the session for Start and reviews the day for a body
 * tap. Settings (or any other tab) must not swallow the id.
 *
 * Not while a session is live (audit UI-1). Switching to Home put Home in place of a workout
 * opened from another tab, to reach a start that is refused while a workout or cardio is live,
 * and the reminder was used up at the tap. A mixed day's composer would open over it too. So,
 * whatever the planned day, the lifter stays where they are and is told why; the tap for the
 * live session's own planned day opens that session.
 */
internal object ReminderHandoff {
    private const val TAG = "PT/ReminderHandoff"

    /**
     * Read when the tap arrives, not from the live bar's state. That reads "nothing live" until
     * the database has answered, so in a new activity, or after Android restored the app, Home
     * took the tap first, or the tap went to Home over the workout. A failed read counts as
     * nothing live: Home then tries the start as before and says what went wrong.
     */
    suspend fun verdict(deps: AppDependencies, occurrenceId: String): TapVerdict =
        runCatchingCancellable {
            val workout = deps.workoutRepository.getInProgress()
            if (workout != null) {
                val followed = PendingOccurrence.followedBy(deps, workout.id)
                return@runCatchingCancellable if (followed == occurrenceId) {
                    TapVerdict.ThisSessionLive(sessionId = workout.id, cardio = false)
                } else {
                    TapVerdict.OtherSessionLive
                }
            }
            val cardio = deps.activityRepository.getLive() ?: return@runCatchingCancellable TapVerdict.NothingLive
            if (cardio.occurrenceId == occurrenceId) {
                TapVerdict.ThisSessionLive(sessionId = cardio.id, cardio = true)
            } else {
                TapVerdict.OtherSessionLive
            }
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Reading the live session for a reminder tap failed", thrown)
            TapVerdict.NothingLive
        }
}

/**
 * Where a reminder tap goes, decided by [verdict] before anything moves:
 * - nothing live: to Home, which is handed the tap (the returned [HomeTap]) and consumes it;
 * - another session live: nothing moves, the tap is consumed here and a dialog says why. A
 *   Start held this way never reaches Home, so its reminder is not used up and stays in the shade;
 * - the tapped day's own session live: it opens, and a Start's reminder is used.
 */
@Composable
internal fun ReminderHandoffHost(
    openStartId: String?,
    openDeliveryId: String?,
    openReviewId: String?,
    verdict: suspend (occurrenceId: String) -> TapVerdict,
    goToTab: (String) -> Unit,
    openLive: (sessionId: String, cardio: Boolean) -> Unit,
    useReminder: suspend (occurrenceId: String, deliveryId: String) -> Unit,
    onStartConsumed: () -> Unit,
    onReviewConsumed: () -> Unit,
): HomeTap? {
    var held by rememberSaveable { mutableStateOf<String?>(null) }
    var forHome by remember { mutableStateOf<HomeTap?>(null) }
    LaunchedEffect(openStartId, openDeliveryId, openReviewId) {
        forHome = null
        val tapped = openStartId ?: openReviewId ?: return@LaunchedEffect
        val found = verdict(tapped)
        // Navigation is main-thread only. An effect resumes where the composition's dispatcher
        // puts it, which in the Compose test harness is wherever the database read finished.
        withContext(Dispatchers.Main.immediate) {
            when (found) {
                TapVerdict.NothingLive -> {
                    forHome = HomeTap(startId = openStartId, deliveryId = openDeliveryId, reviewId = openReviewId)
                    goToTab(Route.Home.path)
                }
                TapVerdict.OtherSessionLive -> {
                    held = if (openStartId != null) ReminderCopy.LIVE_START_BODY else ReminderCopy.LIVE_REVIEW_BODY
                    if (openStartId != null) onStartConsumed()
                    if (openReviewId != null) onReviewConsumed()
                }
                is TapVerdict.ThisSessionLive -> {
                    if (openStartId != null && openDeliveryId != null) useReminder(openStartId, openDeliveryId)
                    if (openStartId != null) onStartConsumed()
                    if (openReviewId != null) onReviewConsumed()
                    openLive(found.sessionId, found.cardio)
                }
            }
        }
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
    return forHome
}
