package com.sinura.personaltrainer.ui.exercise

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.ExerciseFloorStats
import com.sinura.personaltrainer.domain.ExerciseFloorStatsCalculator
import com.sinura.personaltrainer.domain.FloorStatCopy
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags
import com.sinura.personaltrainer.ui.workout.showFloor
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.QuantityFormat
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Packet W1d: Best set and Volume are one tap away in the lift's Details.
 *
 * At large text (font 1.6 and above) the floor's stats row keeps Last alone, before and after
 * the first working set, so that set never moves the entry (owner decision of 23 September 2026,
 * ADR-030). The other two cells have to be somewhere, and Details — the lift's picture on the
 * floor — only read finished sessions: it could not show them at all. Now, once the workout in
 * progress holds a working set of the lift (the floor's own rule for when Best and Volume
 * appear), Details leads with them under "Session in progress": in the floor's words, from the
 * floor's calculator, without the Last cell the floor keeps. Best set is the floor's standing
 * best, `Today` only when a set this session beat it.
 *
 * The card is optional: a workout in progress that cannot be read leaves Details as it was.
 *
 * ADR-032's matrix, each frame written to `app/build/screen-renders/w1d/`: the two cells must be
 * reachable in every one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class ExerciseDetailThisWorkoutRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ExerciseDetailViewModel>()

    /**
     * The real main looper, as on the phone, not an unconfined Main: Details computes its history
     * on the compute dispatcher, and an unconfined Main would carry that work's results — and the
     * screen's state writes with them — onto a background thread the composition then lays out on.
     */
    @Before
    fun setUp() {
        deps = graph()
    }

    /** Cancelled on the main looper and drained there: joining from it with runBlocking would wait on itself. */
    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        viewModels.clear()
        deps.close()
    }

    private fun graph(workoutDaoDecorator: (WorkoutDao) -> WorkoutDao = { it }): FakeAppDependencies =
        FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), workoutDaoDecorator = workoutDaoDecorator).also {
            runBlocking { it.preferencesRepository.setWeightUnit(WeightUnit.LBS) }
        }

    @Test
    fun detailsShowsTheSessionsBestAndVolumeAt360By640() = showsTheSession(360, 640, 1f)

    @Test
    fun detailsShowsTheSessionsBestAndVolumeAt360By640Font16() = showsTheSession(360, 640, 1.6f)

    @Test
    fun detailsShowsTheSessionsBestAndVolumeAt360By640Font20() = showsTheSession(360, 640, 2f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun detailsShowsTheSessionsBestAndVolumeAt412By840() = showsTheSession(412, 840, 1f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun detailsShowsTheSessionsBestAndVolumeAt412By840Font16() = showsTheSession(412, 840, 1.6f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun detailsShowsTheSessionsBestAndVolumeAt412By840Font20() = showsTheSession(412, 840, 2f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun detailsShowsTheSessionsBestAndVolumeInLandscape() = showsTheSession(800, 360, 1f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun detailsShowsTheSessionsBestAndVolumeInLandscapeFont16() = showsTheSession(800, 360, 1.6f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun detailsShowsTheSessionsBestAndVolumeInLandscapeFont20() = showsTheSession(800, 360, 2f)

    /**
     * A standing best from an earlier session outranks today's set: the card shows it, as the
     * floor does, with the kind of record it is rather than `Today`. The heading claims the
     * session, not today's numbers.
     */
    @Test
    fun aStandingBestFromHistoryShowsWithItsRecordKind() {
        val (sessionId, vm) = seed(lastTimeLb = 150.0, lastTimeReps = 10)
        show(vm, 360, 640, 1f)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.history.sessions.isNotEmpty() }
        assertTheSession(sessionId, "standing-best-360x640-font1.0", best = listOf("Best set · Est. 1RM", "150 × 10"))
    }

    /** A lift's first session: nothing finished yet, but the session's sets are the floor's and show. */
    @Test
    fun aFirstSessionShowsTheSessionsBestAndVolumeAboveNothingLoggedYet() {
        val (sessionId, vm) = seed(lastTimeLb = null)
        show(vm, 360, 640, 2f)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.exercise != null }
        // Nothing finished to beat, so the record names its kind rather than "Today".
        assertTheSession(sessionId, "first-session-360x640-font2.0", best = listOf("Best set · Est. 1RM", "135 × 10"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Nothing logged yet"))
        compose.onNode(hasText("Nothing logged yet")).assertIsDisplayed()
    }

    /**
     * Before the first working set of the lift this session, the floor shows Last alone at every
     * size, and Details shows no card: its Best would be last time's and its Volume empty, under a
     * heading that says this session. The first working set brings it.
     */
    @Test
    fun noCardBeforeTheFirstWorkingSetThenTheFirstBringsIt() {
        val (sessionId, vm) = seed(lastTimeLb = 90.0, today = emptyList())
        show(vm, 360, 640, 1f)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.history.sessions.isNotEmpty() }
        assertNoCard("before the first working set", "before-first-set-360x640-font1.0")
        runBlocking {
            deps.workoutRepository.logSet(sessionId = sessionId, exerciseId = LIFT, weightKg = WeightConverter.lbsToKg(135.0), reps = 10, rpe = null, isWarmup = false)
        }
        assertTheSession(sessionId, "after-first-set-360x640-font1.0", best = listOf("Best set · Today", "135 × 10"))
    }

    /** A warm-up is preparation, not the session's work: still no card. */
    @Test
    fun aWarmupAloneShowsNoCard() {
        val (_, vm) = seed(lastTimeLb = 90.0, today = listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(45.0), reps = 10, isWarmup = true)))
        show(vm, 360, 640, 1f)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.history.sessions.isNotEmpty() }
        assertNoCard("after a warm-up alone", "warmup-only-360x640-font1.0")
    }

    /** A workout in progress that does not hold this lift has nothing of it to show. */
    @Test
    fun aWorkoutInProgressWithoutThisLiftShowsNoCard() {
        runBlocking {
            seedTestWorkout(
                deps = deps, exerciseId = LIFT, exerciseName = "Leg Extension", routineName = "Lower B",
                targetReps = 10, targetWeightKg = WeightConverter.lbsToKg(90.0),
                loggedSets = listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(90.0), reps = 5)), finish = true,
            )
            seedTestWorkout(
                deps = deps, exerciseId = "leg-curl", exerciseName = "Leg Curl", routineId = "upper-routine", routineName = "Upper A",
                targetReps = 10, targetWeightKg = WeightConverter.lbsToKg(135.0),
                loggedSets = listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(135.0), reps = 10)),
            )
        }
        val vm = viewModel()
        show(vm, 360, 640, 1f)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.history.sessions.isNotEmpty() }
        assertNoCard("for a workout in progress without this lift", "other-lift-360x640-font1.0")
    }

    /**
     * A backdated activity set heavier than anything else is History's record, and counts in
     * Details' own records, but it is not the floor's record to beat: the floor judges Best set
     * against workouts only (WorkoutRepository.historyBefore), and so does the card.
     */
    @Test
    fun aHeavierBackdatedActivityDoesNotChangeTheSessionsBest() {
        val (sessionId, vm) = seed(lastTimeLb = 90.0)
        val now = JvmTime.captureNow()
        val write = runBlocking {
            deps.confirmActivity(
                ActivityDraft(
                    status = ActivityStatus.COMPLETED,
                    origin = ActivityOrigin.BACKDATED,
                    title = "Make-up legs",
                    performedStart = now,
                    performedEnd = now,
                    blocks = listOf(backdatedLegExtension(completedAtMs = now.instantMillis + 1_000L, lb = 200.0, reps = 5)),
                ),
                now,
            )
        }
        assertTrue(write is ActivityWrite.Accepted)
        show(vm, 360, 640, 1f)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.history.sessions.any { it.kind == HistoryKind.ACTIVITY } }
        assertTheSession(sessionId, "activity-not-a-record-360x640-font1.0", best = listOf("Best set · Today", "135 × 10"))
    }

    /**
     * The workout in progress cannot be read (its read fails, which the repository reports by
     * emitting nothing): Details still loads, with its history and without the card. On fc6b6c57
     * it waited on that read and spun for good.
     */
    @Test
    fun aWorkoutInProgressThatCannotBeReadLeavesDetailsLoaded() {
        deps.close()
        deps = graph { real ->
            object : WorkoutDao by real {
                override fun observeInProgressSession(): Flow<WorkoutSessionEntity?> = flow { error("the in-progress read failed") }
            }
        }
        val (_, vm) = seed(lastTimeLb = 90.0)
        compose.showFloor(fontScale = 1f) {
            Box(modifier = Modifier.width(360.dp).height(640.dp).background(Pit)) {
                ExerciseDetailScreen(onBack = {}, onOpenSession = {}, onOpenActivity = {}, viewModel = vm)
            }
        }
        val loaded = runCatching { compose.waitUntil(LOAD_WAIT_MS) { !vm.uiState.value.isLoading && vm.uiState.value.history.sessions.isNotEmpty() } }.isSuccess
        compose.waitForIdle()
        capture("unreadable-workout-360x640-font1.0")
        assertTrue("Details must load when the workout in progress cannot be read", loaded)
        // The screen past its spinner: its first section, the lift's records.
        compose.onNode(hasText("RECORDS")).assertIsDisplayed()
        assertNoCard("when the workout in progress cannot be read", frame = null)
    }

    /** Opened from History with no workout running, Details is exactly as before. */
    @Test
    fun withNoWorkoutInProgressDetailsShowsNoCard() {
        runBlocking {
            seedTestWorkout(
                deps = deps, exerciseId = LIFT, exerciseName = "Leg Extension", routineName = "Lower B",
                targetReps = 10, targetWeightKg = WeightConverter.lbsToKg(90.0),
                loggedSets = listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(90.0), reps = 5)), finish = true,
            )
        }
        val vm = viewModel()
        show(vm, 360, 640, 1f)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.history.sessions.isNotEmpty() }
        assertNoCard("without a workout", "no-workout-360x640-font1.0")
    }

    /**
     * The card is not the floor: its cells sit flush with its heading, and a label is as tall as
     * its words. The floor's cells are inset because they are tapped, and its side-by-side labels
     * reserve a second line to hold the entry still; neither reason holds here. Side by side the
     * two numbers still share a line.
     */
    @Test
    fun theCardsCellsSitFlushWithItsHeadingAndReserveNoBlankLine() {
        val (_, vm) = seed(lastTimeLb = 90.0)
        show(vm, 360, 640, 1f)
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag(WorkoutTestTags.STAT_BEST).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        capture("layout-360x640-font1.0")
        assertCardLayout()
    }

    private fun showsTheSession(widthDp: Int, heightDp: Int, fontScale: Float) {
        val (sessionId, vm) = seed(lastTimeLb = 90.0)
        show(vm, widthDp, heightDp, fontScale)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.history.sessions.isNotEmpty() }
        assertTheSession(sessionId, "details-${widthDp}x$heightDp-font$fontScale", best = listOf("Best set · Today", "135 × 10"))
        assertCardLayout()
    }

    /**
     * Best and Volume are shown, reachable, under "Session in progress", in the floor's words and
     * TalkBack's, and the Last cell the floor keeps is not repeated.
     */
    private fun assertTheSession(sessionId: String, frame: String, best: List<String>) {
        val found = runCatching {
            compose.waitUntil(STATS_WAIT_MS) { compose.onAllNodesWithTag(WorkoutTestTags.STAT_BEST).fetchSemanticsNodes().isNotEmpty() }
        }.isSuccess
        compose.waitForIdle()
        capture(frame)
        assertTrue("Details must show the session's Best set and Volume once a working set of the lift is logged", found)
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(ExerciseDetailTags.THIS_WORKOUT))
        assertEquals("the card's heading", listOf(KICKER), cardTexts().filter { SemanticsProperties.Heading in it.config }.map { it.words() })
        val floor = floorStats(sessionId)
        val volume = floor.volumeColumn(WeightUnit.LBS)
        val volumeValue = "${QuantityFormat.formatVolumeNumber(floor.work.volumeKg, WeightUnit.LBS)} ${volume.label}"
        val expected = mapOf(
            WorkoutTestTags.STAT_BEST to listOf(floor.bestSet.label + FloorStatCopy.DETAIL_JOIN + floor.bestSet.detail, floor.bestSet.value),
            WorkoutTestTags.STAT_VOLUME to listOf(FloorStatCopy.VOLUME + FloorStatCopy.DETAIL_JOIN + FloorStatCopy.VOLUME_DETAIL, volumeValue),
        )
        val spoken = mapOf(
            WorkoutTestTags.STAT_BEST to floor.bestSet.spoken,
            WorkoutTestTags.STAT_VOLUME to "Volume this exercise, $volumeValue",
        )
        // The fixture's own numbers, so a calculator change cannot pass unnoticed either.
        assertEquals(best, expected.getValue(WorkoutTestTags.STAT_BEST))
        assertEquals(listOf("Volume · this exercise", "1,350 lb"), expected.getValue(WorkoutTestTags.STAT_VOLUME))
        expected.forEach { (tag, words) ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
            val cell = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            assertEquals("$tag reads as the floor's", words, cell.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
            assertEquals("$tag speaks as the floor's", listOf(spoken.getValue(tag)), cell.config.getOrNull(SemanticsProperties.ContentDescription))
        }
        assertTrue("the Last cell stays on the floor", compose.onAllNodesWithTag(WorkoutTestTags.STAT_LAST).fetchSemanticsNodes().isEmpty())
    }

    /** The card does not appear, given long enough that it would have: the read is Room's, on its own threads. */
    private fun assertNoCard(why: String, frame: String?) {
        val appeared = runCatching {
            compose.waitUntil(ABSENCE_WAIT_MS) { compose.onAllNodesWithTag(ExerciseDetailTags.THIS_WORKOUT).fetchSemanticsNodes().isNotEmpty() }
        }.isSuccess
        compose.waitForIdle()
        if (frame != null) capture(frame)
        assertFalse("Details must show no session card $why", appeared)
        listOf(WorkoutTestTags.STAT_LAST, WorkoutTestTags.STAT_BEST, WorkoutTestTags.STAT_VOLUME).forEach { tag ->
            assertTrue("$tag must not show $why", compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty())
        }
    }

    /**
     * The first cell's label starts at the heading's left edge, and side by side the second starts
     * one hairline after the first cell; each label is exactly as tall as its lines; side by side,
     * the two numbers end on one line.
     */
    private fun assertCardLayout() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(WorkoutTestTags.STAT_VOLUME))
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(ExerciseDetailTags.THIS_WORKOUT))
        val heading = cardTexts().single { SemanticsProperties.Heading in it.config }
        val cells = listOf(WorkoutTestTags.STAT_BEST, WorkoutTestTags.STAT_VOLUME).map { tag ->
            compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { SemanticsProperties.Text in it.config }
                .sortedBy { it.positionInRoot.y }
        }
        val (best, volume) = cells.map { it.last() }
        val sideBySide = best.boundsInRoot.right <= volume.boundsInRoot.left
        val bestCell = compose.onNodeWithTag(WorkoutTestTags.STAT_BEST).fetchSemanticsNode()
        val hairline = with(bestCell.layoutInfo.density) { Metrics.hairline.toPx() }
        cells.forEachIndexed { index, texts ->
            val (label, value) = texts
            val layout = label.laidOut()
            val start = if (index == 1 && sideBySide) bestCell.boundsInRoot.right + hairline else heading.boundsInRoot.left
            assertEquals("\"${label.words()}\" starts at its cell's edge, with no inset", start, label.boundsInRoot.left, 1f)
            assertEquals(
                "\"${label.words()}\" is as tall as its ${layout.lineCount} line(s), with no blank line reserved",
                layout.getLineBottom(layout.lineCount - 1),
                label.size.height.toFloat(),
                1f,
            )
            assertTrue("\"${value.words()}\" is laid out", value.size.height > 0)
        }
        if (sideBySide) {
            assertEquals("side by side, the two numbers end on one line", best.boundsInRoot.bottom, volume.boundsInRoot.bottom, 1f)
        }
    }

    /** Every text inside the card, in the unmerged tree, top first. */
    private fun cardTexts(): List<SemanticsNode> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(ExerciseDetailTags.THIS_WORKOUT)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { SemanticsProperties.Text in it.config }
            .sortedBy { it.positionInRoot.y }

    private fun SemanticsNode.words(): String = config[SemanticsProperties.Text].joinToString { it.text }

    private fun SemanticsNode.laidOut(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        return layouts.single()
    }

    /** What the floor's stats row computes for the same lift and session. */
    private fun floorStats(sessionId: String): ExerciseFloorStats = runBlocking {
        val session = checkNotNull(deps.workoutRepository.getSession(sessionId))
        ExerciseFloorStatsCalculator.of(
            session = session,
            exerciseId = LIFT,
            lastPerformance = null,
            priorHistory = deps.workoutRepository.historyBefore(sessionId, listOf(LIFT)).getValue(LIFT),
            unit = WeightUnit.LBS,
        )
    }

    /**
     * Leg extension in a workout in progress with [today]'s sets (135 lb × 10 unless named), after a
     * finished session of one [lastTimeLb] × [lastTimeReps] set when one is named.
     */
    private fun seed(
        lastTimeLb: Double?,
        lastTimeReps: Int = 5,
        today: List<TestSetInput> = listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(135.0), reps = 10)),
    ): Pair<String, ExerciseDetailViewModel> {
        val sessionId = runBlocking {
            val seed: suspend (List<TestSetInput>, Boolean) -> String = { sets, finish ->
                seedTestWorkout(
                    deps = deps, exerciseId = LIFT, exerciseName = "Leg Extension", routineName = "Lower B",
                    targetReps = 10, targetWeightKg = WeightConverter.lbsToKg(135.0), loggedSets = sets, finish = finish,
                ).session.id
            }
            if (lastTimeLb != null) seed(listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(lastTimeLb), reps = lastTimeReps)), true)
            seed(today, false)
        }
        return sessionId to viewModel()
    }

    private fun backdatedLegExtension(completedAtMs: Long, lb: Double, reps: Int) = StrengthBlock(
        id = "blk-legs",
        sortOrder = 0,
        exerciseId = LIFT,
        exerciseName = "Leg Extension",
        loadType = LoadType.EXTERNAL,
        equipment = EquipmentType.MACHINE,
        muscles = emptyList(),
        sets = listOf(
            StrengthSet(
                id = "set-act-legs",
                setNumber = 1,
                weightKg = WeightConverter.lbsToKg(lb),
                reps = reps,
                rpe = null,
                isWarmup = false,
                completedAtMs = completedAtMs,
            ),
        ),
    )

    private fun viewModel() = ExerciseDetailViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(mapOf("exerciseId" to LIFT)),
        container = deps,
    ).also(viewModels::add)

    private fun show(vm: ExerciseDetailViewModel, widthDp: Int, heightDp: Int, fontScale: Float) {
        compose.showFloor(fontScale = fontScale) {
            Box(modifier = Modifier.width(widthDp.dp).height(heightDp.dp).background(Pit)) {
                ExerciseDetailScreen(onBack = {}, onOpenSession = {}, onOpenActivity = {}, viewModel = vm)
            }
        }
        compose.waitUntil(WAIT_MS) { !vm.uiState.value.isLoading }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        // Robolectric never delivers the draw callback captureToImage waits on; draw the
        // window ourselves, as WorkoutFloorRenderTest does.
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val out = Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(out))
            out
        }
        val out = File("build/screen-renders/w1d").apply { mkdirs() }
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    private companion object {
        const val LIFT = "leg-extension"
        const val WAIT_MS = 20_000L

        /** The heading, as the kicker draws it: the app's own words for a live workout (StartOptionsSheet). */
        const val KICKER = "SESSION IN PROGRESS"

        /** How long a Details that will never show the cells is given to show them. */
        const val STATS_WAIT_MS = 5_000L

        /** How long a card that must not appear is given to appear. */
        const val ABSENCE_WAIT_MS = 3_000L

        /** How long a Details that cannot read the workout in progress is given to load. */
        const val LOAD_WAIT_MS = 5_000L
    }
}
