# Handoff — 7 September 2026, UX P0 batch (Fable)

The verification record for the first batch of the UX handoff package prepared on 6 September 2026 (30 work packages, 97 acceptance checks; master document `Temper-UX-Design-Handoff.md`). It follows [HANDOFF-2026-09-06.md](HANDOFF-2026-09-06.md), which landed the engineering findings R01–R19 that several UX packages depend on. This record does not change any signed decision in [architecture/](architecture/README.md); one documentation addendum to ADR-021 is flagged for the owner in §5; the pre-merge review and its fixes are §6, and the compile break it did not catch is §6.1.

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

**Typing a number.** Boxes keep exactly what is typed. Add set, Add cardio, Finish (live cardio), a routine card's box losing focus, and the rest dialog's Set now either store exactly that number or refuse the box with the rule it broke, under the box, with focus moved there — "Enter a whole number of reps, 1 to 100." for a logged set, instead of silently saving 85 for "8.5". Nothing rewrites the text on the way. A routine or week target reads reps with no upper bound, because storage never had one and a stored 3×120 must stay editable.

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
| Limitations | Paste, hardware keyboard, IME composition and TalkBack's reading of the error semantics are unverified. `WeightConverter.toKg` still rounds kilograms to a tenth at the storage boundary (pre-existing, inside display precision). Routine target reps and composer reps are whole numbers of at least 1 with **no upper cap**, as storage and the pre-batch code allowed; the 100-rep guard stays where it was, on the live-workout typing dialog. A first cut of this batch had routed both through the capped reader, which would have made an existing 3×120 card uneditable; the final review caught it (§8). Focus moves to the refused box only in the composer; live cardio and the routine card show the complaint under the box without moving focus. |

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
| After the pre-merge review fixes (`8967888`) | same | static OK at every ratchet (`required_args_mixed` 178); JVM lane **172 classes, 1202 tests, 0 failures**; `NO SYNTAX ERRORS` |
| The compile lane, first run | `tools/compile-check.sh` on `0cc5013` | base stage OK (293 files); **compose stage FAILED — 1 error**, `CustomWeekScreen.kt:201:46: argument type mismatch: actual type is 'Function5<…>', but 'Function6<…>' was expected` |
| The new arity checker against `0cc5013` | `check-lambda-arity.py` over all five source roots of a tree archived at `0cc5013` | **2 findings**: `CustomWeekScreen.kt:201` and `IdentityBeforeMetricsInstrumentedTest.kt:124`, both "lambda takes 5, declaration takes 6" |
| After the custom-week fix (`b6250f4`) | static gate incl. the new checker, JVM lane, `syntax-check.sh app/src/test/java`, `tools/compile-check.sh --self-test` | static OK at every ratchet; **0 arity mismatches across 662 files**; `test_lambda_arity` 15/15; JVM lane **172 classes, 1202 tests, 0 failures**; lane **base OK (293 files), compose OK (378 files)** with its self-test confirming the compiler and the Compose plugin are live (62 errors on mutated copies, 168 on a broken design token) |
| After the two dead-end fixes (`1801821`) | same, with the lane's unit-test stage | static OK; JVM lane **172 classes, 1202 tests, 0 failures**; lane **base OK (293), compose OK (378), test OK (255 of 261)** — the six held out are timer tests where Robolectric types `getSystemService` as nullable and `compileSdk` 36 does not. **All six new Robolectric custom-week tests compile.** They have still never run |
| After the four checker widenings (`d7552de`) | static gate | **662 files** in scope, up from 624; `check-named-args` 0 across 662, `check-required-args` 0 across 662 with `required_args_mixed` still 178, `check-missing-imports` 0, `check-internal-imports` 0, `check-lambda-arity` 0. Four negative controls in an instrumented test file each produced exactly one finding |
| The finished lane (`776f511`) | `tools/compile-check.sh --self-test` on the branch head, then the same lane against a worktree at `0cc5013` | Head: **base OK (293), compose OK (378), test OK (255 of 261)**, negative control 11 desktop-only APIs unreachable, every self-test non-vacuous. `0cc5013`: exit 1, one error, `CustomWeekScreen.kt:201:46` and nothing else. ~3 min warm, ~4 with `--self-test` |

Two ratchets were held rather than raised during the batch: the two new `when` blocks over `StrengthEntry`/`CardioEntry` were typed by giving their variants distinct names (`ReadySet`/`RefusedSet`, `ReadyCardio`/`RefusedCardio`), and every new call with named arguments is fully named. One lane failure during the batch was real and fixed: the round-trip test's tolerance assumed a tenth of a pound where `toDisplayValue` rounds pounds to a half.

