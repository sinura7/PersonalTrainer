# Phase 1b — Log repair

## 1. Mission

Make the training log correctable and reusable: edit sets in finished sessions (preserving heat-window attribution and PR chronology), delete a session, repeat a finished session as a new one, and replace the delete-set confirm dialog with immediate-delete-plus-undo. This honors ROADMAP's recorded decision that editing history "deserves its own change" (docs/ROADMAP.md:83-85) and closes the two headline defects the plan named (edit, delete). Schema-free by hard constraint. Execution follows docs/gameplan/PROTOCOL.md: branch `claude/phase-1b-log-repair`, one PR, starts only after the Phase 1a PR merges, closes only on owner sign-off.

## 2. Read first

1. `docs/gameplan/PROTOCOL.md` — protocol; not repeated here. (Delivered by the protocol packet — `docs/gameplan/` does not exist on the branch today. If missing, the protocol facts in this packet are sufficient and binding.)
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
12. `app/src/main/java/com/sinura/personaltrainer/workout/FinishWorkout.kt` / `DiscardWorkout.kt` (from 1a) — session delete must NOT reuse discard; see settled decision 6.
13. `app/src/main/java/com/sinura/personaltrainer/data/local/dao/RoutineDao.kt` — `getById` (:26), used by Repeat's routine-still-exists check.
14. `docs/DESIGN_AUDIT.md` §6.10 (I-04, I-07) and `docs/UI_REDESIGN.md` §6 (:152) + appendix MO-06 (:331).

## 3. Binding doctrine

