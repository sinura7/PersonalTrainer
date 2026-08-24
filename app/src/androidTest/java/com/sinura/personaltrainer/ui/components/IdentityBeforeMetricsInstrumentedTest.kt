package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.routines.CompactLiftCopy
import com.sinura.personaltrainer.ui.routines.CompactLiftRow
import com.sinura.personaltrainer.ui.routines.CompactLiftTags
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FND-006 / FND-016: at 360 dp, identity stays readable through font 2.0.
 * Metrics wrap first. Target weight is the label; kg/lbs is the suffix.
 */
@RunWith(AndroidJUnit4::class)
class IdentityBeforeMetricsInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sessionRowKeepsTitleAndDateAt360Font1() = assertSessionRow(fontScale = 1f)

    @Test
    fun sessionRowKeepsTitleAndDateAt360Font2() = assertSessionRow(fontScale = 2f)

    @Test
    fun compactLiftKeepsNameAndTargetLabelAt360Font1() = assertCompactLift(fontScale = 1f)

    @Test
    fun compactLiftKeepsNameAndTargetLabelAt360Font2() = assertCompactLift(fontScale = 2f)

    private fun assertSessionRow(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                    LocalWeightUnit provides WeightUnit.LBS,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(360.dp, 800.dp)) {
                            SessionLogRow(
                                title = TITLE,
                                dateLabel = DATE,
                                workingSets = 12,
                                work = SetWork(volumeKg = 227.0, bodyweightReps = 0),
                                durationMinutes = 48,
                                onClick = {},
                                onRepeat = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(SessionLogTags.ROW).assertIsDisplayed()
        compose.onNodeWithTag(SessionLogTags.ROW)
            .assertTextContains("Lower", substring = true)
        compose.onNodeWithTag(SessionLogTags.ROW)
            .assertTextContains("squat", substring = true)
        compose.onNodeWithTag(SessionLogTags.ROW)
            .assertTextContains("24 Aug", substring = true)
        compose.onNodeWithText("SETS").assertIsDisplayed()
        compose.onNodeWithText("MIN").assertIsDisplayed()
    }

    private fun assertCompactLift(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                    LocalWeightUnit provides WeightUnit.LBS,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(360.dp, 800.dp)) {
                            CompactLiftRow(
                                exercise = LONG_LIFT,
                                sets = 4,
                                reps = 6,
                                restSeconds = 120,
                                canMoveUp = true,
                                canMoveDown = true,
                                expanded = true,
                                onToggle = {},
                                onMoveUp = {},
                                onMoveDown = {},
                                onRemove = {},
                                onSwap = {},
                                onStageTargets = { _, _, _, _ -> },
                                onCommitTargets = {},
                                targetWeightKg = 100.0,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(CompactLiftTags.ROW).assertIsDisplayed()
        compose.onNodeWithTag(CompactLiftTags.NAME, useUnmergedTree = true)
            .assertTextContains("Romanian", substring = true)
        compose.onNodeWithText(CompactLiftCopy.TARGET_WEIGHT).assertIsDisplayed()
        compose.onNodeWithText("lbs").assertIsDisplayed()
        compose.onNodeWithText("kg").assertDoesNotExist()
    }

    private companion object {
        const val TITLE = "Lower body · heavy squat emphasis"
        const val DATE = "24 Aug 2026"
        val LONG_LIFT = Exercise(
            id = "lift-rdl",
            name = "Barbell Romanian deadlift from deficit",
            muscleGroup = "Hamstrings",
            notes = "",
            isCustom = true,
            equipment = EquipmentType.BARBELL,
            loadType = LoadType.EXTERNAL,
        )
    }
}
