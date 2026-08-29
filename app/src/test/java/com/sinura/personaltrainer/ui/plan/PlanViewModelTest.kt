package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WeeklySchedulePlanner
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.util.toJavaDayOfWeek
import com.sinura.personaltrainer.util.toWeekday
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: PlanViewModel? = null
    private val weekStart = LocalDate.of(2026, 8, 17)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun suggestFillsOnlyUnpinnedTrainingDays() = runBlocking {
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = emptyWeek()),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.suggestFills()
        // Suggest re-plans from wall clock and will not propose days already behind
        // today. On a Sunday of a Mon-start 4-day week that set is empty — waiting
        // for a non-empty list hangs. The filter still has to hold.
        val proposals = withTimeout(5_000) {
            if (plannerHasARemainingTrainingDay()) {
                viewModel!!.uiState.first { it.proposals.isNotEmpty() }.proposals
            } else {
                delay(1_000)
                viewModel!!.uiState.value.proposals
            }
        }
        assertTrue(proposals.all { it.slotId == null && !it.isRest })
        if (plannerHasARemainingTrainingDay()) {
            assertTrue(proposals.isNotEmpty())
        }
    }

    @Test
    fun aFullyPinnedWeekYieldsNoProposals() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights(snapshot = emptyHeat()))
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        val upper = deps.routineRepository.create(name = "Upper")
        val lower = deps.routineRepository.create(name = "Lower Body")
        listOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY, Weekday.SATURDAY)
            .forEach { day ->
                deps.scheduleRepository.pin(
                    routineId = if (day == Weekday.WEDNESDAY) lower.id else upper.id,
                    focusKind = null,
                    anchorDay = day,
                )
            }
        insights.value = TrainingInsights(
            snapshot = emptyHeat(),
            routines = listOf(upper, lower),
        )
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.suggestFills()
        withTimeout(5_000) { viewModel!!.uiState.first { !it.isLoading } }
        assertTrue(viewModel!!.uiState.value.proposals.isEmpty())
        assertEquals(null, viewModel!!.uiState.value.error)
    }

    @Test
    fun replayPinsExistingRoutineIdsAndDoesNotCreate() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        deps.dbMaintenance.seedCatalog()
        val upper = deps.routineRepository.create(name = "Upper")
        val lower = deps.routineRepository.create(name = "Lower Body")
        val before = deps.routineRepository.count()
        val answers = OnboardingAnswers(
            trainingAge = TrainingAge.RETURNING,
            daysPerWeek = 4,
            preferredDays = emptySet(),
            place = TrainingPlace.FULL_GYM,
        )
        deps.preferencesRepository.setTrainingAge(answers.trainingAge)
        deps.preferencesRepository.setPreferredDays(answers.preferredDays)
        deps.preferencesRepository.setTrainingPlace(answers.place)
        deps.preferencesRepository.setTrainingDaysPerWeek(4)
        insights.value = TrainingInsights(
            routines = listOf(upper, lower),
            weekPlan = emptyWeek(),
        )

        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.replayStoredAnswers()
        val proposals = withTimeout(5_000) {
            viewModel!!.uiState.first { it.proposals.isNotEmpty() || it.error != null }.proposals
        }
        assertEquals(before, deps.routineRepository.count())
        assertTrue(proposals.isNotEmpty())
        assertTrue(proposals.all { it.routineId in setOf(upper.id, lower.id) })
        assertTrue(proposals.all { it.slotId == null })
    }

    @Test
    fun addMorningCardioCreatesASecondOccurrenceOnThatDay() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinFocus(monday.toEpochDay(), SessionFocusKind.PUSH)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.isNotEmpty() }
        }
        viewModel!!.addMorningCardio(monday.toEpochDay())
        dispatcher.scheduler.advanceUntilIdle()
        val mondayOcc = withTimeout(5_000) {
            deps.plannerRepository.observeOccurrences().first { rows ->
                rows.count { it.localEpochDay == monday.toEpochDay() } >= 2
            }
        }
        assertEquals(2, mondayOcc.count { it.localEpochDay == monday.toEpochDay() })
        assertTrue(
            deps.plannerRepository.rules().any {
                it.modality == com.sinura.personaltrainer.domain.ScheduleModality.CARDIO &&
                    it.templateId == com.sinura.personaltrainer.domain.ScheduleKind.cardio(
                        com.sinura.personaltrainer.domain.CardioType.RUN,
                    )
            },
        )
    }

    @Test
    fun addWalkCardioStoresTheTypeTag() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinFocus(monday.toEpochDay(), SessionFocusKind.PUSH)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.isNotEmpty() }
        }
        viewModel!!.addCardio(monday.toEpochDay(), com.sinura.personaltrainer.domain.CardioType.WALK)
        dispatcher.scheduler.advanceUntilIdle()
        val cardio = withTimeout(5_000) {
            deps.plannerRepository.observeRules().first { rows ->
                rows.any { it.modality == com.sinura.personaltrainer.domain.ScheduleModality.CARDIO }
            }.single { it.modality == com.sinura.personaltrainer.domain.ScheduleModality.CARDIO }
        }
        assertEquals(
            com.sinura.personaltrainer.domain.ScheduleKind.cardio(
                com.sinura.personaltrainer.domain.CardioType.WALK,
            ),
            cardio.templateId,
        )
        assertEquals(7, cardio.hour)
    }

    @Test
    fun setSessionHourMovesTheRuleAndPlannedOccurrence() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinFocus(monday.toEpochDay(), SessionFocusKind.PUSH)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.isNotEmpty() }
        }
        viewModel!!.addCardio(monday.toEpochDay(), com.sinura.personaltrainer.domain.CardioType.WALK)
        val cardio = withTimeout(5_000) {
            deps.plannerRepository.observeRules().first { rows ->
                rows.any { it.modality == com.sinura.personaltrainer.domain.ScheduleModality.CARDIO }
            }.single { it.modality == com.sinura.personaltrainer.domain.ScheduleModality.CARDIO }
        }
        assertEquals(7, cardio.hour)
        viewModel!!.setSessionHour(cardio.id, 9)
        dispatcher.scheduler.advanceUntilIdle()
        val updated = withTimeout(5_000) {
            deps.plannerRepository.observeRules().first { rows ->
                rows.any { it.id == cardio.id && it.hour == 9 }
            }.single { it.id == cardio.id }
        }
        assertEquals(9, updated.hour)
        val occ = deps.plannerRepository.observeOccurrences().first { rows ->
            rows.any { it.ruleId == cardio.id && it.hour == 9 }
        }.first { it.ruleId == cardio.id }
        assertEquals(9, occ.hour)
    }

    @Test
    fun addAuxiliaryMintsAStretchRoutine() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        deps.dbMaintenance.seedCatalog()
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinFocus(monday.toEpochDay(), SessionFocusKind.PUSH)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.isNotEmpty() }
        }
        viewModel!!.addAuxiliary(monday.toEpochDay(), "stretch")
        dispatcher.scheduler.advanceUntilIdle()
        val aux = withTimeout(5_000) {
            deps.plannerRepository.observeRules().first { rows ->
                rows.any {
                    it.templateId == com.sinura.personaltrainer.domain.ScheduleKind.aux("stretch")
                }
            }.single {
                it.templateId == com.sinura.personaltrainer.domain.ScheduleKind.aux("stretch")
            }
        }
        assertEquals(com.sinura.personaltrainer.domain.ScheduleModality.STRENGTH, aux.modality)
        val routine = deps.routineRepository.getById(aux.routineId!!)!!
        assertEquals("Stretch", routine.name)
        assertTrue(routine.exercises.isNotEmpty())
    }

    @Test
    fun deleteSessionUnpinsTheImportedEveningPin() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinFocus(monday.toEpochDay(), SessionFocusKind.PUSH)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.isNotEmpty() }
        }
        val imported = deps.plannerRepository.rules().single {
            com.sinura.personaltrainer.domain.SlotRuleImport.isImportedSlotRule(it.id)
        }
        viewModel!!.deleteSession(monday.toEpochDay(), imported.id)
        dispatcher.scheduler.advanceUntilIdle()
        withTimeout(5_000) {
            deps.plannerRepository.observeRules().first { rows ->
                rows.none {
                    com.sinura.personaltrainer.domain.SlotRuleImport.isImportedSlotRule(it.id)
                }
            }
        }
        assertTrue(deps.scheduleRepository.slots().isEmpty())
        assertTrue(
            deps.plannerRepository.rules().none {
                com.sinura.personaltrainer.domain.SlotRuleImport.isImportedSlotRule(it.id)
            },
        )
    }

    @Test
    fun addLaterSessionCreatesAThirdOccurrenceOnThatDay() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        val extra = deps.routineRepository.create("Monday extra")
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinFocus(monday.toEpochDay(), SessionFocusKind.PUSH)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.isNotEmpty() }
        }
        viewModel!!.addMorningCardio(monday.toEpochDay())
        withTimeout(5_000) {
            deps.plannerRepository.observeOccurrences().first { rows ->
                rows.count { it.localEpochDay == monday.toEpochDay() } >= 2
            }
        }
        viewModel!!.addLaterSession(monday.toEpochDay(), extra.id)
        val mondayOcc = withTimeout(5_000) {
            deps.plannerRepository.observeOccurrences().first { rows ->
                rows.count { it.localEpochDay == monday.toEpochDay() } >= 3
            }
        }
        val onMonday = mondayOcc.filter { it.localEpochDay == monday.toEpochDay() }
        assertEquals(3, onMonday.size)
        assertEquals(setOf(7, 18, 20), onMonday.map { it.hour }.toSet())
        val laterRule = deps.plannerRepository.rules().single {
            it.routineId == extra.id
        }
        assertEquals(20, laterRule.hour)
        assertEquals(
            com.sinura.personaltrainer.domain.ScheduleModality.STRENGTH,
            laterRule.modality,
        )
    }

    @Test
    fun swapKeepsTheLaterSession() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        val push = deps.routineRepository.create("Push")
        val pull = deps.routineRepository.create("Pull")
        val extra = deps.routineRepository.create("Monday extra")
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinRoutine(monday.toEpochDay(), push.id)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.any { rule -> rule.routineId == push.id } }
        }
        viewModel!!.addLaterSession(monday.toEpochDay(), extra.id)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.any { rule -> rule.routineId == extra.id } }
        }
        val slotId = deps.scheduleRepository.slots().single().id
        viewModel!!.swapRoutine(slotId, pull.id)
        dispatcher.scheduler.advanceUntilIdle()
        withTimeout(5_000) {
            deps.plannerRepository.observeRules().first { rows ->
                rows.any { it.routineId == pull.id } && rows.any { it.routineId == extra.id }
            }
        }
        val rules = deps.plannerRepository.rules()
        assertEquals(2, rules.size)
        assertEquals(extra.id, rules.single { it.routineId == extra.id }.routineId)
        assertEquals(pull.id, rules.single { it.routineId == pull.id }.routineId)
    }

    @Test
    fun composeLaterSessionNamesTheExtraAndOpensTheEditor() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinFocus(monday.toEpochDay(), SessionFocusKind.PUSH)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.isNotEmpty() }
        }
        viewModel!!.composeLaterSession(monday.toEpochDay())
        val editorId = withTimeout(5_000) {
            viewModel!!.navigateToEditor.first { it != null }!!
        }
        val routine = deps.routineRepository.getById(editorId)!!
        assertEquals("Monday extra", routine.name)
        val later = deps.plannerRepository.rules().single { it.routineId == editorId }
        assertEquals(20, later.hour)
        assertEquals(2, deps.plannerRepository.occurrencesBetween(monday.toEpochDay(), monday.toEpochDay()).size)
    }

    @Test
    fun removeTimedRuleDropsTheExtraOccurrence() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        val extra = deps.routineRepository.create("Monday extra")
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinFocus(monday.toEpochDay(), SessionFocusKind.PUSH)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.isNotEmpty() }
        }
        viewModel!!.addLaterSession(monday.toEpochDay(), extra.id)
        withTimeout(5_000) {
            deps.plannerRepository.observeOccurrences().first { rows ->
                rows.count { it.localEpochDay == monday.toEpochDay() } >= 2
            }
        }
        val laterId = deps.plannerRepository.rules().single { it.routineId == extra.id }.id
        viewModel!!.removeTimedRule(laterId)
        withTimeout(5_000) {
            deps.plannerRepository.observeOccurrences().first { rows ->
                rows.count { it.localEpochDay == monday.toEpochDay() } == 1
            }
        }
        assertEquals(1, deps.plannerRepository.rules().size)
        assertTrue(
            deps.plannerRepository.rules().none { it.routineId == extra.id },
        )
    }

    @Test
    fun buildDayNamesTheWeekdayPinsItAndOpensTheEditor() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }

        viewModel!!.buildDay(monday.toEpochDay())
        dispatcher.scheduler.advanceUntilIdle()
        val editorId = withTimeout(5_000) {
            viewModel!!.navigateToEditor.first { it != null }!!
        }
        val routine = deps.routineRepository.getById(editorId)!!
        assertEquals("Monday", routine.name)
        assertEquals(editorId, deps.scheduleRepository.slots().single().routineId)
        val rule = withTimeout(5_000) {
            deps.plannerRepository.observeRules().first { it.isNotEmpty() }.single()
        }
        assertEquals(editorId, rule.routineId)
        assertEquals(
            com.sinura.personaltrainer.domain.OccurrenceStatus.PLANNED,
            deps.plannerRepository.occurrencesBetween(monday.toEpochDay(), monday.toEpochDay())
                .single().status,
        )
    }

    @Test
    fun swapRoutineRefreshesTheImportedRule() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(monday)),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        val push = deps.routineRepository.create("Push")
        val pull = deps.routineRepository.create("Pull")
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinRoutine(monday.toEpochDay(), push.id)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.any { rule -> rule.routineId == push.id } }
        }
        val slotId = deps.scheduleRepository.slots().single().id
        viewModel!!.swapRoutine(slotId, pull.id)
        dispatcher.scheduler.advanceUntilIdle()
        withTimeout(5_000) {
            deps.plannerRepository.observeRules().first { rows ->
                rows.any { it.routineId == pull.id }
            }
        }
        assertEquals(pull.id, deps.plannerRepository.rules().single().routineId)
    }

    @Test
    fun freeWorkoutLeavesThePlannedOccurrenceOpen() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = weekStarting(
                today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
            )),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.pinFocus(today.toEpochDay(), SessionFocusKind.PUSH)
        withTimeout(5_000) {
            viewModel!!.uiState.first { it.rules.isNotEmpty() }
        }
        viewModel!!.startFreeWorkout()
        val sessionId = withTimeout(5_000) {
            viewModel!!.navigateToSession.first { it != null }!!
        }
        val session = deps.workoutRepository.getSession(sessionId)!!
        assertEquals("Free workout", session.routineName)
        assertTrue(session.exercises.isEmpty())
        assertEquals(
            com.sinura.personaltrainer.domain.OccurrenceStatus.PLANNED,
            deps.plannerRepository.occurrencesBetween(today.toEpochDay(), today.toEpochDay())
                .single().status,
        )
    }

    @Test
    fun pendingAnswerReplayReplaysWithoutCreating() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        deps.dbMaintenance.seedCatalog()
        val upper = deps.routineRepository.create(name = "Upper")
        val lower = deps.routineRepository.create(name = "Lower Body")
        val before = deps.routineRepository.count()
        deps.preferencesRepository.setTrainingAge(TrainingAge.RETURNING)
        deps.preferencesRepository.setPreferredDays(emptySet())
        deps.preferencesRepository.setTrainingPlace(TrainingPlace.FULL_GYM)
        deps.preferencesRepository.setTrainingDaysPerWeek(4)
        insights.value = TrainingInsights(
            routines = listOf(upper, lower),
            weekPlan = emptyWeek(),
        )
        deps.pendingAnswerReplay.value = true

        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val proposals = withTimeout(5_000) {
            viewModel!!.uiState.first { it.proposals.isNotEmpty() || it.error != null }.proposals
        }
        assertEquals(before, deps.routineRepository.count())
        assertTrue(proposals.isNotEmpty())
        assertTrue(proposals.all { it.routineId in setOf(upper.id, lower.id) })
        assertFalse(deps.pendingAnswerReplay.value)
    }

    private fun plannerHasARemainingTrainingDay(): Boolean {
        val today = LocalDate.now(ZoneId.systemDefault())
        val prefs = SchedulePreferences.DEFAULT.sanitized()
        val weekStart = today.with(
            TemporalAdjusters.previousOrSame(prefs.weekStart.toJavaDayOfWeek()),
        )
        return WeeklySchedulePlanner.trainingDayIndices(prefs.trainingDaysPerWeek)
            .map { weekStart.plusDays(it.toLong()) }
            .any { !it.isBefore(today) }
    }

    private fun emptyHeat() = BodyHeatSnapshot(
        window = HeatWindow.CURRENT_WEEK,
        windowStartMs = 0L,
        generatedAtMs = 0L,
        loads = emptyList(),
        hasAnyWorkingSets = false,
        hasWindowWorkingSets = false,
    )

    private fun emptyWeek(): WeeklySchedulePlan = weekStarting(weekStart)

    private fun weekStarting(startDate: LocalDate): WeeklySchedulePlan {
        val start = startDate.toEpochDay()
        return WeeklySchedulePlan(
            weekStartEpochDay = start,
            generatedAtMs = 0L,
            preferences = SchedulePreferences.DEFAULT,
            resolvedSplit = SplitStyle.UPPER_LOWER,
            days = (0L..6L).map { offset ->
                val date = LocalDate.ofEpochDay(start + offset)
                SuggestedTrainingDay(
                    epochDay = date.toEpochDay(),
                    dayOfWeek = date.dayOfWeek.toWeekday(),
                    isRest = true,
                    focusKind = SessionFocusKind.FULL_BODY,
                    focusTitle = "Rest",
                    routineId = null,
                    routineName = null,
                    reason = "",
                    emphasisMuscles = emptyList(),
                    confidence = ScheduleConfidence.HIGH,
                )
            },
            thinHistory = false,
            summary = "No sessions pinned yet.",
        )
    }
}
