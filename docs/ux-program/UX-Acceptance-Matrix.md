# Temper UX acceptance matrix — updated 7 September 2026

Baseline for this update: `156cc400a0bc7974209e494e4e4cf0525b29bb7d` on `claude/file-visibility-check-jraqc2` (trunk `28f485f` carries R01–R19). Candidate commit: `f528299` on `claude/file-visibility-check-jraqc2`, merged with trunk `d77ca8c` at `76ef74f`. Earlier versions of this line named `8967888`, a commit the same branch documents as not compiling; do not read acceptance against it.

**Status vocabulary.** *Executed (JVM lane)* — a JUnit test ran on this host and passed. *Written, not executed* — a Robolectric or instrumented test exists on the branch but no Android SDK, Gradle, emulator or device was available here. **As of 10 September CI runs `testDebugUnitTest` on this branch and it passes, so a row still marked this way is stale rather than blocked — see handoff §6.2.** *Implemented, device check pending* — behaviour changed; only a device can close it. *Not executed* — untouched. Nothing below is marked passed from source reading alone.

### UX01 — Clarify Home's planned versus freestyle actions

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX01-AC01 | P1 | F02, F11 | Given both a planned workout and freestyle control, a participant can correctly explain which follows the routine and which starts empty. | Not executed |
| UX01-AC02 | P1 | F02, F11 | Given another selected day, starting/logging behavior matches explicit copy and existing date rules; no silent backdating. | Not executed |
| UX01-AC03 | P1 | F02, F11 | At 360 dp and large text, the planned row's start action is discoverable without an unrelated statistics detour; measure rather than assert this has improved. | Not executed |

### UX02 — Separate starting now from recording completed activity

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX02-AC01 | P1 | F01, F06 | With 0, 3 and 50 routines, participants can find past strength, manual cardio, mixed entry and live cardio. | Not executed |
| UX02-AC02 | P1 | F01, F06 | Selecting a manual entry does not start a timer; dismissing the sheet makes no training-data write. | Not executed |
| UX02-AC03 | P1 | F01, F06 | Opening the sheet while a session runs produces one clear answer and no duplicate session. | Not executed |

### UX03 — Refine the existing logging dock and action transitions

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX03-AC01 | P1 | F03, F08, F11 | Rapid repeated activation while a write is pending commits one set. | Not executed |
| UX03-AC02 | P1 | F03, F08, F11 | Warmup, bodyweight, added-weight and assisted movements show the correct labels and units; no universal '0 kg' assumption. | Not executed |
| UX03-AC03 | P1 | F03, F08, F11 | Keyboard open at 360 dp and landscape leaves the active field and commit action reachable. | Not executed |
| UX03-AC04 | P1 | F03, F08, F11 | Undo/edit/next transitions preserve exercise identity and announce accepted results once. | Not executed |

### UX04 — Make routine Save truthful and clarify autosave

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX04-AC01 | P0 | F04, F08 | Inject details-write failure after editing a name: Save remains on screen, preserves text and offers retry. | Written, not executed (Robolectric: RoutineEditorViewModelTest) |
| UX04-AC02 | P0 | F04, F08 | Inject one staged-target failure: no blanket success/exit; retry persists the intended value once. | Written, not executed (Robolectric: RoutineEditorViewModelTest) |
| UX04-AC03 | P0 | F04, F08 | Back after successful edits remains consistent with existing autosave; empty new stubs retain their documented cleanup. | Written, not executed (Robolectric: RoutineEditorViewModelTest, existing + new) |
| UX04-AC04 | P0 | F04, F08 | Process recreation during failure retains sufficient draft state; reopening shows the accepted persisted result. | Implemented (SavedStateHandle keeps name/notes); Robolectric processDeathKeepsNameAndDoesNotMintASecondRoutine written, not executed; device check pending |

