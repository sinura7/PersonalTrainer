package com.sinura.personaltrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.SavePostureCopy
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextTertiary

@Composable
internal fun SavePostureSection(
    posture: SavePosture,
    onUseAccount: () -> Unit,
    onUseLocal: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsTags.SAVE_POSTURE),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        GymSectionHeader(title = SavePostureCopy.SETTINGS_CURRENT, compact = true)
        Text(
            SavePostureCopy.settingsSummary(posture),
            style = InstrumentType.bodyStrong,
            modifier = Modifier.testTag(SettingsTags.SAVE_POSTURE_CURRENT),
        )
        Text(
            when (posture) {
                SavePosture.ACCOUNT -> SavePostureCopy.SETTINGS_ACCOUNT_CAPTION
                SavePosture.LOCAL -> SavePostureCopy.SETTINGS_LOCAL_CAPTION
            },
            style = InstrumentType.caption,
            color = TextTertiary,
        )

        when (posture) {
            SavePosture.LOCAL -> {
                PrimaryGymButton(
                    text = SavePostureCopy.SETTINGS_USE_ACCOUNT,
                    onClick = onUseAccount,
                    modifier = Modifier.testTag(SettingsTags.SAVE_POSTURE_USE_ACCOUNT),
                )
                SecondaryGymButton(
                    text = SavePostureCopy.SETTINGS_OPEN_BACKUP,
                    onClick = onOpenBackup,
                    modifier = Modifier.testTag(SettingsTags.SAVE_POSTURE_OPEN_BACKUP),
                )
            }
            SavePosture.ACCOUNT -> {
                PrimaryGymButton(
                    text = SavePostureCopy.SETTINGS_OPEN_ACCOUNT,
                    onClick = onOpenAccount,
                    modifier = Modifier.testTag(SettingsTags.SAVE_POSTURE_OPEN_ACCOUNT),
                )
                SecondaryGymButton(
                    text = SavePostureCopy.SETTINGS_USE_LOCAL,
                    onClick = onUseLocal,
                    modifier = Modifier.testTag(SettingsTags.SAVE_POSTURE_USE_LOCAL),
                )
            }
        }
    }
}
