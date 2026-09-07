# Handoff — 7 September 2026, UX P0 batch (Fable)

The verification record for the first batch of the UX handoff package prepared on 6 September 2026 (30 work packages, 97 acceptance checks; master document `Temper-UX-Design-Handoff.md`). It follows [HANDOFF-2026-09-06.md](HANDOFF-2026-09-06.md), which landed the engineering findings R01–R19 that several UX packages depend on. This record does not change any signed decision in [architecture/](architecture/README.md); one documentation addendum to ADR-021 is flagged for the owner in §5.

The updated backlog, acceptance matrix and decision register live in [ux-program/](ux-program/UX-Decision-Log.md).

## 1. Baseline

| Thing | State |
|---|---|
| Reviewed baseline in the package | `22161c54f5b56d7b86294dc7f9490bb1a79f14fe` (`trunk` on 6 September) |
| Starting commit here | `156cc400a0bc7974209e494e4e4cf0525b29bb7d` on `claude/file-visibility-check-jraqc2`, clean working tree, equal to `trunk` |
| Difference from the reviewed baseline | Two commits: `28f485f` (PR #168, R01–R19 and live test 22) and `156cc40` (its record). 89 files, +6208/−480. Every UX finding was re-checked against `156cc40`, not the reviewed commit, because R06, R09 and R10 changed the ground under UX05, UX07 and UX23. |
| Host | Linux 6.18 x86_64, 4 CPU, 15 GB RAM, OpenJDK 21.0.10, Python 3.11.15. Gradle 8.14.3 is installed but cannot configure this project: the Android Gradle Plugin and SDK are served from `dl.google.com`, which the egress policy refuses (`CONNECT` 403; `maven.google.com` redirects there). No KVM, no emulator, no device. |
| Executable gate here | `PT_JARS=build/test-jars tools/preflight.sh`: the 20 exit-code checkers, four summary checkers, the parse-level syntax check, and the domain + backup JVM lanes compiled with `kotlinc` 2.0.21 against stubs (jars fetched from Maven Central). Robolectric, lint, `assembleDebug`, `connectedDebugAndroidTest` and every device lane are **not runnable** here. |
| Baseline gate result | `preflight: OK`; JVM lane **170 classes, 1175 tests, 0 failures** — identical to the 6 September record. |
| Production captures | **None.** No screen of the running app was seen. Every visual claim below is about code, not pixels. |

### 1.1 What the package got right and where it was already behind

- UX04, UX05 and UX06 hold at `156cc40` exactly as pinned; an adversarial re-read (one agent per premise told to refute it) upheld each with file:line evidence and added one finding the package missed: on the routine editor's exit path a *read* that throws (`getById` behind the stub check or the details compare) was uncaught, so the coroutine died with `leaving` stuck true and the editor could not be left at all.
- UX07's premise was written against the reviewed baseline. At `156cc40`, R09's `SavedStateComposerDraft` already carried the composer's title, date and rows through process death (UX07-AC01), so that half is an existing capability, not a gap. The other half — Cancel and Back running during an unresolved save, and onboarding's step and answers living only in memory — was real at `156cc40`.
- UX23's example ("pull may be behind") was real. A full sweep found 37 wording or state-mapping items across the app; six are fixed in this batch and 31 are carried as backlog with proposed text (`ux-program/UX-Backlog.json`, UX23 → `audit`).
- The package's claim that `NumberEntryDialog` callers (the live-workout steppers, bodyweight) were part of the UX06 problem is not true: those already parse before confirming and disable the button until the text parses. They were left alone.

## 2. What changed and what the owner will notice

**Typing a number.** Boxes keep exactly what is typed. Add set, Add cardio, Finish (live cardio), a routine card's box losing focus, and the rest dialog's Set now either store exactly that number or refuse the box with the rule it broke, under the box, with focus moved there — "Enter a whole number of reps, 1 to 100." instead of silently saving 85 for "8.5". Nothing rewrites the text on the way.

**Saving a routine.** Save and Back pop the editor only when the name, notes and staged targets actually reached Room. A failed write keeps the editor open and says why directly above Save; Save is the retry. Back with something unsaved asks — *Try again* or *Leave without saving these* — and says the lifts you added, moved or removed are already saved. A quiet caption above Save explains the split once. A value the routine cannot hold (0 sets, "8.5" reps) blocks Save and Back the same way, with the box's own rule.

**Finishing a workout.** The summary says "Workout saved" only when the finished row was read back. A row that is not there says "Session not found" and does not mention History. A summary that would not compute says "Workout saved. Summary unavailable." with Retry and the full session, and a read that threw before the row says the summary is unavailable and that it cannot tell whether the save landed. Retry only re-reads. A push-up-only session leads with its rep total, not "0 kg".

**Logging a past session.** Cancel is disabled and Back does nothing while the save is in flight; the draft is cleared only by an accepted save. The composer's error appears directly above Save instead of at the top of the list, and a Create-row failure shows inside the picker sheet.

**Setup.** After the process is reclaimed in the background, setup reopens on the same question with the same answers. Nothing is written until *Use this plan*, as before.

**Two sentences fixed.** History's stale caption reads "Showing the last history that loaded. Recent changes may not be shown." Finishing a workout whose row is gone says "Workout not found…" instead of "Could not finish… Try again."; a finish write that failed says the logged sets are still there.

## 3. Batch records

Each record names the premise's status at `156cc40`, the files, the acceptance IDs, exact commands and results, and what only a device can close.

### 3.1 UX06 — numeric entry

| Field | Record |
|---|---|
| Premise at 156cc40 | **Holds.** `NumericEntry.filterDecimal` stripped the sign and merged separators; composer reps/minutes and routine sets/reps/rest used `filter(Char::isDigit)`; live cardio's distance ran through `filterDecimal`; the rest dialog kept digits and `:` so "1.5" became "15". Composer Add set committed `toIntOrNull() ?: 0` and a 0.0 weight fallback. `NumberEntryDialog` callers were already reject-on-parse. |
| Decision | Preserve raw text; validate at the commit boundary with one parser contract (`NumericEntry.typedWeightKg / typedReps / typedWhole / typedDistanceKm`, each returning `Blank`, `Valid`, or `Invalid(rule)`). A stricter keystroke filter was rejected because "8", ".", "5" typed in sequence still lands as 85. Two salvages are deliberate and documented: a blank composer weight is 0 kg (bodyweight), a typed 0 distance is "no distance". |
| Files (main) | `domain/NumericEntry.kt` (filters removed, typed readers and field rules), `domain/ComposerCopy.kt` (`strengthEntry`, `cardioEntry`; fallback parsers removed), `domain/TargetEntry.kt` (new: the routine card's four boxes), `ui/activity/ActivityComposerScreen.kt` (raw text, `isError` + supporting text per box, focus to the first refused box, `Haptics.reject`), `ui/activity/LiveCardioScreen.kt` + `LiveCardioViewModel.kt` (`distanceError`; Finish refuses an unreadable distance and refuses to guess a unit when the preference read fails), `ui/components/Common.kt` (`CustomRestDialog` keeps raw text), `ui/routines/SessionLiftStrip.kt` (boxes read through `TargetEntry`, complaint under each box once the finger leaves it), `ui/routines/RoutineEditorViewModel.kt` (`stageTargets(... invalidReason)`; a rejected box is refused at commit and counted unsaved at exit), `ui/routines/RoutineEditorScreen.kt` |
| Files (test) | `domain/NumericEntryTest.kt` (+6), `domain/ComposerCopyTest.kt` (+2), `domain/TargetEntryTest.kt` (new, 5), `ui/activity/LiveCardioViewModelTest.kt` (+1, Robolectric), `ui/routines/RoutineEditorViewModelTest.kt` (+1, Robolectric), `ui/units/DateCopyTest.kt` (source-shape assertion re-pointed at `cardioEntry`), `ui/routines/CompactLiftInputTest.kt` (deleted: it asserted the salvage) |
| Acceptance | UX06-AC01 **executed** (`aDecimalRepCountIsRefusedNotReadAsEightyFive`, `aNegativeWeightIsRefusedNotReadAsPositive`, `addSetRefusesWhatItCannotStoreAsWritten`, `aDecimalRepCountIsAComplaintNotEightyFiveAndNotLeaveAlone`); UX06-AC02 **executed** (`ambiguousTextNeverBecomesADifferentAcceptedValue`, `commaDecimalWorksOnEveryTypedPath`); UX06-AC03 **executed** (`storedWeightsSurviveADisplayRoundTripWithinDisplayPrecision`: every 0.5 kg to 300 kg, shown in kg and lbs and typed back, lands within ±0.05 kg / ±0.25 lb — the precision `toDisplayValue` actually applies — and re-shows as the same text); UX06-AC04 domain rules executed for every caller, the Compose wiring itself is **device check pending** (no Compose test runs here). |
| Limitations | Paste, hardware keyboard, IME composition and TalkBack's reading of `isError` + supporting text are unverified. `WeightConverter.toKg` still rounds kilograms to a tenth at the storage boundary (pre-existing, inside display precision). `MAX_REPS` is not enforced on routine target reps (pre-existing; a 850-rep target is still storable). |

### 3.2 UX05 — summary truthfulness

| Field | Record |
|---|---|
| Premise at 156cc40 | **Holds.** `WorkoutSummaryViewModel` set `missing = true` for a null row *and* for any thrown read or build; the screen rendered "Workout saved / It is in your history" for `missing || !hasWork`. The hero was total kilograms for every session ("0 kg" for push-ups while the rows beneath said "20 reps"). No receipt of the accepted finish reaches the route; only the id does. |
| Decision | Evidence-named states: `missing` (read found no row), `failed` (read or computation threw), `savedConfirmed` (the finished row came back in this load). "Saved" is said only on that evidence. Headline is the session's measure (`WorkoutSummary.headline`: `Volume` / `BodyweightReps` / `WorkingSets`); a mixed day keeps its reps as a tile. Passing a receipt token through navigation was rejected because the row is stronger evidence and survives process death. |
| Files | `domain/WorkoutSummary.kt` (`bodyweightReps`, `headline`, `SummaryHeadline`, `SummaryCopy`), `ui/summary/WorkoutSummaryViewModel.kt` (rewritten load with `retry()`), `ui/summary/WorkoutSummaryScreen.kt` (`failed` before `missing`, `SummaryUnavailable`, type-aware hero, `SummaryTags.RETRY`); tests `domain/WorkoutSummaryBuilderTest.kt` (+3), `ui/summary/WorkoutSummaryViewModelTest.kt` (+4 and strengthened assertions; a delegating `WorkoutDao` fails `getSession` or `finishedWorkingSetsForExercises` on demand) |
| Acceptance | UX05-AC01 **written, not executed** (blank id, missing row, read fault before the row, computation fault after the row, warm-up-only); UX05-AC02 **written, not executed** (`aSummaryThatWillNotComputeIsSavedButUnavailableNotMissing` asserts one row, the same `finishedAt`, no live session after Retry); UX05-AC03 **executed** (`aBodyweightOnlySessionHeadlinesItsRepsNotZeroKilograms`, `aMixedSessionLeadsWithKilogramsAndKeepsItsReps`, `aLoadedSessionWithNoTonnageFallsBackToItsSetCount`) and written for the ViewModel; UX05-AC04 **not executed** — navigation is unchanged (`popUpTo(Home)` on finish; Back is Done) and needs an emulator. |
| Limitations | A blank session id (a routing fault, not a discard) renders the "not found" copy. A row that exists but is not finished can reach the receipt only by deep link or restore; it renders without the word "saved". Neither has been seen on a device. |

### 3.3 UX04 — routine Save

| Field | Record |
|---|---|
| Premise at 156cc40 | **Holds**, plus the read-fault bricking path above. Implemented in an isolated worktree by a delegated agent from a written brief, then reviewed here and extended (read-fault hardening, the UX06 rejected-box path). |
| Decision | Typed outcomes per write (`RoutineWriteOutcome`: Stored / NothingToWrite / Rejected / Failed) and one pure rule, `RoutineEditorPolicy.exitOutcome`, decide whether the exit was honest. Failed and Rejected both block the exit; on Save the message sits above the dock and Save retries; on Back a prompt offers *Try again* / *Leave without saving these* and states that write-through edits are already saved. `leaveAnyway()` is Back minus the flush and still discards an empty stub created this session. `leaving` is reset whenever an attempt stays. A read fault during the attempt is reported as an unsaved outcome instead of stranding the screen. Copy in `RoutineSaveCopy`. |
| Files | `domain/RoutineSaveOutcome.kt` (new), `domain/RoutineSaveCopy.kt` (new), `domain/RoutineEditorPolicy.kt` (`exitOutcome`), `ui/routines/RoutineEditorViewModel.kt`, `ui/routines/RoutineEditorScreen.kt` (dock prelude with caption and failure line, `RoutineEditorTags.SAVE_ERROR`, Back prompt), `docs/architecture/ADR-021-home-start-and-day-add.md` (one paragraph, see §5); tests `domain/RoutineSaveOutcomeTest.kt` (new, 10, executed), `ui/routines/RoutineEditorViewModelTest.kt` (+9, Robolectric: details-write failure, target-write failure, rejected target on Save and on Back, Back prompt and leave-anyway, empty-stub discard on leave-anyway, saving visible and second press ignored, unreadable box refused, read fault on exit) |
| Acceptance | UX04-AC01 and UX04-AC02 **written, not executed**; UX04-AC03 **written, not executed** (existing `leavePersistsRenamedNotesOnAnExistingRoutine`, `leaveDiscardsAnEmptyStubCreatedThisSession` plus `leaveAnywayStillDiscardsAnEmptyStubCreatedThisSession`); UX04-AC04 name/notes already ride `SavedStateHandle` (`processDeathKeepsNameAndDoesNotMintASecondRoutine`), **device check pending** for a recreation mid-failure. |
| Limitations | The dock caption, failure line and prompt are unrendered. `RoutineRepository.updateDetails` still silently no-ops when the row is gone (pre-existing; the exit then reads `NothingToWrite`). |

### 3.4 UX07 / UX23 — drafts, save-in-progress, shared states

| Field | Record |
|---|---|
| Premise at 156cc40 | Composer Cancel/Back during an unresolved save: **holds**. Composer draft through process death: **already covered by R09** (existing capability). Onboarding step/answers memory-only: **holds**. UX23 wording and state conflations: **hold**; 37 items found, six fixed here. |
| Files | `ui/activity/ActivityComposerScreen.kt` (leave refused while saving via `viewModel.canLeave()`, Cancel disabled, banner above Save, picker error in-sheet), `ui/activity/ActivityComposerViewModel.kt` (`discardDraft` refused while saving, `canLeave()`), `ui/onboarding/OnboardingViewModel.kt` (`SavedStateHandle` for step, answers via `OnboardingAnswers.encodeDraft`, pending unit; restore counts as answered so the stored-answers seed cannot clobber it), `domain/HistoryCopy.kt` + `ui/history/HistoryScreen.kt` (`STALE_LIST`), `domain/DataHealth.kt` (`FINISH_NOT_FOUND`, `FINISH_FAILED`), `ui/workout/ActiveWorkoutViewModel.kt`, `ui/navigation/LiveSessionBarViewModel.kt`; tests `ui/activity/ActivityComposerViewModelTest.kt` (+1: a gated `insertSession` holds the save open; `discardDraft()` is refused, exactly one row lands, the draft clears only then), `ui/onboarding/OnboardingViewModelTest.kt` (+1: recreation restores step and answers and writes no plan, no completion flag, no unit), constructor updates in `OnboardingFocusTest.kt` |
| Acceptance | UX07-AC01 existing (R09) — Robolectric `processRecreationRestoresTheTypedDraft` written, not executed; UX07-AC02, UX07-AC03, UX07-AC04 **written, not executed**; UX23-AC01 partial (summary, composer, finish outcomes); UX23-AC02 implemented for the composer, **device check pending**; UX23-AC03 implemented for summary, finish and History, remaining conflations in the residue list. |
| Limitations | No Compose test pins the banner's co-location with Save. The composer's `error` is shared by the dock banner and the picker sheet, so a Create-row failure shows in both (cosmetic). No background continuation of a save was built; a durable receipt would be needed first. |

## 4. Gate results

| Run | Command | Result |
|---|---|---|
| Baseline | `PT_JARS=build/test-jars sh tools/preflight.sh` | `preflight: OK`; JVM lane 170 classes, 1175 tests, 0 failures |
| After UX05 + UX06 | static gate, JVM lane, `syntax-check.sh app/src/test/java` | static OK at every ratchet; JVM lane 171 classes, 1191 tests, 0 failures; `NO SYNTAX ERRORS` |
| After UX07/UX23 | same | static OK (`required_args_mixed` 180 = baseline, `when_exhaustive` 47 = baseline, `state_members` 2 = baseline); JVM lane 171 classes, 1191 tests, 0 failures; test tree parses |
| After UX04 merge and routine-card wiring | same | static OK (`required_args_mixed` 178 — the ratchet was lowered from 180 because two card calls became fully named; `when_exhaustive` 47; `state_members` 2); JVM lane **172 classes, 1201 tests, 0 failures**; `NO SYNTAX ERRORS` over `app/src/test/java` |

Two ratchets were held rather than raised during the batch: the two new `when` blocks over `StrengthEntry`/`CardioEntry` were typed by giving their variants distinct names (`ReadySet`/`RefusedSet`, `ReadyCardio`/`RefusedCardio`), and every new call with named arguments is fully named. One lane failure during the batch was real and fixed: the round-trip test's tolerance assumed a tenth of a pound where `toDisplayValue` rounds pounds to a half.

Android-side sources (Compose screens, ViewModels, Robolectric tests) were re-read for compile errors against the declarations they use — `PinnedDock`'s `prelude` slot, `OutlinedTextField`'s `supportingText` type via the `NumberEntryDialog` pattern, `ExerciseEntity.loadType` being a `String`, `TrainingAge` members — since nothing here can compile them. That is a review, not a build.

## 5. Recommendations revised, rejected or flagged

- **UX07 scope narrowed.** The composer's process-death draft was already R09; only the save-in-progress and onboarding halves were built.
- **UX06 method changed.** The package offered "preserve raw text" or "reject the whole edit". Rejecting at the keystroke was dropped (sequential typing defeats it); raw text plus commit-boundary refusal was built, matching the app's own `NumberEntryDialog` model.
- **UX05 receipt token rejected.** The read-back row is the receipt.
- **UX04 Rejected-blocks-Back chosen.** The package left rejected values undefined on Back; leaving would have kept a card showing a value Room does not hold. Recorded as D05.
- **ADR-021 addendum flagged (D16).** The delegated UX04 agent appended a paragraph to ADR-021 item 7 describing Save's new truthfulness. It is consistent with the decision but edits a signed record; the owner should accept it or have it moved to the register.
- **Nothing in the package's preserved-decision list was touched:** Home's filled action, five tabs, Library pushed, the live bar, logging/rest docks, once-vs-recurring, units, IDs, backup format, signing.

## 6. Delivery state

Everything is **local and committed** on `claude/file-visibility-check-jraqc2` in the session container. Nothing has been pushed, merged, tagged, published or deployed; no production configuration, credential or user data was touched. The branch is five commits on `156cc40`:

| Commit | Subject |
|---|---|
| `55301ae` | Summary says saved only when the finished row is in hand (UX05) |
| `c856c01` | Typed numbers are kept as typed; drafts survive a Cancel mid-save (UX06, UX07, UX23) |
| `5375672` | Routine editor: Save leaves only when its writes landed (UX04) — implemented in an isolated worktree by a delegated agent from a written brief, reviewed and cherry-picked here |
| `e5d2956` | Routine card refuses an unreadable box; a read fault on exit no longer strands the editor (UX06, UX04) |
| (this record) | This file and `docs/ux-program/` |

The owner's separate authorization is needed to push. On a machine with the Android SDK the full gate is `./gradlew testDebugUnitTest assembleDebug lintDebug`, which executes the Robolectric tests written here; `connectedDebugAndroidTest` on the `temper-tests-api29` profile renders the new states.

## 7. Next actions, in order

1. **Owner:** push authorization for this branch; a decision on D16.
2. **SDK machine or CI with a runner:** `./gradlew testDebugUnitTest` — the 17 Robolectric tests written here are the regression proof for UX04, UX05 and UX07 and have never run.
3. **Device (Temper Debug):** the eight production captures this batch owes — routine editor Save failure and Back prompt at 360 dp / font 2.0 with the keyboard open; summary "not found", "saved, summary unavailable", "unavailable" and a push-up-only receipt; composer Add set refusal; live cardio Finish refusal; setup reopened after `adb shell am kill`.
4. **Batch B (main gym journey):** UX01–UX03, UX08, UX12, UX22, UX24/UX25 per the master sequencing. UX12 and UX24 cannot start without device bounds.
5. **UX23 residue:** the four read-fault-vs-missing screens (SessionDetail, ActiveWorkout, RestTimer, ExerciseDetail) need health-carrying flows, the R10 shape; the rest is copy and banner placement.
