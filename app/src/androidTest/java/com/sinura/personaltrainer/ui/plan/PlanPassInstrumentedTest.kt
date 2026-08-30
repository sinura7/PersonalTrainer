package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.WeekTwoCopy
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P9.6 Plan / FND-031: Add session is the fill Volt; Tune / New are gone.
 * Recovery stays named. 360 dp / font 2.0 keeps the named acts.
 */
@RunWith(AndroidJUnit4::class)
class PlanPassInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyWeekReplayStaysNamedAt360Font2() {
        setConstrainedContent(2f) {
            PlanHeader(onOpenLibrary = {})
            PlanRecoveryCommands(
                hasPins = false,
                hasRoutines = true,
                hasOpenDay = true,
                hasProposals = false,
                onReplay = {},
                onSuggest = {},
                onAccept = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(PlanTags.REPLAY).assertIsDisplayed()
        compose.onNodeWithContentDescription(WeekTwoCopy.VOLT).assertIsDisplayed()
        compose.onNodeWithTag(PlanTags.SUGGEST).assertIsDisplayed()
        compose.onNodeWithTag(PlanTags.LIBRARY).assertIsDisplayed()
        compose.onNodeWithTag(PlanTags.USE_WEEK).assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithText("Tune").assertDoesNotExist()
        compose.onNodeWithText("New").assertDoesNotExist()
    }

    @Test
    fun proposalConfirmsWithOneVoltAct() {
        setConstrainedContent(1f) {
            PlanRecoveryCommands(
                hasPins = false,
                hasRoutines = true,
                hasOpenDay = true,
                hasProposals = true,
                onReplay = {},
                onSuggest = {},
                onAccept = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(PlanTags.USE_WEEK).assertIsDisplayed()
        compose.onNodeWithText("Use this week").assertIsDisplayed()
        compose.onNodeWithTag(PlanTags.REPLAY).assertDoesNotExist()
    }

    @Test
    fun lighterWeekChipIsOnTheWeek() {
        setConstrainedContent(1f) {
            PlanLighterChip(enabled = false, onToggle = {})
        }
        compose.onNodeWithTag(PlanTags.LIGHTER).assertIsDisplayed()
        compose.onNodeWithText(LighterWeek.TUNE_LABEL).assertIsDisplayed()
    }

    private fun setConstrainedContent(fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(360.dp, 800.dp)) {
                            Column { content() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