Android-side sources (Compose screens, ViewModels, Robolectric tests) were re-read for compile errors against the declarations they use — `PinnedDock`'s `prelude` slot, `OutlinedTextField`'s `supportingText` type via the `NumberEntryDialog` pattern, `ExerciseEntity.loadType` being a `String`, `TrainingAge` members — since nothing here can compile them. That is a review, not a build.

## 5. Recommendations revised, rejected or flagged

- **UX07 scope narrowed.** The composer's process-death draft was already R09; only the save-in-progress and onboarding halves were built.
- **UX06 method changed.** The package offered "preserve raw text" or "reject the whole edit". Rejecting at the keystroke was dropped (sequential typing defeats it); raw text plus commit-boundary refusal was built, matching the app's own `NumberEntryDialog` model.
- **UX05 receipt token rejected.** The read-back row is the receipt.
- **UX04 Rejected-blocks-Back chosen.** The package left rejected values undefined on Back; leaving would have kept a card showing a value Room does not hold. Recorded as D05.
- **ADR-021 addendum flagged (D16).** The delegated UX04 agent appended a paragraph to ADR-021 item 7 describing Save's new truthfulness. It is consistent with the decision but edits a signed record; the owner should accept it or have it moved to the register.
- **Nothing in the package's preserved-decision list was touched:** Home's filled action, five tabs, Library pushed, the live bar, logging/rest docks, once-vs-recurring, units, IDs, backup format, signing.

## 6. Final review before merge

After the batch was pushed, three independent reviewers were run over the whole diff: a compile-level read of the Android-side main sources (which also compiled the pure-Kotlin domain files with the real Kotlin 2.0.21 compiler: 0 errors), a compile-and-runtime read of the Robolectric tests, and an adversarial behavioural critique against `156cc40`. Findings and dispositions:

| Finding | Severity | Disposition |
|---|---|---|
| Routine card and composer reps inherited the 100-rep cap from `typedReps`; a stored 3×120 card became uneditable and the record said the opposite | regression | Fixed: both read reps as whole numbers ≥ 1 with no cap (`REPS_WHOLE_RULE`); `TargetEntryTest.aHighRepTargetIsNotRefused`; record corrected |
| `OnboardingAnswers.encodeDraft` dropped `availableEquipment`, so a setup restored after process death applied with derived kit instead of the owner's explicit Settings kit | should-fix | Fixed: ninth codec field, eight-field drafts still decode; `OnboardingAnswersRestoreTest` extended |
| New error texts (dock, composer boxes, live cardio, routine card) were plain `Text` with no live region, and `isError` alone announces "Invalid input" rather than the rule | should-fix | Fixed: `FieldComplaint` (polite live region) and `Modifier.fieldError(message)` (error semantics) in `ui/components/Common.kt`, used at every new complaint |
| Back from a routine editor that never hydrated produced "Some changes are not saved" | should-fix | Fixed: `leave()` with nothing hydrated and nothing staged is `leaveAnyway()` |
| A rejected target's complaint stayed in the error slot after the box was fixed back to the stored value | note | Fixed: `NothingToWrite` clears a target-rule complaint |
| Any undismissed composer error showed inside the picker sheet | note | Fixed: create-lift errors are a separate `createError` shown only in the sheet |
| Bodyweight summary test logged 0 kg working sets on a loaded lift, which `logSet` refuses | test defect | Fixed: the lift is inserted as BODYWEIGHT before the fixture logs |
| Live cardio refusal test could observe `distanceError` before `finishing` cleared | flaky test | Fixed: the wait requires both |
| Back is inert for as long as a composer save runs (no timeout) | note | Accepted; the save is one transaction and a timeout would reintroduce the ambiguity |
| After process death a routine box can show a complaint while nothing is staged, so Save exits | note | Carried as UX07 residue (staged targets are in-memory; the texts are saved state) |
| "Save keeps the name, notes and targets" understates that targets also write on focus loss | note | Copy left; recorded here |

Static gate and JVM lane were re-run after these fixes (§4, final row).

### 6.1 The pushed branch did not compile, and none of the above found it

The three reviewers above, the twenty static checkers and the JVM lane all passed on `0cc5013`, and `0cc5013` **does not build**. `./gradlew assembleDebug` was red on the branch as pushed.

