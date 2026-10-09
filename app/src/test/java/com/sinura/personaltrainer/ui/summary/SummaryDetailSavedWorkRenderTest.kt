package com.sinura.personaltrainer.ui.summary

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.FilledSessionLift
import com.sinura.personaltrainer.domain.FilledSessionPrescription
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SummaryCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.history.FilledLiftCard
import com.sinura.personaltrainer.ui.history.FilledLiftCardTags
import com.sinura.personaltrainer.ui.history.SessionDetailScreen
import com.sinura.personaltrainer.ui.history.SessionDetailTestTags
import com.sinura.personaltrainer.ui.history.SessionDetailViewModel
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.isNear
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
 * Saved facts travel from real synthetic Room rows through the shipping Summary and detail
 * screens. Controlled read outcomes use the same Summary composable, so they also have to
 * fit actual native windows. These frames prove JVM rendering and interactions, not phone
 * touch feel, TalkBack speech, an Android restart, or system process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class SummaryDetailSavedWorkRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<ViewModel>()
    private lateinit var deps: FakeAppDependencies
    private lateinit var summaryModel: WorkoutSummaryViewModel
    private lateinit var detailModel: SessionDetailViewModel
    private lateinit var session: WorkoutSession
    private lateinit var exercise: Exercise
    private lateinit var unrelated: Exercise
    private var mountedLift by mutableStateOf<FilledSessionLift?>(null)
    private var shownLift: FilledSessionLift
        get() = checkNotNull(mountedLift)
        set(value) { mountedLift = value }
    private val surface = mutableStateOf(Surface.SUMMARY)
    private val controlled = mutableStateOf(WorkoutSummaryUiState())
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private var loadClass by mutableStateOf(LoadClass.LOADED)
    private val done = AtomicInteger(0)
    private val retries = AtomicInteger(0)
    private val openedSessions = mutableListOf<String>()
    private val openedExercises = mutableListOf<String>()
    private val editedSets = mutableListOf<SetLog>()
    private val addedExercises = mutableListOf<String>()
    private lateinit var host: SavedWorkRenderHost

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        host = SavedWorkRenderHost(
            compose = compose,
            contentTag = {
                when (surface.value) {
                    Surface.SUMMARY, Surface.STATE -> SummaryTags.CONTENT
                    Surface.DETAIL -> SessionDetailTestTags.CONTENT
                    Surface.CARD -> CARD_CONTENT
                    Surface.REFERENCES -> null
                }
            },
            facts = { "surface=${surface.value}\ndirection=${direction.value}\nreducedMotion=true\n" +
                "controlled=${controlled.value}\nsessionId=$SESSION_ID\nroutes=$openedSessions\n" +
                "exerciseRoutes=$openedExercises\neditIds=${editedSets.map { it.id }}\naddIds=$addedExercises\n" +
                "done=${done.get()}\nretry=${retries.get()}" },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After fun tearDown() {
        try { runBlocking { models.forEach { it.clearAndJoinForTest() } } }
        finally {
            deps.restTimerController.stop()
            dispatcher.scheduler.advanceUntilIdle()
            deps.close()
            Dispatchers.resetMain()
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun smallDefaultText() = matrix("360x640-font10", 1f)
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
    @Test @Config(qualifiers = "ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlLargestTextAndReducedMotion() = matrix("360x640-rtl-font20", 2f, LayoutDirection.Rtl)

    @Test
    fun sameMuscleAndFallbackImagesRemainBoundToExactExerciseIds() = host.evidence("exact-artwork-identities") {
        seed()
        val knownFront = DefaultExercises.catalog().first { it.name == "Front Squat" }
        val additional = listOf(
            ExerciseEntity(id = knownFront.id, name = EXERCISE_NAME, muscleGroup = exercise.muscleGroup,
                notes = "", isCustom = false, equipment = knownFront.equipment.name,
                loadType = knownFront.loadType.name, movementKey = knownFront.movementKey,
                imageKey = knownFront.imageKey, nameKey = EXERCISE_NAME.lowercase()),
            ExerciseEntity(id = "custom-same-name", name = EXERCISE_NAME, muscleGroup = exercise.muscleGroup,
                notes = "", isCustom = true, equipment = "OTHER", loadType = "EXTERNAL",
                movementKey = "squat", imageKey = null, nameKey = EXERCISE_NAME.lowercase()),
            ExerciseEntity(id = "restored-unknown-key", name = EXERCISE_NAME, muscleGroup = exercise.muscleGroup,
                notes = "", isCustom = true, equipment = "OTHER", loadType = "EXTERNAL",
                movementKey = "squat", imageKey = "not-a-real-catalog-key", nameKey = EXERCISE_NAME.lowercase()),
        )
        runBlocking {
            deps.database.exerciseDao().insertAll(additional)
            additional.forEachIndexed { index, row ->
                deps.database.workoutDao().insertSessionExercises(listOf(
                    SessionExerciseEntity("identity-session-lift-$index", SESSION_ID, row.id, index + 1, 1, 3, 2.0, 60),
                ))
                deps.database.workoutDao().insertSet(SetLogEntity("identity-saved-set-$index", SESSION_ID,
                    row.id, 1, 2.0, 3, 8, false, STAMP + 200_000L + index))
            }
        }
        summaryModel = WorkoutSummaryViewModel(ApplicationProvider.getApplicationContext(),
            SavedStateHandle(mapOf("sessionId" to SESSION_ID)), deps).also(models::add)
        val expectedExercises = runBlocking {
            (listOf(exercise.id) + additional.map { it.id }).map { checkNotNull(deps.exerciseRepository.getById(it)) }
        }
        val before = inventory()
        mount()
        compose.awaitThat("all exact identities reach the actual Summary", { summaryModel.uiState.value }) {
            summaryModel.uiState.value.highlightExercises.size == 4 && !summaryModel.uiState.value.isLoading
        }
        val actual = expectedExercises.associate { expected ->
            host.readable(host.words(EXERCISE_NAME, SummaryTags.lift(expected.id)), EXERCISE_NAME)
            host.capture("summary-art-${expected.id}")
            expected.id to host.still(SummaryTags.art(expected.id), ThumbSize.header)
        }
        expectedExercises.forEach { expected ->
            exercise = expected
            switchTo(Surface.REFERENCES)
            host.sameArt(checkNotNull(actual[expected.id]), host.still(REFERENCE_ART, ThumbSize.header),
                host.still(UNRELATED_ART, ThumbSize.header))
            host.capture("expected-art-${expected.id}")
            switchTo(Surface.SUMMARY)
        }
        assertTrue("two keyed squat images sharing name and muscle draw different native pixels",
            checkNotNull(actual[expectedExercises[0].id]).indices.count { index ->
                !isNear(checkNotNull(actual[expectedExercises[0].id])[index],
                    Color(checkNotNull(actual[knownFront.id])[index]))
            } > 40)
        assertEquals("image lookup is read-only", before, inventory())
    }

    private fun matrix(name: String, font: Float, layoutDirection: LayoutDirection = LayoutDirection.Ltr) =
        host.evidence(name) {
            host.font = font
            direction.value = layoutDirection
            assertEquals("native resource font scale", font, RuntimeEnvironment.getApplication().resources.configuration.fontScale, .001f)
            seed()
            val before = inventory()
            mount()
            compose.awaitThat("actual saved Summary is loaded", { summaryModel.uiState.value }) {
                !summaryModel.uiState.value.isLoading && summaryModel.uiState.value.summary.hasWork
            }
            host.drain()
            val loaded = summaryModel.uiState.value
            assertTrue("saved claim comes from the finished row", loaded.savedConfirmed)
            assertEquals(SESSION_ID, loaded.summary.sessionId)
            assertEquals(255.0, loaded.summary.volumeKg, .001)
            assertEquals(1, loaded.summary.workingSets)
            assertEquals(setOf(PersonalRecordKind.WEIGHT, PersonalRecordKind.ESTIMATED_ONE_REP_MAX), loaded.summary.highlights.single().records)
            assertEquals(exercise, loaded.highlightExercises[exercise.id])
            host.readable(host.words(SESSION_NAME), SESSION_NAME)
            host.capture("summary-complete-title")
            host.readable(host.words("2 personal records"), "2 personal records")
            host.readable(host.words("Heaviest ever · Best estimated 1RM"), "Heaviest ever · Best estimated 1RM")
            val recordArt = host.still(SummaryTags.recordArt(exercise.id), ThumbSize.header)
            host.capture("summary-record-image-and-meaning")
            val lift = SummaryTags.lift(exercise.id)
            host.readable(host.words(EXERCISE_NAME, lift), EXERCISE_NAME)
            host.readable(host.words("Top set 85 kg × 3", lift), "Top set 85 kg × 3")
            host.readable(host.words("Personal record", lift), "Personal record")
            host.readable(host.words("1", lift), "1")
            host.readable(host.words("sets", lift), "sets")
            host.readable(host.words("255", lift), "255")
            host.readable(host.words("kg", lift), "kg")
            host.capture("summary-saved-values")
            val summaryArt = host.still(SummaryTags.art(exercise.id), ThumbSize.header)
            host.touch(host.tag(SummaryTags.DONE))
            assertEquals("actual pointer activates Done once", 1, done.get())
            host.touch(host.tag(SummaryTags.OPEN_SESSION))
            assertEquals("actual pointer routes the finished exact ID", listOf(SESSION_ID), openedSessions)
            host.capture("summary-route-actions")

            switchTo(Surface.DETAIL)
            compose.awaitThat("actual saved detail is loaded", { detailModel.uiState.value }) {
                detailModel.uiState.value.session?.id == SESSION_ID && !detailModel.uiState.value.isLoading
            }
            host.drain()
            assertDetailedSavedFacts()
            val detailArt = host.still(SessionDetailTestTags.liftCard(exercise.id), ThumbSize.header)
            host.touch(host.tag(SessionDetailTestTags.liftCard(exercise.id)))
            assertEquals("detail identity opens the exact exercise", listOf(exercise.id), openedExercises)
            host.capture("detail-exact-exercise-route")

            switchTo(Surface.REFERENCES)
            val expectedArt = host.still(REFERENCE_ART, ThumbSize.header)
            val unrelatedArt = host.still(UNRELATED_ART, ThumbSize.header)
            host.sameArt(summaryArt, expectedArt, unrelatedArt)
            host.sameArt(recordArt, expectedArt, unrelatedArt)
            host.sameArt(detailArt, expectedArt, unrelatedArt)
            host.capture("independent-image-identities")

            assertCardCorrectionAndHold()
            assertReadOutcomes(loaded)
            assertEquals("read-only Summary, route taps and uncommitted correction callbacks leave every durable row unchanged", before, inventory())
        }

    private fun seed() = runBlocking {
        val known = DefaultExercises.catalog().first { it.name == "Barbell Back Squat" }
        val other = DefaultExercises.catalog().first { it.name == "Barbell Bench Press" }
        val entities = listOf(known to EXERCISE_NAME, other to other.name).map { (seed, name) ->
            ExerciseEntity(
                id = seed.id, name = name, muscleGroup = seed.muscleGroup, notes = "", isCustom = false,
                equipment = seed.equipment.name, loadType = seed.loadType.name,
                movementKey = seed.movementKey, imageKey = seed.imageKey, nameKey = name.lowercase(),
            )
        }
        deps.database.exerciseDao().insertAll(entities)
        exercise = checkNotNull(deps.exerciseRepository.getById(known.id))
        unrelated = checkNotNull(deps.exerciseRepository.getById(other.id))
        val dao = deps.database.workoutDao()
        dao.upsertSession(WorkoutSessionEntity(PRIOR_ID, null, "Earlier synthetic workout", STAMP - 86_400_000L,
            "", 10, STAMP - 86_400_000L, STAMP - 85_800_000L))
        dao.insertSessionExercises(listOf(SessionExerciseEntity("prior-lift", PRIOR_ID, exercise.id, 0, 3, 5, 80.0, 120)))
        dao.insertSet(SetLogEntity("prior-set", PRIOR_ID, exercise.id, 1, 80.0, 3, 8, false, STAMP - 86_399_000L))
        dao.upsertSession(WorkoutSessionEntity(SESSION_ID, null, SESSION_NAME, STAMP, "", 24, STAMP, STAMP + 1_440_000L))
        dao.insertSessionExercises(listOf(SessionExerciseEntity("saved-lift", SESSION_ID, exercise.id, 0, 3, 5, 140.0, 120)))
        dao.insertSet(SetLogEntity(WORK_SET_ID, SESSION_ID, exercise.id, 1, 85.0, 3, 9, false, STAMP + 120_000L))
        dao.insertSet(SetLogEntity(WARM_SET_ID, SESSION_ID, exercise.id, 2, 60.0, 5, null, true, STAMP + 60_000L))
        session = checkNotNull(deps.workoutRepository.getSession(SESSION_ID))
        shownLift = session.filledLifts().single()
        summaryModel = WorkoutSummaryViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("sessionId" to SESSION_ID)), deps).also(models::add)
        detailModel = SessionDetailViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("sessionId" to SESSION_ID)), deps).also(models::add)
    }

    private fun mount() {
        compose.setContent {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalLayoutDirection provides direction.value) {
                PersonalTrainerTheme(reduceMotion = true) {
                    when (surface.value) {
                        Surface.SUMMARY -> WorkoutSummaryScreen(
                            onDone = { done.incrementAndGet() }, onOpenSession = { openedSessions += it }, viewModel = summaryModel,
                        )
                        Surface.STATE -> WorkoutSummaryContent(
                            state = controlled.value, unit = WeightUnit.KG,
                            onRetry = { retries.incrementAndGet() }, onOpenSession = { openedSessions += it },
                            onDone = { done.incrementAndGet() },
                        )
                        Surface.DETAIL -> SessionDetailScreen(
                            onBack = { done.incrementAndGet() }, onOpenExercise = { openedExercises += it },
                            onOpenActiveSession = { throw AssertionError("saved review must not start a workout") }, viewModel = detailModel,
                        )
                        Surface.CARD -> Column(
                            Modifier.fillMaxSize().background(Pit).verticalScroll(rememberScrollState())
                                .padding(Metrics.gutter).testTag(CARD_CONTENT),
                        ) {
                            FilledLiftCard(
                                lift = shownLift, loadClass = loadClass, unit = WeightUnit.KG,
                                onOpen = { openedExercises += shownLift.exercise.id },
                                onEditSet = { editedSets += it }, onAddSet = { addedExercises += shownLift.exercise.id },
                            )
                        }
                        Surface.REFERENCES -> Row(Modifier.fillMaxSize().background(Pit).padding(16.dp)) {
                            Box(Modifier.testTag(REFERENCE_ART)) { ExerciseThumb(exercise = exercise, size = ThumbSize.header) }
                            Box(Modifier.testTag(UNRELATED_ART)) { ExerciseThumb(exercise = unrelated, size = ThumbSize.header) }
                        }
                    }
                }
            }
        }
        host.drain()
    }

    private fun assertDetailedSavedFacts() {
        val identity = SessionDetailTestTags.liftCard(exercise.id)
        host.readable(host.words(EXERCISE_NAME, identity), EXERCISE_NAME)
        val spoken = host.tag(identity).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue("spoken recorded count differs from the plan", spoken.contains("Recorded: 1 working set"))
        assertTrue("spoken targets are qualified", spoken.contains("Planned: Work 3 × 5") && spoken.contains("Load 140 kg"))
        host.capture("detail-complete-identity")
        val plan = FilledLiftCardTags.planned(exercise.id)
        for (value in listOf("Planned", "3 × 5", "2:00", "140 kg")) host.readable(host.words(value, plan), value)
        host.numericOrder(host.words("3 × 5", plan), "3 × 5", orderedOffsets = listOf(0, 2, 4))
        val plannedMetrics = compose.onAllNodes(SemanticsMatcher("spoken planned metric") {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { words -> words.startsWith("Planned ") } == true
        }).fetchSemanticsNodes()
        assertEquals("every planned metric qualifies its own spoken value", 3, plannedMetrics.size)
        host.capture("detail-planned-targets")
        val recorded = FilledLiftCardTags.recorded(exercise.id)
        host.readable(host.words("Recorded sets", recorded), "Recorded sets")
        val working = FilledLiftCardTags.setRow(WORK_SET_ID)
        host.readable(host.words("85 kg × 3", working), "85 kg × 3")
        host.readable(host.words("Set 1 · RPE 9", working), "Set 1 · RPE 9")
        host.action(edit(WORK_SET_ID))
        host.capture("detail-recorded-working-set")
        val warm = FilledLiftCardTags.setRow(WARM_SET_ID)
        host.readable(host.words("60 kg × 5", warm), "60 kg × 5")
        host.readable(host.words("Warm-up", warm), "Warm-up")
        host.action(edit(WARM_SET_ID))
        host.action(host.tag(FilledLiftCardTags.addSet(exercise.id)))
        host.capture("detail-recorded-warmup-add-action")
    }

    private fun assertCardCorrectionAndHold() {
        shownLift = session.filledLifts().single()
        switchTo(Surface.CARD)
        for (id in listOf(WORK_SET_ID, WARM_SET_ID)) {
            host.touch(edit(id))
            val original = shownLift.sets.single { it.id == id }
            assertSame("Edit passes the original exact saved row", original, editedSets.last())
            assertEquals(id, editedSets.last().id)
            assertEquals(original.completedAt, editedSets.last().completedAt)
            host.capture("detail-exact-edit-$id")
        }
        host.touch(host.tag(FilledLiftCardTags.addSet(exercise.id)))
        assertEquals(listOf(exercise.id), addedExercises)
        host.capture("detail-add-exact-exercise")

        val holdExercise = exercise.copy(id = "custom-long-hold", name = HOLD_NAME,
            isCustom = true, movementKey = "hold", imageKey = "not-a-real-catalog-key", loadType = LoadType.BODYWEIGHT)
        val held = SetLog(id = "saved-hold", sessionId = SESSION_ID, exerciseId = holdExercise.id,
            exerciseName = HOLD_NAME, setNumber = 1, weightKg = 0.0, reps = 0, rpe = null,
            isWarmup = false, completedAt = STAMP + 1_000L, durationSeconds = 23)
        val holdPrescription = SessionExercise(id = "planned-hold", sessionId = SESSION_ID,
            exercise = holdExercise, sortOrder = 1, targetSets = 3, targetReps = 1,
            targetWeightKg = null, restSeconds = 45, targetSeconds = 20, targetSecondsMax = 30)
        shownLift = FilledSessionLift(number = 2, exercise = holdExercise,
            prescriptions = listOf(FilledSessionPrescription(2, holdPrescription)), sets = listOf(held))
        loadClass = LoadClass.BODYWEIGHT
        host.drain()
        host.readable(host.words(HOLD_NAME, SessionDetailTestTags.liftCard(holdExercise.id)), HOLD_NAME)
        val plan = FilledLiftCardTags.planned(holdExercise.id)
        host.readable(host.words("3 × 20–30s", plan), "3 × 20–30s")
        host.readable(host.words("0:45", plan), "0:45")
        host.capture("detail-hold-planned-range")
        val actual = FilledLiftCardTags.setRow(held.id)
        host.readable(host.words("23s", actual), "23s")
        host.touch(edit(held.id))
        assertSame("hold correction retains exact duration-bearing row", held, editedSets.last())
        host.capture("detail-hold-recorded-duration")

        shownLift = shownLift.copy(prescriptions = emptyList(), sets = emptyList())
        host.drain()
        assertTrue("set-only or unplanned history invents no target section", compose.onAllNodes(hasTestTag(plan)).fetchSemanticsNodes().isEmpty())
        host.readable(host.words("No recorded sets", FilledLiftCardTags.recorded(holdExercise.id)), "No recorded sets")
        host.touch(host.tag(FilledLiftCardTags.addSet(holdExercise.id)))
        assertEquals(holdExercise.id, addedExercises.last())
        host.capture("detail-no-planned-or-recorded-work")
    }

    private fun assertReadOutcomes(loaded: WorkoutSummaryUiState) {
        controlled.value = WorkoutSummaryUiState(sessionId = SESSION_ID, isLoading = true)
        switchTo(Surface.STATE)
        assertAbsent("Workout saved")
        assertTrue("loading offers no settled receipt actions", compose.onAllNodes(hasTestTag(SummaryTags.DONE)).fetchSemanticsNodes().isEmpty())
        host.capture("summary-loading")
        controlled.value = WorkoutSummaryUiState(sessionId = SESSION_ID, isLoading = false, failed = true)
        host.drain()
        verifyOutcome(SummaryCopy.UNAVAILABLE_TITLE, SummaryCopy.UNAVAILABLE_BODY, "summary-read-failed-unknown-save")
        assertAbsent("Workout saved")
        assertTrue("unknown save offers no saved session route", compose.onAllNodes(hasTestTag(SummaryTags.OPEN_SESSION)).fetchSemanticsNodes().isEmpty())
        host.touch(host.tag(SummaryTags.RETRY))
        assertEquals(1, retries.get())
        controlled.value = loaded
        host.drain()
        host.readable(host.words("Top set 85 kg × 3", SummaryTags.lift(exercise.id)), "Top set 85 kg × 3")
        host.capture("summary-retry-same-saved-receipt")
        controlled.value = WorkoutSummaryUiState(sessionId = SESSION_ID, isLoading = false, failed = true, savedConfirmed = true)
        host.drain()
        verifyOutcome(SummaryCopy.SAVED_SUMMARY_UNAVAILABLE_TITLE, SummaryCopy.SAVED_SUMMARY_UNAVAILABLE_BODY, "summary-saved-computation-failed")
        host.touch(host.tag(SummaryTags.OPEN_SESSION))
        assertEquals(SESSION_ID, openedSessions.last())
        host.touch(host.tag(SummaryTags.RETRY))
        assertEquals(2, retries.get())
        controlled.value = WorkoutSummaryUiState(sessionId = SESSION_ID, isLoading = false, missing = true)
        host.drain()
        verifyOutcome(SummaryCopy.MISSING_TITLE, SummaryCopy.MISSING_BODY, "summary-missing")
        assertAbsent("Workout saved")
        controlled.value = WorkoutSummaryUiState(sessionId = SESSION_ID, isLoading = false, savedConfirmed = true,
            summary = WorkoutSummary(sessionId = SESSION_ID))
        host.drain()
        verifyOutcome(SummaryCopy.SAVED_NO_WORK_TITLE, SummaryCopy.SAVED_NO_WORK_BODY, "summary-finished-no-working-sets")
        controlled.value = controlled.value.copy(savedConfirmed = false)
        host.drain()
        verifyOutcome(SummaryCopy.NO_WORK_TITLE, SummaryCopy.NO_WORK_BODY, "summary-unfinished-no-working-sets")
        assertAbsent("Workout saved")
    }

    private fun verifyOutcome(title: String, body: String, stage: String) {
        host.readable(host.words(title), title)
        host.capture("$stage-title")
        host.readable(host.words(body), body)
        host.action(host.tag(SummaryTags.DONE))
        host.capture("$stage-body-and-done")
    }

    private fun edit(setId: String) = host.reach(compose.onNode(
        hasTestTag(SessionDetailTestTags.EDIT_SET) and hasAnyAncestor(hasTestTag(FilledLiftCardTags.setRow(setId))),
    ))

    private fun assertAbsent(words: String) {
        assertTrue("$words is never claimed in this evidence state", compose.onAllNodes(hasText(words)).fetchSemanticsNodes().isEmpty())
    }

    private fun switchTo(next: Surface) {
        compose.runOnUiThread { surface.value = next }
        host.drain()
    }

    private fun inventory(): List<Any?> = runBlocking {
        listOf(deps.database.workoutDao().getAllSessions(), deps.database.workoutDao().getAllSessionExercises(),
            deps.database.workoutDao().getAllSets(), deps.database.exerciseDao().getAll())
    }

    private enum class Surface { SUMMARY, STATE, DETAIL, CARD, REFERENCES }

    private companion object {
        const val SESSION_ID = "saved-receipt-exact-session"
        const val PRIOR_ID = "saved-receipt-earlier-session"
        const val WORK_SET_ID = "saved-receipt-working-exact-id"
        const val WARM_SET_ID = "saved-receipt-warmup-exact-id"
        const val STAMP = 1_790_856_000_000L
        const val SESSION_NAME = "Lower body strength after a deliberately longer training day"
        const val EXERCISE_NAME = "Barbell Back Squat with controlled depth and a complete exercise name"
        const val HOLD_NAME = "Long named static hold with careful breathing and a complete exercise identity"
        const val CARD_CONTENT = "saved-work-card-content"
        const val REFERENCE_ART = "saved-work-independent-exercise-art"
        const val UNRELATED_ART = "saved-work-unrelated-exercise-art"
    }
}
