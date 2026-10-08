package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.Pit
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Quiet's changed hierarchy through the real screen, ViewModel and Room. A manual
 * result is recorded through actual numeric, effort and primary-action touches at
 * every supported profile. A displayed semantics node is insufficient: each touch
 * must have its whole layout target inside its unobstructed viewport.
 *
 * The fixture uses the real Incline Dumbbell Bench Press catalog identity and keyed
 * artwork, rather than a blank-image synthetic lift. Initial frames permit matching
 * artwork/name review; semantics alone cannot certify which bitmap is drawn.
 * Numeric confirmation uses FloorTestKit's documented Robolectric clock/Set-action
 * seam. This is JVM/native-graphics evidence, not Android IME, AppNav or phone evidence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class QuietWorkoutFlowRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private var insertGate: CompletableDeferred<Unit>? = null
    private var failNextInsert = false
    private val runId = UUID.randomUUID().toString()
    private val observations = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override suspend fun insertSet(value: SetLogEntity) {
                        insertGate?.await()
                        if (failNextInsert) {
                            failNextInsert = false
                            throw IllegalStateException("Quiet UI injected write failure")
                        }
                        real.insertSet(value)
                    }
                }
            },
        )
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
            deps.preferencesRepository.markRestBatteryHintShown()
        }
    }

    @After
    fun tearDown() {
        insertGate?.complete(Unit)
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test fun portrait360_font10() = recordManualResult("360x640-font10", 1f)
    @Test fun portrait360_font16() = recordManualResult("360x640-font16", 1.6f)
    @Test fun portrait360_font20() = recordManualResult("360x640-font20", 2f)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun portrait412_font10() = recordManualResult("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun portrait412_font16() = recordManualResult("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun portrait412_font20() = recordManualResult("412x840-font20", 2f)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun landscape_font10() = recordManualResult("640x360-land-font10", 1f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun landscape_font16() = recordManualResult("640x360-land-font16", 1.6f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun landscape_font20() = recordManualResult("640x360-land-font20", 2f)

    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi")
    fun width600_font10() = recordManualResult("600x960-font10", 1f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi")
    fun width600_font16() = recordManualResult("600x960-font16", 1.6f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi")
    fun width600_font20() = recordManualResult("600x960-font20", 2f)

    @Test
    fun rtl_largeText() = recordManualResult("360x640-rtl-font20", 2f, LayoutDirection.Rtl)

    @Test @Config(qualifiers = "w360dp-h1600dp-xhdpi")
    fun entryAndVisibleReadinessPrecedeSavedWorkCoachAndStats() {
        val vm = openScreen(fontScale = 1f)
        compose.awaitThat("the first-set advice is ready", vm.tempoCoachTip::value) { vm.tempoCoachTip.value != null }
        val order = listOf(
            WorkoutTestTags.CURRENT_LIFT, WorkoutTestTags.SECTION_ENTRY,
            WorkoutTestTags.SECTION_RPE, WorkoutTestTags.SECTION_SET_HISTORY,
            WorkoutTestTags.TEMPO_COACH_CARD, WorkoutTestTags.SECTION_STATS,
        )
        val tops = order.map { compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        capture("hierarchy-360x1600", "all-context")
        assertEquals("recording precedes the secondary context", tops.sorted(), tops)
        assertEquals("each region has its own place", tops.size, tops.toSet().size)
        compose.onNode(
            hasTestTag(WorkoutTestTags.LOG_READINESS) and hasAnyAncestor(hasTestTag(WorkoutTestTags.SECTION_RPE)),
            useUnmergedTree = true,
        ).assert(hasText(EFFORT_MISSING))
        compose.onAllNodesWithTag(WorkoutTestTags.LOG_SET).assertCountEquals(1)
        assertNoFloatingCoach()
    }

    @Test
    fun savingFailureAndRetryKeepTheOriginalManualResultAndOnePrimaryAction() {
        val profile = "save-recovery-360x640"
        val vm = openScreen(fontScale = 1f)
        enterManual(vm, profile)
        val originalDraft = vm.uiState.value.draft
        val sessionId = checkNotNull(vm.uiState.value.session).id
        assertTrue(stored(vm).sets.isEmpty())
        failNextInsert = true
        val gate = CompletableDeferred<Unit>().also { insertGate = it }
        assertPrimaryReachable().performClick()
        compose.awaitThat("the real write is held", vm.uiState::value) {
            vm.uiState.value.save.phase == WorkoutSavePhase.SAVING && vm.uiState.value.entryLocked
        }
        val command = checkNotNull(vm.uiState.value.save.command)
        // The repository read takes a transaction too. Reading it while this write
        // deliberately holds that transaction would deadlock the test. Inspect the
        // published session now, then verify Room after the failed write is released.
        assertTrue(checkNotNull(vm.uiState.value.session).sets.isEmpty())
        assertPrimaryReachable(enabled = false)
        reveal(WorkoutTestTags.LOG_READINESS, profile)
        compose.onNodeWithTag(WorkoutTestTags.LOG_READINESS).assert(hasText("Saving your entry…"))
        reveal(WorkoutTestTags.rpeChoice(8), profile).assertIsNotEnabled()
        capture(profile, "saving-held")

        gate.complete(Unit)
        insertGate = null
        compose.awaitThat("the demonstrated write failure keeps a retryable set", vm.uiState::value) {
            vm.uiState.value.save.phase == WorkoutSavePhase.FAILED &&
                vm.primaryAction.value.kind == WorkoutPrimaryKind.RETRY_SAVE
        }
        assertEquals(originalDraft, vm.uiState.value.draft)
        assertEquals(command, vm.uiState.value.save.command)
        assertTrue(stored(vm).sets.isEmpty())
        reveal(WorkoutTestTags.LOG_READINESS, profile)
        compose.onNodeWithTag(WorkoutTestTags.LOG_READINESS).assert(hasText("Your entry is kept. Retry save below."))
        capture(profile, "failed-write")
        compose.onAllNodesWithTag(WorkoutTestTags.LOG_SET).assertCountEquals(1)
        assertPrimaryReachable().assert(hasText("Retry save")).performClick()
        awaitSaved(vm, sessionId)
        val saved = stored(vm).sets.single()
        assertEquals(command.setId, saved.id)
        assertManual(saved.weightKg, saved.reps, saved.rpe)
        assertEquals("retry must preserve the same frozen payload", command.values.weightKg, saved.weightKg, EPSILON)
        assertEquals(command.values.reps, saved.reps)
        assertEquals(command.values.rpe, saved.rpe)
        capture(profile, "retry-saved")
    }

    private fun recordManualResult(profile: String, fontScale: Float, direction: LayoutDirection = LayoutDirection.Ltr) {
        val vm = openScreen(fontScale, direction)
        val sessionId = checkNotNull(vm.uiState.value.session).id
        val selected = vm.uiState.value.selectedExerciseId
        assertEquals(BUILT_IN_NAME, checkNotNull(vm.uiState.value.session).exercises.single().exercise.name)
        assertNotNull(checkNotNull(vm.uiState.value.session).exercises.single().exercise.imageKey)
        capture(profile, "identity-and-entry")
        assertNoFloatingCoach()
        enterManual(vm, profile)
        capture(profile, "manual-ready")
        assertPrimaryReachable().performClick()
        awaitSaved(vm, sessionId)
        val saved = stored(vm).sets.single()
        assertEquals(sessionId, saved.sessionId)
        assertEquals(selected, saved.exerciseId)
        assertManual(saved.weightKg, saved.reps, saved.rpe)
        assertEquals("logging cannot advance the lift", selected, vm.uiState.value.selectedExerciseId)
        assertNull("the next working set needs its own effort", vm.uiState.value.draft.rpe)
        assertPrimaryReachable(enabled = false)
        reveal(WorkoutTestTags.LOG_READINESS, profile).assert(hasText(EFFORT_MISSING))
        capture(profile, "saved-next-draft")
        writeObservations(profile)
    }

    private fun enterManual(vm: ActiveWorkoutViewModel, profile: String) {
        assertPrimaryReachable(enabled = false)
        reveal(WorkoutTestTags.LOG_READINESS, profile).assert(hasText(EFFORT_MISSING))
        assertReadableReadiness()
        reveal(WorkoutTestTags.WEIGHT_STEPPER, profile)
        compose.withKeypad(compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER), "82.5")
        reveal(WorkoutTestTags.REPS_STEPPER, profile)
        compose.withKeypad(compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER), "12")
        compose.awaitThat("manual numbers reach the actual draft", vm.uiState::value) {
            abs(vm.uiState.value.draft.weightKg - kg(82.5)) < EPSILON && vm.uiState.value.draft.reps == 12
        }
        assertNull(vm.uiState.value.draft.rpe)
        assertTrue("draft editing cannot save a set", stored(vm).sets.isEmpty())
        reveal(WorkoutTestTags.rpeChoice(8), profile, minimumTargetDp = 48f).assertIsEnabled().performClick()
        compose.awaitThat("the actual effort touch preserves the manual values", vm.uiState::value) {
            vm.uiState.value.draft.rpe == 8
        }
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(8)).assertIsSelected()
        assertManual(vm.uiState.value.draft.weightKg, vm.uiState.value.draft.reps, vm.uiState.value.draft.rpe)
        reveal(WorkoutTestTags.LOG_READINESS, profile).assert(hasText(READY))
        assertReadableReadiness()
        assertPrimaryReachable().assert(hasText("82.5 lb × 12 · RPE 8"))
    }

    private fun openScreen(fontScale: Float, direction: LayoutDirection = LayoutDirection.Ltr): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            val seed = DefaultExercises.catalog().first { it.name == BUILT_IN_NAME }
            val lift = Exercise(
                id = seed.id, name = seed.name, muscleGroup = seed.muscleGroup, notes = "", isCustom = false,
                equipment = seed.equipment, loadType = seed.loadType, movementKey = seed.movementKey,
                imageKey = seed.imageKey, muscles = seed.credits,
            )
            // insertAll ignores an existing id, so seedTestWorkout retains this real
            // catalog row rather than replacing its keyed art with the synthetic default.
            deps.database.exerciseDao().insertAll(listOf(lift.toEntity()))
            seedTestWorkout(
                deps = deps, exerciseId = lift.id, exerciseName = lift.name,
                routineName = "Upper B", targetSets = 3, targetReps = 8,
                targetWeightKg = kg(60.0), restSeconds = 120,
            ).session.id
        }
        val vm = floorViewModel(deps, sessionId).also(viewModels::add)
        compose.showFloor(fontScale) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize()) {
                    ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = vm, restNotificationsEnabledOverride = true)
                }
            }
        }
        compose.awaitThat("the actual catalog lift is ready", vm.uiState::value) { FLOOR_LIFT_READY(vm.uiState.value) }
        compose.waitForIdle()
        awaitIllustration()
        return vm
    }

    private fun awaitSaved(vm: ActiveWorkoutViewModel, sessionId: String) {
        compose.awaitThat("one intended set is durable and the next entry is unlocked", vm.uiState::value) {
            val state = vm.uiState.value
            state.session?.id == sessionId && state.session?.sets?.size == 1 &&
                !state.entryLocked && state.draft.rpe == null
        }
        assertEquals(1, stored(vm).sets.size)
        assertFalse(stored(vm).sets.single().isWarmup)
    }

    private fun assertReadableReadiness() {
        val readiness = compose.onNodeWithTag(WorkoutTestTags.LOG_READINESS, useUnmergedTree = true)
        val layout = readiness.textLayout()
        val width = readiness.fetchSemanticsNode().size.width.toFloat()
        // GetTextLayoutResult rebuilds a wider paragraph for short text in Compose;
        // hasVisualOverflow alone reports that unused width as overflow (FloorTestKit).
        // Check every actual line and its final character against the rendered box.
        repeat(layout.lineCount) { line ->
            assertFalse("essential readiness cannot be ellipsized", layout.isLineEllipsized(line))
            assertTrue("readiness glyphs fit their actual box", layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= width + 1f)
        }
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
    }

    private fun awaitIllustration() {
        val still = compose.onNodeWithTag(WorkoutTestTags.DETAILS).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val density = compose.density.density
        // Stay inside the border and above the Details badge in either direction.
        // Ink here proves the async illustration painted; matching the correct pose
        // remains a visual review of the real catalog fixture's frame.
        val art = box(still.left + 8f * density, still.top + 8f * density, still.right - 8f * density, still.bottom - 32f * density)
        compose.awaitThat("the keyed exercise illustration has painted", { art }) {
            val frame = compose.drawWindow()
            try {
                frame.inkBox(art, Pit) != null
            } finally {
                frame.recycle()
            }
        }
    }

    private fun assertNoFloatingCoach() {
        compose.onAllNodes(
            hasTestTag(WorkoutTestTags.TEMPO_COACH_CARD) and !hasAnyAncestor(hasTestTag(WorkoutTestTags.CONTENT)),
        ).assertCountEquals(0)
    }

    private fun assertPrimaryReachable(enabled: Boolean = true): SemanticsNodeInteraction {
        val node = compose.onNodeWithTag(WorkoutTestTags.LOG_SET)
        val target = node.fetchSemanticsNode()
        val bounds = target.boundsInWindow
        val density = target.layoutInfo.density.density
        val window = compose.runOnIdle { compose.activity.window.decorView.width to compose.activity.window.decorView.height }
        val whole = bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f &&
            bounds.left >= -1f && bounds.top >= -1f && bounds.right <= window.first + 1f && bounds.bottom <= window.second + 1f
        observe("primary", "layout=${target.size} bounds=$bounds window=$window density=$density fullyVisible=$whole enabled=$enabled")
        assertTrue("the anchored primary must retain its full target", whole)
        assertTrue("the primary retains at least72dp", bounds.height / density + 0.01f >= 72f)
        node.assertIsDisplayed()
        return if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
    }

    /** Scroll only the actual content and accept only a whole, reachable target. */
    private fun reveal(tag: String, profile: String, minimumTargetDp: Float? = null): SemanticsNodeInteraction {
        val node = compose.onNodeWithTag(tag)
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
        for (attempt in 0..3) {
            val target = node.fetchSemanticsNode()
            val bounds = target.boundsInRoot
            val content = compose.onNodeWithTag(WorkoutTestTags.CONTENT).fetchSemanticsNode().boundsInRoot
            val density = target.layoutInfo.density.density
            val whole = bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f &&
                bounds.left >= content.left - 1f && bounds.right <= content.right + 1f &&
                bounds.top >= content.top - 1f && bounds.bottom <= content.bottom + 1f
            observe(tag, "attempt=$attempt bounds=$bounds layout=${target.size} content=$content fullyVisible=$whole")
            if (whole) {
                minimumTargetDp?.let {
                    assertTrue("$tag retains a48dp target", bounds.width / density + 0.01f >= it && bounds.height / density + 0.01f >= it)
                }
                return node.assertIsDisplayed()
            }
            capture(profile, "$tag-clipped-$attempt")
            check(attempt < 3) { "$tag remains clipped after three real content gestures" }
            check(content.height > target.size.height) { "No scrollable viewport can contain the whole $tag target" }
            compose.onNodeWithTag(WorkoutTestTags.CONTENT).performTouchInput {
                if (target.positionInRoot.y < content.top) swipeDown(durationMillis = 300)
                else swipeUp(durationMillis = 300)
            }
            compose.waitForIdle()
        }
        error("No reachable control: $tag")
    }

    private fun stored(vm: ActiveWorkoutViewModel) = checkNotNull(runBlocking {
        withTimeout(5_000) {
            deps.workoutRepository.getSession(checkNotNull(vm.uiState.value.session).id)
        }
    })

    private fun assertManual(weightKg: Double, reps: Int, effort: Int?) {
        assertEquals(kg(82.5), weightKg, EPSILON)
        assertEquals(12, reps)
        assertEquals(8, effort)
    }

    private fun observe(tag: String, detail: String) {
        observations += "$tag\t$detail"
        println("QUIET_WORKOUT run=$runId ${observations.last()}")
    }

    private fun writeObservations(profile: String) {
        evidenceDirectory(profile).resolve("reachability.tsv").writeText(observations.joinToString("\n", postfix = "\n"))
    }

    private fun evidenceDirectory(profile: String): File = File("build/screen-renders/quiet-workout-flow/$runId/$profile").also {
        check(it.exists() || it.mkdirs()) { "Cannot create Quiet render directory $it" }
    }

    private fun capture(profile: String, name: String) {
        writeObservations(profile)
        val frame = compose.drawWindow()
        try {
            evidenceDirectory(profile).resolve("$name.png").outputStream().use {
                check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot save $profile/$name" }
            }
        } finally {
            frame.recycle()
        }
    }

    private fun kg(lb: Double) = WeightConverter.toKg(lb, WeightUnit.LBS)

    private companion object {
        const val BUILT_IN_NAME = "Incline Dumbbell Bench Press"
        const val EFFORT_MISSING = "Choose effort to log this set."
        const val READY = "Ready to log your entry."
        const val EPSILON = 0.000001
    }
}
