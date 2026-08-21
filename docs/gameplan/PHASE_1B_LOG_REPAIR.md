# Phase 1b — Log repair

## 1. Mission

Make the training log correctable and reusable: edit sets in finished sessions (preserving heat-window attribution and PR chronology), edit a finished session's notes, delete a session, repeat a finished session as a new one, and replace the delete-set confirm dialog with immediate-delete-plus-undo. This honors ROADMAP's recorded decision that editing history "deserves its own change" (docs/ROADMAP.md:83-85) and closes the two headline defects the plan named (edit, delete). Schema-free by hard constraint. Execution follows docs/gameplan/PROTOCOL.md §3. **Phase 1 is ONE phase built from TWO packets:** `PHASE_1A_SESSION_LIFECYCLE.md` and this packet share a single branch `claude/phase-1-session-hygiene`, a single PR, and a single combined owner evening. Both packets go to the SAME executor session, executed strictly in order: 1A's work items in full with **its §7 acceptance gate green FIRST**, then this packet's work items on top of the same branch. There is no separate 1B branch, no separate 1B PR, and no wait for a 1A merge — 1A's code is already on the branch under your hand. This packet's gate greps re-assert 1A's invariants, so the sequence self-verifies. The one PR contains both packets' work, and the owner runs both owner checklists in one sitting. Phase 1 starts only after the **Phase 2** (test substrate) PR merges — Phase 2 runs ahead of Phase 1 in the execution order (0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b → 7 → 8; **phase numbers are identifiers, not sequence**). The phase closes only on owner sign-off.

## 2. Read first

1. `docs/gameplan/PROTOCOL.md` — protocol; not repeated here. It is committed and readable at that path. Note especially §6's **mandatory phase-start re-baseline** (D-G): the FIRST commit on `claude/phase-1-session-hygiene` is the re-baseline report (current trunk tip, measured domain-test and test-class totals, every drifted packet literal with its verified current value). That commit is made once for Phase 1, at 1A's start; if you are picking this packet up after 1A's gate went green, re-check only the literals THIS packet cites and record any that moved. Drift explained by a merged prior phase or by the game plan's own commits is EXPECTED — adopt the new value and continue; stop only on a mismatch nothing accounts for.
2. `app/src/main/java/com/sinura/personaltrainer/data/repository/WorkoutRepository.kt` — every `finishedAt` guard (:123, :154-156, :196-199, :226-230, :235), `deleteSet` renumbering (:214-224), `logSet` (:144-186), `insertSessionIfIdle` (:100-112), `LoggedSet` nested type (:377-380).
3. `app/src/main/java/com/sinura/personaltrainer/ui/history/SessionDetailScreen.kt` + `SessionDetailViewModel.kt` — the read-only screen being made editable (header :70-91, receipt :168-218, `SetRow` :280-304; ViewModel :18-31).
4. `app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutScreen.kt` — discard-dialog anatomy (:472-503), delete-set dialog to delete (:505-535), `SetRow` latest-only Edit/Delete (:903-958), snackbar host (:161-168).
5. `app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutViewModel.kt` — `deleteSet` (:594-614, note the `wasLatest` rest-timer stop), `editSet` (:575-588).
6. `app/src/main/java/com/sinura/personaltrainer/domain/MuscleLoadCalculator.kt` — `trainedAtMs` (:110-113): `completedAt` first, then session dates — WHY added-set timestamps must land inside the session window.
7. `app/src/main/java/com/sinura/personaltrainer/domain/PersonalRecords.kt` — tie-break `.thenBy { it.first.completedAt }` (:135), `achievedAt = completedAt` (:147) — WHY edits must preserve `completedAt`.
8. `app/src/main/java/com/sinura/personaltrainer/ui/history/HistoryScreen.kt` — the rows gaining a Repeat overflow (:108-125); `SessionLogRow` lives in `ui/components/GymSurfaces.kt:325`.
9. `app/src/main/java/com/sinura/personaltrainer/ui/workout/StartWorkoutScreen.kt` — `ResumeBlock` (:169-183): the blocked-resume copy Repeat reuses.
10. `app/src/main/java/com/sinura/personaltrainer/ui/components/Common.kt` — `ConfirmActionDialog` (:147), `SetEntryPanel`/`WeightStepper`/`RepsStepper` (:200-300), `InstrumentChip` (:838).
11. `app/src/main/java/com/sinura/personaltrainer/ui/components/ExercisePickerSheet.kt` and `ui/library/ExerciseEditorSheet.kt` — ModalBottomSheet precedents for the new SetEditSheet.
12. `app/src/main/java/com/sinura/personaltrainer/workout/FinishWorkout.kt` / `DiscardWorkout.kt` (landed earlier on this same branch by the 1A packet) — session delete must NOT reuse discard; see settled decision 6.
13. `app/src/main/java/com/sinura/personaltrainer/data/local/dao/RoutineDao.kt` — `getById` (:26), used by Repeat's routine-still-exists check.
14. `docs/DESIGN_AUDIT.md` §6.10 (I-04, I-07) and `docs/UI_REDESIGN.md` §6 (:152) + appendix MO-06 (:331).