(As in the 1a packet: `REVISED_STRUCTURE.md` and `attacks.md` are planning documents not present in this repository; their constraints are restated here and this packet's text is authoritative.)

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
   - `:226-230` `updateSessionNotes` finished-return — **KEPT.** Post-finish notes editing is out of scope.
   - `:235` `finishSession` already-finished-return — **KEPT** (idempotence).
3. **Field editability:** per set — `weightKg`, `reps`, `rpe`, `isWarmup`; plus add set and delete set. NEVER editable: `date`, `startedAt`, `finishedAt`, `durationMinutes` (`finishSession` :236-239 stays the only writer of `durationMinutes`), `completedAt` of an existing set, session notes, the exercise list.
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
   UI: SessionDetail header gains a trailing overflow `IconButton` (`Icons.Outlined.MoreVert`) → `DropdownMenu` (material3; precedent: the LiveSessionBar overflow landed in Phase 1a) with "Repeat workout" and "Delete session…". Delete is two-step, mirroring the discard dialog anatomy (ActiveWorkoutScreen.kt:472-503) via `ConfirmActionDialog(destructive = true)`: title "Delete this session?", body "This deletes the session and its N logged sets from history. This cannot be undone." (N = total sets), confirm "Delete", dismiss "Cancel". On success the ViewModel emits a one-shot `deleted` event; the screen calls `onBack()`. Delete lives ONLY on SessionDetail (History rows get Repeat only).
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

### 1b-1 · Repository + domain rules for editable finished sessions

- **Modify** `WorkoutRepository.kt` per settled decision 2 (guard edits), add `addSetToFinishedSession` (decision 5), `deleteFinishedSession` (decision 6), `maxCompletedAt` DAO query.
- **Create** `domain/FinishedSessionEdits.kt` (decision 4).
- **Tests** — `app/src/test/java/com/sinura/personaltrainer/domain/FinishedSessionEditsTest.kt`:
  `addedSetLandsAfterLastSet`, `addedSetNeverExceedsFinishedAt`, `addedSetFallsBackToStartedAtWhenNoSets`, `addedSetTotalOnDegenerateWindow` (finishedAt ≤ startedAt does not throw).
- **Tests** — `app/src/test/java/com/sinura/personaltrainer/domain/EditedSessionAttributionTest.kt` (the item-14 pins; build fixtures in the style of `MuscleLoadCalculatorTest`/`PersonalRecordsTest`):
  - `addedSetHeatsTheOriginalDayNotToday`: a session finished 20 days ago gains a set stamped via `FinishedSessionEdits`; `MuscleLoadCalculator` with a THIS_WEEK-equivalent window anchored at today credits nothing; a 30-day window credits it — because `trainedAtMs` reads `set.completedAt` first (MuscleLoadCalculator.kt:110-113).
  - `editedSetKeepsPrChronologyOnOriginalDay`: raising an old set's weight above a newer PR makes it the weight record with `achievedAt` = the OLD `completedAt` (PersonalRecords.kt:135, :147), and the newer lighter set holds no record.
  - `durationIsNeverRecomputedByEdits`: assert by construction — the only `durationMinutes` writer is `finishSession` (grep-asserted in the gate; the test documents the invariant on the domain model: editing set fields leaves `WorkoutSession.durationMinutes` untouched in the fixture round-trip).

### 1b-2 · SessionDetail edit UI

- **Create** `ui/history/SetEditSheet.kt` (decision 9). **Modify** `SessionDetailScreen.kt` (Edit buttons on `SetRow`, "Add set" per `ExerciseBlock`, overflow menu in the header row :70-91, snackbar host, Blocked dialog) and `SessionDetailViewModel.kt` (decision 10). **Modify** `AppNav.kt` SessionDetail composable (:352-360): add `onOpenActiveSession` wiring.
- **Tests:** the pure rules are 1b-1's; UI wiring policed by `check-screen-wiring.py`.

### 1b-3 · Session delete

- Covered by decisions 6 + 10; lands with 1b-2's overflow. Confirm copy exactly as settled. After delete, `onBack()`; History updates reactively.
- **Gate grep** (below) proves discard/delete separation.

### 1b-4 · Repeat-last-session

- **Create** `domain/RepeatSessionPlan.kt` (decision 7). **Modify** `WorkoutRepository.kt` (`repeatSession`, `RepeatOutcome`), `HistoryViewModel.kt`/`HistoryScreen.kt` (decision 11), `SessionDetailViewModel.kt` (already in 1b-2's overflow), `ui/components/GymSurfaces.kt` `SessionLogRow` (optional `onRepeat`), `AppNav.kt` History composable (:313-318, add `onOpenActiveSession`).
- **Tests** — `app/src/test/java/com/sinura/personaltrainer/domain/RepeatSessionPlanTest.kt`:
  `preservesExerciseOrder`, `targetSetsFromActualWorkingSetsExcludingWarmups`, `plannedButUnloggedExerciseKeepsPlannedTargets`, `targetRepsFromLastWorkingSetWithFallbacks`, `freeWorkoutDerivesExercisesFromSetsInCompletionOrder`, `neverEmitsWeightsOrSets` (plan carries no `targetWeightKg`, no logged sets).

### 1b-5 · Delete-set undo

- **Modify** `WorkoutRepository.kt` (`deleteSet` return + `restoreSet`, decision 8), `ActiveWorkoutViewModel.kt` (:594-614 + undo state), `ActiveWorkoutScreen.kt` (dialog deletion + snackbar), `SessionDetailViewModel/Screen` (same mechanic).
- **Tests:** renumbering/restore round-trip is repository-level (no lane yet — Phase 2); the deletion→snackbar→undo path is owner-checklist verified (steps 3 and 7). Keep `restoreSet` symmetrical with `deleteSet` so review can verify by inspection.

## 6. Out of scope

- ANY Room schema change (hard constraint — entities, tables, columns, indices, migrations).
- Editing session notes, date, duration, `startedAt`/`finishedAt`; adding/removing exercises in finished sessions.
- Editing any non-latest set in the ACTIVE workout (W-09's general fix) — only the delete mechanic changes there.
- History month grouping, Body-absorbs-History, calendar multi-session sheet — Phase 6a.
- `StartTrainingDay` changes, pins, planner — Phase 4.
- Recommendation surfaces, RPE consumption, increment table — Phases 5/7.
- Backup format changes: v1 backups already carry all set fields; edited history round-trips as-is — do not touch `data/backup/*`.
- The finish/discard use cases from 1a — Repeat and Delete must not route through or modify them.

## 7. Acceptance gate

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

Domain tests by name: `FinishedSessionEditsTest`, `EditedSessionAttributionTest`, `RepeatSessionPlanTest` — all methods listed in §5 — plus the entire pre-existing suite unchanged and green. On the owner's machine: `./gradlew testDebugUnitTest assembleDebug` green.

## 8. Owner device checklist

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

## 9. Estimates

- Executor: 2-3 days (1b-1 ≈ 0.75 with tests; 1b-2/1b-3 ≈ 0.75; 1b-4 ≈ 0.5-0.75; 1b-5 ≈ 0.5; gate + PR ≈ 0.25).
- Owner: 0.5-1 day (PR review ≈ 1.5h — review the guard diff and `repeatSession` hardest; device checklist ≈ 40 min; sign-off).

## 10. Hand-back

The completion report must contain: (1) the PR link on `claude/phase-1b-log-repair`; (2) pasted output of every gate command and all four invariant greps; (3) the guard-disposition table from settled decision 2 with each line's final state confirmed against the diff; (4) the three new domain-test classes with method counts and lane totals before/after; (5) file-by-file change list, one line each; (6) any deviation from a settled decision, flagged with rationale (target: zero); (7) the owner checklist verbatim with an empty result column; (8) the statement that the phase stays open until the owner posts checklist results and merges, and that Phase 2 (test substrate) is next per REVISED_STRUCTURE.