Commit `e5d2956` widened `SessionLiftStrip`'s callback to carry the rule a box broke:

```kotlin
onStageTargets: (String, Int?, Int?, Int?, Double?, String?) -> Unit   // SessionLiftStrip.kt:118
```

`RoutineEditorScreen.kt` was updated with it. Two other call sites were not: `CustomWeekScreen.kt:201` and `IdentityBeforeMetricsInstrumentedTest.kt:124` each still passed a five-parameter lambda. Kotlin rejects that at type-check.

Nothing in the local gate can see this. The static checkers are line-oriented Python; none of them types a lambda. `run-domain-tests.sh` compiles `domain/` only, and `SessionLiftStrip` is Compose. `syntax-check.sh` parses, it does not resolve. The three reviewers read the diff and read it well — the two missed sites are in files the diff never touches, so nothing drew their eye to them. A review that reads changed files cannot find a caller that broke because it did not change.

**What actually found it.** After the owner asked whether an environment could be built to test in, one was: `tools/compile-check.sh` runs the real Kotlin 2.0.21 compiler over the Android-side sources, substituting real library declarations wherever Maven Central has them — Robolectric's `android-all-instrumented` for `android.*`, JetBrains Compose Multiplatform 1.8.2 for `androidx.compose.*`, the same Compose compiler plugin the app uses — and hand-written declaration-only stubs only where nothing is downloadable (Room, DataStore, WorkManager, lifecycle, GMS, androidx.test). Google's Maven is unreachable from here and every mirror tried was refused; the proxy was not bypassed. The lane's base stage (293 non-Compose files) was clean. Its compose stage failed on the arity, on a pristine checkout with nothing injected.

That result was reproduced deliberately, not just remembered: a worktree checked out at `0cc5013`, with the finished lane copied in, prints

```
app/src/main/java/com/sinura/personaltrainer/ui/routines/CustomWeekScreen.kt:201:46: error:
argument type mismatch: actual type is 'kotlin.Function5<…>',
but 'kotlin.Function6<…>' was expected.
compile-check: compose FAILED — 1 compile errors (378 files in scope)
```

and exits 1, while the branch head exits 0. The lane's own `--self-test` confirms the compiler and the Compose plugin are live rather than silently absent: 62 errors on two deliberately mutated copies, 168 on a deliberately broken design token.

**The fix** is not a lambda pad. `CustomWeekPolicy.updateTargets` reads a null argument as "keep what is stored", so passing nulls for an unreadable box would have left the week holding one number while the card showed another — the exact UX06 mismatch this batch exists to end, in the one screen the batch had not reached. `CustomWeekViewModel` now takes the reason, holds it per lift id, and `confirm()` refuses with that rule instead of applying the plan underneath it. Six tests cover it, and reading the fix back found two further dead ends the same shape, both fixed here: removing a card left its complaint behind, blocking Confirm on a rule with no box left to fix; and because the box text is saved state while the staged rejection was not, a card rebuilt after process death showed "8.5" and its rule while nothing upstream knew, so Confirm would have written the stored number underneath it. That second one closes the UX07 residue item §6 carried as a note.

**What this changes about the record.** §7 previously described a pushed branch as sound; it was not. The overclaim was mine and it is the class the owner's brief named — a check that was never executed reported as if it had been. The corrected state is in §7. The lane and its caveats ledger are `tools/compile-check.sh --explain`; it is committed here so the next batch has it, and it is **not** wired into `tools/preflight.sh`, because its first run downloads about 186 MB and the preflight is expected to be offline-quick. Run it before any push that changes a shared signature.

**The offline guard.** The lane is the strong check but it is not free, so the defect class also got a cheap one: `tools/check-lambda-arity.py` counts the parameters of a lambda passed as a named argument and compares them with the declared function type. It is in `preflight.sh` and needs no network. Against a tree archived at `0cc5013` it reports **both** stale call sites, with exact lines; over the branch head it reports 0 across 662 files. `tools/test_lambda_arity.py` holds fifteen fixtures — the defect, its fix, and every shape that must stay quiet — so the guard itself is tested rather than assumed.

