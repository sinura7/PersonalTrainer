package com.sinura.personaltrainer.ui.saveposture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.SavePostureCopy
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim

object SavePostureTags {
    const val ROOT = "save-posture-chooser"
    const val ACCOUNT = "save-posture-account"
    const val LOCAL = "save-posture-local"
    const val DRIVE = "save-posture-drive"
    const val ACCOUNT_PAUSED = "save-posture-account-paused"
}

/**
 * The first-launch question: where does this phone keep your training?
 *
 * It is drawn over Home, which stays composed underneath. So the chooser is the target of every
 * touch that lands on it (taps on its empty space used to fall through to Home and the tab bar), owns
 * Back (which used to reach the screen beneath), and is announced as its own pane. TalkBack
 * isolation needs the screen beneath hidden too — see [hiddenUnderFirstLaunchOverlay].
 */
@Composable
fun SavePostureChooser(
    onChooseAccount: () -> Unit,
    onChooseLocal: () -> Unit,
    onSetUpDrive: () -> Unit,
    onBack: () -> Unit,
    syncPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Pit)
            .blockTouchesBeneath()
            .semantics { paneTitle = SavePostureCopy.CHOOSER_HEADLINE }
            .testTag(SavePostureTags.ROOT),
    ) {
        // Scrolls, and clears the system bars: in landscape, or at a large text size on a small
        // phone, the last two choices used to sit below the screen with no way to reach them.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(Metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // A floor, not a fixed height: the headline and blurb grow with text size.
                    .heightIn(min = Metrics.space8 * 5)
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(VoltDim, Pit, Pit),
                        ),
                    )
                    .padding(Metrics.gutter),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Kicker(
                        text = SavePostureCopy.CHOOSER_KICKER,
                        color = if (revealed) Volt else TextTertiary,
                    )
                    Text(
                        SavePostureCopy.CHOOSER_HEADLINE,
                        style = InstrumentType.display,
                        color = if (revealed) TextPrimary else TextTertiary,
                    )
                    Text(
                        SavePostureCopy.CHOOSER_BLURB,
                        style = InstrumentType.body,
                        color = TextSecondary,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                PrimaryGymButton(
                    text = SavePostureCopy.CHOOSE_ACCOUNT,
                    onClick = {
                        Haptics.tick(view)
                        onChooseAccount()
                    },
                    modifier = Modifier.testTag(SavePostureTags.ACCOUNT),
                )
                Text(
                    SavePostureCopy.CHOOSE_ACCOUNT_HINT,
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
                if (syncPaused) {
                    Text(
                        SavePostureCopy.CHOOSE_ACCOUNT_PAUSED,
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        modifier = Modifier.testTag(SavePostureTags.ACCOUNT_PAUSED),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                SecondaryGymButton(
                    text = SavePostureCopy.CHOOSE_LOCAL,
                    onClick = {
                        Haptics.tick(view)
                        onChooseLocal()
                    },
                    modifier = Modifier.testTag(SavePostureTags.LOCAL),
                )
                Text(
                    SavePostureCopy.CHOOSE_LOCAL_HINT,
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
            }

            SecondaryGymButton(
                text = SavePostureCopy.SET_UP_DRIVE,
                onClick = {
                    Haptics.tick(view)
                    onSetUpDrive()
                },
                modifier = Modifier.testTag(SavePostureTags.DRIVE),
            )
        }
    }
}

/**
 * Makes this layer a touch target without acting on any touch. Hit-testing stops at the top-most
 * sibling with a pointer-input node, so nothing drawn beneath the chooser is reached.
 *
 * It must not consume: the chooser's buttons and its scroll check for consumed moves in the
 * Final pass, after this parent's Main pass, and cancel. A finger that wobbles a pixel between
 * down and up would then press nothing.
 */
private fun Modifier.blockTouchesBeneath(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/**
 * Hides what is drawn under the cold-start intro or the chooser from accessibility services.
 * Home is composed beneath both, and a screen reader could swipe past the chooser into it.
 */
fun Modifier.hiddenUnderFirstLaunchOverlay(covered: Boolean): Modifier =
    if (covered) clearAndSetSemantics { } else this
