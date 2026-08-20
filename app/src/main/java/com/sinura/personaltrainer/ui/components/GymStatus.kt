package com.sinura.personaltrainer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.DangerContainer
import com.sinura.personaltrainer.ui.theme.GoldContainer
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.Warn
import kotlinx.coroutines.delay

/**
 * One banner shape, three tones.
 *
 * The tone is carried by a leading rule and the accent on the title, not by flooding the
 * whole surface with colour — a saturated fill behind body text is hard to read on a dark
 * field, and it made the app's *reward* look exactly like its *failures*.
 */
@Composable
private fun InstrumentBanner(
    accent: Color,
    container: Color,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, shape)
            .border(Metrics.hairline, accent.copy(alpha = 0.35f), shape)
            .padding(start = Metrics.space4, end = Metrics.space2, top = Metrics.space3, bottom = Metrics.space3),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) icon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(title, style = InstrumentType.title, color = accent)
            if (body != null) {
                Text(body, style = InstrumentType.body, color = TextSecondary)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Text(actionLabel, style = InstrumentType.bodyStrong, color = accent)
                }
            }
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = TextSecondary)
            }
        }
    }
}

/**
 * The standard way to show a failure that the user can do something about.
 *
 * Before this, every screen improvised: a red `Text` dropped into a LazyColumn with no retry,
 * no dismiss, and no guarantee it was even on screen. One traced path rendered nowhere at all
 * — a failed exercise creation in an empty free workout wrote to a state field whose only
 * reader lived inside a bottom bar that is not composed until a lift is selected.
 */
@Composable
fun GymErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    InstrumentBanner(
        accent = Danger,
        container = DangerContainer,
        title = "Something failed",
        body = message,
        modifier = modifier,
        icon = {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Danger)
        },
        actionLabel = if (onRetry != null) "Retry" else null,
        onAction = onRetry,
        onDismiss = onDismiss,
    )
}

/**
 * A transient confirmation that dismisses itself.
 *
 * Status used to be an unstyled string in the accent colour dropped into the middle of a
 * scrolling form — it shifted the layout when it appeared and then stayed there forever,
 * because nothing ever cleared it.
 */
@Composable
fun GymStatusBanner(
    message: String,
    modifier: Modifier = Modifier,
    onDismissed: (() -> Unit)? = null,
) {
    var visible by remember(message) { mutableStateOf(true) }
    LaunchedEffect(message) {
        delay(STATUS_DWELL_MS)
        visible = false
        onDismissed?.invoke()
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.FAST)),
        exit = fadeOut(tween(Motion.FAST)),
        modifier = modifier,
    ) {
        InstrumentBanner(
            accent = Volt,
            container = Surface2,
            title = message,
            body = null,
        )
    }
}

/**
 * The moment a record breaks.
 *
 * Deliberately not a dialog: this fires mid-set with the phone on the floor and a rest timer
 * running, and anything that has to be dismissed before the next set can be logged would be
 * a punishment rather than a reward. That call was already right — what was wrong is that
 * the reward was drawn in the same card anatomy, the same radius and the same container
 * colour as the error banner sitting directly above it in the same list, with no motion and
 * no haptic. The app could not tell its user apart from its failures.
 *
 * Gold is reserved for records and used nowhere else, and the entrance springs in once.
 *
 * This composable is purely visual. The haptic beats and the self-clearing dwell live in the
 * screen, not here, because this banner is mounted as an item in a scrolling list: logging
 * the set that breaks a record also scrolls the list, so the item is disposed within a second
 * or two. Owning the dwell here meant the acknowledgement never fired — the record stayed
 * pending for the rest of the session — and scrolling back replayed the celebration from the
 * top, haptics and all, every time.
 */
@Composable
fun PersonalRecordBanner(
    headline: String,
    detail: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(headline, detail) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.92f, animationSpec = Motion.celebrate()) +
            fadeIn(tween(Motion.FAST)),
        exit = fadeOut(tween(Motion.FAST)),
        modifier = modifier,
    ) {
        InstrumentBanner(
            accent = PrGold,
            container = GoldContainer,
            title = headline,
            body = detail,
            icon = {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = PrGold)
            },
            onDismiss = onDismiss,
        )
    }
}

/** Tells the lifter something is off with the phone rather than with the workout. */
@Composable
fun GymNoticeBanner(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InstrumentBanner(
        accent = Warn,
        container = Surface2,
        title = title,
        body = body,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

private const val STATUS_DWELL_MS = 2_600L