### UX05 — Separate saved receipts from missing or unavailable summaries

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX05-AC01 | P0 | F03, F08 | Missing ID, deleted/restored-over row, computation failure after confirmed save and valid no-work data each render distinct truthful states. | Written, not executed (Robolectric: WorkoutSummaryViewModelTest — missing id, missing row, read fault, summary fault, no-work); device render pending |
| UX05-AC02 | P0 | F03, F08 | Retrying summary never creates or re-finishes a workout. | Written, not executed (Robolectric: aSummaryThatWillNotComputeIsSavedButUnavailableNotMissing asserts one row, same finish stamp, no live session) |
| UX05-AC03 | P0 | F03, F08 | A bodyweight-only workout does not appear worthless because its load-volume is zero. | Executed (JVM lane: WorkoutSummaryBuilderTest.aBodyweightOnlySessionHeadlinesItsRepsNotZeroKilograms); Robolectric written; device render pending |
| UX05-AC04 | P0 | F03, F08 | Back and Done exit once and do not return to an already-finished live session. | Not executed — navigation unchanged (popUpTo Home on finish; Back is Done); emulator check pending |

### UX06 — Stop numeric input from silently changing meaning

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX06-AC01 | P0 | F10 | 8.5 reps is rejected rather than saved as 85; -50 kg is rejected rather than saved as 50. | Executed (JVM lane: NumericEntryTest, ComposerCopyTest, TargetEntryTest); production-screen paste/keyboard pass pending |
| UX06-AC02 | P0 | F10 | 1.2.3 and 8e2 do not become different accepted values; 62,5 and 62.5 behave equivalently under the supported decimal policy. | Executed (JVM lane: NumericEntryTest.ambiguousTextNeverBecomesADifferentAcceptedValue, blank/62,5 == 62.5) |
| UX06-AC03 | P0 | F10 | Existing valid values survive unit conversion/round trips without drift beyond specified display precision. | Executed (JVM lane: storedWeightsSurviveADisplayRoundTripWithinDisplayPrecision — ±0.05 kg / ±0.25 lb) |
| UX06-AC04 | P0 | F10 | All shared filter callers are covered, including composer, live cardio and routine targets. | Executed for the domain rules; caller wiring changed in composer, live cardio, custom rest, routine card and the custom week — every caller now type-checks against the widened signature (`./gradlew assembleDebug`, BUILD SUCCESSFUL) — Compose-level render check pending (no SDK here) |
| UX06-AC05 | P0 | F10 | The custom week's target boxes refuse what the week cannot hold, instead of applying the stored number underneath the shown one. | Written, not executed (Robolectric `CustomWeekViewModelTest`, 4 tests); the caller compiles (compile lane) and the arity guard is executed (`check-lambda-arity.py`, `test_lambda_arity.py` 15/15) |

### UX07 — Define draft preservation and leave-during-save behavior

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX07-AC01 | P0 | F04, F08 | Build a multi-row mixed draft, background, recreate the process and restore: date, rows, order and values survive. | Already covered at HEAD by R09 (SavedStateComposerDraft); Robolectric processRecreationRestoresTheTypedDraft written, not executed |
| UX07-AC02 | P0 | F04, F08 | Cancel/Back during delayed accepted save neither creates a duplicate nor tells the user committed work was discarded. | Written, not executed (Robolectric: cancelWhileSavingIsRefusedAndTheSaveStillLandsOnce) |
| UX07-AC03 | P0 | F04, F08 | Failed save preserves the draft; successful save clears only that draft. | Written, not executed (Robolectric: anAcceptedSaveClearsTheDraft, save() keeps draft on Rejected/thrown) |
| UX07-AC04 | P0 | F04, F08 | Onboarding draft restoration does not overwrite the saved current plan or create a new one without acceptance. | Written, not executed (Robolectric: processRecreationRestoresTheStepAndAnswersWithoutWritingAPlan) |