**The source sets no call-resolving checker read.** Chasing the second stale caller turned up the more general fault — though the first version of this paragraph overstated it, and an audit on 10 September caught that. Precisely, at `156cc40`: `app/src/sharedTest/java` was read by nothing at all; `app/src/debug/java` was already read by `check-design-tokens` and `check-state-members`; and `app/src/androidTest/java` was read by `check-doc-authority`, but only to scan Kotlin comments for stale Room-schema claims. **No checker that resolves a call site, an import or an argument read any of the three.** That is the fault that mattered, and it is why nothing could see a five-parameter lambda handed to a six-parameter callback in an instrumented test. Two checkers had a quieter version of the same fault — they took a single root, and a root scanned alone is a false clean, because nothing outside it is in the declaration index. `check-named-args.py` was being run on `app/src/test` alone, where nearly every call targets main and was therefore skipped rather than checked; `check-internal-imports.py` reports 146 correct imports as unresolved when pointed at `androidTest` alone, which is why it had never been pointed there. Four checkers now read all five source sets in one invocation: `check-named-args`, `check-required-args`, `check-missing-imports` and `check-internal-imports`. The gate went from 624 files to 662.

Each widening was negative-controlled rather than assumed. In an instrumented test file: an invented named argument, a dropped required argument, an unimported project symbol and an import of a name that does not exist each produce exactly one finding, and none of them did before.

