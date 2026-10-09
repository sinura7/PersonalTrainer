package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupExercise
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupPreferences
import com.sinura.personaltrainer.data.backup.BackupSession
import com.sinura.personaltrainer.data.backup.BackupSessionExercise
import com.sinura.personaltrainer.data.backup.BackupSetLog
import com.sinura.personaltrainer.data.backup.BackupValidation
import com.sinura.personaltrainer.data.backup.BackupValidator
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.summary.SavedWorkRenderHost
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * Accepted serialized backup graphs must keep every original saved row inspectable in Detail.
 *
 * Both cases pass BackupService.prepareRestore/commitRestore, including the production
 * decoder/validator, verified safety copy, LocalBackupRepository replacement and journal
 * epilogue over real isolated Room. The shipping Detail VM and screen supply the rows and
 * Edit sheet. This is a native-graphics component counter, not AppNav, device touch feel,
 * TalkBack speech, or an authorization to collapse distinct restored prescriptions.
 *
 * An exception is a failure. Native frames/semantics are retained when rendering allows it;
 * the complete post-restore inventory is checked again in teardown even if a counter fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
class SessionDetailRestoredGraphRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var host: SavedWorkRenderHost
    private var detail: SessionDetailViewModel? = null
    private var originalSession: WorkoutSession? = null
    private var restoredInventory: List<Any?>? = null
    private var fixtureName = "not-restored"
    private var direction = LayoutDirection.Ltr
    private val artifactRunId = UUID.randomUUID().toString()
    private val artifactDirectory = File("build/screen-renders/session-detail-restored-graph/$artifactRunId")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        host = SavedWorkRenderHost(
            compose = compose,
            contentTag = { SessionDetailTestTags.CONTENT },
            facts = {
                "fixture=$fixtureName; runId=$artifactRunId; sourceInventory=${artifactDirectory.absolutePath}; " +
                    "direction=$direction; resourceFont=${RuntimeEnvironment.getApplication().resources.configuration.fontScale}; " +
                    "resourceLocales=${RuntimeEnvironment.getApplication().resources.configuration.locales}; " +
                    "resourceDirection=${RuntimeEnvironment.getApplication().resources.configuration.layoutDirection}; " +
                    "defaultLocale=${Locale.getDefault()}; " +
                    "original=$originalSession; detail=${detail?.uiState?.value}"
            },
        )
    }

    @After
    fun tearDown() {
        try {
            try {
                runBlocking { detail?.clearAndJoinForTest() }
            } finally {
                restoredInventory?.let { original ->
                    val after = inventory()
                    writeInventory("teardown", after)
                    assertEquals(
                        "even a failed render/correction inspection leaves every restored row, " +
                            "prescription, captured time and preference unchanged",
                        original,
                        after,
                    )
                }
            }
        } finally {
            try {
                deps.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
            } finally {
                try { deps.close() } finally { Dispatchers.resetMain() }
            }
        }
    }

    @Test
    fun restoredPartialMembershipKeepsOriginalSavedSetAndEditReachableExactlyOnce() =
        host.evidence("restored-partial-membership-360x640-font10") {
            verify(partialFixture())
        }

    @Test
    fun restoredRepeatedExercisePrescriptionsKeepSavedSetAndEditExactlyOnceWithoutDroppingTargets() =
        host.evidence("restored-repeated-prescriptions-360x640-font10") {
            verify(repeatedFixture())
        }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun restoredPartialMembershipSmallLargeText() =
        matrix(partialFixture(), "360x640-font16", 1.6f)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun restoredPartialMembershipSmallLargestText() =
        matrix(partialFixture(), "360x640-font20", 2f)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun restoredPartialMembershipStandardDefaultText() =
        matrix(partialFixture(), "412x840-font10", 1f)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun restoredPartialMembershipStandardLargeText() =
        matrix(partialFixture(), "412x840-font16", 1.6f)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun restoredPartialMembershipStandardLargestText() =
        matrix(partialFixture(), "412x840-font20", 2f)

    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun restoredPartialMembershipLandscapeDefaultText() =
        matrix(partialFixture(), "800x360-font10", 1f)

    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun restoredPartialMembershipLandscapeLargeText() =
        matrix(partialFixture(), "800x360-font16", 1.6f)

    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun restoredPartialMembershipLandscapeLargestText() =
        matrix(partialFixture(), "800x360-font20", 2f)

    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun restoredPartialMembershipTabletDefaultText() =
        matrix(partialFixture(), "600x960-font10", 1f)

    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun restoredPartialMembershipTabletLargeText() =
        matrix(partialFixture(), "600x960-font16", 1.6f)

    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun restoredPartialMembershipTabletLargestText() =
        matrix(partialFixture(), "600x960-font20", 2f)

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun restoredPartialMembershipNarrowLargestText() =
        matrix(partialFixture(), "320x640-font20", 2f)

    // An explicit RTL locale keeps Android resources RTL; Hebrew also retains the
    // fixture's ordinary decimal numerals, so this remains a layout/prefill counter.
    @Test @Config(qualifiers = "he-ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun restoredPartialMembershipRtlLargestTextAndReducedMotion() =
        matrix(partialFixture(), "360x640-rtl-font20", 2f, LayoutDirection.Rtl)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun restoredRepeatedExercisePrescriptionsSmallLargeText() =
        matrix(repeatedFixture(), "360x640-font16", 1.6f)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun restoredRepeatedExercisePrescriptionsSmallLargestText() =
        matrix(repeatedFixture(), "360x640-font20", 2f)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun restoredRepeatedExercisePrescriptionsStandardDefaultText() =
        matrix(repeatedFixture(), "412x840-font10", 1f)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun restoredRepeatedExercisePrescriptionsStandardLargeText() =
        matrix(repeatedFixture(), "412x840-font16", 1.6f)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun restoredRepeatedExercisePrescriptionsStandardLargestText() =
        matrix(repeatedFixture(), "412x840-font20", 2f)

    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun restoredRepeatedExercisePrescriptionsLandscapeDefaultText() =
        matrix(repeatedFixture(), "800x360-font10", 1f)

    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun restoredRepeatedExercisePrescriptionsLandscapeLargeText() =
        matrix(repeatedFixture(), "800x360-font16", 1.6f)

    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun restoredRepeatedExercisePrescriptionsLandscapeLargestText() =
        matrix(repeatedFixture(), "800x360-font20", 2f)

    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun restoredRepeatedExercisePrescriptionsTabletDefaultText() =
        matrix(repeatedFixture(), "600x960-font10", 1f)

    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun restoredRepeatedExercisePrescriptionsTabletLargeText() =
        matrix(repeatedFixture(), "600x960-font16", 1.6f)

    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun restoredRepeatedExercisePrescriptionsTabletLargestText() =
        matrix(repeatedFixture(), "600x960-font20", 2f)

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun restoredRepeatedExercisePrescriptionsNarrowLargestText() =
        matrix(repeatedFixture(), "320x640-font20", 2f)

    @Test @Config(qualifiers = "he-ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun restoredRepeatedExercisePrescriptionsRtlLargestTextAndReducedMotion() =
        matrix(repeatedFixture(), "360x640-rtl-font20", 2f, LayoutDirection.Rtl)

    private fun matrix(
        fixture: RestoredFixture,
        profile: String,
        font: Float,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = host.evidence("restored-${fixture.name}-$profile") {
        verify(fixture, font, layoutDirection)
    }

    private fun verify(
        fixture: RestoredFixture,
        font: Float = 1f,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        host.font = font
        direction = layoutDirection
        val configuration = RuntimeEnvironment.getApplication().resources.configuration
        assertEquals("the host pins the actual native resource font scale", font, configuration.fontScale, .001f)
        assertEquals(
            "the requested layout direction matches actual native resources",
            if (layoutDirection == LayoutDirection.Rtl) android.view.View.LAYOUT_DIRECTION_RTL
                else android.view.View.LAYOUT_DIRECTION_LTR,
            configuration.layoutDirection,
        )
        fixtureName = fixture.name
        restore(fixture.document)
        mount()

        val model = checkNotNull(detail)
        compose.awaitThat("the exact restored session reaches the actual Detail", { model.uiState.value }) {
            !model.uiState.value.isLoading && model.uiState.value.session?.id == SESSION_ID
        }
        val state = model.uiState.value
        assertFalse("accepted restored session is not a failed read", state.failed)
        assertFalse("accepted restored session is not missing", state.missing)
        assertEquals("Detail reads the complete original graph", originalSession, state.session)
        val loaded = checkNotNull(state.session)
        assertEquals(1, loaded.workingSetCount())
        assertEquals(270.0, loaded.work().volumeKg, .0001)
        assertEquals(0, loaded.work().bodyweightReps)
        host.drain()
        val actualContent = compose.onNodeWithTag(SessionDetailTestTags.CONTENT).fetchSemanticsNode()
        assertEquals("the actual Detail uses the resource font scale", font, actualContent.layoutInfo.density.fontScale, .001f)
        assertEquals("the actual Detail uses the resource layout direction", layoutDirection, actualContent.layoutInfo.layoutDirection)
        host.readable(host.words("270"), "270")
        host.readable(host.words("WORKING VOLUME"), "WORKING VOLUME")
        host.capture("restored-detail-receipt-before-row-inspection")

        // Both distinct prescriptions have to remain readable. Keeping a unique key while
        // discarding a plan row or choosing one row's targets cannot satisfy this counter.
        fixture.prescriptions.forEach { plan ->
            val under = FilledLiftCardTags.planned(plan.exerciseId)
            for (value in listOf(plan.work, plan.load, plan.rest)) {
                host.readable(host.words(value, under), value)
            }
            val entry = FilledLiftCardTags.plannedEntry(plan.id)
            for (value in listOf(plan.work, plan.load, plan.rest)) {
                host.readable(host.words(value, entry), value)
            }
            assertEquals(
                "each original prescription has exactly one actual planned entry",
                1,
                compose.onAllNodes(hasTestTag(entry), useUnmergedTree = true).fetchSemanticsNodes().size,
            )
            host.numericOrder(
                host.words(plan.work, entry),
                plan.work,
                plan.work.indices.filterNot { plan.work[it].isWhitespace() },
            )
            if (fixture.prescriptions.count { it.exerciseId == plan.exerciseId } > 1) {
                val label = "Planned · ${plan.originalPosition}"
                host.readable(host.words(label, entry), label)
            }
            host.capture("preserved-prescription-${plan.id}")
            assertInventoryUnchanged("after-prescription-${plan.id}")
        }
        fixture.prescriptions.groupBy { it.exerciseId }.values.filter { it.size > 1 }.forEach { plans ->
            val positions = plans.map { plan ->
                compose.onNodeWithTag(FilledLiftCardTags.plannedEntry(plan.id), useUnmergedTree = true)
                    .fetchSemanticsNode().positionInWindow.y
            }
            assertTrue(
                "repeated prescriptions keep their original displayed position order: $positions",
                positions.zipWithNext().all { (first, next) -> first < next },
            )
        }

        // An initial viewport is not enough: exercise the actual list to its end so a later
        // duplicate key or duplicated recorded row is not hidden behind the first card.
        traverseActualList()

        val savedSets = checkNotNull(originalSession).sets
        assertEquals("fixture pins the original saved ID", listOf(SET_ID), savedSets.map { it.id })
        val recorded = FilledLiftCardTags.recorded(savedSets.single().exerciseId)
        host.readable(host.words("Recorded sets", recorded), "Recorded sets")
        assertEquals(
            "the exact saved exercise has one actual Recorded section",
            1,
            compose.onAllNodes(hasTestTag(recorded), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        savedSets.forEach { original ->
            val rowTag = FilledLiftCardTags.setRow(original.id)
            host.reach(host.tag(rowTag))
            assertEquals(
                "the original saved row occurs exactly once in actual composed Detail semantics",
                1,
                compose.onAllNodes(hasTestTag(rowTag), useUnmergedTree = true).fetchSemanticsNodes().size,
            )
            host.readable(host.words("45 kg × 6", rowTag), "45 kg × 6")
            host.readable(host.words("Set 1 · RPE 8", rowTag), "Set 1 · RPE 8")
            val edit = hasTestTag(SessionDetailTestTags.EDIT_SET) and hasAnyAncestor(hasTestTag(rowTag))
            assertEquals(
                "the original saved row has exactly one original Edit action",
                1,
                compose.onAllNodes(edit).fetchSemanticsNodes().size,
            )
            host.capture("original-row-and-edit-${original.id}")

            // This catches a key-only workaround even when one duplicated card is currently
            // offscreen. The rendering projection must conserve each saved ID exactly once.
            assertEquals(
                "all original saved IDs occur exactly once across the complete Detail projection",
                savedSets.associate { it.id to 1 },
                loaded.filledLifts().flatMap { it.sets }.groupingBy { it.id }.eachCount(),
            )

            host.touch(compose.onNode(edit))
            host.readable(host.words("Edit set 1"), "Edit set 1")
            compose.onNodeWithTag("Type a weight").assertContentDescriptionEquals("Weight 45 kg")
            compose.onNodeWithTag("Type a rep count").assertContentDescriptionEquals("reps 6")
            expandCorrectionSheetIfOffered(original.id)
            // These history RPE chips use InstrumentChip's default Checkbox role and
            // toggleable state. Inspect the original checked value without toggling it.
            val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
            val originalEffort = compose.onNode(hasText("8") and checkbox)
            host.action(originalEffort).assertIsOn()
            host.readable(host.words("8"), "8")
            for (other in listOf(6, 7, 9, 10)) {
                compose.onNode(hasText(other.toString()) and checkbox).assertIsOff()
            }
            host.capture("original-row-open-for-correction-${original.id}")
            assertInventoryUnchanged("correction-open-${original.id}")

            // Dismiss through the real sheet's accessibility action. No Save, Delete or
            // direct callback substitutes for opening and inspecting the saved row.
            val dismiss = SemanticsMatcher("the actual bottom sheet offers Dismiss") {
                it.config.getOrNull(SemanticsActions.Dismiss)?.action != null
            }
            compose.onAllNodes(dismiss).onFirst().performSemanticsAction(SemanticsActions.Dismiss) {
                assertTrue("the sheet accepts its own Dismiss action", it())
            }
            host.drain()
            compose.awaitThat("the correction sheet dismisses without a write", { model.uiState.value }) {
                compose.onAllNodes(hasTestTag(SetEditTestTags.DELETE)).fetchSemanticsNodes().isEmpty()
            }
            host.capture("correction-dismissed-${original.id}")
            assertInventoryUnchanged("correction-dismissed-${original.id}")
        }
        assertInventoryUnchanged("complete-counter")
    }

    private fun restore(document: BackupDocument) = runBlocking {
        check(artifactDirectory.isDirectory || artifactDirectory.mkdirs())
        val json = BackupJson.encode(document)
        File(artifactDirectory, "$fixtureName-incoming.json").writeText(json)
        val decoded = BackupJson.decode(json)
        assertEquals("serialized backup preserves every original session", document.sessions, decoded.sessions)
        assertEquals("serialized backup preserves every distinct prescription", document.sessionExercises, decoded.sessionExercises)
        assertEquals("serialized backup preserves every original saved row", document.setLogs, decoded.setLogs)
        assertEquals("serialized backup preserves all original exercise identities", document.exercises, decoded.exercises)
        val validation = BackupValidator.validate(decoded, AuthoredInventory.EMPTY)
        assertTrue("this is an accepted supported backup shape: $validation", validation is BackupValidation.Valid)
        assertTrue("the isolated fixture begins with no authored rows", deps.localBackupRepository.authoredInventory().isEmpty)

        // Take the same prepare/confirm-commit path as a supported file restore. There is
        // no destructive-restore override, DAO seed or shortened journal/catalog epilogue.
        val beforePrepare = inventory()
        writeInventory("before-prepare", beforePrepare)
        val plan = deps.backupService.prepareRestore(json, sourceName = "$fixtureName.json")
        assertEquals("the service accepts the exact supported serialized graph", decoded, plan.document)
        assertEquals("prepare is read-only for all saved data and preferences", beforePrepare, inventory())
        val restored = deps.backupService.commitRestore(plan)
        restoredInventory = inventory()
        writeInventory("restored", checkNotNull(restoredInventory))
        assertTrue("the full service commit restores its preferences", restored.preferencesRestored)
        assertFalse("the supported restore has no unfinished settings phase", restored.settingsPending)
        assertEquals(
            "the full commit records its verified safety copy",
            restored.safetySnapshotId,
            deps.backupService.listSafetySnapshots().single().id,
        )

        val snapshot = deps.localBackupRepository.createSnapshot()
        assertEquals("replacement keeps all sessions and captured timestamps", decoded.sessions, snapshot.sessions)
        assertEquals("replacement keeps distinct row IDs and every prescription field", decoded.sessionExercises, snapshot.sessionExercises)
        assertEquals("replacement keeps complete original SetLog values", decoded.setLogs, snapshot.setLogs)
        val originalExerciseIds = decoded.exercises.map { it.id }.toSet()
        assertEquals(
            "catalog reconciliation preserves these custom restored exercise identities",
            decoded.exercises,
            snapshot.exercises.filter { it.id in originalExerciseIds },
        )
        assertTrue("no live workout is introduced by replacement", deps.database.workoutDao().getAllSessions().all { it.finishedAt != null })
        assertNull(deps.workoutRepository.getInProgress())
        assertFalse("the full supported restore closes its journal", deps.restoreJournal.isOpen())
        originalSession = checkNotNull(deps.workoutRepository.getSession(SESSION_ID))
        assertEquals(decoded.sessionExercises.map { it.id }, checkNotNull(originalSession).exercises.map { it.id })
        val saved = checkNotNull(originalSession).sets.single()
        assertEquals(SET_ID, saved.id)
        assertEquals(SESSION_ID, saved.sessionId)
        assertEquals(decoded.setLogs.single().exerciseId, saved.exerciseId)
        assertEquals(1, saved.setNumber)
        assertEquals(45.0, saved.weightKg, .0001)
        assertEquals(6, saved.reps)
        assertEquals(8, saved.rpe)
        assertFalse(saved.isWarmup)
        assertEquals(STAMP + 1_000L, saved.completedAt)
        assertNull(saved.durationSeconds)
        assertInventoryUnchanged("restored-before-mount")
    }

    private fun mount() {
        detail = SessionDetailViewModel(
            ApplicationProvider.getApplicationContext(),
            SavedStateHandle(mapOf("sessionId" to SESSION_ID)),
            deps,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalWeightUnit provides WeightUnit.KG,
                LocalLayoutDirection provides direction,
            ) {
                PersonalTrainerTheme(reduceMotion = true) {
                    SessionDetailScreen(
                        onBack = { throw AssertionError("inspection must not leave this saved session") },
                        onOpenExercise = { throw AssertionError("Edit must use the original saved row") },
                        onOpenActiveSession = { throw AssertionError("inspection must not create a live workout") },
                        viewModel = checkNotNull(detail),
                    )
                }
            }
        }
        host.drain()
    }

    private fun expandCorrectionSheetIfOffered(setId: String) {
        val dialog = SemanticsMatcher("the actual correction dialog") {
            it.config.getOrNull(SemanticsProperties.IsDialog) != null
        }
        val sheet = SemanticsMatcher("the actual modal sheet pane") {
            it.config.getOrNull(SemanticsProperties.PaneTitle) != null
        } and hasAnyAncestor(dialog)
        val expand = SemanticsMatcher("the actual sheet offers Expand") {
            it.config.getOrNull(SemanticsActions.Expand)?.action != null
        } and hasAnyAncestor(dialog)
        assertEquals(
            "the original correction uses exactly one actual modal sheet",
            1,
            compose.onAllNodes(sheet, useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        host.capture("correction-default-anchor-$setId")
        assertInventoryUnchanged("correction-default-anchor-$setId")
        val initial = compose.onNode(sheet, useUnmergedTree = true).fetchSemanticsNode()
        val initialTop = initial.positionInWindow.y
        val expanders = compose.onAllNodes(expand, useUnmergedTree = true).fetchSemanticsNodes()
        if (expanders.isEmpty()) {
            assertTrue(
                "a sheet without an Expand action is already fully inside its actual window",
                initial.boundsInWindow.height >= initial.size.height - 1f,
            )
            return
        }
        assertEquals("the visible sheet offers one actual Expand action", 1, expanders.size)
        assertTrue("the actual Expand handle is visible", expanders.single().boundsInWindow.height > 1f)

        // A half-open anchored sheet can have zero inner scroll range because its content
        // fits its fully expanded measured height. Use its shipping accessibility action
        // before asking those real scroll ancestors to reveal the original RPE control.
        compose.onNode(expand, useUnmergedTree = true).performSemanticsAction(SemanticsActions.Expand) {
            assertTrue("the actual sheet accepts its own Expand action", it())
        }
        host.drain()
        compose.awaitThat("the actual correction sheet settles fully expanded", {
            compose.onNode(sheet, useUnmergedTree = true).fetchSemanticsNode().let {
                "origin=${it.positionInWindow}; size=${it.size}; clip=${it.boundsInWindow}"
            }
        }) {
            val expanded = compose.onNode(sheet, useUnmergedTree = true).fetchSemanticsNode()
            compose.onAllNodes(expand, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                expanded.positionInWindow.y < initialTop - 1f &&
                expanded.boundsInWindow.height >= expanded.size.height - 1f
        }
        val expanded = compose.onNode(sheet, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the expanded sheet moved above its original half-open anchor", expanded.positionInWindow.y < initialTop - 1f)
        assertTrue("the expanded sheet fits its actual window", expanded.boundsInWindow.height >= expanded.size.height - 1f)
        host.capture("correction-expanded-anchor-$setId")
        assertInventoryUnchanged("correction-expanded-anchor-$setId")
    }

    private fun traverseActualList() {
        repeat(30) { frame ->
            host.drain()
            val rows = compose.onAllNodes(SemanticsMatcher("actual recorded row tags") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("session-detail-set-") == true
            }, useUnmergedTree = true).fetchSemanticsNodes()
            val counts = rows.groupingBy { it.config[SemanticsProperties.TestTag] }.eachCount()
            assertTrue("actual list never composes two logical rows for one saved ID: $counts", counts.values.all { it == 1 })
            host.capture("actual-list-frame-$frame")
            val list = compose.onNodeWithTag(SessionDetailTestTags.CONTENT).fetchSemanticsNode()
            val range = checkNotNull(list.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
            if (range.value() >= range.maxValue() - .001f) return
            val height = list.boundsInWindow.height
            assertTrue("actual Detail leaves a usable scroll viewport", height > 1f)
            compose.runOnUiThread {
                assertTrue(
                    "actual list accepts a forward scroll before its end",
                    checkNotNull(list.config.getOrNull(SemanticsActions.ScrollBy)?.action)(0f, height * .75f),
                )
            }
        }
        throw AssertionError("the actual restored Detail did not reach its end after 30 bounded scrolls")
    }

    private fun assertInventoryUnchanged(stage: String) {
        val after = inventory()
        writeInventory(stage, after)
        assertEquals("saved graph and all prescriptions remain exact at $stage", restoredInventory, after)
    }

    /**
     * All raw workout/catalog/routine rows, including unfinished rows that export excludes,
     * plus the complete exportable Room tables and restored preference payload. Only the
     * generated export time is normalized; all saved/captured timestamps remain literal.
     */
    private fun inventory(): List<Any?> = runBlocking {
        listOf(
            deps.database.workoutDao().getAllSessions().sortedBy { it.id },
            deps.database.workoutDao().getAllSessionExercises().sortedBy { it.id },
            deps.database.workoutDao().getAllSets().sortedBy { it.id },
            deps.database.exerciseDao().getAll().sortedBy { it.id },
            deps.database.routineDao().getAllRoutines().sortedBy { it.id },
            deps.database.routineDao().getAllRoutineExercises().sortedBy { it.id },
            deps.database.catalogDao().getAllCredits().sortedWith(compareBy({ it.exerciseId }, { it.muscleKey })),
            deps.database.catalogDao().getSeedMeta(),
            deps.localBackupRepository.createSnapshot().copy(exportedAt = EXPORTED_AT),
            deps.workoutRepository.getInProgress(),
            deps.restoreJournal.isOpen(),
        )
    }

    private fun writeInventory(stage: String, value: List<Any?>) {
        check(artifactDirectory.isDirectory || artifactDirectory.mkdirs())
        File(artifactDirectory, "$fixtureName-$stage-inventory.txt").writeText(
            "fixture=$fixtureName\nrunId=$artifactRunId\n" +
                value.mapIndexed { index, rows -> "$index: $rows" }.joinToString("\n"),
        )
    }

    private fun partialFixture(): RestoredFixture {
        val plan = prescription("recovery-intact-plan", INTACT_ID, 0, 3, 5, 100.0, 90)
        return RestoredFixture(
            name = "partial-membership",
            document = document(
                exercises = listOf(exercise(INTACT_ID, "Untouched plan lift"), exercise(ORPHAN_ID, "Original saved lift")),
                plans = listOf(plan),
                recordedExerciseId = ORPHAN_ID,
            ),
            prescriptions = listOf(PrescriptionWords(plan.id, plan.exerciseId, "3 × 5", "100 kg", "1:30")),
        )
    }

    private fun repeatedFixture(): RestoredFixture {
        val first = prescription("recovery-plan-a", REPEATED_ID, 0, 3, 5, 100.0, 90)
        val second = prescription("recovery-plan-b", REPEATED_ID, 1, 2, 8, 80.0, 120)
        return RestoredFixture(
            name = "repeated-prescriptions",
            document = document(
                exercises = listOf(exercise(REPEATED_ID, "Repeated prescribed lift")),
                plans = listOf(first, second),
                recordedExerciseId = REPEATED_ID,
            ),
            prescriptions = listOf(
                PrescriptionWords(first.id, first.exerciseId, "3 × 5", "100 kg", "1:30"),
                PrescriptionWords(second.id, second.exerciseId, "2 × 8", "80 kg", "2:00", originalPosition = 2),
            ),
        )
    }

    private fun document(
        exercises: List<BackupExercise>,
        plans: List<BackupSessionExercise>,
        recordedExerciseId: String,
    ) = BackupDocument(
        exportedAt = EXPORTED_AT,
        preferences = BackupPreferences(weightUnit = "kg", onboardingComplete = true),
        exercises = exercises,
        routines = emptyList(),
        routineExercises = emptyList(),
        sessions = listOf(BackupSession(
            SESSION_ID, null, "Saved graph recovery probe", STAMP, "Keep every original saved row",
            10, STAMP, STAMP + 600_000L,
        )),
        sessionExercises = plans,
        setLogs = listOf(BackupSetLog(
            SET_ID, SESSION_ID, recordedExerciseId, 1, 45.0, 6, 8, false, STAMP + 1_000L,
        )),
    )

    private fun exercise(id: String, name: String) = BackupExercise(
        id = id, name = name, muscleGroup = "Quads", notes = "", isCustom = true,
        equipment = "OTHER", loadType = "EXTERNAL",
    )

    private fun prescription(
        id: String, exerciseId: String, order: Int, sets: Int, reps: Int, kg: Double, rest: Int,
    ) = BackupSessionExercise(id, SESSION_ID, exerciseId, order, sets, reps, kg, rest)

    private data class RestoredFixture(
        val name: String,
        val document: BackupDocument,
        val prescriptions: List<PrescriptionWords>,
    )

    private data class PrescriptionWords(
        val id: String,
        val exerciseId: String,
        val work: String,
        val load: String,
        val rest: String,
        val originalPosition: Int = 1,
    )

    private companion object {
        const val SESSION_ID = "recovery-graph-session"
        const val SET_ID = "recovery-original-set"
        const val INTACT_ID = "recovery-intact-lift"
        const val ORPHAN_ID = "recovery-orphan-lift"
        const val REPEATED_ID = "recovery-repeated-lift"
        const val STAMP = 1_790_856_000_000L
        const val EXPORTED_AT = "2026-10-09T00:00:00Z"
    }
}