### UX08 — Make every onboarding step fit and remain editable

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX08-AC01 | P1 | F07, F11 | Every question and Continue/Skip action is reachable at 360×640 dp, font 2.0, and the smaller supported window configuration. | Not executed |
| UX08-AC02 | P1 | F07, F11 | Typed bodyweight, wheel, unit switch and Skip produce consistent optional values. | Not executed |
| UX08-AC03 | P1 | F07, F11 | Screen-reader focus lands on the new question and Back keeps the selected answer. | Not executed |
| UX08-AC04 | P1 | F07, F11 | Accepting a plan once creates the intended program; reopening setup does not silently apply a draft. | Not executed |

### UX09 — Carry an exercise through Create routine

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX09-AC01 | P1 | F01, F06 | From an empty routine catalogue, choose a lift → Create routine → save: exactly one routine contains that lift, without another search. | Not executed |
| UX09-AC02 | P1 | F01, F06 | Cancel returns to the original context and leaves no empty stub. | Not executed |
| UX09-AC03 | P1 | F01, F06 | Rotation/process recreation retains the pending exercise ID according to the draft contract. | Not executed |

### UX10 — Reduce accidental custom-exercise creation and keep picker errors visible

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX10-AC01 | P1 | F06, F08 | Searching 'bench' with existing matching variants prioritizes choosing them over creating 'bench'. | Not executed |
| UX10-AC02 | P1 | F06, F08 | Duplicate/disk/create errors are visible within the active sheet in single and multi modes. | Not executed |
| UX10-AC03 | P1 | F06, F08 | Create then cancel activity follows the disclosed catalogue persistence rule. | Not executed |
| UX10-AC04 | P1 | F06, F08 | Double confirmation adds the selected IDs once and preserves order. | Not executed |

### UX11 — Make edit scope and secondary actions discoverable

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX11-AC01 | P1 | F02, F06 | A participant can state the scope before saving a swap or schedule edit. | Not executed |
| UX11-AC02 | P1 | F02, F06 | Routine management is reachable without relying solely on long press, or documented usability evidence supports retaining the existing affordance. | Not executed |
| UX11-AC03 | P1 | F02, F06 | Deleting a routine preserves history and accurately explains its impact on scheduled days. | Not executed |

### UX12 — Make calendar dates and statuses distinguishable at small widths

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX12-AC01 | P1 | F02, F09, F11 | At 320/360/412 dp, date targets are measurable and usable without overlapping ambiguous activation regions, or an equivalent accessible selector is supplied. | Not executed |
| UX12-AC02 | P1 | F02, F09, F11 | A week containing done, skipped, missed, future and rest days has unambiguous spoken and visible detail. | Not executed |
| UX12-AC03 | P1 | F02, F09, F11 | Selecting a neighboring-month date opens the correct civil date; today remains distinguishable from selection. | Not executed |

### UX13 — Expose recurrence and ordering consequences without restoring clock clutter

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX13-AC01 | P1 | F02, F09 | Add the same type once and recurring in separate scenarios; next week contains only the recurring case. | Not executed |
| UX13-AC02 | P1 | F02, F09 | Reordering states exactly which future/reminder behavior changes and preserves completed history. | Not executed |
| UX13-AC03 | P1 | F02, F09 | Home and Plan show the same dated occurrences after changes. | Not executed |

### UX14 — Preview bulk missed-work changes before applying

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX14-AC01 | P1 | F02, F09, F08 | Preview and committed outcome match for missed work crossing week boundaries. | Not executed |
| UX14-AC02 | P1 | F02, F09, F08 | Completed sessions remain unchanged and are explicitly separated from future schedule adjustments. | Not executed |
| UX14-AC03 | P1 | F02, F09, F08 | Commit failure preserves the proposal and offers retry; repeated taps do not apply it twice. | Not executed |

