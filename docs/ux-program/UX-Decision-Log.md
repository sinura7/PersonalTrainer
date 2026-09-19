# Temper UX decision register — updated 7 September 2026

Supplements the register in the 6 September handoff package. Entries D01–D14 keep their IDs; this file records what this session did with each and adds D15–D19. Nothing here is an owner approval unless it says so; "implemented" means code on branch `claude/file-visibility-check-jraqc2`.

| ID | Topic | What this session did | Status |
|---|---|---|---|
| D01 | Home primary action | Untouched. ADR-021 freestyle filled action and planned-row confirmation preserved. | Preserved baseline |
| D02 | Global navigation | Untouched. Five tabs, Library pushed. | Preserved baseline |
| D03 | Settings structure | Untouched. No prototype built. | Pending evidence |
| D04 | Calendar small-window access | Untouched. No device to measure hit regions. | Device evidence pending |
| D05 | Routine persistence | **Implemented (UX04).** Write-through kept; Save is now a gate on the two writes it owns (name/notes, staged targets). Each write reports Stored / NothingToWrite / Rejected / Failed; `RoutineEditorPolicy.exitOutcome` decides. Failed keeps the editor open with the message beside the dock and Save as the retry. Back with an unsaved write asks *Try again* / *Leave without saving these* and states that write-through edits are already saved. A read fault on the way out is reported the same way instead of leaving the editor deaf. **Design choice recorded:** a *rejected* value (0 sets, "8.5" reps) blocks Back as well as Save, because a card showing a value Room does not hold is the same quiet loss. No conversion to transactional discard-all. | Correctness repair implemented; device render pending |
| D06 | Chart x axis | Untouched. | Design decision pending |
| D07 | Independent distance unit | Untouched. Distance still follows the weight unit (`DistanceUnit.fromWeight`). | Additional product scope |
| D08 | Bodyweight correction | Untouched. | Additional product scope |
| D09 | Recurrence/order | Untouched. | Preserved model |
| D10 | Catalogue create semantics | Untouched in substance. The composer's picker now shows a Create-row failure inside the sheet (UX10 modal-error half); immediate catalogue creation is unchanged and still undisclosed. | Persistence decision still to specify |
| D11 | Visual identity | Untouched. New states reuse existing tokens (`InstrumentType.caption`, `Danger`, `TextTertiary`, `PinnedDock` prelude, `EmptyState`). | Preserved baseline |
| D12 | Save-in-progress exit | **Implemented (UX07).** Composer: while `confirmActivity` is unresolved, Cancel is disabled, Back is swallowed, and `discardDraft()` is refused in the ViewModel; the guard is read from the ViewModel's own flag so a Back in the same frame as the Save tap cannot pop the entry and cancel the write. The draft is spent only by an accepted write. No background continuation was added: a save is a single Room transaction that completes in milliseconds, so a durable completion receipt was not built. Routine editor: the same shape via `saving`, a disabled *Saving…* dock and ignored second presses. | Interaction contract implemented |
| D13 | Summary headline | **Implemented (UX05).** Missing, failed-before-row, failed-after-row and no-work are four states; "Workout saved" is said only when the finished row was read back in the same load. Headline is the measure the session was made of: kilograms when any were moved, the bodyweight-rep total for a bodyweight day, the working-set count otherwise; a mixed day keeps its reps as a tile. This does not rank sessions; it names their measure. Copy in `SummaryCopy`. | Correctness first done; headline wording to test on device |
| D14 | Exercise management discoverability | Untouched. | Comparative refinement pending |
| D15 | Typed-number contract (new) | **Implemented (UX06).** Boxes hold exactly what was typed; no live filter rewrites text. The commit boundary (Add set, Add cardio, Finish, focus-leave on a routine card, the rest dialog's Set) parses with one contract (`NumericEntry.typed*`) and either stores exactly that number or refuses the box with the rule it broke, under the box, focus moved there. Rules: one decimal separator, point or comma, at most two fraction digits (existing); a whole number for reps/sets/rest/minutes; reps at least 1 with no upper cap on routine targets or backdated sets (the 100-rep mis-tap guard stays on the live-workout typing dialog only, where it already was); weight ≥ 0. Deliberate salvages, both documented in code: a blank composer weight is 0 kg (bodyweight, as the field default and hints already say); a typed 0 distance is "no distance". Storage still rounds kilograms to a tenth at `WeightConverter.toKg` (pre-existing; within the stated display precision). Non-ASCII digits and grouping are refused, not interpreted. | Implemented; production-screen paste/IME pass pending. A first cut capped target and composer reps at 100; the pre-merge review caught the regression and it was removed before merge (handoff record §6). |
| D16 | ADR-021 wording | The UX04 implementation appended a paragraph to ADR-021 §7 describing Save's truthfulness (leaves only when its writes landed; Back asks). It does not reverse the decision — it says what "Save keeps the program" means when a write does not land, which §7 as accepted left open. **Resolved 10 September 2026: it stays in ADR-021, marked.** The repo's own convention is that a signed ADR is amended in place with the amendment dated and attributed — ADR-002 carries an `Amended:` header and an inline marker at §6, ADR-023 amends ADR-005 §5, ADR-024 amends ADR-002 §6. What `docs/architecture/README.md` forbids is *silent* contradiction, and unmarked was exactly what this paragraph was. Moving it to this register was rejected: a reader consulting ADR-021 to learn the rule would not find it there, so the ADR would be the incomplete record rather than the authoritative one. A new ADR was also rejected — ADR-023 and ADR-024 exist because they CHANGED prior decisions; this clarifies the same one, and the inline dated amendment is the weight the convention gives that. | Resolved; ADR-021 carries an `Amended:` header and an inline marker at §7 |
| D17 | UX23 residue (new) | An adversarial vocabulary audit over every screen and copy object found 37 items; six are addressed in this batch (summary exception→missing, summary saved-without-evidence, History "pull may be behind", composer error placement, and finish not-found vs failed on both the workout screen and the live bar). The other 31 are listed in `UX-Backlog.json` under UX23 `residue` with file, line, class and proposed text. The largest cluster is read-fault-vs-missing on SessionDetail, ActiveWorkout, RestTimer and ExerciseDetail, which need health-carrying flows (the R10 pattern) rather than copy. | Carried as backlog |
| D18 | Evidence classes (new) | Every acceptance check is marked by what ran here: *Executed (JVM lane)*, *Written, not executed* (Robolectric exists, no SDK), *Implemented, device check pending*, or *Not executed*. No check is marked passed from source reading. | Process rule for this record |
| D19 | An Android compiler for this environment | Nothing here could type-check the Android side, so a signature widened in `e5d2956` left two stale callers and the branch was pushed unbuildable (handoff §6.1). A substitute lane, `tools/compile-check.sh`, was built: the real Kotlin 2.0.21 compiler over `app/src/main/java` against Robolectric's framework jar and JetBrains Compose Multiplatform, with declaration-only stubs where nothing was fetchable. It found the defect on a pristine checkout. **Superseded on 10 September**: `dl.google.com` began answering, `./gradlew assembleDebug` built a real APK in 5m24s, and trunk's #227 declined to carry the lane because its premise — that Gradle could not run here — was false. The lane and its 34 stub files are deleted (handoff §6.4); the real gate `./gradlew testDebugUnitTest assembleDebug lintDebug` replaces it and runs before every push. What survives is the part that was never a substitution: `check-lambda-arity.py`, the offline guard for the same defect class, and four existing checkers widened to read `androidTest`, `debug` and `sharedTest` — source sets no tool had opened, which is where the second stale caller sat. Every widening was negative-controlled. | Superseded; the real build is the check |

## Decision records for the P0 batch

Template fields from the 6 September register, filled once per package.

### UX04 — routine Save
- Exact current commit: see `docs/archive/handoffs/HANDOFF-2026-09-07-UX.md` §6.
- Observed user problem: Save animates and pops; a rename or typed targets never reached Room; no signal, no retry.
- Current code: `RoutineEditorViewModel.leave/saveAndLeave` set `_exitRequested` unconditionally; `persistDetailsOnExit` swallowed the exception; `writeTargets` returned false into a discarded result. Confirmed at 156cc40 by an independent re-read that also found the read-fault bricking path.
- ADR affected: ADR-021 item 7 (documentation addendum, D16).
- Alternatives: keep autosave-and-pop (rejected: zero signal, zero retry); transactional discard-all editing (rejected: separate design, would change the write-through model the owner chose); gate the exit on typed outcomes (chosen).
- Test tasks: Robolectric `RoutineEditorViewModelTest` (7 new), JVM `RoutineSaveOutcomeTest` (10, executed here).
- Outcome and limitations: JVM rule tests pass; ViewModel tests are written and syntax-checked, not executed (no SDK).
- Data/compatibility: no schema or backup change; IDs untouched.
- Owner decision: D16 only.
- Remaining: device render of the dock caption, the failure line and the Back prompt at 360 dp / font 2.0 with the keyboard open.

### UX05 — summary
- Observed problem: a missing row was told "saved, in your history"; a computation fault was told the same; push-up-only sessions read "0 kg".
- Alternatives: pass a receipt token through navigation (rejected: the row itself is stronger evidence and survives process death); trust the route (rejected: the premise); read the row and say only what it establishes (chosen).
- Tests: `WorkoutSummaryViewModelTest` +4 (Robolectric, not executed), `WorkoutSummaryBuilderTest` +3 (executed).
- Remaining: device render of the four states; TalkBack reading of the new hero label.

### UX06 — numeric entry
- Observed problem: filters rewrote text before parsing (-50→50, 8.5→85, 1.2.3→1.23, 8e2→82; rest dialog 1.5→15).
- Alternatives: a stricter keystroke filter (rejected: "8", ".", "5" typed in sequence still becomes 85); keep raw text and validate at commit with field errors (chosen; matches the existing `NumberEntryDialog` model).
- Tests: `NumericEntryTest` +6, `ComposerCopyTest` +2, `TargetEntryTest` (5), all executed; `LiveCardioViewModelTest` +1 and `RoutineEditorViewModelTest` +1 written, not executed.
- Remaining: paste, hardware keyboard and IME composition on a device; TalkBack announcement of `isError` + supporting text.

### UX07 / UX23 — drafts and states
- Observed problem: Cancel/Back during a composer save; onboarding step/answers memory-only; summary states conflated; composer error far from Save; History wording opaque; finish outcomes shared one sentence.
- Alternatives for the composer: allow leave and continue the save in the background with a durable receipt (rejected for now: needs a receipt store; the save is milliseconds); refuse leave until resolved (chosen).
- Tests: `ActivityComposerViewModelTest` +1, `OnboardingViewModelTest` +1 (Robolectric, not executed).
- Remaining: the 31 UX23 residue items; a Compose-level check that the composer banner is co-located with Save.