## 3. Binding doctrine

(As in the 1A packet: `docs/gameplan/PROTOCOL.md` and `docs/gameplan/REVISED_STRUCTURE.md` **are committed and readable** at those paths — read them for context. `attacks.md` is a planning document not present in this repository. Every constraint any of them imposes is restated here, and this packet's text remains authoritative on detail.)

- **ROADMAP.md** :83-85 (editable sessions are their own change — this phase IS that change) and the known-open row "Finished sessions cannot be edited" (:146), closed by this phase.
- **DESIGN_AUDIT.md** I-04/I-07 (:422-430 — session detail shape; duration is stored once at finish and must stay real), W-09 (:302 — latest-only editing in the live workout is a known defect; this phase changes the delete mechanic only, not the latest-only rule), W-14/W-15 (:307-308 — destructive actions are named text actions behind their own confirm).
- **UI_REDESIGN.md** §6 "Confirm destruction, never completion" (:152) — delete-set becomes immediate + snackbar Undo; sheets host tasks, alerts only confirm; appendix MO-06 (:331).
- **DIRECTION_B_INSTRUMENT.md** — sheets on `surface/3` with drag handle (~:170); danger is a verb, never decoration (:14).
- **REVISED_STRUCTURE.md** — item 14 (verbatim rules: edits preserve `completedAt`; sets added post-finish are timestamped inside `[startedAt, finishedAt]`; `durationMinutes` never recomputed; domain tests pin heat-window attribution and PR chronology to the original day); Phase 1b definition (:107-111); repeat must respect the single-in-progress transaction and never silently open the current session (item 18's principle applied here).
- **attacks.md** constraints: integration MAJOR "completedAt is load-bearing in three computations" (the three citations in Read-first 6-7 plus WorkoutSummaryBuilder); executor MAJOR (h) repeat-last must go through `insertSessionIfIdle`; scope MAJOR "Session delete … assigned to no phase" — assigned here.

## 4. Settled decisions

1. **Schema-free is a hard constraint:** no Room entity, table, column, or index changes; no migration. New `@Query`/`@Insert` DAO methods only.
2. **Guard disposition, by line** (WorkoutRepository.kt):
   - `:123` `addExerciseToSession` finished-guard — **KEPT.** Adding exercises to finished sessions is out of scope; only sets are editable.
   - `:154-156` `logSet` finished-error — **KEPT.** The live-logging path (PR detection :183, rest-timer follow-up in the VM) must not run for history edits. Post-finish adds go through the NEW `addSetToFinishedSession`.
   - `:196-199` `updateSet` finished-error — **RELAXED: delete these four lines.** `updateSet` already preserves `completedAt` and `setNumber` (the `copy` at :204-211 touches neither) — state that as an invariant in its KDoc.
   - `:214-224` `deleteSet` — has NO finished guard today; now intentional. Keep, with the return-value change of decision 8.
   - `:226-230` `updateSessionNotes` finished-return — **RELAXED: delete the `if (current.finishedAt != null) return` line.** This is the same one-line guard removal as `updateSet`, and it is safe for the same reason: notes carry **no timestamp, PR, or heat semantics**, so nothing downstream shifts. The method writes `notes` and nothing else (`date`, `startedAt`, `finishedAt`, `durationMinutes` are untouched by its `copy`), the string is read only by SessionDetail/summary display and the backup writer (LocalBackupRepository.kt:122, :228 — so it round-trips already), and no calculator reads it. State that as an invariant in its KDoc: writes `notes` only, on finished and in-progress sessions alike. Post-finish notes editing is work item 1b-6.
   - `:235` `finishSession` already-finished-return — **KEPT** (idempotence).
3. **Field editability:** per set — `weightKg`, `reps`, `rpe`, `isWarmup`; plus add set and delete set. Per session — `notes` (decision 2's relaxed guard; work item 1b-6). NEVER editable: `date`, `startedAt`, `finishedAt`, `durationMinutes` (`finishSession` :236-239 stays the only writer of `durationMinutes`), `completedAt` of an existing set, the exercise list.
4. **Added-set timestamp rule** (item 14, made literal): new pure object
   ```kotlin
   // domain/FinishedSessionEdits.kt
   object FinishedSessionEdits {
       /** Monotone, and always inside the session's lifetime. Total even for degenerate inputs. */
       fun timestampForAddedSet(startedAt: Long, finishedAt: Long, lastCompletedAt: Long?): Long =
           ((lastCompletedAt ?: startedAt) + 1)
               .coerceAtLeast(startedAt)
               .coerceAtMost(maxOf(finishedAt, startedAt))
   }
   ```
   `lastCompletedAt` = `MAX(completedAt)` across ALL sets of the session (new DAO query `@Query("SELECT MAX(completedAt) FROM set_logs WHERE sessionId = :sessionId") suspend fun maxCompletedAt(sessionId: String): Long?`).
5. **New repository method** for post-finish adds:
   ```kotlin
   suspend fun addSetToFinishedSession(
       sessionId: String, exerciseId: String,
       weightKg: Double, reps: Int, rpe: Int?, isWarmup: Boolean,
   ): SetLog
   ```
   Reads the session (error if missing); errors if `finishedAt == null` ("This workout is still in progress."); validates exactly like `logSet` (:157-162: reps ≥ 1, `SetLogRules.validate`, weight sanitation); `setNumber` = that exercise's set count + 1 (mirror :160); `completedAt` from decision 4; `insertSet`; returns the domain `SetLog`. NO PR detection, NO rest timer, NO records announcement.
6. **Session delete:** new repository method — NOT `discardSession` (that belongs to `DiscardWorkout` and the 1a invariant grep):
   ```kotlin
   suspend fun deleteFinishedSession(sessionId: String) {
       val current = workoutDao.getSessionRow(sessionId) ?: return
       check(current.finishedAt != null) { "Only finished sessions can be deleted; discard owns in-progress." }
       workoutDao.deleteSession(sessionId)   // FK CASCADE clears session_exercises (SessionExerciseEntity.kt:16) + set_logs (SetLogEntity.kt:15)
   }
   ```
   UI: SessionDetail header gains a trailing overflow `IconButton` (`Icons.Outlined.MoreVert`) → `DropdownMenu` (material3; precedent: the LiveSessionBar overflow, already on this branch from the 1A packet) with "Repeat workout" and "Delete session…". Delete is two-step, mirroring the discard dialog anatomy (ActiveWorkoutScreen.kt:472-503) via `ConfirmActionDialog(destructive = true)`: title "Delete this session?", body "This deletes the session and its N logged sets from history. This cannot be undone." (N = total sets), confirm "Delete", dismiss "Cancel". On success the ViewModel emits a one-shot `deleted` event; the screen calls `onBack()`. Delete lives ONLY on SessionDetail (History rows get Repeat only).
7. **Repeat-last-session.** Pure plan in domain:
   ```kotlin
   // domain/RepeatSessionPlan.kt
   data class RepeatItem(val exerciseId: String, val targetSets: Int, val targetReps: Int, val restSeconds: Int)
   object RepeatSessionPlan { fun from(source: WorkoutSession): List<RepeatItem> }
   ```
   Copy semantics (settled, exhaustive): exercises + their order come from `source.exercises` by `sortOrder`; if that list is empty (free workout), derive distinct `exerciseId`s from `source.sets` in first-`completedAt` order. `targetSets` = the exercise's actual non-warm-up set count in the source, `coerceAtLeast(1)`; an exercise planned but never logged keeps its planned `targetSets`. `targetReps` = reps of the exercise's last (highest `completedAt`) working set; fallback planned `targetReps`; fallback 5. `restSeconds` = planned value when the exercise was planned, else 90. `targetWeightKg` = null always (the progression prefill owns weight suggestions — ActiveWorkoutViewModel.kt:379-390). NEVER copied: logged sets, notes, date, duration, timestamps.
   Repository entry, through the single-in-progress transaction:
   ```kotlin
   sealed interface RepeatOutcome {
       data class Started(val sessionId: String) : RepeatOutcome
       data class Blocked(val inProgressSessionId: String, val inProgressName: String?) : RepeatOutcome
       data class Failed(val message: String) : RepeatOutcome
   }
   suspend fun repeatSession(sourceSessionId: String): RepeatOutcome
   ```
   Reads the source via `getSession` (Failed "That session is no longer available." if missing; Failed if `finishedAt == null`); builds a new `WorkoutSessionEntity` (new UUID, `date`/`startedAt` = now, `finishedAt` = null, `durationMinutes` = 0, notes "", `routineName` = source's, `routineId` = `source.routineId?.takeIf { database.routineDao().getById(it) != null }` — never insert a dangling FK) plus `SessionExerciseEntity` rows from the plan; calls `insertSessionIfIdle(session, exercises)` (:100-112). If the returned id ≠ the new session's id → `Blocked(returnedId, name of that in-progress session)` — NEVER silently `Started` with the old id. UI: "Repeat workout" in the SessionDetail overflow (decision 6) and a trailing overflow on History rows — `SessionLogRow` (GymSurfaces.kt:325) gains an optional `onRepeat: (() -> Unit)? = null` rendering a trailing overflow `IconButton`+`DropdownMenu`("Repeat workout") only when non-null; HistoryScreen passes it, Home's Recent list passes nothing. Blocked UX reuses the StartWorkout blocked-resume affordance verbatim (StartWorkoutScreen.kt:174-180): `ConfirmActionDialog` title "Session in progress", body "Finish or discard the current session before starting another.", confirm "Resume workout" (volt, non-destructive) → navigate to the in-progress session, dismiss "Cancel". Started → one-shot navigation to `Route.ActiveWorkout.create(newId)` (`launchSingleTop = true`), pattern HomeViewModel.kt:79-84. AppNav wires new callbacks on the History (:313-318) and SessionDetail (:352-360) composables: `onOpenActiveSession: (String) -> Unit = navigate(ActiveWorkout){ launchSingleTop }`.
8. **Delete-set undo** (active workout AND session detail; supersedes the confirm dialog per MO-06):
   ```kotlin
   // nested in WorkoutRepository, beside LoggedSet (:377-380)
   data class DeletedSet(
       val setId: String, val sessionId: String, val exerciseId: String,
       val setNumber: Int, val weightKg: Double, val reps: Int,
       val rpe: Int?, val isWarmup: Boolean, val completedAt: Long,
   )
   suspend fun deleteSet(setId: String): DeletedSet?    // signature change: returns what was removed
   suspend fun restoreSet(set: DeletedSet)              // re-insert with ORIGINAL id + completedAt, then renumber
   ```
   `restoreSet` no-ops if the session row is gone; renumbering reuses the exact pass at :217-223. In `ActiveWorkoutScreen`: delete `pendingDeleteSetId` and its dialog (:123, :333, :395, :505-535); the SetRow Delete button calls `viewModel.deleteSet(set.id)` immediately. `ActiveWorkoutViewModel.deleteSet` (:594-614) keeps the `wasLatest → restTimer.stop()` behavior, stores the returned `DeletedSet` in a one-shot `StateFlow<DeletedSet?>`; the screen shows it on the existing `snackbarHostState` (:161): message `"Set deleted · {weight} × {reps}"`, `actionLabel = "Undo"`, `SnackbarDuration.Short`; action → `viewModel.undoDeleteSet()` (calls `restoreSet`; does NOT restart the rest timer); dismiss/timeout → clears. SessionDetail uses the same `DeletedSet`/snackbar mechanic with its own host (wrap the screen root in `Box(fillMaxSize)` with `SnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))`). The latest-only Edit/Delete affordance in the live workout (ActiveWorkoutScreen.kt:949-956) is unchanged — W-09's broader fix is not this phase.
9. **SessionDetail edit UI shape:** new file `ui/history/SetEditSheet.kt` — a `ModalBottomSheet` (precedent: ExerciseEditorSheet; `surface/3`, drag handle per DIRECTION_B ~:170) with two modes:
   ```kotlin
   @Composable fun SetEditSheet(
       exerciseName: String,
       initial: SetLog?,            // null = add mode
       onSave: (weightKg: Double, reps: Int, rpe: Int?, isWarmup: Boolean) -> Unit,
       onDelete: (() -> Unit)?,     // null in add mode
       onDismiss: () -> Unit,
   )
   ```
   Contents top-to-bottom: Kicker(exerciseName) + title ("Edit set N" / "Add set"); `SetEntryPanel` (Common.kt:200) for weight/reps; RPE chip row (6-10 via `InstrumentChip`) + warm-up toggle chip; `PrimaryGymButton("Save")`; in edit mode a bare `Danger` text action "Delete set" (immediate delete + undo snackbar per decision 8, sheet dismisses). Entry points on SessionDetailScreen: each `SetRow` (:280-304) gains a trailing "Edit" text button (TextSecondary); each `ExerciseBlock` gains a "Add set" tertiary text action under its set list. Add mode prefills from that exercise's last set (weight/reps), else 0.0 × 5.
10. **SessionDetailViewModel growth:** actions `updateSet(setId, weightKg, reps, rpe, isWarmup)` (repo `updateSet`), `addSet(exerciseId, …)` (repo `addSetToFinishedSession`), `deleteSet(setId)`/`undoDeleteSet()`, `deleteSession()`, `repeatSession()`; state additions: `error: StateFlow<String?>` (rendered as a snackbar), one-shot `deleted: StateFlow<Boolean>`, one-shot `navigateToSession: StateFlow<String?>`, `blockedRepeat: StateFlow<RepeatOutcome.Blocked?>`. All writes wrapped in `runCatchingCancellable` with user-message errors ("Could not save that set. Try again." etc., reusing SetLogRules.isUserMessage filtering like ActiveWorkoutViewModel.kt:567-571).
11. **HistoryViewModel growth:** `repeatSession(sessionId)` + the same one-shot `navigateToSession` / `blockedRepeat` pair; HistoryScreen renders the Blocked dialog and forwards navigation.
12. **Reactive propagation needs no work:** PRs, summary, heat, and insights all recompute from live history (PersonalRecords recompute-only; MuscleLoadCalculator/TrainingInsights are flows) — do not add caches or refresh calls.

## 5. Work items

**Robolectric lane note (applies to every "Tests (Robolectric lane)" bullet below).** Phase 2
shipped the JVM Robolectric lane and it merged before this phase, so the repository writes this
packet adds — `restoreSet`, `repeatSession`, `deleteFinishedSession`, `addSetToFinishedSession`,
`updateSessionNotes` post-finish — finally have a home. One new class holds them all:
`app/src/test/java/com/sinura/personaltrainer/data/local/SessionRepairRepositoryTest.kt`,
`@RunWith(RobolectricTestRunner::class)`, in-memory `TrainerDatabase`, package
`com.sinura.personaltrainer.data.local`. Placement and naming follow the Phase 4 precedent
(`scheduleSlotCascadeOnRoutineDelete`): **outside** `app/src/test/.../domain/`, because
`tools/run-domain-tests.sh` compiles that tree with no Android classpath and a Robolectric
import there breaks the jar lane. Consequence, stated plainly: these tests run only under
`./gradlew testDebugUnitTest` (owner's machine / Studio), never in the executor's jar lane —
so the executor's gate evidence for them is the committed test source plus review, and the
owner's `testDebugUnitTest` run is the proof. The pure-domain tests below stay in `domain/`.

### 1b-1 · Repository + domain rules for editable finished sessions

- **Modify** `WorkoutRepository.kt` per settled decision 2 (guard edits), add `addSetToFinishedSession` (decision 5), `deleteFinishedSession` (decision 6), `maxCompletedAt` DAO query.
- **Create** `domain/FinishedSessionEdits.kt` (decision 4).
- **Tests** — `app/src/test/java/com/sinura/personaltrainer/domain/FinishedSessionEditsTest.kt`:
  `addedSetLandsAfterLastSet`, `addedSetNeverExceedsFinishedAt`, `addedSetFallsBackToStartedAtWhenNoSets`, `addedSetTotalOnDegenerateWindow` (finishedAt ≤ startedAt does not throw).
- **Tests** — `app/src/test/java/com/sinura/personaltrainer/domain/EditedSessionAttributionTest.kt` (the item-14 pins; build fixtures in the style of `MuscleLoadCalculatorTest`/`PersonalRecordsTest`):
  - `addedSetHeatsTheOriginalDayNotToday`: a session finished 20 days ago gains a set stamped via `FinishedSessionEdits`; `MuscleLoadCalculator` with a THIS_WEEK-equivalent window anchored at today credits nothing; a 30-day window credits it — because `trainedAtMs` reads `set.completedAt` first (MuscleLoadCalculator.kt:110-113).
  - `editedSetKeepsPrChronologyOnOriginalDay`: raising an old set's weight above a newer PR makes it the weight record with `achievedAt` = the OLD `completedAt` (PersonalRecords.kt:135, :147), and the newer lighter set holds no record.
  - `durationIsNeverRecomputedByEdits`: assert by construction — the only `durationMinutes` writer is `finishSession` (grep-asserted in the gate; the test documents the invariant on the domain model: editing set fields leaves `WorkoutSession.durationMinutes` untouched in the fixture round-trip).
- **Tests (Robolectric lane — Phase 2 shipped it; see the lane note under §5)** — `SessionRepairRepositoryTest.addSetToFinishedSessionTimestampsInsideTheSessionWindow`: build a finished session with a known `[startedAt, finishedAt]` window and a last set, call `addSetToFinishedSession`, assert the stored `completedAt` is strictly after the previous last set's, `>= startedAt`, `<= finishedAt`, and that `setNumber` is that exercise's count + 1.

### 1b-2 · SessionDetail edit UI

- **Create** `ui/history/SetEditSheet.kt` (decision 9). **Modify** `SessionDetailScreen.kt` (Edit buttons on `SetRow`, "Add set" per `ExerciseBlock`, overflow menu in the header row :70-91, snackbar host, Blocked dialog) and `SessionDetailViewModel.kt` (decision 10). **Modify** `AppNav.kt` SessionDetail composable (:352-360): add `onOpenActiveSession` wiring.
- **Tests:** the pure rules are 1b-1's; UI wiring policed by `check-screen-wiring.py`.

### 1b-3 · Session delete

- Covered by decisions 6 + 10; lands with 1b-2's overflow. Confirm copy exactly as settled. After delete, `onBack()`; History updates reactively.
- **Gate grep** (below) proves discard/delete separation.
- **Tests (Robolectric lane)** — `SessionRepairRepositoryTest.deleteFinishedSessionCascadesExercisesAndSetLogs`: insert a finished session with two `session_exercises` rows and several `set_logs`, call `deleteFinishedSession`, assert all three tables have zero rows for that session id (proves the FK cascade decision 6 relies on). Add the negative half in the same method or a sibling: an in-progress session (`finishedAt == null`) makes `deleteFinishedSession` throw and leaves every row in place.

### 1b-4 · Repeat-last-session

- **Create** `domain/RepeatSessionPlan.kt` (decision 7). **Modify** `WorkoutRepository.kt` (`repeatSession`, `RepeatOutcome`), `HistoryViewModel.kt`/`HistoryScreen.kt` (decision 11), `SessionDetailViewModel.kt` (already in 1b-2's overflow), `ui/components/GymSurfaces.kt` `SessionLogRow` (optional `onRepeat`), `AppNav.kt` History composable (:313-318, add `onOpenActiveSession`).
- **Tests** — `app/src/test/java/com/sinura/personaltrainer/domain/RepeatSessionPlanTest.kt`:
  `preservesExerciseOrder`, `targetSetsFromActualWorkingSetsExcludingWarmups`, `plannedButUnloggedExerciseKeepsPlannedTargets`, `targetRepsFromLastWorkingSetWithFallbacks`, `freeWorkoutDerivesExercisesFromSetsInCompletionOrder`, `neverEmitsWeightsOrSets` (plan carries no `targetWeightKg`, no logged sets).
- **Tests (Robolectric lane)** — `SessionRepairRepositoryTest.repeatSessionBlocksWhenASessionIsInProgress`: with one in-progress session already inserted, call `repeatSession(finishedId)` and assert the outcome is `RepeatOutcome.Blocked` carrying the IN-PROGRESS session's id (never `Started`, and never `Started` with the old id), and that no new session row was created. Pair it with the happy path in the same class — `repeatSessionStartsWhenIdle`: no in-progress session, outcome is `Started(newId)`, the new row has `finishedAt == null`, zero `set_logs`, and `session_exercises` matching the plan's order.

### 1b-5 · Delete-set undo

- **Modify** `WorkoutRepository.kt` (`deleteSet` return + `restoreSet`, decision 8), `ActiveWorkoutViewModel.kt` (:594-614 + undo state), `ActiveWorkoutScreen.kt` (dialog deletion + snackbar), `SessionDetailViewModel/Screen` (same mechanic).
- **Tests (Robolectric lane)** — `SessionRepairRepositoryTest.deleteThenRestoreSetPreservesIdCompletedAtAndOrdering`: insert three sets for one exercise, `deleteSet` the middle one, assert the survivors renumbered to 1..2 and the returned `DeletedSet` carries the original id/`completedAt`/`setNumber`; then `restoreSet` it and assert the row is back with **the same id and the same `completedAt`**, and that `setNumber` ordering is 1..3 again in `completedAt` order. Also assert `restoreSet` no-ops (no throw, no row) when the session row is gone. The deletion→snackbar→undo UI path stays owner-checklist verified (steps 3 and 7). Keep `restoreSet` symmetrical with `deleteSet` so review can verify by inspection.

### 1b-6 · Post-finish notes editing

The same pain class as set edits, and nearly free once decision 2's guard is relaxed: a session
you finished last week says nothing about how it went, and today there is no way to add that.

- **Modify** `WorkoutRepository.kt` — `updateSessionNotes` (:226-230) loses its finished-return
  per settled decision 2. No other change: it still `trim()`s and still writes `notes` alone.
- **Modify** `SessionDetailViewModel.kt` — mirror the ActiveWorkout notes idiom EXACTLY
  (ActiveWorkoutViewModel.kt:130, :136, :205, :238-264, :436): a private
  `notes = MutableStateFlow("")` seeded from the session row's first emission (which also sets
  `lastPersistedNotes`), a public `setNotes(value: String)` that only updates that flow, and an
  `init` collector `notes.collectLatest { delay(NOTES_WRITE_DEBOUNCE_MS); writeNotes(it) }` with
  `private const val NOTES_WRITE_DEBOUNCE_MS = 400L`. `writeNotes` keeps all four guarantees
  verbatim — they are the ordering contract, not incidental style:
  (a) `collectLatest` + `delay` means only the newest value survives the debounce window;
  (b) `lastPersistedNotes` starts **null** until the row has been read, so a cold start can never
  push the empty initial value over stored notes;
  (c) the `if (value == known) return` short-circuit keeps recomposition from rewriting;
  (d) `withContext(NonCancellable) { runCatchingCancellable { … } }` so a write in flight
  completes when the screen leaves composition, with `AppLog.w` on failure and no error UI
  (the next keystroke retries). Expose `notes` read-only on `SessionDetailUiState`.
- **Modify** `SessionDetailScreen.kt` — the notes affordance replaces the read-only tail of
  `SessionReceipt` (the `if (notes.isNotBlank()) { HairlineDivider; Text(notes) }` block at
  :212-215), staying in that exact position: last element of the receipt card, under a
  `HairlineDivider(startIndent = 0.dp)`, above nothing. It renders the ActiveWorkout `NotesBlock`
  idiom (ActiveWorkoutScreen.kt:860-889) unchanged in structure: a `TextButton` toggle labelled
  `"Session notes"` / `"Session notes · saved"` (notes non-blank) / `"Hide notes"` (expanded)
  with the `ExpandMore`/`ExpandLess` icon in `TextSecondary`, and when expanded an
  `OutlinedTextField(value = notes, onValueChange = viewModel::setNotes, minLines = 2,
  label = { Text("Notes") })` at `fillMaxWidth()`. Collapsed by default; expanded state is
  `rememberSaveable` on the screen. `NotesBlock` is `private` in ActiveWorkoutScreen.kt — either
  hoist it to `ui/components/Common.kt` unchanged and call it from both screens, or write the
  same structure locally; do not fork its copy strings.
- **Tests (Robolectric lane)** — `SessionRepairRepositoryTest.updateSessionNotesWritesOnFinishedSession`:
  insert a finished session with notes `"old"`, call `updateSessionNotes(id, "  new  ")`, assert
  the stored notes are `"new"` and that `finishedAt`, `durationMinutes`, `date` and `startedAt`
  are byte-for-byte unchanged. The debounce itself is ViewModel-level and is owner-checklist
  verified (step 11), not unit-tested — the DI seam that would make ViewModels constructible
  under test is not built in this plan.

## 6. Out of scope

- ANY Room schema change (hard constraint — entities, tables, columns, indices, migrations).
- Editing a finished session's date, duration, `startedAt`/`finishedAt`; adding/removing exercises in finished sessions. (Session **notes** ARE editable post-finish in this phase — settled decisions 2-3, work item 1b-6.)
- Editing any non-latest set in the ACTIVE workout (W-09's general fix) — only the delete mechanic changes there.
- History month grouping, Body-absorbs-History, calendar multi-session sheet — Phase 6a.
- `StartTrainingDay` changes, pins, planner — Phase 4.
- Recommendation surfaces, RPE consumption, increment table — Phases 5/7.
- Backup format changes: v1 backups already carry all set fields; edited history round-trips as-is — do not touch `data/backup/*`.
- The finish/discard use cases from 1A — Repeat and Delete must not route through or modify them. (They are on this same branch, landed by the 1A packet before this one starts; treat them as fixed.)
- **Recorded NON-GOAL — backdated / manual session entry** ("I trained yesterday and forgot to
  log it"). Deliberately not built, here or anywhere else in this plan, and this is the written
  disposition so the ask has an answer instead of silence. Nothing in the plan gives it a path:
  `date`, `startedAt` and `finishedAt` stay uneditable in every phase (settled decision 3),
  `repeatSession` stamps `date`/`startedAt` = **now** (decision 7), and `addSetToFinishedSession`
  only ever times a set INSIDE an existing session's window (decision 4). The nearest thing this
  phase offers is Repeat + edit, which produces a session dated **today** — honest, but not the
  same thing. Real backdating would need its own change: an editable session date, a decision
  about what that does to heat windows, PR chronology (`achievedAt`) and "days since", a create-
  session-without-starting-it entry point, and its own owner sign-off. If the owner asks for it,
  that is a new phase, not a widening of this one.

## 7. Acceptance gate

```bash
tools/preflight.sh    # PRIMARY GATE: the eight static checks + the domain-test lane, one command
```

Expected: no findings in any check section, the domain lane green including the new classes, and
a final line `preflight: OK`, exit 0. `tools/preflight.sh` **exists** — Phase 2 shipped it (WI-4)
and merged before Phase 1; it also bootstraps the jars `tools/run-domain-tests.sh` needs on a
cold clone. Judge on that final line, not on exit code alone (four of the eight checks always
exit 0 and report findings on stdout only).

Fallback only if `tools/preflight.sh` is genuinely unavailable — itself a stop-worthy re-baseline
mismatch — run the same checks individually:

```bash
python3 tools/check-named-args.py app/src/main/java
python3 tools/check-when-exhaustive.py app/src/main/java
python3 tools/check-unused-imports.py app/src/main/java
python3 tools/check-internal-imports.py app/src/main/java
python3 tools/check-missing-imports.py
python3 tools/check-design-tokens.py app/src/main/java
python3 tools/check-screen-wiring.py app/src/main/java
tools/syntax-check.sh app/src/main/java
PT_JARS=build/test-jars tools/run-domain-tests.sh
```

All exit 0; domain lane green including the new classes. Invariant greps with expected literal results:

```bash
grep -rn "durationMinutes =" app/src/main/java/com/sinura/personaltrainer/data
# → the only COMPUTED writer is WorkoutRepository.finishSession (today :243). Other matches
#   are legal and expected: construction sites setting 0 (startRoutine :61, startFreeWorkout
#   :91, and the new repeatSession), and pass-through copies of an already-stored value in
#   data/mapper/Mappers.kt (:79, :120) and data/repository/LocalBackupRepository.kt
#   (:123, :229). No new computation of durationMinutes may appear.
grep -rn "workoutRepository.discardSession\|container.workoutRepository.discardSession" app/src/main/java
# → still exactly one matching line: workout/DiscardWorkout.kt (1a invariant holds; delete uses deleteFinishedSession)
grep -rn "pendingDeleteSetId" app/src/main/java
# → no matches (the delete-set dialog is gone)
grep -rn "completedAt =" app/src/main/java/com/sinura/personaltrainer/data/repository/WorkoutRepository.kt
# → SetLogEntity writes assign it only in logSet, addSetToFinishedSession (via
#   FinishedSessionEdits), and restoreSet (original value); updateSet never assigns it (its
#   copy at :204-211 omits it). The grep ALSO matches read-side constructions of
#   ExerciseSetRecord/ExerciseSetEntry (today in recordsBrokenBy, lastPerformance, toEntry —
#   :309, :344, :354, :388) — those copy a stored value into a domain read model and are
#   expected; the invariant is about entity writes only.
```

Domain tests by name (jar lane, `app/src/test/.../domain/`): `FinishedSessionEditsTest`, `EditedSessionAttributionTest`, `RepeatSessionPlanTest` — all methods listed in §5 — plus the entire pre-existing suite unchanged and green, and 1A's `LiveSessionRulesTest` still green on this branch.

Robolectric tests by name (`app/src/test/java/com/sinura/personaltrainer/data/local/SessionRepairRepositoryTest.kt`, Phase-2 lane, runs only under Gradle): `deleteThenRestoreSetPreservesIdCompletedAtAndOrdering`, `repeatSessionBlocksWhenASessionIsInProgress`, `repeatSessionStartsWhenIdle`, `deleteFinishedSessionCascadesExercisesAndSetLogs`, `addSetToFinishedSessionTimestampsInsideTheSessionWindow`, `updateSessionNotesWritesOnFinishedSession`. The executor commits them and proves them by source + review; the owner's run is the proof of green.

On the owner's machine: `./gradlew testDebugUnitTest assembleDebug` green — including `SessionRepairRepositoryTest` — with the output pasted into the PR. **No gate in this phase depends on a CI run** (D-F): CI has never executed in this repo, so the owner-machine output IS the gate; CI green is an ADDITIONAL check to be re-run once the owner's standing, non-gating billing errand is done.

## 8. Owner device checklist

Run in ONE sitting, immediately after `PHASE_1A_SESSION_LIFECYCLE.md` §8, on the same build from the same PR (D-B).

1. Install the phase build. Open History → any old finished session.
2. Tap Edit on a set → sheet opens with its values → raise the weight → Save. **Observe:** the row and the receipt volume update instantly; Body tab heat for THIS WEEK does not light up for that old session; the session's date and minutes are unchanged.
3. In the same sheet flow, Delete a set. **Observe:** no confirm dialog — the set vanishes and a snackbar "Set deleted · … × …" offers Undo. Tap Undo. **Observe:** the set returns with its original set number.
4. "Add set" under a lift → enter values → Save. **Observe:** the set appears; the session still shows its original date; Home's "Days since" is unchanged.
5. PR chronology: edit an old set to a weight heavier than that lift's current record → open the lift's Exercise Detail. **Observe:** the record shows the OLD session's date, not today.
6. Overflow → "Delete session…" → confirm. **Observe:** you land back on History; the session is gone; Body/Home numbers recompute.
7. In an ACTIVE workout, log two sets, delete the latest. **Observe:** immediate delete + Undo snackbar, no dialog; the rest timer (if running for that set) stops; Undo restores the set and does NOT restart the timer.
8. From a History row's overflow, tap "Repeat workout" (no session in progress). **Observe:** a new live session opens with the same lifts in the same order, target sets equal to what you actually did last time, and ZERO logged sets.
9. Discard that session. Start any workout, then try Repeat from History again. **Observe:** a "Session in progress" dialog with "Finish or discard the current session before starting another." and a Resume action that opens the live session — no new session is created.
10. Export a backup (Settings) and restore it. **Observe:** edited sets survive the round trip exactly as edited.
11. Notes on an old session: open any finished session, tap "Session notes", type a line ("felt heavy, short on sleep"), wait a second, then leave the screen and reopen it. **Observe:** the note is there and the toggle reads "Session notes · saved"; the session's date, minutes and sets are unchanged. Now export a backup and restore it as in step 10. **Observe:** the note survives the round trip verbatim.

## 9. Estimates

- Executor: 2-3 days (1b-1 ≈ 0.75 with tests; 1b-2/1b-3 ≈ 0.75; 1b-4 ≈ 0.5-0.75; 1b-5 ≈ 0.5; gate + PR ≈ 0.25).
- Owner: 0.5-1 day (PR review ≈ 1.5h — review the guard diff and `repeatSession` hardest; device checklist ≈ 40 min; sign-off).

## 10. Hand-back

The completion report must contain: (1) the PR link on `claude/phase-1-session-hygiene` — the ONE Phase 1 PR carrying BOTH packets — and a pointer to the re-baseline report commit required by PROTOCOL §6 (D-G), the first commit on the branch; (2) pasted output of `tools/preflight.sh` (or the fallback commands) and all four invariant greps; (3) the guard-disposition table from settled decision 2 with each line's final state confirmed against the diff, calling out explicitly that `updateSessionNotes` (:226-230) moved from KEPT to **RELAXED** and that the phase therefore ships **post-finish notes editing** (work item 1b-6) as an addition to the packet's original four headline items; (4) the three new domain-test classes with method counts and lane totals before/after, **plus** the new Robolectric class `SessionRepairRepositoryTest` with its six method names and the plain statement that it runs only under `./gradlew testDebugUnitTest` (Phase-2 lane, outside `domain/`), never in the jar lane; (5) file-by-file change list, one line each, covering both packets' work on this branch; (6) any deviation from a settled decision, flagged with rationale (target: zero); (7) BOTH owner checklists verbatim — 1A's then this packet's — with an empty result column, since one PR closes one combined owner evening; (8) the statement that the phase stays open until the owner posts checklist results and merges, and that the next phase is **3 — Schema v2** (Phase 2 already merged ahead of Phase 1 under the D-A execution order 0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b → 7 → 8).