### UX15 — Make History's scope and deeper sections reachable

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX15-AC01 | P1 | F05 | With years of synthetic history, a participant can reach lifetime records and the newest activity without an unbounded scroll. | Not executed |
| UX15-AC02 | P1 | F05 | Changing a horizon produces exactly the documented scope across totals/list/records. | Not executed |
| UX15-AC03 | P1 | F05 | Editing a prior best refreshes visible results and record drill-down identifies the supporting session. | Not executed |

### UX16 — Make chart axes, comparisons and sparse data honest

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX16-AC01 | P1 | F05, F10 | Jan 1, Jan 2 and Apr 1 samples do not imply equal elapsed intervals without an explicit session-axis label. | Not executed |
| UX16-AC02 | P1 | F05, F10 | Screen-reader/data-list users can recover values and dates without relying on the plotted line. | Not executed |
| UX16-AC03 | P1 | F05, F10 | Bodyweight-rep series use reps, loaded series use the selected physical unit, and empty/flat series are truthful. | Not executed |

### UX17 — Explain the body map as recorded muscle workload

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX17-AC01 | P1 | F02, F11 | Participants can explain that an uncolored muscle means no mapped work in the selected period, not a recovery guarantee. | Not executed |
| UX17-AC02 | P1 | F02, F11 | All muscles remain reachable via full-size text rows with equivalent detail/actions. | Not executed |
| UX17-AC03 | P1 | F02, F11 | The selected window and contributor data agree; caption remains readable at font 2.0. | Not executed |

### UX18 — Support mixed unit preferences deliberately

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX18-AC01 | P2 | F10, F12 | All four kg/lb × km/mi combinations render consistent input, summary, history and export behavior. | Not executed |
| UX18-AC02 | P2 | F10, F12 | Changing preference does not change stored distance or weight. | Not executed |
| UX18-AC03 | P2 | F10, F12 | Old backups lacking distance preference restore to the documented default and drafts do not reinterpret numbers. | Not executed |

### UX19 — Make bodyweight history correctable and clearing explicit

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX19-AC01 | P2 | F05, F10 | Correcting an older synthetic measurement changes that date and refreshes the affected review. | Not executed |
| UX19-AC02 | P2 | F05, F10 | Clear/delete copy identifies the exact affected data; cancelling leaves it unchanged. | Not executed |
| UX19-AC03 | P2 | F05, F10 | No bodyweight entry is required to record a workout or complete setup. | Not executed |

### UX20 — Reduce Settings scanning without burying recovery

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX20-AC01 | P2 | F07, F11 | A participant can change units/rest preferences and find export/restore with no worse success than the baseline. | Not executed |
| UX20-AC02 | P2 | F07, F11 | Reopening Settings restores useful context without hiding an unresolved save error. | Not executed |
| UX20-AC03 | P2 | F07, F11 | Production and debug build screenshots are evaluated separately. | Not executed |

### UX21 — Specify backup/recovery as a truthful multi-stage task

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX21-AC01 | P1 | F12, F08 | Wrong password, oversized input, unsupported version, offline Drive, live workout, partial preferences failure and completed restore each have a distinct actionable screen. | Not executed |
| UX21-AC02 | P1 | F12, F08 | Displayed success follows the actual accepted operation; no UI-only fix masks R01–R04. | Not executed |
| UX21-AC03 | P1 | F12, F08 | Back/rotation/permission return retains the operation's correct stage and never repeats a destructive commit. | Not executed |

### UX22 — Preserve context through navigation, notifications and external screens

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX22-AC01 | P1 | F04, F09 | Repeated delivery/recreation of the same reminder intent does not stack duplicate live screens or start twice. | Not executed |
| UX22-AC02 | P1 | F04, F09 | Returning from system settings keeps the active session/draft and reflects the real permission result. | Not executed |
| UX22-AC03 | P1 | F04, F09 | Back from a completed summary cannot reopen the finished workout as live. | Not executed |

