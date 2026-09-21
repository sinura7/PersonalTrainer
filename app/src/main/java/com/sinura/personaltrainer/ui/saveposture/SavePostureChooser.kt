package com.sinura.personaltrainer.ui.saveposture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
}

@Composable
fun SavePostureChooser(
    onChooseAccount: () -> Unit,
    onChooseLocal: () -> Unit,
    onSetUpDrive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Pit)
            .testTag(SavePostureTags.ROOT),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Metrics.space8 * 5)
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
