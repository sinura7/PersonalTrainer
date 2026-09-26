package com.sinura.personaltrainer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

/** Where Home stands once a tap with nothing live has asked for it. */
internal enum class HomeReach {
    /** Home is on screen, or is on the next frame. */
    IN_FRONT,

    /** A screen over Home asks before it is left (an activity being logged, a routine edited). */
    BEHIND_AN_EDIT,
}

/** Why a tap was held where the lifter is. Kept across the activity being recreated. */
internal enum class HeldTap(val title: String, val body: String) {
    LIVE_START(ReminderCopy.LIVE_TITLE, ReminderCopy.LIVE_START_BODY),
    LIVE_REVIEW(ReminderCopy.LIVE_TITLE, ReminderCopy.LIVE_REVIEW_BODY),
    EDIT_START(ReminderCopy.EDIT_TITLE, ReminderCopy.EDIT_START_BODY),
    EDIT_REVIEW(ReminderCopy.EDIT_TITLE, ReminderCopy.EDIT_REVIEW_BODY),
}

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
 * - nothing live: Home is brought in front ([bringHomeForward]) and handed the tap through
 *   [handToHome], and consumes it. Home only consumes it while drawn: switching to its tab
 *   brought back a screen left open over it, and the tap waited under that screen, then started
 *   the session by itself when it was left (audit X6, R4). Only a screen that asks before it is
 *   left is kept, and then the tap is held as below;
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
    bringHomeForward: () -> HomeReach,
    openLive: (sessionId: String, cardio: Boolean) -> Unit,
    useReminder: suspend (occurrenceId: String, deliveryId: String) -> Unit,
    handToHome: (HomeTap?) -> Unit,
    onStartConsumed: () -> Unit,
    onReviewConsumed: () -> Unit,
) {
    var held by rememberSaveable { mutableStateOf<HeldTap?>(null) }
    LaunchedEffect(openStartId, openDeliveryId, openReviewId) {
        handToHome(null)
        val tapped = openStartId ?: openReviewId ?: return@LaunchedEffect
        val found = verdict(tapped)
        // Navigation is main-thread only. An effect resumes where the composition's dispatcher
        // puts it, which in the Compose test harness is wherever the database read finished.
        withContext(Dispatchers.Main.immediate) {
            when (found) {
                TapVerdict.NothingLive -> {
                    val reach = bringHomeForward()
                    if (reach == HomeReach.IN_FRONT) {
                        handToHome(HomeTap(startId = openStartId, deliveryId = openDeliveryId, reviewId = openReviewId))
                    } else {
                        held = if (openStartId != null) HeldTap.EDIT_START else HeldTap.EDIT_REVIEW
                        if (openStartId != null) onStartConsumed()
                        if (openReviewId != null) onReviewConsumed()
                    }
                }
                TapVerdict.OtherSessionLive -> {
                    held = if (openStartId != null) HeldTap.LIVE_START else HeldTap.LIVE_REVIEW
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
    held?.let { tap ->
        ConfirmActionDialog(
            title = tap.title,
            body = tap.body,
            confirmLabel = ReminderCopy.LIVE_OK,
            onConfirm = { held = null },
            onDismiss = { held = null },
            dismissLabel = null,
        )
    }
}