### UX23 — Create a shared language for loading, failure, saving and retry

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX23-AC01 | P0 | F08, F11 | Injected initial/later load failures, invalid fields and post-commit side-effect failures produce the defined states. | Partially: summary read/compute faults, composer save faults, finish outcomes distinguished; injected load faults on other screens remain open (see UX23 residue) |
| UX23-AC02 | P0 | F08, F11 | Users can locate the error and retry at the point of action without losing authored data. | Implemented for the composer (error above Save, field errors inline); device/IME check pending |
| UX23-AC03 | P0 | F08, F11 | Missing, empty and unavailable never share a misleading generic success or start-new-workout remedy. | Implemented for summary, finish and History stale wording; remaining conflations listed in UX23 residue |

### UX24 — Apply responsive type and layout rules to actual content

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX24-AC01 | P1 | F06, F11 | Two long similarly named variants can be distinguished before selection. | Not executed |
| UX24-AC02 | P1 | F06, F11 | At font 2.0, primary actions and full values/units are readable and not clipped. | Not executed |
| UX24-AC03 | P1 | F06, F11 | Shared component changes preserve all callers and do not reduce touch targets. | Not executed |

### UX25 — Validate complete-screen accessibility, not just component tags

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX25-AC01 | P1 | F11 | Physical TalkBack can complete planned start, set log/edit, cardio finish, routine create, history correction and export review. | Not executed |
| UX25-AC02 | P1 | F11 | Search and routine-name fields announce their purpose when empty and populated. | Not executed |
| UX25-AC03 | P1 | F11 | Large text/IME does not hide essential actions; measured issues have production-screen regression coverage. | Not executed |

### UX26 — Tune feedback timing without adding decorative motion

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX26-AC01 | P2 | F03, F08, F11 | With reduced motion enabled, all data and records appear immediately and remain accessible. | Not executed |
| UX26-AC02 | P2 | F03, F08, F11 | An extended accessibility timeout is honored for relevant messages or equivalent persistent access exists. | Not executed |
| UX26-AC03 | P2 | F03, F08, F11 | Save failure remains actionable after any transient banner disappears. | Not executed |

### UX27 — Make exercise discovery precise and stateful

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX27-AC01 | P2 | F06, F08 | A participant finds a specified muscle+equipment variant and clears a restrictive filter without losing orientation. | Not executed |
| UX27-AC02 | P2 | F06, F08 | Returning from detail restores the original results position. | Not executed |
| UX27-AC03 | P2 | F06, F08 | Load failure does not invite creating a duplicate exercise because the catalogue appeared empty. | Not executed |

### UX28 — Improve setup preview and returning-user orientation

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX28-AC01 | P2 | F07 | A user can inspect the proposed week, change equipment/days and return to an updated preview with other answers preserved. | Not executed |
| UX28-AC02 | P2 | F07 | An existing user can explain the acceptance impact before tapping Use this plan. | Not executed |
| UX28-AC03 | P2 | F07 | Dismissed/failed setup does not create a partial unexpected program. | Not executed |

### UX29 — Make activity-type differences explicit in details and receipts

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX29-AC01 | P1 | F05, F08, F10 | Each supported type has an accurate receipt and correct units with clear action capabilities. | Not executed |
| UX29-AC02 | P1 | F05, F08, F10 | Opening history detail does not replay a misleading new-save confirmation. | Not executed |
| UX29-AC03 | P1 | F05, F08, F10 | Repeat/edit where supported preserves correct IDs, chronology and plan linkage and cannot silently convert one type to another. | Not executed |

### UX30 — Establish an evidence-led design delivery and regression process

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX30-AC01 | P1 | F01, F02, F03, F05, F11, F12 | Each implemented UX item has acceptance evidence, source/commit links and limitations. | Not executed |
| UX30-AC02 | P1 | F01, F02, F03, F05, F11, F12 | Global-navigation changes follow the repository's current comparative task gate; no extra tab appears from taste alone. | Not executed |
| UX30-AC03 | P1 | F01, F02, F03, F05, F11, F12 | Required device/accessibility gaps remain visibly open until actually tested. | Not executed |
