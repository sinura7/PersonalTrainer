package com.sinura.personaltrainer.ui.history

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.LayoutDirection
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.StepperRepeat
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.summary.SavedWorkRenderHost
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.holdingTheClock
import com.sinura.personaltrainer.ui.workout.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shipping Set Edit modal, with real native windows and resource fonts.
 *
 * Independent thumbnails precede the modal; every identity, sign, digit and unit must
 * draw inside its actual clip. Pointer actions use complete 48 dp controls and the real
 * scroll ancestors. These component callbacks do not stand in for the separate restored
 * BackupService -> shipping Detail -> completed VM write/inventory counters.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
class SetEditSheetRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var host: SavedWorkRenderHost
    private var open by mutableStateOf(false)
    private var exercise by mutableStateOf(catalogExercise("Barbell Back Squat").copy(name = LONG_NAME))
    private var initial by mutableStateOf<SetLog?>(original("sheet-strength", 6, null))
    private var unit by mutableStateOf(WeightUnit.KG)
    private var direction = LayoutDirection.Ltr
    private var prefillWeight by mutableStateOf(30.0)
    private var prefillReps by mutableStateOf(5)
    private val saved = mutableListOf<Saved>()
    private val deleted = mutableListOf<String?>()
    private var dismissed = 0

    @Before fun setUp() {
        host = SavedWorkRenderHost(compose = compose, contentTag = { if (open) SetEditTestTags.CONTENT else REFERENCES }) {
            "exercise=$exercise; initial=$initial; unit=$unit; direction=$direction; saved=$saved; deleted=$deleted; dismissed=$dismissed"
        }
    }

    @Test fun smallDefaultText() = matrix("360x640-font10", 1f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallLargeText() = matrix("360x640-font16", 1.6f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun smallLargestText() = matrix("360x640-font20", 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardDefaultText() = matrix("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standardLargeText() = matrix("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standardLargestText() = matrix("412x840-font20", 2f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscapeDefaultText() = matrix("800x360-font10", 1f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeLargeText() = matrix("800x360-font16", 1.6f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeLargestText() = matrix("800x360-font20", 2f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun tabletDefaultText() = matrix("600x960-font10", 1f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun tabletLargeText() = matrix("600x960-font16", 1.6f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tabletLargestText() = matrix("600x960-font20", 2f)
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun narrowLargestText() = matrix("320x640-font20", 2f)
    @Test @Config(qualifiers = "he-ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlLargestTextAndReducedMotion() = matrix("360x640-rtl-font20", 2f, LayoutDirection.Rtl)

    private fun matrix(profile: String, font: Float, layoutDirection: LayoutDirection = LayoutDirection.Ltr) =
        host.evidence("set-editor-$profile") {
            host.font = font
            direction = layoutDirection
            val config = RuntimeEnvironment.getApplication().resources.configuration
            assertEquals("actual native resource font", font, config.fontScale, .001f)
            assertEquals("actual native resource direction", if (layoutDirection == LayoutDirection.Rtl) View.LAYOUT_DIRECTION_RTL
                else View.LAYOUT_DIRECTION_LTR, config.layoutDirection)
            mount()
            val expected = host.still(REFERENCE_ART, ThumbSize.header)
            val unrelated = host.still(UNRELATED_ART, ThumbSize.header)
            showEditor()
            assertIdentity(expected, unrelated)
            assertNumeral(TYPE_WEIGHT, "102.5", "kg")
            assertNumeral(TYPE_REPS, "6")
            signedControls(listOf("−2.5", "+2.5", "−1", "+1"))
            host.touch(control("+2.5"))
            assertNumeral(TYPE_WEIGHT, "105", "kg")
            host.touch(control("−2.5"))
            host.touch(control("+1"))
            assertNumeral(TYPE_REPS, "7")
            host.touch(control("−1"))
            effortTenSelectsAndClears()
            host.touch(effort(8))
            effort(8).assertIsOn()
            host.touch(warmup())
            warmup().assertIsOn()
            host.touch(warmup())
            warmup().assertIsOff()
            assertSheetActions()
            host.capture("strength-values-effort-and-full-actions")
            host.touch(control("Save"))
            assertEquals(listOf(Saved(102.5, 6, 8, false, null)), saved)

            initial = original("sheet-time", -3, 45)
            showEditor()
            assertIdentity(expected, unrelated)
            compose.onNodeWithTag(TYPE_REPS).assertDoesNotExist()
            compose.onNodeWithTag(TYPE_TIME).assertContentDescriptionEquals("Time 45 seconds")
            assertNumeral(TYPE_TIME, "45", "s")
            signedControls(listOf("−2.5", "+2.5", "−5", "+5"))
            host.touch(control("+5"))
            assertNumeral(TYPE_TIME, "50", "s")
            host.touch(control("−5"))
            assertNumeral(TYPE_TIME, "45", "s")
            effortTenSelectsAndClears()
            assertSheetActions()
            host.capture("time-values-effort-and-full-actions")
            host.touch(control("Save"))
            assertEquals(Saved(102.5, -3, null, false, 45), saved.last())
            assertEquals(2, saved.size)
            assertTrue("inspecting action targets did not delete an original", deleted.isEmpty())
            showEditor()
            host.touch(host.tag(SetEditTestTags.CANCEL))
            assertEquals("actual quiet Cancel dismisses without saving", 1, dismissed)
            assertEquals(2, saved.size)
        }

    @Test fun unknownImageKeysUseTheExistingDecorativeFallback() = host.evidence("set-editor-art-fallback") {
        exercise = exercise.copy(id = "custom-fallback", imageKey = "unknown-image-key", isCustom = true)
        initial = checkNotNull(initial).copy(exerciseId = exercise.id, exerciseName = exercise.name)
        mount()
        val expected = host.still(REFERENCE_ART, ThumbSize.header)
        val unrelated = host.still(UNRELATED_ART, ThumbSize.header)
        showEditor()
        assertIdentity(expected, unrelated)
        host.touch(host.tag(SetEditTestTags.CANCEL))
        assertTrue(saved.isEmpty())
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun maximumCapturedSecondsKeepTheirLastDigitAndUnitAndCannotOverflow() =
        host.evidence("set-editor-maximum-time-320-font20") {
            host.font = 2f
            initial = original("maximum-time", -7, Int.MAX_VALUE)
            mount()
            showEditor()
            assertNumeral(TYPE_TIME, Int.MAX_VALUE.toString(), "s")
            host.touch(control("+5"))
            assertNumeral(TYPE_TIME, Int.MAX_VALUE.toString(), "s")
            host.touch(control("Save"))
            assertEquals(Saved(102.5, -7, null, false, Int.MAX_VALUE), saved.single())
        }

    @Test fun changingExactSetIdsDropsThePreviousOpenKeypadAndTimeDraft() =
        host.evidence("set-editor-exact-set-id-reset") {
            initial = original("first-time", -3, 45)
            mount()
            showEditor()
            compose.holdingTheClock {
                host.touch(host.tag(TYPE_TIME))
                compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("90")
                host.drain()
                compose.runOnUiThread { initial = original("second-time", -7, 2).copy(setNumber = 7) }
                host.drain()
                compose.onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
                assertNumeral(TYPE_TIME, "2", "s")
            }
            host.readable(host.words("Edit set 7"), "Edit set 7")
            host.touch(control("Save"))
            assertEquals(Saved(102.5, -7, null, false, 2), saved.single())
        }

    @Test fun changingAddExerciseIdsDropsThePreviousOpenKeypadAndPrefill() =
        host.evidence("set-editor-add-exercise-id-reset") {
            initial = null
            mount()
            showEditor()
            compose.holdingTheClock {
                host.touch(host.tag(TYPE_WEIGHT))
                compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("90")
                host.drain()
                compose.runOnUiThread {
                    exercise = catalogExercise("Front Squat").copy(name = LONG_NAME)
                    prefillWeight = 10.0
                    prefillReps = 7
                }
                host.drain()
                compose.onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
                assertNumeral(TYPE_WEIGHT, "10", "kg")
                assertNumeral(TYPE_REPS, "7")
            }
            compose.onNodeWithTag(TYPE_TIME).assertDoesNotExist()
            compose.onNodeWithTag(SetEditTestTags.DELETE).assertDoesNotExist()
            host.touch(control("Save"))
            assertEquals(Saved(10.0, 7, null, false, null), saved.single())
        }

    @Test fun aHeldSignedPlateUsesEachNewValueAndReleaseAddsNoExtraStep() =
        host.evidence("set-editor-held-signed-plate") {
            mount()
            showEditor()
            val plate = host.action(control("+2.5"))
            compose.mainClock.autoAdvance = false
            val during: String
            try {
                plate.performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(StepperRepeat.HOLD_BEFORE_REPEAT_MS + StepperRepeat.REPEAT_MS * 3 + 100)
                during = compose.onNodeWithTag(TYPE_WEIGHT).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
                plate.performTouchInput { up() }
            } finally {
                compose.mainClock.autoAdvance = true
            }
            host.drain()
            compose.onNodeWithTag(TYPE_WEIGHT).assertContentDescriptionEquals(during)
            val actual = during.removePrefix("Weight ").removeSuffix(" kg").toDouble()
            assertTrue("a held plate advances through current values instead of repeating one captured target", actual >= 107.5)
            assertEquals("each repeat is the signed 2.5 kg increment", 0.0, (actual - 102.5) % 2.5, .0001)
            host.touch(control("Save"))
            assertEquals(actual, saved.single().weightKg, .0001)
        }

    @Test fun poundLabelsKeepTheUnitAndAFullDisplayStep() = host.evidence("set-editor-pound-step") {
        unit = WeightUnit.LBS
        mount()
        showEditor()
        val initialKg = checkNotNull(initial).weightKg
        val shown = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(initialKg, unit))
        assertNumeral(TYPE_WEIGHT, shown, "lb")
        signedControls(listOf("−5", "+5", "−1", "+1"))
        host.touch(control("+5"))
        host.touch(control("Save"))
        assertEquals(WeightConverter.incrementKg(initialKg, unit, 1), saved.single().weightKg, .0001)
    }

    @Test fun aSavedStopwatchWithOneHundredAndOneRepsNudgesToOneHundredAndTwo() =
        host.evidence("set-editor-historical-reps-101-actual-pointer-save") {
            initial = original("historical-stopwatch-101", 101, 45)
            mount()
            showEditor()
            compose.onNodeWithTag(TYPE_TIME).assertDoesNotExist()
            assertNumeral(TYPE_REPS, "101")
            host.capture("actual-original-101-reps-before-nudge")
            host.touch(control("+1"))
            host.capture("actual-rep-nudge-before-save")
            host.touch(control("Save"))
            // Retain the actual callback payload before checking the expected correction.
            host.capture("actual-saved-rep-stopwatch-callback")
            assertEquals(Saved(102.5, 102, 8, false, null), saved.single())
        }

    @Test fun aHistoricalRepKeypadAcceptsOneHundredAndOneExactly() =
        host.evidence("set-editor-historical-keypad-101") {
            initial = original("historical-typed-101", 8, 45)
            mount()
            showEditor()
            typeReps("101")
            assertNumeral(TYPE_REPS, "101")
            host.touch(control("Save"))
            host.capture("actual-historical-keypad-101-callback")
            assertEquals(Saved(102.5, 101, 8, false, null), saved.single())
        }

    @Test fun historicalRepKeypadAndNudgesKeepTheMaximumAndMinimumWholeValues() =
        host.evidence("set-editor-historical-rep-boundaries") {
            initial = original("historical-max-reps", 8, 45)
            mount()
            showEditor()
            typeReps(Int.MAX_VALUE.toString())
            assertNumeral(TYPE_REPS, Int.MAX_VALUE.toString())
            host.touch(control("+1"))
            assertNumeral(TYPE_REPS, Int.MAX_VALUE.toString())
            host.touch(control("−1"))
            assertNumeral(TYPE_REPS, (Int.MAX_VALUE - 1).toString())
            host.touch(control("+1"))
            host.touch(control("Save"))
            host.capture("actual-maximum-reps-callback")
            assertEquals(Saved(102.5, Int.MAX_VALUE, 8, false, null), saved.single())
            initial = original("historical-min-reps", 1, 45)
            showEditor()
            host.touch(control("−1"))
            assertNumeral(TYPE_REPS, "1")
            host.touch(control("+1"))
            assertNumeral(TYPE_REPS, "2")
            host.touch(control("−1"))
            host.touch(control("Save"))
            assertEquals(Saved(102.5, 1, 8, false, null), saved.last())
            assertEquals(2, saved.size)
        }

    @Test fun historicalRepKeypadRefusesInvalidAndOverflowTextWithoutSalvagingDigits() =
        host.evidence("set-editor-historical-rep-invalid-text") {
            initial = original("historical-invalid-reps", 101, 45)
            mount()
            showEditor()
            compose.holdingTheClock {
                host.touch(host.tag(TYPE_REPS))
                for (text in listOf("", "0", "-1", "1.5", "1,5", "1:01", "+1", "1e3", "abc", "2147483648")) {
                    val field = compose.onNodeWithTag(NumberEntryTags.FIELD)
                    field.performTextReplacement(text)
                    host.drain()
                    assertEquals("the keypad retains exactly authored text", text,
                        field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
                    host.reach(host.tag(NumberEntryTags.CONFIRM)).assertIsNotEnabled()
                    field.performImeAction()
                    host.drain()
                    compose.onNodeWithTag(NumberEntryTags.FIELD).assertExists()
                    assertTrue("invalid Set/IME never saves a component payload", saved.isEmpty())
                    host.capture("invalid-historical-reps-" + text.ifEmpty { "blank" }.replace(':', '_'))
                }
                host.touch(keypadCancel())
            }
            assertNumeral(TYPE_REPS, "101")
            host.touch(control("Save"))
            assertEquals(Saved(102.5, 101, 8, false, null), saved.single())
        }

    @Test fun addModeKeepsItsHundredRepNudgeAndTypedGuard() = host.evidence("set-editor-add-rep-100-guard") {
        initial = null
        prefillReps = 100
        mount()
        showEditor()
        assertNumeral(TYPE_REPS, "100")
        host.touch(control("+1"))
        assertNumeral(TYPE_REPS, "100")
        compose.holdingTheClock {
            host.touch(host.tag(TYPE_REPS))
            compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("101")
            host.drain()
            host.reach(host.tag(NumberEntryTags.CONFIRM)).assertIsNotEnabled()
            compose.onNodeWithTag(NumberEntryTags.FIELD).performImeAction()
            host.drain()
            compose.onNodeWithTag(NumberEntryTags.FIELD).assertExists()
            assertTrue(saved.isEmpty())
            compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("100")
            host.drain()
            host.touch(host.tag(NumberEntryTags.CONFIRM))
        }
        host.touch(control("−1"))
        assertNumeral(TYPE_REPS, "99")
        host.touch(control("+1"))
        assertNumeral(TYPE_REPS, "100")
        host.touch(effort(8))
        host.touch(control("Save"))
        host.capture("actual-add-100-guard-callback")
        assertEquals(Saved(30.0, 100, 8, false, null), saved.single())
    }

    private fun typeReps(text: String) {
        compose.holdingTheClock {
            host.touch(host.tag(TYPE_REPS))
            compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement(text)
            host.drain()
            host.touch(host.tag(NumberEntryTags.CONFIRM))
        }
    }

    private fun keypadCancel() = compose.onNode(hasText("Cancel") and hasAnyAncestor(
        dialog() and hasAnyDescendant(hasTestTag(NumberEntryTags.FIELD)),
    ))

    private fun mount() {
        compose.setContent {
            CompositionLocalProvider(LocalWeightUnit provides unit, LocalLayoutDirection provides direction) {
                PersonalTrainerTheme(reduceMotion = true) {
                    Column(
                        modifier = Modifier.fillMaxSize().testTag(REFERENCES).verticalScroll(rememberScrollState()).padding(Metrics.gutter),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                    ) {
                        Box(Modifier.testTag(REFERENCE_ART)) { ExerciseThumb(exercise = exercise, size = ThumbSize.header) }
                        Box(Modifier.testTag(UNRELATED_ART)) { ExerciseThumb(exercise = catalogExercise("Barbell Bench Press"), size = ThumbSize.header) }
                        PrimaryGymButton(text = "Open original", onClick = { open = true }, modifier = Modifier.testTag(OPEN))
                    }
                    if (open) SetEditSheet(
                        exercise = exercise,
                        initial = initial,
                        onSave = { kg, reps, rpe, warm, seconds ->
                            saved += Saved(kg, reps, rpe, warm, seconds)
                            open = false
                        },
                        onDelete = if (initial == null) null else ({
                            deleted += initial?.id
                            open = false
                        }),
                        onDismiss = { dismissed++; open = false },
                        prefillWeightKg = prefillWeight,
                        prefillReps = prefillReps,
                        loadClass = LoadClass.LOADED,
                    )
                }
            }
        }
        host.drain()
    }

    private fun showEditor() {
        host.touch(host.tag(OPEN))
        val pane = SemanticsMatcher("actual modal pane") { it.config.getOrNull(SemanticsProperties.PaneTitle) != null }
        compose.awaitThat("actual modal opens fully expanded without an Expand workaround", {
            compose.onAllNodes(pane, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInWindow }
        }) {
            compose.onAllNodes(pane, useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()?.let {
                it.boundsInWindow.height >= it.size.height - 1f
            } == true
        }
        assertTrue("the actual default sheet offers no partial-anchor Expand action",
            compose.onAllNodes(SemanticsMatcher("Expand") { it.config.getOrNull(SemanticsActions.Expand)?.action != null },
                useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        val content = host.tag(SetEditTestTags.CONTENT).fetchSemanticsNode()
        assertEquals("resource font reaches actual modal content", host.font, content.layoutInfo.density.fontScale, .001f)
        assertEquals("requested direction reaches actual modal content", direction, content.layoutInfo.layoutDirection)
        host.capture("actual-expanded-modal-" + initial?.id)
    }

    private fun assertIdentity(expected: List<Int>, unrelated: List<Int>) {
        host.readable(host.tag(SetEditTestTags.NAME, unmerged = true), exercise.name)
        val actual = host.still(SetEditTestTags.IDENTITY, ThumbSize.header)
        host.sameArt(actual, expected, unrelated)
        val art = host.tag("set-edit-art-" + exercise.id, unmerged = true).fetchSemanticsNode()
        assertTrue("exact-ID art is decorative", art.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty())
        assertTrue("exact-ID art adds no separate action", art.config.getOrNull(SemanticsActions.OnClick) == null)
        host.capture("complete-identity-" + initial?.id)
    }

    private fun assertNumeral(tag: String, value: String, suffix: String? = null) {
        val field = host.action(host.tag(tag))
        assertEquals(Role.Button, field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        val number = host.words(value, tag)
        host.readable(number, value, singleLine = true)
        if (suffix != null) {
            val unitNode = host.words(suffix, tag)
            host.readable(unitNode, suffix, singleLine = true)
            // Values stay before units even when their containing sheet is RTL.
            host.reach(number)
            val n = number.fetchSemanticsNode()
            val u = unitNode.fetchSemanticsNode()
            val numberLayout = number.textLayout()
            val unitLayout = unitNode.textLayout()
            assertTrue("every number digit draws before its complete unit", n.positionInWindow.x + numberLayout.getLineRight(0)
                <= u.positionInWindow.x + unitLayout.getLineLeft(0) + 1f)
            assertEquals("number and unit stay on a shared baseline",
                n.positionInWindow.y + numberLayout.firstBaseline, u.positionInWindow.y + unitLayout.firstBaseline, 1.5f)
        }
    }

    private fun signedControls(labels: List<String>) {
        labels.forEach { label ->
            host.action(control(label))
            host.readable(host.words(label), label, singleLine = true)
        }
    }

    private fun effortTenSelectsAndClears() {
        host.touch(effort(10))
        effort(10).assertIsOn()
        host.readable(host.words("10"), "10", singleLine = true)
        host.capture("actual-effort-ten-selected-" + initial?.id)
        host.touch(effort(10))
        for (value in listOf(6, 7, 8, 9, 10)) host.action(effort(value)).assertIsOff()
    }

    private fun assertSheetActions() {
        host.action(control("Save"))
        host.action(host.tag(SetEditTestTags.CANCEL))
        host.action(host.tag(SetEditTestTags.DELETE))
    }

    private fun dialog() = SemanticsMatcher("actual sheet dialog") { it.config.getOrNull(SemanticsProperties.IsDialog) != null }
    private fun control(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(dialog()))
    private fun effort(value: Int) = compose.onNode(hasText(value.toString()) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasAnyAncestor(dialog()))
    private fun warmup() = compose.onNode(hasText("Warm-up") and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasAnyAncestor(dialog()))

    private data class Saved(val weightKg: Double, val reps: Int, val rpe: Int?, val warmup: Boolean, val seconds: Int?)

    private companion object {
        const val LONG_NAME = "Paused barbell back squat with deliberate control through the complete movement"
        const val REFERENCES = "set-editor-reference-content"
        const val REFERENCE_ART = "set-editor-reference-art"
        const val UNRELATED_ART = "set-editor-unrelated-art"
        const val OPEN = "set-editor-open"
        const val TYPE_WEIGHT = "Type a weight"
        const val TYPE_REPS = "Type a rep count"
        const val TYPE_TIME = "Type a duration"

        fun catalogExercise(name: String): Exercise {
            val row = DefaultExercises.catalog().first { it.name == name }
            return Exercise(row.id, row.name, row.muscleGroup, "", false, row.equipment, row.loadType,
                row.movementKey, row.imageKey, row.credits)
        }

        fun original(id: String, reps: Int, duration: Int?) = SetLog(
            id, "set-editor-session", DefaultExercises.catalog().first { it.name == "Barbell Back Squat" }.id,
            LONG_NAME, 2, 102.5, reps, if (reps > 0) 8 else null, false, 1_790_856_001_000L, duration,
        )
    }
}