**What a green from the lane does not mean.** It is not `assembleDebug` — and as of 10 September that is measurable rather than rhetorical: see §6.2, where CI ran the real thing. The lane's verdict and Gradle's agreed on this branch, which is evidence for the lane but not proof of it. It substitutes a nearby Compose build (1.8.2 for the app's 1.11.4/Material3 1.4.0), so a member added or removed between those versions is invisible to it: where 1.8 accepts something 1.11 removed, the lane is green and the merge gate is red. It runs no KSP, so Room's `@Query` SQL and the generated DAOs are unchecked. Robolectric's framework jar is an AOSP build and types `getSystemService` as nullable where `compileSdk` 36 does not, which is why six timer tests compile in a separate pass — they are not dropped, and the lane fails if that exclusion ever goes stale. `app/src/androidTest` (23 files) is compiled by no stage: the second stale caller lived there, `assembleDebug` does not build it either, and it is `check-lambda-arity.py` that guards it rather than a compiler. The desktop-only strip's keep-list is the residual risk — six facades kept because Android Compose has the same API, any of which could still carry a desktop-only overload. Stubs are hand-written to be no more permissive than the real API, which is a promise rather than a proof. `tools/compile-check.sh --explain` prints the full ledger.

What it does catch is the whole ViewModel-to-screen and ViewModel-to-test boundary: a state type or a callback arity that changed on one side and not the other. That is the class of defect that reached the branch, and it is the class nothing else here could see.

## 6.2 The merge, and the first real build — 10 September

Two claims in the sections above were wrong, and both mattered.

**"CI has had no runner since 5 September" was false.** Trunk's CI runs on 9 and 10 September
are green three-to-eight-minute builds. The runner works. This branch's ten failures on
7 September were the earlier outage, which has since been fixed, and `ci.yml` runs on
`claude/**` branches *deliberately* — its own comment says why: work is pushed from
environments with no Android SDK, so the branch build is the only thing that ever compiles it.
The gate this record kept describing as unobtainable was one push away the whole time.

**The branch no longer merged.** It was cut at `156cc40`; trunk had moved 31 commits. Twelve
files conflicted, and not cosmetically: `ErrorSlot` (#190) had landed across eleven ViewModels
including all five this batch rewrote, the picker had become write-as-you-go under a Mutex with
an atomic compare-and-remove on the very `stagedTargets` map UX04 rebuilt (#201), and 32
catches had been guarded against swallowing a cancellation (#197). Everything §4 records was
verified against a base that no longer existed in trunk.

The merge is `76ef74f`, resolved by one rule — take trunk's machinery, re-express this batch's
behaviour on it. `ErrorSlot` is a properly built version of what this branch hand-rolled:
`RoutineSaveCopy.TARGET_RULES` compared message text to decide whether a complaint was its own
to clear, where `ErrorSlot` answers with a source family and a monotonic mark and fixes a race
the string comparison cannot see. `TARGET_RULES` is deleted; nothing referenced it.

Three defects surfaced that no check on the old base could have found: 56 unbounded test waits
(trunk added `check-unbounded-waits.py` after two such waits wedged CI for a full thirty-minute
job — every wait in this batch's four ViewModel tests was the bare form it forbids), the new
custom-week tests calling `togglePendingAdd`/`confirmPendingAdd` which #201 deleted, and a
`StartActivityForResult` stub missing from `compile-check.sh` because #188 began using it.

### What CI actually said

Run **1124** on `76ef74f`, the blocking job, every step green:

| Step | Result |
|---|---|
| `PT_STATIC_ONLY=1 sh tools/preflight.sh` | pass |
| `./gradlew testDebugUnitTest` | pass — **the first execution of this batch's Robolectric tests** |
| `./gradlew lintDebug` (warningsAsErrors) | pass |
| `./gradlew assembleDebug` | pass |
| `./gradlew assembleDebugAndroidTest` | pass — the androidTest source set the compile lane cannot reach |

The non-blocking emulator job was red, and the comparison is the point. Trunk at `d77ca8c` —
the exact commit merged — fails **four** instrumented tests. Attempt 1 on this branch failed
**five**; attempt 2 failed **four**, the same four. The fifth,
`ActiveWorkoutJourneyInstrumentedTest.leaveResume_finishFromBar_rotateSummary_andRepairUndo`,
passed on the re-run. It asserts on `SessionDetailScreen.kt`, which this branch never touched
and which uses none of its additions, so there was no causal path to find; one re-run was spent
to establish that rather than assume it. **No instrumented failure on this branch is this
branch's.** One of the four, `ExactAlarmCapabilityInstrumentedTest`, has an open fix on trunk
already ([#207](https://github.com/sinura7/PersonalTrainer/pull/207)).

### What the audit found afterwards

Ten adversarial lenses were run over the branch, each finding attacked by three independent
skeptics before it survived. The one blocking defect was real and is fixed in `fb7615f`: a
folded-away lift card discards its box text (`SessionLiftEditor` is composed only
`if (selected)`) but the ViewModel kept the staged rejection, so Save refused and named a rule
for a box that had gone back to its stored value — the third dead end of the shape already
fixed for a removed card and a restored one. Two lenses found it independently.

Four holes in the checkers themselves are fixed in `f528299`, the worst being that
`preflight.sh` judged three checkers with `grep -F "0 mismatch(es)"`, which
`"10 mismatch(es)"` satisfies: the shared gate had been reporting clean at 10, 20, 30 …
findings. `tools/test_summary_gate.sh` now proves both directions before preflight trusts it.

## 6.3 The premise changed: Google's Maven is reachable and the real build runs here

Set out to audit `tools/compile-check.sh`. Found something that matters more than any of its
findings: **`dl.google.com` now answers.**

    $ curl -o /dev/null -w "%{http_code}" \
        https://dl.google.com/dl/android/maven2/androidx/activity/activity/1.12.4/activity-1.12.4.pom
    200

Earlier in this same session it refused with a 403 at the CONNECT, and every mirror with it —
that refusal is the entire reason the compile lane exists. It is no longer true. Whether the
egress policy changed or the container came back with a different one, the observation is
what it is, and it should be re-checked at the start of any session that plans to rely on it.

What followed, in order, all of it executed:

| Step | Result |
|---|---|
| `./gradlew projects` | **BUILD SUCCESSFUL** — AGP 8.9.2 resolved from Google's Maven and the project configured |
| `./gradlew assembleDebug` (no SDK yet) | failed with **"SDK location not found"** — a missing toolchain, not an unresolvable dependency |
| Android command-line tools, then `sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"` | installed to `/opt/android-sdk`, licences accepted |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL in 5m 24s** — `PersonalTrainer-1.0.0-debug.apk`, 17,905,677 bytes |
| `./gradlew testDebugUnitTest` | **1,946 tests, 1 failure** |

The one failure, `RoutineEditorViewModelTest.leaveDiscardsAnEmptyStubCreatedThisSession`, is
`awaitExit gave up` — a wait that timed out under the full parallel suite. It passes on its
own (`--tests '*leaveDiscardsAnEmptyStubCreatedThisSession'`, BUILD SUCCESSFUL in 26s) and it
passes in CI on this commit, so it is the load flake this repo already raised `TestWaits`'
ceiling for, not a defect. Note the JDK differs from CI's: Temurin 17 there, OpenJDK 21 here.

### What this changes

Every "written, not executed" in §4 and in the acceptance matrix was a statement about this
environment, and this environment can now execute them. The merge gate is runnable locally,
which is the thing this record has said was out of reach since the first version.

The compile lane's job changes with it. It was built as a *substitute* for a build that could
not run; it is now, at best, **fast local feedback** — about four minutes against Gradle's
five and a half cold, and less than that warm. Its ~3,000 lines of shell and hand-written
stubs carry documented soundness caveats that the real toolchain does not have. It should not
be trusted over `assembleDebug` on any question where the two disagree.

One caveat against retiring it outright: the SDK at `/opt/android-sdk` was installed by hand
in this session and a fresh container will not have it. Until that bootstrap is scripted, the
lane is still the only thing that works on a cold start.

### The audit itself

Eight lenses, three skeptics per material finding. **One confirmed false green out of ten
material findings; nine refuted.** For 1,520 lines of hand-written stubs standing in for
Room, DataStore, WorkManager, lifecycle, Play Services, navigation and the Android-only parts
of Compose, that is a good result.

The survivor: `tools/compose-stubs/activity.kt:17` declares
`open class ComponentActivity : android.app.Activity()` with no members, so an Activity
override binds to AOSP's Java signatures, which carry no nullability annotation and therefore
present as Kotlin platform types. The real `androidx.activity.ComponentActivity` is Kotlin and
re-declares those callbacks non-null. So `override fun onNewIntent(intent: Intent?)` would
type-check here and fail under Gradle with "overrides nothing". Latent, not live —
`MainActivity.kt:76` and `RestLockActivity.kt:106` both write the non-null form today — and
the stub's own header claims the opposite is checked. Thirteen further findings are minor.

## 6.4 The compile lane is retired — 10 September

The owner took this branch's checker work onto trunk directly as **#227** ("The gate was blind
in three source sets and green at ten findings"), and that commit says plainly what it did not
take: *"The source branch's `tools/compile-check.sh` and its hand-written stubs are deliberately
not carried: their premise was that Gradle could not run in this environment, and it can."*

That is right, and this merge honours it. `tools/compile-check.sh`, `tools/compile-stubs/`,
`tools/compose-stubs/` and `tools/test-stubs/` — about 1,500 lines of substitute library
declarations and a 1,495-line driver — are **deleted here**. The lane existed to answer one
question, "does the Android side type-check", using nearby real builds because the real one was
impossible. `./gradlew assembleDebug` answers that question exactly, with no substitutions and
no soundness ledger to read before trusting a green. Keeping both would mean maintaining stubs
against a compiler nobody consults.

What survives it is the part that was never about substitution: `tools/check-lambda-arity.py`,
the four widened checkers, and `tools/test_summary_gate.sh` — all now on trunk via #227. They
stay in the preflight because they cost a second and need no SDK, so a stale caller is named
before a build is started. They do not stand in for the build.

The audit's one confirmed false green (§6.3, the empty `ComponentActivity` stub) dies with the
lane rather than being fixed in it. The other thirteen minor findings were all findings *about*
the lane; they are closed by the same deletion. The audit of the batch's own behaviour, and its
findings, are unaffected — those were about `app/src`, not about `tools/`.

**What replaces it as the pre-push check** is the owner's standing instruction of 10 September:
`./gradlew testDebugUnitTest assembleDebug lintDebug`, run in full before every push.

**Executed on the merged tree**, before this commit was pushed and before trunk was touched:

```
preflight (static only)                     OK — 31 steps, every ratchet at or below baseline
./gradlew testDebugUnitTest assembleDebug lintDebug   BUILD SUCCESSFUL in 4m 44s
  testDebugUnitTest   1,965 tests, 0 failures, 0 errors, 0 skipped
  assembleDebug       PersonalTrainer-1.0.0-debug.apk, 17,905,677 bytes
  lintDebug           0 issues
```

The one unit-test failure seen on 10 September did not recur; it passed in isolation and in CI
then, and the full run is clean now. `required_args_mixed` ratcheted 181 -> 178 (three mixed
call sites became fully named); `lambda_arity_declined` is 62 here against trunk's 61, the one
extra being a fully-qualified call this batch adds. Both count sites a checker declines to
judge, not defects; the defect counts are 0.

## 6.5 The audit at head: seven fixes, one of them data loss — 10 September

The batch was merged to trunk green — CI run **1253**, both jobs including the emulator — and
then audited. Seven adversarial lenses read the code **at head**, not at the commit the earlier
audit had read, and every finding was attacked by three independent skeptics before it counted.
Fifteen findings were raised, **four were refuted and discarded**, eleven survived. The
important number is not eleven; it is one.

### The one that mattered

**A weight box that could not be read cleared the stored target, and Save reported success.**
Type `-50` over a 100 kg target, fold the card shut, press Save: the 100 kg is gone.

Weight is the one target column where an empty value is an instruction — it means *no target*.
Sets, reps and rest all read empty as *leave this alone*, so `RoutineEditorPolicy.targetsToPersist`
falls back to storage for them and takes the weight at face value. `TargetEntry.typedWeightKg`
returns null for an unreadable box exactly as it does for a deliberately emptied one, and the
only thing separating them was the staged `invalidReason`. While that rule stands the commit
refuses the card outright, so the two never meet. `forgetTargetRule` — the fix in `fb7615f` for
a folded card stranding its complaint — stripped the rule and kept the null. From that instant
nothing remembered it had never been an answer.

**Four of the seven lenses found it independently, and none of the nine skeptics could refute
any of them.** One reproduced it against the real in-memory database.

Fixed twice over, because it loses stored data: `TargetEntry.weightToStage(stored)` stages the
stored value for an unreadable box, and `forgetTargetRule` puts the weight back to what the
routine holds — the seam where "unreadable" silently becomes "deliberate", and also what the
owner sees if they reopen the card, since the boxes re-read the routine.

**Why 1,965 tests did not see it.** `foldingACardAwayKeepsTheValuesItStagedAndDropsOnlyTheRule`
walked this exact path — the fixture stores 100 kg — and asserted only sets and reps. The
assertion that would have caught it was simply absent. Tests find what someone thought to ask.

### The rest

| Severity | What | Where |
|---|---|---|
| Blocking | Unreadable weight box clears the stored target | `RoutineEditorViewModel`, `TargetEntry` |
| Major | A lift's refusal printed under the ROUTINE's name, undismissably | `RoutineEditorScreen` |
| Major | `writePick` marked after the mutex, so a queued tap's success erased the previous tap's failure | `RoutineEditorViewModel` |
| Major | `applySuggestedWeight` did not mirror the draft, so the tap was lost to process death | `ActiveWorkoutViewModel` |
| Major | A notes write carried five other columns, un-finishing a session the summary had called complete | `WorkoutRepository`, `WorkoutDao` |
| Minor | The dock's refusal outlived the box it was about, with no dismiss | `RoutineEditorViewModel` |
| Minor | A rotation on the summary started a second concurrent Drive upload | `WorkoutSummaryViewModel` |
| Minor | "Nothing was changed" after a read fault when writes had landed | **already fixed on trunk by #229** |

Every fix carries a test that fails without it, and the negative control was actually run.
**Two of the four major tests did not fail on the first attempt** and were rewritten until they
did — the `writePick` one needed a gated DAO, because under `UnconfinedTestDispatcher` two
sequential taps never share the queue the defect lives in, and the notes one needed a
repository-level assertion, because the DAO tests alone would pass again if the read-modify-write
were ever restored.

**One fix has no test and it is named rather than papered over:** the auto-backup in-flight
guard. `BackupRepository` is a final class with no interface, so nothing can stand in for a
Drive upload, and the guard's in-flight window cannot be opened without adding a seam to
production code that a two-line fix does not otherwise need.

### What this says about the gate

The gate was green — static, 1,965 unit tests, `assembleDebug`, `lintDebug`, and a full CI run
including the emulator — over a defect that silently deletes a stored number. The gate proves
the code runs. It does not prove the code is right. That is the second time in this program the
thing which found the real defect was not the gate; the first was a compile lane finding a
branch that did not build (§6.1). **The audit belongs before a merge, not after it.**

### One test failure, recorded rather than dismissed

`aSecondTapTakesTheLiftBackOut` failed once in a full-suite run, then passed in isolation, in
its class, in the three-class subset with `--rerun-tasks`, and in four consecutive full-suite
runs. It is not skipped, weakened or quarantined. Trunk's **#230** identifies this as the known
intermittent 30-second wedge in `RoutineEditorViewModelTest` — "several reproductions and taught
us nothing each time" — and adds `stalledThreads()` so the next occurrence names the thread that
was parked. It is an open issue with diagnosis now in place, not a one-off and not mine.

## 7. Delivery state

Everything is committed on `claude/file-visibility-check-jraqc2` and **pushed to origin with the owner's authorization on 7 September**. Nothing has been merged, tagged, published or deployed; no production configuration, credential or user data was touched.

**The branch was not mergeable as first pushed, twice over.** `0cc5013` did not compile (§6.1), and the branch had also fallen 31 commits behind trunk and stopped merging (§6.2). Both are fixed, and both fixes are verified by a compiler and by CI rather than by reading. The branch is **eighteen commits of its own** on `156cc40`, plus trunk's 31 brought in by the merge — 49 in all:

| Commit | Subject |
|---|---|
| `55301ae` | Summary says saved only when the finished row is in hand (UX05) |
| `c856c01` | Typed numbers are kept as typed; drafts survive a Cancel mid-save (UX06, UX07, UX23) |
| `5375672` | Routine editor: Save leaves only when its writes landed (UX04) — implemented in an isolated worktree by a delegated agent from a written brief, reviewed and cherry-picked here |
| `e5d2956` | Routine card refuses an unreadable box; a read fault on exit no longer strands the editor (UX06, UX04) |
| `1947caf` | This record and `docs/ux-program/`, first version |
| `8967888` | Pre-merge review fixes: no rep cap on targets, kit survives the setup draft, complaints are announced |
| `0cc5013` | This record and the register updated with §6 — **this commit does not compile**, see §6.1 |
| `b6250f4` | The custom week refuses a box it cannot read, and a checker that counts lambda parameters |
| `a71225e` | Fixing one custom-week card moves the complaint; it does not clear it |
| `1801821` | Two dead ends around a refused target box: a removed card, and a restored one |
| `41ad418` | Three source sets nothing was reading |
| `9bb971d` | `check-named-args` took one root, and one root is a false clean |
| `d7552de` | `check-internal-imports` reads every source set now, not just main |
| `776f511` | A real compiler for the Android side |
| `0b68842` | This record corrected with §6.1 |
| `76ef74f` | **Merge trunk**: the batch's semantics, on trunk's machinery (§6.2) |
| `fb7615f` | A folded-away card takes its complaint with it — the audit's one blocking finding |
| `f528299` | Four holes an audit found in the checkers themselves |
| (this commit) | This record corrected again: §6.2, the counts below, and the CI claim |

**CI has run this branch, and the blocking job is green.** The claim that no runner had been available since 5 September — repeated in earlier versions of this section — was false by 9 September; §6.2 has the detail and run **1124** has the result: static gate, `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleDebugAndroidTest` all pass on `76ef74f`. The Robolectric tests written for this batch have now executed. The non-blocking emulator job is red with the same four failures trunk has at the same commit, none of them this branch's.

Still owed by a device, not by CI: the eight production captures in §8, and `connectedDebugAndroidTest` on the `temper-tests-api29` profile rendering the new states.

## 8. Next actions, in order

**Batch A is merged and on trunk.** Trunk carries it at `3b3aa33`, and the audit's eleven
surviving findings are worked: seven fixed here, one already fixed on trunk by #229, and three
that were about the retired compile lane and closed with it (§6.4, §6.5). CI is green on the
merge and on the fixes.

1. **Device (Temper Debug):** the eight production captures this batch owes — routine editor
   Save failure and Back prompt at 360 dp / font 2.0 with the keyboard open; summary "not
   found", "saved, summary unavailable", "unavailable" and a push-up-only receipt; composer
   Add set refusal; live cardio Finish refusal; setup reopened after `adb shell am kill`.
   **The only thing CI cannot stand in for**, and the only item on this list that needs the
   owner's hands.
2. **Batch B (main gym journey):** UX01–UX03, UX08, UX12, UX22, UX24/UX25 per the master
   sequencing. UX12 and UX24 cannot start without device bounds from item 1.
3. **UX23 residue:** the four read-fault-vs-missing screens (SessionDetail, ActiveWorkout,
   RestTimer, ExerciseDetail) need health-carrying flows, the R10 shape; the rest is copy and
   banner placement.
4. **Audit before merge, not after** (§6.5). A green gate did not see a defect that deletes a
   stored number; seven independent lenses with adversarial refutation found it four times
   over. Run the audit on a batch before it goes to trunk.
5. **A seam for `BackupRepository`,** if the auto-backup path is worked again. It is a final
   class, which is why the one fix in §6.5 ships without a test.
6. **The gate before every push:** `./gradlew testDebugUnitTest assembleDebug lintDebug`.
   The compile lane is retired (§6.4); the real build replaced it, and the owner's standing
   instruction is that the real gate runs before a push, not a static approximation of it.

**Owner decision still open: D16** — the UX04 work appended a paragraph to ADR-021 item 7
describing what Save now guarantees. It does not reverse the decision, but it edits a signed
record. Stay in ADR-021, or move to the UX decision register?
