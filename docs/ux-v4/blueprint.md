# Temper v4 flow blueprint

Date 2026-10-31. Trunk 2610f40c ("R6: Cardio pause, Plan week day rows, TargetsSheet screen test (#566)"). Source: six flow changes approved by Allen, plus the R6 Critic minors. No code was changed by this document.

Conventions (same as docs/ux-v3/blueprint.md): paths are relative to app/src/main/java/com/sinura/personaltrainer/ unless they start with app/ or tools/. NEW marks files, tags, strings or tests that do not exist on trunk. INFERRED marks facts read from code but not run. ASSUMPTION marks guesses. DECISION marks a call Allen must make before the named packet starts. Values like 62.5 kg are example data. All dimensions are dp unless marked sp. No new raw dp literals: every size below maps to an existing Metrics token or a NEW token added to ui/theme/Metrics.kt.

## 1. Scope, non-goals, decisions

Scope: six flow changes (F1 to F6), the R6 Critic minors (folded into V1), the screens and components they touch, two state machines (start, rest), seven packets V1 to V7 (V1 carries the R6 Critic minors), an acceptance list (acceptance.json).

| Id | Change | One line |
|---|---|---|
| F1 | One Start path | One ordered chooser everywhere: today's planned session, then a routine, then an empty workout. Replaces the Today hero vs Start sheet split. |
| F2 | Plan is week-only | Plan tab shows only the week. The routine library moves to a NEW Routines screen opened from the top of Plan. |
| F3 | Inline rest | Rest is a countdown bar on the live workout screen, above the next set. The next set stays visible and editable. The pushed rest route goes away. Overlay, lock widget and notification keep working. |
| F4 | Edit mode on past sessions | Session detail gets an explicit Edit mode: change weight and reps, delete a set. History, PRs and progression recompute. |
| F5 | Up next on Summary | Summary gets an "Up next" line naming the next planned day; tap opens Plan on that day. |
| F6 | History vs Body ownership | History owns finished sessions (list, open, edit). Body owns trends (bodyweight, PRs, weekly volume per muscle) and has no session list. |

Non-goals: Room schema changes (none needed, see 1.2); cloud sync; signer, Obtainium or release flow changes; Gradle or library upgrades; TalkBack beyond what each spec states; supersets; changing set logging rules (SetLogRules stays as is); changing the cardio timer storage.

Invariants (every packet):
- Metrics.commit stays 72.dp (ui/theme/Metrics.kt line 137: "Primary commit control app-wide (recovery Keep, rest card, cardio, dock Next/Finish)"). Metrics.logFloorCommit stays 64.dp. No packet edits either value; DockCommitRenderTest stays green unchanged.
- tools/checker-baselines.toml must not rise. Current values on trunk: required_args_mixed 180, required_args_lambda 46, when_exhaustive 46, lambda_arity_declined 47, real_time_sleeps 27, real_time_delays 6, unbounded_waits 0, cancellation_swallow 0, alpha_copy 7, named_color 2, raw_dp 14, motion 11, stock_icons 9, xml_hex 18, xml_monospace 2, defaults_elevation 1, stock_component 1, all others 0.
- Static gate: `PT_STATIC_ONLY=1 sh tools/preflight.sh` passes before every push. Never pass -PskipStaticChecks to a gate run.
- Two required hosted checks green: "Tests, lint, debug build" and "Emulator walkthrough screens". Instrumented smoke (non-blocking) must add no failure that is absent on the latest trunk run (compare by distinct "Class > method" FAILED lines).
- No production releases, no new signer.

### 1.1 Decisions

| Id | Decision | Recommended default | Blocks |
|---|---|---|---|
| D1 | Does the Start chooser still offer cardio, "log a past session" and extra kit (today on both sheets)? | Yes, as a fourth group "Other" below Empty workout, collapsed into one row "Cardio or log past" that opens the existing pages. The three ordered groups stay first. | V2 |
| D2 | Today's planned session already started (live): what does Start show first? | A single Resume row replaces group 1; groups 2 and 3 are disabled with "Finish or discard the live workout first" (existing START_BLOCKED_MESSAGE copy reworded). | V2 |
| D3 | Rest day today: group 1 content | "Rest day. Nothing planned." non-tappable row, then next planned day as a hint line ("Next: Thu, Push day"). Groups 2 and 3 enabled. | V2 |
| D4 | Routines screen: tab or pushed? | Pushed route "routines-library" from the top of Plan. Plan tab label and Route.Routines path are unchanged so the nav graph, deep links and walkthrough tab taps keep working. | V3 |
| D5 | Rest bar when the user edits the next set during rest | Rest keeps counting. Editing never pauses or skips rest. | V4 |
| D6 | RestTimerScreen (route session/{id}/rest) | Removed from the nav graph. Notification and lock-screen taps open the live workout (route session/{id}), which already shows the bar. Keep RestTimerScreen.kt deleted, not dead. | V4 |
| D7 | Deleting the last set of a lift in Edit mode | Allowed; the lift disappears from the session. Deleting the last set of the whole session is blocked with "Delete the session instead" (DELETE in the overflow already exists). | V5 |
| D8 | Set numbers after a delete | Display numbers are recomputed from order (1..n) at render. Stored setNumber is not rewritten. No Room change. | V5 |
| D9 | Up next when no plan exists | Line reads "Up next: plan your week" and taps through to Plan. Hidden for cardio summaries (ActivitySummary). | V6 |
| D10 | PR and lifetime records list: History or Body? | Body (F6). History keeps the period readout (sessions, days, sets, active time) but its "PRs" metric becomes a link "See records on Body". | V7 |
| D11 | Bodyweight trend source | Existing bodyweight_entries via PreferencesRepository (observeAll). Read-only chart on Body plus the existing check-in. No new table. | V7 |
| D12 | Weekly volume per muscle | Computed from set_logs (working sets only, warm-ups excluded) joined to exercise_muscles, last 8 ISO weeks. No new table or cached column. | V7 |

### 1.2 Room impact (explicit)

Finding: none of F1 to F6 needs a Room schema change, a new entity, a new column or a migration. Evidence on trunk:
- F4 edit and delete already exist in the data layer: WorkoutRepository.updateSet (no finished guard, keeps completedAt and setNumber), WorkoutRepository.deleteSet / restoreSet (undo), addSetToFinishedSession, deleteFinishedSessionAcknowledged. SessionDetailViewModel already exposes updateSet, deleteSet, undoDeleteSet, addSet, deleteSession (ui/history/SessionDetailViewModel.kt lines 580 to 680).
- PRs are derived, not stored: WorkoutRepository computes Set<PersonalRecordKind> from ExerciseRecordPriorsRow priors over set_logs (lines 939, 1489, 1648). Editing a set therefore recomputes PRs on next read. INFERRED: no cache table holds PR flags.
- Progression hints are read from set_logs at load (ui/workout/ProgressionHintLoader.kt). INFERRED: an edited past set changes the next hint on the next session start.
- Bodyweight already has bodyweight_entries (data/local/entity/HistoryEntities.kt).
- Weekly volume per muscle can be a DAO query over set_logs + exercise_muscles (existing tables). A NEW @Query in an existing DAO is not a schema change and needs no migration; it does not change the Room schema hash. INFERRED.
- The cardio pause (R6) uses SharedPreferences "cardio_timer_state", not Room.

Decision for Allen (DECISION R1): confirm "no Room change" for v4. If any packet discovers it needs a column (most likely candidate: a stored "edited at" marker on set_logs to show "Edited" on a past set), it must stop and ask; the default in this blueprint is to show no "Edited" marker at all. See D13.

| Id | Decision | Recommended default | Blocks |
|---|---|---|---|
| D13 | Show an "Edited" marker on corrected past sets? | No. It would need a set_logs column (Room migration). Defer. | V5 |
| D14 | Recompute scope after an edit | Lazy: every screen reads derived values from set_logs on next load. No eager recompute job, no WorkManager. | V5 |

## 2. Tokens

No colour, type, radius, motion or haptic token changes. v4 reuses docs/ux-v3 section 2 as is (Pit, Surface1 to 3, Volt, RestCyan, Warn, TextPrimary, TextSecondary, InstrumentType). NEW size tokens, all in ui/theme/Metrics.kt, each with a KDoc line naming its use:

| Token | Value | Status | Use |
|---|---|---|---|
| Metrics.commit | 72.dp | Existing, unchanged | Start chooser primary row, Summary Done, Edit mode Save |
| Metrics.rowMin | 56.dp | Existing | Plan rest day row min height, Start chooser routine rows, Routines rows |
| Metrics.control | 56.dp | Existing | Rest bar height (inline), Edit mode toolbar |
| Metrics.touchMin | 48.dp | Existing | Every tappable floor, rest bar nudge buttons, per-row Plan add |
| PlanWeekRowMetrics.TRAINING | 72.dp | Existing (R6, ui/plan/PlanWeekRows.kt) | Plan training day row min height. Move to Metrics.planTrainingRow (NEW alias) in V1 so raw_dp does not count it twice. |
| PlanWeekRowMetrics.REST | 56.dp | Existing (R6) | Plan rest day row min height. Move to Metrics.planRestRow (NEW alias, = rowMin) in V1. |
| Metrics.restBarProgress | 4.dp | NEW | Height of the progress track inside the inline rest bar |
| Metrics.restBar | 56.dp | NEW (= control) | Inline rest bar container min height; grows with font scale |

Blueprint 4.2 check (R6 minor): docs/ux-v3/blueprint.md 4.2 specifies day rows "training 72, rest 56" as minimum heights, not fixed heights. PlanWeekDayRow on trunk uses heightIn(min = ...). V1 adds a test that confirms min-height semantics at font scale 1.0 (exact 72 / 56) and 2.0 (taller, nothing clipped).

## 3. Components

Each component: file, signature, layout, states, tags, strings. Strings use ids in res/values/strings.xml (D8 of v3 carries forward).

### 3.1 StartChooser (NEW, ui/start/StartChooser.kt)

Replaces both HomeStartSheet (ui/home/HomeStartSheet.kt, 321 lines) and StartOptionsSheet (ui/workout/StartOptionsSheet.kt, 435 lines). One ModalBottomSheet, one ViewModel (StartOptionsViewModel, kept and extended; it already has startToday, startRoutine, startFree, startCardio, openComposer, discardInProgress and the todayStart field).

```
@Composable
fun StartChooser(
    onDismiss: () -> Unit,
    onWorkoutStarted: (sessionId: String) -> Unit,
    onOpenLiveActivity: (activityId: String) -> Unit,
    onLogPast: () -> Unit,
    onLogCardio: () -> Unit,
    onLogMixed: () -> Unit,
    viewModel: StartOptionsViewModel = viewModel(),
)
```

Layout, top to bottom, inside the sheet (horizontal padding Metrics.gutter, vertical gap Metrics.space2 within a group, Metrics.sectionGap between groups):
1. Sheet title "Start" (InstrumentType.title, heading semantics). Tag START_TITLE.
2. Group 1 "Today" kicker (InstrumentType.kicker, TextSecondary). One row:
   - Planned: PrimaryButton-styled row, height Metrics.commit, Volt fill, text "Start {routineName}" and sub line "{n} lifts, about {m} min" (sub hidden if unknown). Tag START_TODAY.
   - Live (D2): same row, text "Resume {title}", sub "{sets} sets logged". Tag START_RESUME.
   - Rest day (D3): non-tappable row height Metrics.rowMin, "Rest day. Nothing planned." plus hint "Next: {weekday}, {routineName}" when one exists. Tag START_TODAY_REST.
   - No plan: row "No plan this week" with ghost action "Plan week" (opens Plan tab). Tag START_TODAY_NONE.
3. Group 2 "Routine" kicker. Up to 5 routine rows (most recently used first, then alphabetical, same order HomeStart.namedRoutines uses today, INFERRED), each height Metrics.rowMin, name plus "{n} lifts". Tag START_ROUTINE(id). If more than 5: row "All routines" opens the NEW Routines screen in pick mode. Tag START_ALL_ROUTINES.
4. Group 3 "Empty" kicker. One SecondaryButton row "Empty workout", height Metrics.rowMin. Tag START_EMPTY.
5. Group 4 "Other" (D1): one GhostButton row "Cardio or log past" opening the existing cardio / extra kit / past log pages (moved from HomeStartSheet KindPage into StartChooser pages). Tag START_OTHER.
6. Error row (existing error text) with Retry. Tags START_ERROR, START_RETRY.

States:

| State | Spec |
|---|---|
| Loading | isLoading: group 1 shows a 72dp placeholder surface (Surface2) with no text, groups 2 and 3 visible but disabled. Never blank. |
| Empty routines | Group 2 shows "No routines yet" and a ghost "Make one" opening Routines. |
| Error | Existing StartOptionsUiState.error, shown above group 1, Retry calls the same start again. |
| Blocked | A start returns Blocked: toast "Finish or discard the live workout first"; sheet stays. |
| Pending | The tapped row shows a progress indicator and every other row is disabled until navigation fires (existing clearPendingNavigation on dispose stays). |
| Process death | Sheet open flag lives in the host (rememberSaveable). Page (main / other) is rememberSaveable. |

| Tag (object StartTags, NEW) | Value |
|---|---|
| SHEET | start-sheet |
| START_TITLE | start-title |
| START_TODAY | start-today |
| START_RESUME | start-resume |
| START_TODAY_REST | start-today-rest |
| START_TODAY_NONE | start-today-none |
| START_ROUTINE(id) | start-routine-{id} |
| START_ALL_ROUTINES | start-all-routines |
| START_EMPTY | start-empty |
| START_OTHER | start-other |
| START_ERROR | start-error |
| START_RETRY | start-retry |

Old tags removed: HomeStartTags.* (home-start-*), HistoryTags.START_SHEET, PlanTags.START_SHEET, BodyTags.START_SHEET. Each opener keeps its own opener tag (see 3.2) but all open the same SHEET.

| String id | Copy |
|---|---|
| start_title | Start |
| start_group_today | Today |
| start_group_routine | Routine |
| start_group_empty | Empty |
| start_group_other | Other |
| start_today | Start %1$s |
| start_today_sub | %1$d lifts, about %2$d min |
| start_resume | Resume %1$s |
| start_resume_sub | %1$d sets logged |
| start_rest_day | Rest day. Nothing planned. |
| start_next_hint | Next: %1$s, %2$s |
| start_no_plan | No plan this week |
| start_plan_week | Plan week |
| start_routine_sub | %1$d lifts |
| start_all_routines | All routines |
| start_no_routines | No routines yet |
| start_make_routine | Make one |
| start_empty | Empty workout |
| start_other | Cardio or log past |
| start_blocked | Finish or discard the live workout first |

### 3.2 StartOpener (rename of StartSheetOpener, ui/workout/StartOptionsSheet.kt line 418, moved to ui/start/StartOpener.kt)

One component used by Today, Plan, History, Body. Today keeps its hero (TODAY_HERO) but its primary button becomes "Start" which opens StartChooser with group 1 pre-highlighted; there is no second start path on Today. Opener tags: HomeTags.START (home-start, kept), PlanTags.START (NEW plan-start), HistoryTags.START (NEW history-start), BodyTags.START (NEW body-start). Height Metrics.touchMin min, label "Start".

Today hero changes (ui/home/HomeScreen.kt, DayBlock.kt, HomeStartPrompt.kt): TODAY_START (today_start_cta) is kept as a tag but its click opens StartChooser instead of starting directly. ASSUMPTION: Allen wants the one-tap start of today's plan kept as two taps (Start, then Start {routine}). If one tap is wanted, D15 below.

| Id | Decision | Recommended default | Blocks |
|---|---|---|---|
| D15 | Today hero: one tap straight into today's session, or open the chooser? | Open the chooser (one path, the same everywhere, as approved). Group 1 is the first, largest row so it is still one more tap. | V2 |

### 3.3 RoutinesScreen (NEW, ui/routines/RoutinesScreen.kt)

Moves out of PlanScreen.kt: the routines header (PlanTags.ROUTINES, line 481), routine rows (PlanTags.routine / routineName / routineThumb / routineOptions / routineEdit / routineDelete), the Library button (PlanTags.LIBRARY, line 562), import (RoutineImportSheet) and routine deletion copy (RoutineDeletionCopy.kt). Route NEW Route.RoutineLibrary("routines-library?pick={pick}").

```
@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    onOpenRoutine: (routineId: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onPicked: ((routineId: String) -> Unit)? = null, // pick mode from StartChooser
    viewModel: PlanViewModel = viewModel(),          // ASSUMPTION: reuse PlanViewModel routine state first; split to RoutinesViewModel in V3 only if PlanViewModel tests allow
)
```

Layout: TopBar (back, title "Routines", actions: Library icon button 48dp tag ROUTINES_LIBRARY, overflow with Import). LazyColumn: "New routine" SecondaryButton row (rowMin), then routine rows exactly as on trunk (same composable, moved). Empty: EmptyState RACK, "No routines yet", body "Make one, or start from the library." Tags (object RoutinesTags, NEW): CONTENT routines-content, BACK routines-back, LIBRARY routines-library, NEW_ROUTINE routines-new, EMPTY routines-empty; row tags keep the PlanTags.routine* values (plan-routine-*) so the 6 instrumented tests that scroll to PlanTags.routine (LibraryWorkflowInstrumentedTest, RoutineWorkflowInstrumentedTest, PlanRoutineActionsInstrumentedTest) only need a navigation step added, not new tags. Strings: routines_title Routines, routines_new New routine, routines_empty_title No routines yet, routines_empty_body Make one, or start from the library.

### 3.4 RestBar (NEW, ui/workout/RestBar.kt)

Replaces WorkoutRestPanel (ui/workout/WorkoutRestPanel.kt, full screen when restImmersion) and RestTimerScreen (ui/workout/RestTimerScreen.kt, pushed). Lives inside the live LazyColumn as an item directly above the next-set entry (key "rest-bar").

```
@Composable
internal fun RestBar(
    remainingSeconds: Int,
    totalSeconds: Int,
    running: Boolean,
    overtime: Boolean,
    onNudge: (deltaSeconds: Int) -> Unit, // -15 / +15
    onSkip: () -> Unit,
    onEditDuration: () -> Unit,           // opens existing REST_DURATION_SHEET
    modifier: Modifier = Modifier,
)
```

Layout: Surface (Surface2, RestCyan 2dp left accent via Metrics.emphasisBorder), min height Metrics.restBar, padding horizontal Metrics.space4, vertical Metrics.space2. Row: kicker "Rest" (RestCyan), clock "1:24" (InstrumentType numeral, tabular), spacer, "-15" (48dp), "+15" (48dp), "Skip" GhostButton (48dp). Below the row: progress track height Metrics.restBarProgress, RestCyan on Surface3, fraction remaining/total, no animation beyond the 1 s tick (motion baseline 11 must not rise; use the existing tick). Overtime: clock counts up "+0:12" in Warn, track full Warn. Completed (0:00 reached, not yet dismissed): row reads "Rest done" for 3 s then the bar collapses (existing completedTimerId). Tap on the clock opens the duration sheet (replaces onOpenRest).

Behaviour: the next set entry (NextSetInputs, WeightRepsEditor, RpeSelector, Log) stays below the bar, visible and editable (D5). Logging during rest skips rest and starts a new rest after the logged set as today (RestCommands, INFERRED). Spoken: liveRegion polite on the clock only once per 15 s and at 0:00, contentDescription "Rest, {m} minutes {s} seconds left".

Tags (WorkoutTestTags, existing object in ActiveWorkoutScreen.kt): REST_BAR workout-rest-bar (exists, reused), REST_MINUS workout-rest-minus (exists), REST_PLUS workout-rest-plus (exists), REST_SKIP workout-rest-skip (exists), NEW REST_CLOCK workout-rest-clock, NEW REST_PROGRESS workout-rest-progress. Removed: REST_FLOOR, REST_FLOOR_RING, REST_START_NOW, REST_EDIT_UP_NEXT, REST_UNDO_LAST, RestFloorTags.* (RestTimerScreen).

Strings: rest_kicker Rest (exists), rest_minus -15, rest_plus +15, rest_skip Skip, rest_done Rest done, rest_overtime +%1$s, rest_spoken Rest, %1$d minutes %2$d seconds left. Remove rest_start_now, rest_start_sub, rest_up_next, rest_logged_effort only after a grep shows no other use.

Outside the app (must keep working, no code change expected): RestTimerService, RestTimerNotifications (content intent carries EXTRA_SESSION_ID, MainActivity.consumeSessionId opens the session), RestTimerOverlayController + RestOverlayRingView, RestLockActivity and LockScreenRest*Widget. The only nav change: anything that navigated to Route.RestTimer now navigates to Route.ActiveWorkout. INFERRED: notification taps already open the session route, not the rest route; V4 verifies with a test.

### 3.5 SessionEditMode (NEW behaviour on ui/history/SessionDetailScreen.kt)

Today, tapping a set opens SetEditSheet (tap-to-edit, always on). v4 makes editing explicit:
- Read mode (default): set rows are not clickable; TopBar action "Edit" (tag SESSION_EDIT, NEW session-detail-edit) next to OPTIONS.
- Edit mode: TopBar title becomes "Editing", actions "Done" (tag SESSION_EDIT_DONE). Each set row shows weight and reps as inline steppers (reuse WeightRepsEditor compact; min 48dp targets) and a delete icon button 48dp (tag sessionSetDelete(setId) = session-detail-set-delete-{id}). "Add set" row per lift (reuses addSet, tag sessionAddSet(exerciseId)).
- Each change writes immediately through existing updateSet (debounced 400 ms per set, NEW) so there is no unsaved state to lose; delete shows the existing undo offer (undoDeleteSet). Effort edits keep SetLogRules.validateEffort.
- D7: deleting the last remaining set of the session is blocked with toast "Delete the session instead".
- D8: displayed set numbers are recomputed 1..n per lift.
- Leaving Edit (Done, back, process death): pending debounced writes flush first (existing releaseSetSave path).

Strings: session_edit Edit, session_editing Editing, session_edit_done Done, session_add_set Add set, session_delete_set Delete set %1$d, session_last_set_blocked Delete the session instead.

### 3.6 UpNextLine (NEW, ui/summary/UpNextLine.kt)

One row on WorkoutSummaryScreen, below FACTS and above DONE. Height Metrics.rowMin, Surface1, chevron. Copy "Up next: {weekday} {day}, {routineName}" or "Up next: plan your week" (D9). Tap: onOpenPlanDay(epochDay) which routes to the Plan tab with that day scrolled into view (PlanTags.day(epochDay)). Source: SchedulePlan.nextTrainingOnOrAfter(today + 1) (domain/ScheduleModels.kt line 107) read through the existing plan repository in WorkoutSummaryViewModel. Tag SummaryTags.UP_NEXT summary-up-next. Strings summary_up_next Up next: %1$s, %2$s; summary_up_next_none Up next: plan your week.

### 3.7 Body trend cards (NEW, ui/progress/BodyTrends.kt)

Three cards appended to ProgressScreen's LazyColumn after "muscles" (keys "trend-bodyweight", "trend-records", "trend-volume"):
- BodyweightTrendCard: last 12 entries, line drawn with the existing chart primitive (ASSUMPTION: TrainingCalendarCard drawing style; no new charting library). Empty: "No bodyweight yet" plus the existing check-in action. Tag BodyTags.TREND_BODYWEIGHT body-trend-bodyweight.
- RecordsCard: the lifetime records list moved from HistoryScreen (RecordRow, PrSummaryRow, records/recordsLoading/recordsUnavailable/recordsStale state moved from HistoryViewModel to ProgressViewModel). Top 5, "All records" expands. Tag BodyTags.TREND_RECORDS body-trend-records; rows keep their onOpenExercise.
- WeeklyVolumeCard: per muscle, working sets per ISO week for the last 8 weeks, sorted by this week desc; bars use the existing muscle heat colours. Tag BodyTags.TREND_VOLUME body-trend-volume.
Strings: body_trend_bodyweight Bodyweight, body_trend_records Records, body_trend_volume Weekly sets per muscle, body_no_bodyweight No bodyweight yet, body_all_records All records.

## 4. Screens

Format per screen: current capture, primary, secondary, regions, states, strings, in/out/back, edge cases, files. Capture names are walkthrough-screens file stems from UxWalkthroughTest (app/src/androidTest/.../ui/walkthrough/UxWalkthroughTest.kt); NEW captures are added by the packet that owns the screen.

### 4.1 Today (Home tab)

- Current capture: flow03_step01_tab-today. NEW capture v4_today_start_open.
- Primary: Start (HomeTags.TODAY_START today_start_cta) opens StartChooser. Live session: Resume (TODAY_RESUME) opens the live workout directly (resume is not a start; unchanged).
- Secondary: week strip (Home keeps its strip; Plan lost it in R6), bodyweight check-in, logged blocks.
- Removed: HomeStartSheet and its 4 pages; the "free start" button HomeTags.FREE home-free-start (Empty workout now lives in the chooser group 3).

| State | Spec |
|---|---|
| Planned today | Hero shows routine name; Start opens chooser with group 1 = Start {routine}. |
| Rest today | Hero shows "Rest day"; Start still visible (training on a rest day is allowed); chooser group 1 = rest row (D3). |
| Live | Resume primary; Start hidden (single entry point to the live session). |
| No plan | Hero TODAY_BUILD_WEEK unchanged; Start visible as secondary. |
| Loading / error | Unchanged (TODAY_ERROR, READ_PROBLEM, RETRY). |
| Process death | Chooser open flag rememberSaveable in HomeScreen (replaces HomeStartSheet's flag at line 682). |

- Files: ui/home/HomeScreen.kt, ui/home/DayBlock.kt, ui/home/HomeStartPrompt.kt, DELETE ui/home/HomeStartSheet.kt, ui/home/HomeViewModel.kt (drop start methods that duplicate StartOptionsViewModel; keep pending occurrence handoff).
- Edge: reminder handoff (ReminderHandoff.kt, PendingOccurrence) still starts the occurrence directly without the chooser: a reminder tap is an explicit "start this" (INFERRED from TapVerdict). Keep.

### 4.2 Plan week (Plan tab)

- Current capture: flow03_step02_tab-plan (seven day rows after R6).
- Primary: per-row Add (NEW, see below). Header actions: Routines (NEW, opens RoutinesScreen), Start (PlanTags.START, opens chooser).
- Secondary: summary item (REPLAY, SUGGEST, USE_WEEK, DISMISS, LIGHTER, unchanged), missed work card.
- Removed from this screen: routines header and rows, Library button, header ADD_SESSION (lines 434 to 457 use selectedEpochDay = today, which is the R6 Critic bug).

Regions top to bottom: TopBar (title "Plan" PlanTags.TITLE; actions Routines icon 48dp tag PlanTags.ROUTINES_ENTRY plan-routines-entry, Start 48dp); week summary card; 7 PlanWeekDayRow items (key day:{epochDay}); bottom padding Metrics.space7.

PlanWeekDayRow v4 (ui/plan/PlanWeekRows.kt):
- Min height Metrics.planTrainingRow (72) for training rows, Metrics.planRestRow (56) for rest rows. heightIn(min), never fixed.
- Header row: weekday short + day number (InstrumentType.bodyStrong), "today" chip if today (string plan_today), status text ("Suggested {focus}" string plan_suggested, or WeekBoard.status, or "Rest" string plan_rest), trailing Add icon button 48dp tag PlanTags.dayAdd(epochDay) plan-day-add-{epochDay}, onClick onOpenDay(epochDay, add = true). Past days: Add hidden (INFERRED: PlanDay add is not offered for past days today; keep).
- Header click: onClickLabel string plan_open_day "Open this day", Role.Button, heading.
- Spoken text (contentDescription on the header, merged): "{weekday full} {day}. {status}. {n} planned." Suggested rest day: "{weekday} {day}. Rest. Suggested rest day." (R6 minor: covered by a test).
- Planned items below the header: PlanAgendaRow per item (unchanged).

| State | Spec |
|---|---|
| Loading | Seven placeholder rows at 56 min height (Surface1), no text. Never blank (v3 A01 stays). |
| Empty week | Seven rest rows, summary card offers SUGGEST. |
| Proposal pending (suggestFills ran) | Rows show "Suggested {focus}"; nothing is written until USE_WEEK. Test: suggestFills writes no schedule slot (R6 minor). |
| Error | actionError toast (existing). |
| Font 2.0 | Rows grow; header wraps to two lines; Add stays 48dp and visible. |
| Process death | Scroll position via rememberLazyListState saveable (default). |

Strings (NEW, R6 minor: move literals from PlanWeekRows.kt lines 61, 82, 92): plan_suggested Suggested %1$s; plan_today today; plan_open_day Open this day; plan_rest Rest; plan_add_day Add to %1$s; plan_routines Routines; plan_day_spoken %1$s %2$d. %3$s. %4$d planned.; plan_rest_suggested_spoken %1$s %2$d. Rest. Suggested rest day.

- In: Plan tab, Summary Up next (scrolls to PlanTags.day(epochDay)), chooser "Plan week".
- Out: PlanDay (row tap or Add), RoutinesScreen, StartChooser.
- Files: ui/plan/PlanScreen.kt, ui/plan/PlanWeekRows.kt, ui/plan/PlanViewModel.kt (no logic change in V1; routine state moves in V3), ui/navigation/AppNav.kt (PlanScreen params), app/src/main/res/values/strings.xml.

### 4.3 Routines (NEW screen)

- NEW capture v4_routines_list.
- Primary: New routine. Secondary: Library, Import, per-row options (edit, delete), row tap opens RoutineEditor.
- Pick mode (from chooser "All routines"): row tap starts the routine via StartOptionsViewModel.startRoutine and pops back; options hidden.
- States: Loading (3 placeholder rows), Empty (3.3), Error (existing routine read error copy), Process death (route arg pick restores mode).
- In: Plan TopBar Routines; chooser All routines / Make one. Out: RoutineEditor, Library, ActiveWorkout (pick mode). Back: pops to caller.
- Files: NEW ui/routines/RoutinesScreen.kt, ui/plan/PlanScreen.kt (remove section), ui/navigation/AppNav.kt (NEW Route.RoutineLibrary, composable), ui/plan/RoutineDeletionCopy.kt (unchanged, now used here).

### 4.4 Start chooser

Spec in 3.1. Openers: Today, Plan, History, Body (3.2). Capture NEW v4_start_planned, v4_start_rest_day, v4_start_live.

### 4.5 Live workout with inline rest

- Current captures: flow04_step03_strength-rest-after-set-1 (today: full screen WorkoutRestPanel). NEW v4_live_rest_bar, v4_live_rest_edit_next.
- Change in ActiveWorkoutScreen.kt: delete the `else if (restImmersion && selected != null)` branch (line 807 onward) that swaps the LazyColumn for WorkoutRestPanel. The LazyColumn always renders. When rest.running (or completed within 3 s) and the selected lift has a next set, insert item("rest-bar") RestBar directly above the next-set entry item. restImmersionLiftId stays to decide which lift owns the bar.
- Scroll: when rest starts, bring the rest bar and the next-set entry into view together (LogLoopBringIntoView.kt).
- Dock: primary stays Log (not "Start set now"); restPrimarySubLabel (line 650, rest_start_sub) is removed.
- Up next text and last-logged line move into the existing next-set recommendation row (NextSetRecommendation.kt) which is already above the inputs. ASSUMPTION: no extra row needed.
- Undo last set: existing UndoFeedback toast (onUndo = undoTopOffer) covers it; REST_UNDO_LAST removed.

| State | Spec |
|---|---|
| Resting | Bar above next set, counting down; inputs editable; Log enabled per SetLogRules. |
| Editing next set during rest | Rest keeps counting (D5). |
| Rest completed | "Rest done" 3 s, bar collapses, phone buzz/notification as today (RestTimerAlerts). |
| Overtime | Counts up in Warn until the next Log or Skip. |
| Rest off in settings | No bar. |
| Last set of last lift | No bar unless RestTimer.shouldStartAfterExtra (existing). |
| Leave screen during rest | Overlay ring (RestTimerOverlayController), notification and lock widget continue; live session bar shows rest remaining (LiveSessionBarUiState.restRemainingSeconds). |
| Return via notification / lock screen | Opens Route.ActiveWorkout(sessionId); bar shows current remaining. |
| Process death | RestTimerStatePersistence restores; bar renders from RestTimerController state at first frame. |
| Font 1.3 and 2.0 | Bar grows in height; buttons wrap to a second row at 2.0; no truncation of clock. |

- Files: ui/workout/ActiveWorkoutScreen.kt, NEW ui/workout/RestBar.kt, DELETE ui/workout/WorkoutRestPanel.kt, DELETE ui/workout/RestTimerScreen.kt (after V4 verifies no caller), ui/workout/RestTimerCard.kt (onOpenRest becomes onEditDuration), ui/workout/RestTimerViewModel.kt (keep if RestBar needs it; else fold), ui/navigation/AppNav.kt (remove Route.RestTimer composable, LIVE_BAR_HIDDEN_ROUTES entry at line 285, onOpenRest param), ui/workout/LogLoopBringIntoView.kt, app/src/main/res/values/strings.xml.

### 4.6 Session detail, read and edit

- Current capture: flow07_* history detail (INFERRED name; V5 confirms). NEW v4_session_read, v4_session_edit, v4_session_edit_deleted_undo.
- Read mode: rows not clickable (today they open SetEditSheet). Primary: Repeat (REPEAT, unchanged). TopBar: back, Edit (NEW), Options (DELETE session).
- Edit mode: 3.5. TopBar back = Done (flushes). Repeat hidden in edit mode.
- After any write: SessionDetailViewModel's existing flow re-emits the session; markSummaryChanged() already fires on delete; V5 makes updateSet call markSummaryChanged too (INFERRED: it does not today; line 596 onward shows no call).

| State | Spec |
|---|---|
| Write fails | Existing report() message "Could not save that set. Try again."; field reverts to stored value. |
| Delete | Row removed, undo toast 5 s (existing undo offer); D7 guard. |
| Hold or timed set | Stepper shows duration, reps hidden (existing timedOriginal logic). |
| Live session open | Edit allowed on finished sessions only; the live one opens ActiveWorkout (existing onOpenActiveSession). |
| Process death | Edit mode flag rememberSaveable; pending debounced write flushed in onCleared via existing releaseSetSave. |

- Recompute (D14): PRs (derived on read), History period readout (re-queried), Body trends (re-queried), progression hints (read at next session start). Acceptance A-F4-3 proves a PR disappears after an edit lowers the weight.
- Files: ui/history/SessionDetailScreen.kt, ui/history/SessionDetailViewModel.kt, ui/history/FilledLiftCard.kt, ui/history/SetEditSheet.kt (kept for add-set only, or deleted if inline add covers it; V5 decides by test coverage), app/src/main/res/values/strings.xml.

### 4.7 Workout summary

- Current capture: flow04_step10_strength-finished-summary. NEW v4_summary_up_next.
- Add UpNextLine (3.6) between FACTS (summary-facts) and DONE (summary-done). Done unchanged (Metrics.commit). OPEN_SESSION unchanged.
- States: plan loading (line hidden until loaded, no layout jump: reserve rowMin), no plan (D9), next day is tomorrow ("Up next: tomorrow, {routine}", string summary_up_next_tomorrow Up next: tomorrow, %1$s).
- Out: Plan tab with that day scrolled into view. Mechanism: navigate with goToTab(Route.Routines.path) and set a one-shot SavedStateHandle key "plan_focus_day" (Long) on the Plan back stack entry; PlanScreen consumes and clears it, then scrolls to PlanTags.day(epochDay). No query arg on the tab route, because Tab.matchPattern (AppNav.kt line 214) relies on tab routes having none.
- Files: ui/summary/WorkoutSummaryScreen.kt, ui/summary/WorkoutSummaryViewModel.kt, NEW ui/summary/UpNextLine.kt, ui/navigation/AppNav.kt, ui/plan/PlanScreen.kt (consume plan_focus_day).

### 4.8 History tab

- Keeps: period readout (DAY/WEEK/MONTH/YEAR/ALL), calendar, session and activity list, SESSION_COUNT, TRAINED_DAYS, WORKING_SETS, ACTIVE_TIME.
- Removes: lifetime records list (LIFETIME_RECORDS, RecordRow) and lifetime blocks? DECISION D16. RECORD_COUNT metric stays as a count but taps through to Body records (D10).
- Adds: Start opener (HistoryTags.START).

| Id | Decision | Recommended default | Blocks |
|---|---|---|---|
| D16 | Training blocks list (LIFETIME_BLOCKS): History or Body? | History (blocks are lists of past sessions, which History owns). | V7 |

- Packet: V7.
- Files: ui/history/HistoryScreen.kt, ui/history/HistoryViewModel.kt (records state removed), ui/navigation/AppNav.kt (History onOpenBodyRecords).

### 4.9 Body tab

- Keeps: body map, window chips, muscles, recommendations.
- Adds: 3.7 cards. Must not show a session list: assert no node with tag prefix "history-" or SessionDetail navigation from Body. INFERRED: Body has no session list on trunk ("first-lifts" item lists exercises, not sessions); V7 adds the guard test.
- Files: ui/progress/ProgressScreen.kt, ui/progress/ProgressViewModel.kt, NEW ui/progress/BodyTrends.kt, data/local/dao (NEW weekly volume @Query in the existing set log DAO; no entity change), data/repository/WorkoutRepository.kt (expose it).

### 4.10 Cardio live (R6 minors)

- Pause debounce: togglePause ignores calls within 300 ms of the last accepted toggle (NEW constant PAUSE_DEBOUNCE_MS = 300L in LiveCardioViewModel, measured with elapsedRealtime, not wall clock; no real sleep in tests: inject the clock that is already injected as elapsedRealtime).
- Paused under 60 s: caption below the clock "Resume to reach one minute" (string cardio_paused_under_minute) instead of cardio_paused_caption, because Finish is enabled after 60 s (v3 D12). Tag CardioTags.PAUSED_UNDER_MINUTE live-cardio-paused-under-minute.
- While paused, the secondary "Leave running" reads "Leave paused" (string cardio_leave_paused) since nothing runs. CardioCopy.LEAVE_RUNNING stays for running. ASSUMPTION on exact copy; Allen may prefer "Leave for now".
- Files: ui/activity/LiveCardioViewModel.kt, ui/activity/LiveCardioScreen.kt, domain/CardioCopy.kt, strings.xml.

### 4.11 Live session bar (R6 minor)

- LiveSessionBarUiState gains `paused: Boolean = false` (cardio only), read from CardioTimerPersistence the same way elapsed is (CardioElapsed). Text "{elapsed} elapsed" (LiveSessionBar.kt line 190) becomes "Paused, {elapsed}" (string live_bar_paused Paused, %1$s) when paused. Tag LiveSessionBarTestTags.PAUSED live-session-bar-paused on the label.
- Files: ui/navigation/LiveSessionBarViewModel.kt, ui/navigation/LiveSessionBar.kt.

### 4.12 Targets sheet (R6 minor, test only)

- TargetsSheetScreenTest.dismissingTheSheetWritesNothing today hides the sheet by flipping its host flag. V1 replaces this with a real dismissal: performTouchInput { swipeDown() } on TargetsSheetTags.CONTENT, then waitUntil the content is gone, then assert Room unchanged. If Robolectric cannot drive the ModalBottomSheet swipe (it failed for back in R6), fall back to the editor path: close via RoutineEditorScreen's own dismissal (onDismiss from the editor host), not a test-owned flag. Record which one worked in the test KDoc.

## 5. State machines

### 5.1 Start (StartOptionsViewModel, ui/workout/StartOptionsViewModel.kt, moved to ui/start/ in V2)

States: Loading, Ready(todayStart, routines, inProgress, liveActivity), Pending(kind), Blocked, Error(message), Navigating(sessionId | activityId | composerMode).

| From | Event | To | Effect |
|---|---|---|---|
| Loading | data loaded | Ready | group 1 resolved: inProgress != null -> Resume; todayStart != null -> Planned; plan rest today -> Rest; no plan -> None |
| Ready | tap Start today | Pending(TODAY) | startToday() (existing) |
| Ready | tap routine | Pending(ROUTINE) | startRoutine(id) (existing) |
| Ready | tap Empty | Pending(EMPTY) | startFree() (existing) |
| Ready | tap Resume | Navigating(inProgress.id) | no write |
| Pending | StartSessionOutcome.Started | Navigating | navigateToSession emits; sheet dismisses then onWorkoutStarted |
| Pending | Blocked | Blocked | toast start_blocked; back to Ready |
| Pending | Failed | Error | message shown, Retry -> same Pending |
| any | sheet disposed | (cleared) | clearPendingNavigation() (existing) |

Rule: exactly one StartOptionsViewModel instance per open sheet, scoped to the sheet host back stack entry (the AppNav-level sheet at line 890 becomes the only host; HomeScreen's local host is deleted).

### 5.2 Rest (RestTimerController, timer/RestTimerController.kt)

No controller change. UI mapping only:

| Controller state | RestBar |
|---|---|
| idle | hidden |
| running, remaining > 0 | countdown, track remaining/total |
| running, remaining <= 0 (overtime) | "+m:ss" Warn |
| completedTimerId set, not running | "Rest done" for 3 s then hidden |
| skipped | hidden immediately |

Events from the bar: nudge(-15/+15) -> viewModel.nudgeRest; skip -> viewModel.skipRest; clock tap -> duration sheet (selectRestDuration / selectCustomRest). Log during rest -> existing log path (skips current rest, starts next per settings).

### 5.3 Session edit (SessionDetailScreen local + SessionDetailViewModel)

Read -> (Edit) -> Editing -> (Done | back | process death flush) -> Read. Inside Editing: per set, Idle -> Dirty (stepper change) -> Writing (after 400 ms debounce) -> Idle | Failed(revert). Delete -> Deleted(undo 5 s) -> Gone | Restored.

## 6. Behaviour rules

1. One start path: no code path outside StartOptionsViewModel creates a workout session from UI, except reminder handoff (PendingOccurrence) and Repeat on session detail (repeatSession). Grep gate in V2: `startSession\(|startFree\(|startRoutine\(` callers listed in the PR body.
2. Rest never blocks input: no composable in the live screen is disabled because rest.running.
3. No pushed rest route: Route.RestTimer does not exist after V4; a unit test asserts the nav graph has no "session/{sessionId}/rest" destination.
4. Edits recompute lazily (D14); no screen caches PRs across a write.
5. History owns sessions, Body owns trends: no session row composable is reachable from ProgressScreen; no records list composable is reachable from HistoryScreen.
6. Every new copy is a string resource in app/src/main/res/values/strings.xml; no new user-visible Kotlin string literals in touched composables. No checker baseline may rise.
7. No new raw dp (raw_dp 14), no new named colour (named_color 2), no new motion (motion 11), no real sleeps or delays in tests (27 / 6).
8. Every new tappable has min 48dp and a role.

## 7. Packets

Every packet: branch from the latest trunk; one PR; static preflight green before every push; both required checks green; smoke failures compared with the latest trunk run (no new "Class > method" FAILED line); walkthrough captures refreshed where named; no checker baseline rise; no Room change (stop and ask if one appears, DECISION R1); do not merge without Allen.

Two lanes. Lane A = Plan, Routines, Summary (ui/plan, ui/routines, ui/summary). Lane B = live workout, cardio, session detail (ui/workout, ui/activity, ui/history detail). Shared files and their owner per packet are listed; the non-owner lane may only append to shared files and must rebase before push.

Order and parallelism:

| Step | Lane A | Lane B | Gate between steps |
|---|---|---|---|
| 1 | V1a Plan minors | V1b Cardio, live bar, Targets minors | both merged before step 2 |
| 2 | V3 Routines screen, Plan week-only | V4 Inline rest | independent |
| 3 | V2 One Start path (single lane, both lanes paused on AppNav.kt) | (idle or V5 prep on a branch, no push) | V2 merged |
| 4 | V6 Summary Up next | V5 Session edit mode | independent |
| 5 | V7 History vs Body (single lane) | | |

V2 runs after V3 because the chooser's "All routines" opens the Routines screen V3 creates.

### V1a Plan week minors (R6 Critic, Lane A)

- Content: per-row Add (PlanTags.dayAdd) replacing header ADD_SESSION that used today; strings plan_suggested, plan_today, plan_open_day, plan_rest, plan_add_day, spoken strings; Metrics.planTrainingRow / planRestRow aliases replacing PlanWeekRowMetrics literals; PlanWeekRows tests restoring the strip coverage lost in R6.
- Files touched: ui/plan/PlanScreen.kt, ui/plan/PlanWeekRows.kt, ui/theme/Metrics.kt, app/src/main/res/values/strings.xml.
- New files: app/src/test/.../ui/plan/PlanWeekRowsRenderTest.kt.
- Tests (NEW unless marked): PlanWeekRowsRenderTest.suggestedRestDaySpeaksItsStatus; .suggestFillsWritesNothing (PlanViewModel.suggestFills then scheduleRepository.slots() unchanged); .trainingRowIsAtLeast72AndRestRowAtLeast56 (assertHeightIsAtLeast at font 1.0 plus exact equality when content is short, confirming min-height per v3 4.2); .rowsGrowAtFontScaleTwo; .rowAddOpensThatDay (tap plan-day-add-{tomorrow} -> onOpenDay(tomorrow, true)); PlanRoutineActionsRenderTest update; FrontendBaselineCaptureTest keeps the "plan-day-header-" matcher.
- Instrumented: none new; FrontendBaselineCaptureTest Plan matcher unchanged.
- Captures: flow03_step02_tab-plan.
- Conflicts: strings.xml (append under a "<!-- v4 plan -->" comment), Metrics.kt (Lane A owns in step 1).
- Risk: Low.
- Done when: A-V1-1..A-V1-6 pass.

### V1b Cardio, live bar and Targets minors (R6 Critic, Lane B)

- Content: Pause debounce 300 ms; "Resume to reach one minute" under 60 s; "Leave paused" wording; live bar Paused label; TargetsSheet real dismiss test.
- Files touched: ui/activity/LiveCardioViewModel.kt, ui/activity/LiveCardioScreen.kt, domain/CardioCopy.kt, ui/navigation/LiveSessionBarViewModel.kt, ui/navigation/LiveSessionBar.kt, app/src/main/res/values/strings.xml (append under "<!-- v4 cardio -->").
- Tests: LiveCardioViewModelTest.pauseTogglesWithin300msAreIgnored (injected elapsedRealtime, no sleep); .pauseAfter300msIsAccepted; LiveCardioRenderTest (NEW or existing) .pausedUnderAMinuteShowsResumeCue; .pausedShowsLeavePaused; LiveSessionBarViewModelTest.pausedCardioReportsPaused; LiveSessionBarRenderTest.pausedLabelShows; TargetsSheetScreenTest.dismissingTheSheetWritesNothing rewritten (4.12).
- Conflicts: LiveSessionBar*.kt are Lane B only. strings.xml append.
- Risk: Low. Robolectric swipe on ModalBottomSheet may not work; fallback documented in 4.12.
- Done when: A-V1-7..A-V1-12 pass.

### V2 One Start path (F1, single lane)

- Content: StartChooser (3.1), StartOpener (3.2), StartTags; delete HomeStartSheet; Today, Plan, History, Body open the same sheet; StartOptionsViewModel moved to ui/start and given group resolution (Planned / Resume / Rest / None) and routine ordering.
- Files touched: ui/navigation/AppNav.kt (sheet host line 890, showStartSheet), ui/home/HomeScreen.kt, ui/home/DayBlock.kt, ui/home/HomeStartPrompt.kt, ui/home/HomeViewModel.kt, ui/plan/PlanScreen.kt (opener only), ui/history/HistoryScreen.kt (opener only), ui/progress/ProgressScreen.kt and ui/progress/BodyMap.kt (opener tag), strings.xml.
- New files: ui/start/StartChooser.kt, ui/start/StartOpener.kt, ui/start/StartTags.kt; move ui/workout/StartOptionsViewModel.kt -> ui/start/StartOptionsViewModel.kt (git mv, package rename).
- Deleted: ui/home/HomeStartSheet.kt, ui/workout/StartOptionsSheet.kt.
- Tests: move and adapt StartOptionsViewModelTest, StartOptionsRoutineCardTest, StartOptionsRoutineCardRenderTest, StartOptionsSheetMixedRowTest, HomeStartSheetTest -> StartChooserRenderTest (NEW): .plannedTodayIsFirst; .routinesThenEmptyOrder (node bounds top-to-bottom); .restDayShowsNextHint; .liveSessionShowsResumeAndDisablesOthers; .noPlanOffersPlanWeek; .sameChooserFromEveryTab (open from Today, Plan, History, Body, assert StartTags.SHEET and identical group order); ConnectedWorkoutJourneyTest update; ExerciseDetailThisWorkoutRenderTest update.
- Instrumented: UxWalkthroughTest start steps (taps HomeTags.START then StartTags.START_TODAY); LibraryWorkflowInstrumentedTest line 367 and 392 (PlanTags.START_SHEET -> PlanTags.START + StartTags.SHEET).
- Captures: v4_today_start_open, v4_start_planned, v4_start_rest_day, v4_start_live; flow04 start steps refreshed.
- Conflicts: AppNav.kt (owner), PlanScreen.kt (opener lines only; Lane A must have V3 merged), HistoryScreen.kt (opener lines only).
- Risk: Medium. Many tests use HomeStartTags; ConnectedWorkoutJourneyTest is long.
- Done when: A-F1-1..A-F1-7 pass.

### V3 Routines screen, Plan week-only (F2, Lane A)

- Content: RoutinesScreen (3.3), Route.RoutineLibrary, Plan TopBar Routines entry, remove routines section and Library button from PlanScreen.
- Files touched: ui/plan/PlanScreen.kt (owner), ui/plan/PlanViewModel.kt, ui/navigation/AppNav.kt (append one composable and Route; Lane B in step 2 does not touch the Route sealed class lines 141 to 190 except removing RestTimer at line 158, a different hunk), strings.xml.
- New files: ui/routines/RoutinesScreen.kt, ui/routines/RoutinesTags.kt.
- Tests: RoutinesScreenRenderTest (NEW) .listsRoutines; .emptyShowsMakeOne; .optionsEditAndDelete (moved from PlanRoutineActionsRenderTest); .pickModeStartsAndPops; PlanScreenRenderTest .planShowsNoRoutineRows (assert PlanTags.ROUTINES absent); ShippingTabsRenderTest unchanged.
- Instrumented: LibraryWorkflowInstrumentedTest, CustomLiftRecoveryInstrumentedTest, LibraryRoutineHandoffInstrumentedTest, RoutineWorkflowInstrumentedTest, PlanRoutineActionsInstrumentedTest: add tap(PlanTags.ROUTINES_ENTRY) before PlanTags.LIBRARY / routine(id). UxWalkthroughTest.awaitPlanTabReadyForCapture: drop the ROUTINES and seed routine waits (they no longer render on Plan); wait for PlanTags.dayHeader(today) instead.
- Captures: flow03_step02_tab-plan, v4_routines_list.
- Conflicts: AppNav.kt (append), strings.xml (append), UxWalkthroughTest.kt (Lane A owns in step 2; Lane B V4 edits rest steps in a different function, rebase).
- Risk: Medium (instrumented tests that assume routines on Plan).
- Done when: A-F2-1..A-F2-5 pass.

### V4 Inline rest (F3, Lane B)

- Content: RestBar (3.4); remove the restImmersion full-screen branch; remove Route.RestTimer and RestTimerScreen; keep overlay, lock widget and notification.
- Files touched: ui/workout/ActiveWorkoutScreen.kt (owner), ui/workout/RestTimerCard.kt, ui/workout/RestTimerViewModel.kt, ui/workout/LogLoopBringIntoView.kt, ui/navigation/AppNav.kt (remove Route.RestTimer line 158, composable at line 759, LIVE_BAR_HIDDEN_ROUTES entry line 285, onOpenRest at line 743), strings.xml.
- New files: ui/workout/RestBar.kt. Deleted: ui/workout/WorkoutRestPanel.kt, ui/workout/RestTimerScreen.kt.
- Tests: RestBarRenderTest (NEW) .countsDownAboveNextSet (rest bar bounds bottom <= next-set entry top); .nextSetEditableDuringRest (change weight while running, assert draft changed and rest still running); .overtimeShowsWarn; .nudgeAndSkip; .fontScaleTwoWraps; ActiveWorkoutViewModelTest .editingDuringRestKeepsRestRunning; NavGraphTest (NEW or existing) .noRestRoute; RestTimerNotifications intent test .contentIntentOpensSession; existing RestTimerService / overlay / lock widget tests unchanged and green.
- Instrumented: UxWalkthroughTest rest step (flow04_step03) waits for WorkoutTestTags.REST_BAR instead of REST_FLOOR / RestFloorTags; any instrumented test using RestFloorTags migrated.
- Captures: flow04_step03_strength-rest-after-set-1 (now bar), v4_live_rest_bar, v4_live_rest_edit_next.
- Conflicts: AppNav.kt (different hunks from V3; rebase), UxWalkthroughTest.kt (different function), strings.xml (append).
- Risk: High. Largest screen file; rest is cross-cutting (service, overlay, widget). Physical acceptance on a device (lock screen, overlay) is NOT covered by CI; mark it "not verified physically" in the PR unless Allen checks.
- Done when: A-F3-1..A-F3-8 pass.

### V5 Session edit mode (F4, Lane B)

- Content: 3.5 and 4.6. No Room change (1.2).
- Files touched: ui/history/SessionDetailScreen.kt (owner), ui/history/SessionDetailViewModel.kt, ui/history/FilledLiftCard.kt, ui/history/SetEditSheet.kt, strings.xml.
- Tests: SessionDetailEditRenderTest (NEW) .readModeRowsAreNotClickable; .editChangesWeightAndReps (Room row updated, completedAt and setNumber unchanged); .deleteSetOffersUndo; .deleteLastSetOfSessionIsBlocked; .numbersRecomputeAfterDelete; SessionDetailViewModelTest .updateMarksSummaryChanged; recompute tests: PersonalRecordsAfterEditTest (NEW) .loweringThePrSetRemovesThePr; HistoryViewModelTest .periodTotalsFollowAnEdit; ProgressionAfterEditTest (NEW) .nextHintReadsTheCorrectedSet.
- Instrumented: none new required; HistoricalSetRecovery tests updated if they tap rows in read mode.
- Captures: v4_session_read, v4_session_edit, v4_session_edit_deleted_undo.
- Conflicts: none with Lane A (ui/history detail files are Lane B only; V7 touches HistoryScreen.kt and HistoryViewModel.kt, not detail files).
- Risk: Medium (existing edit/recovery code is intricate: releaseSetSave, beginSetEdit, retained edit state).
- Done when: A-F4-1..A-F4-6 pass.

### V6 Summary Up next (F5, Lane A)

- Content: UpNextLine (3.6), plan_focus_day handoff, Plan scroll-to-day.
- Files touched: ui/summary/WorkoutSummaryScreen.kt, ui/summary/WorkoutSummaryViewModel.kt, ui/navigation/AppNav.kt (summary composable onOpenPlanDay), ui/plan/PlanScreen.kt (consume key), strings.xml.
- New files: ui/summary/UpNextLine.kt.
- Tests: WorkoutSummaryRenderTest .upNextNamesTheNextPlannedDay; .upNextTomorrowCopy; .noPlanSaysPlanYourWeek; .tapOpensPlanOnThatDay (PlanTags.day(epochDay) displayed); SummaryRendersRenderTest unchanged.
- Captures: flow04_step10_strength-finished-summary, v4_summary_up_next.
- Conflicts: AppNav.kt (summary block only; Lane B V5 does not touch AppNav).
- Risk: Low.
- Done when: A-F5-1..A-F5-4 pass.

### V7 History vs Body (F6, single lane)

- Content: 3.7, 4.8, 4.9; records state moves HistoryViewModel -> ProgressViewModel; weekly volume query.
- Files touched: ui/history/HistoryScreen.kt, ui/history/HistoryViewModel.kt, ui/progress/ProgressScreen.kt, ui/progress/ProgressViewModel.kt, data/local/dao (set log DAO: one NEW @Query, no entity change), data/repository/WorkoutRepository.kt, ui/navigation/AppNav.kt, strings.xml.
- New files: ui/progress/BodyTrends.kt.
- Tests: BodyTrendsRenderTest (NEW) .bodyweightTrendShowsEntries; .recordsCardListsTopFive; .weeklyVolumeCountsWorkingSetsOnly (warm-ups excluded); .bodyHasNoSessionList; HistoryRenderTest .historyHasNoRecordsList; .prCountLinksToBody; WeeklyVolumeQueryTest (NEW, Room in-memory) .groupsByIsoWeekAndMuscle; schema check: app/schemas unchanged (git diff --exit-code app/schemas).
- Captures: flow03 History and Body tabs, v4_body_trends.
- Conflicts: single lane.
- Risk: Medium (query performance on large histories: add EXPLAIN check in the test, set_logs has indices on sessionId and exerciseId only (SetLogEntity.kt line 24), none on completedAt; filter by session date via workout_sessions instead. A NEW index would be a schema change: stop and ask, DECISION R1).
- Done when: A-F6-1..A-F6-6 pass.

## 8. Acceptance checklist

Machine-readable copy: acceptance.json (same ids).

| Id | Check | Evidence | Packet |
|---|---|---|---|
| A-V1-1 | Plan training day row min height 72dp, rest row 56dp; both are minimums (v3 4.2 confirmed) | PlanWeekRowsRenderTest.trainingRowIsAtLeast72AndRestRowAtLeast56 | V1a |
| A-V1-2 | Rows grow at font scale 2.0 with nothing clipped and Add still 48dp | PlanWeekRowsRenderTest.rowsGrowAtFontScaleTwo | V1a |
| A-V1-3 | A suggested rest day speaks 'Rest. Suggested rest day.' | PlanWeekRowsRenderTest.suggestedRestDaySpeaksItsStatus | V1a |
| A-V1-4 | suggestFills writes no schedule slot until Use week | PlanWeekRowsRenderTest.suggestFillsWritesNothing | V1a |
| A-V1-5 | Each day row's Add opens that day in add mode, not today | PlanWeekRowsRenderTest.rowAddOpensThatDay | V1a |
| A-V1-6 | 'Suggested', 'today', 'Open this day', 'Rest' come from string resources | grep: no literal in ui/plan/PlanWeekRows.kt + strings.xml ids plan_suggested, plan_today, plan_open_day, plan_rest | V1a |
| A-V1-7 | Pause toggles within 300ms of the last accepted toggle are ignored | LiveCardioViewModelTest.pauseTogglesWithin300msAreIgnored | V1b |
| A-V1-8 | Paused under 60s shows 'Resume to reach one minute' | LiveCardioRenderTest.pausedUnderAMinuteShowsResumeCue | V1b |
| A-V1-9 | While paused the leave action reads 'Leave paused' | LiveCardioRenderTest.pausedShowsLeavePaused | V1b |
| A-V1-10 | Live session bar shows 'Paused, {elapsed}' for a paused cardio session | LiveSessionBarRenderTest.pausedLabelShows + LiveSessionBarViewModelTest.pausedCardioReportsPaused | V1b |
| A-V1-11 | TargetsSheet dismissed by a real swipe or the editor's own dismissal writes nothing | TargetsSheetScreenTest.dismissingTheSheetWritesNothing | V1b |
| A-V1-12 | No real sleeps or delays added (baselines 27 / 6) | tools/preflight.sh static output | V1b |
| A-F1-1 | Today, Plan, History and Body all open the same StartChooser (StartTags.SHEET) | StartChooserRenderTest.sameChooserFromEveryTab | V2 |
| A-F1-2 | Group order top to bottom: today's planned session, routines, empty workout | StartChooserRenderTest.routinesThenEmptyOrder + plannedTodayIsFirst | V2 |
| A-F1-3 | Rest day shows 'Rest day. Nothing planned.' and the next planned day | StartChooserRenderTest.restDayShowsNextHint | V2 |
| A-F1-4 | A live session shows Resume first and disables new starts | StartChooserRenderTest.liveSessionShowsResumeAndDisablesOthers | V2 |
| A-F1-5 | No plan shows 'No plan this week' with Plan week | StartChooserRenderTest.noPlanOffersPlanWeek | V2 |
| A-F1-6 | HomeStartSheet.kt and StartOptionsSheet.kt are deleted; no HomeStartTags reference remains | grep in app/src | V2 |
| A-F1-7 | Walkthrough starts a workout through the chooser | screenshot v4_start_planned + UxWalkthroughTest pass | V2 |
| A-F2-1 | Plan tab shows no routine rows and no Library button | PlanScreenRenderTest.planShowsNoRoutineRows | V3 |
| A-F2-2 | Plan TopBar Routines opens RoutinesScreen | RoutinesScreenRenderTest + screenshot v4_routines_list | V3 |
| A-F2-3 | Routine options edit and delete work from RoutinesScreen | RoutinesScreenRenderTest.optionsEditAndDelete | V3 |
| A-F2-4 | Pick mode from the chooser starts the routine and pops | RoutinesScreenRenderTest.pickModeStartsAndPops | V3 |
| A-F2-5 | Instrumented routine and library flows pass via the Routines entry | LibraryWorkflowInstrumentedTest, RoutineWorkflowInstrumentedTest, PlanRoutineActionsInstrumentedTest (no new smoke failures) | V3 |
| A-F3-1 | During rest the bar sits directly above the next set entry | RestBarRenderTest.countsDownAboveNextSet | V4 |
| A-F3-2 | The next set stays editable during rest and rest keeps counting | RestBarRenderTest.nextSetEditableDuringRest + ActiveWorkoutViewModelTest.editingDuringRestKeepsRestRunning | V4 |
| A-F3-3 | Overtime counts up in Warn | RestBarRenderTest.overtimeShowsWarn | V4 |
| A-F3-4 | -15, +15 and Skip work from the bar | RestBarRenderTest.nudgeAndSkip | V4 |
| A-F3-5 | No pushed rest route exists | NavGraphTest.noRestRoute | V4 |
| A-F3-6 | Notification tap opens the live workout | RestTimerNotifications intent test .contentIntentOpensSession | V4 |
| A-F3-7 | Overlay, lock widget and service tests stay green | existing timer/* tests in Tests, lint, debug build | V4 |
| A-F3-8 | Font scale 2.0: bar wraps, clock not truncated | RestBarRenderTest.fontScaleTwoWraps + screenshot | V4 |
| A-F4-1 | Read mode rows are not clickable; Edit enters Edit mode | SessionDetailEditRenderTest.readModeRowsAreNotClickable | V5 |
| A-F4-2 | Editing weight and reps updates the stored set, keeping completedAt and setNumber | SessionDetailEditRenderTest.editChangesWeightAndReps | V5 |
| A-F4-3 | Lowering the PR set's weight removes that PR | PersonalRecordsAfterEditTest.loweringThePrSetRemovesThePr | V5 |
| A-F4-4 | History totals and the next progression hint follow an edit | HistoryViewModelTest.periodTotalsFollowAnEdit + ProgressionAfterEditTest.nextHintReadsTheCorrectedSet | V5 |
| A-F4-5 | Delete offers undo; deleting the last set of a session is blocked | SessionDetailEditRenderTest.deleteSetOffersUndo + deleteLastSetOfSessionIsBlocked | V5 |
| A-F4-6 | No Room schema change | git diff --exit-code app/schemas | V5 |
| A-F5-1 | Summary shows 'Up next: {day}, {routine}' for the next planned day | WorkoutSummaryRenderTest.upNextNamesTheNextPlannedDay | V6 |
| A-F5-2 | Tomorrow reads 'Up next: tomorrow, {routine}' | WorkoutSummaryRenderTest.upNextTomorrowCopy | V6 |
| A-F5-3 | No plan reads 'Up next: plan your week' | WorkoutSummaryRenderTest.noPlanSaysPlanYourWeek | V6 |
| A-F5-4 | Tapping Up next opens Plan with that day in view | WorkoutSummaryRenderTest.tapOpensPlanOnThatDay | V6 |
| A-F6-1 | Body shows bodyweight trend | BodyTrendsRenderTest.bodyweightTrendShowsEntries | V7 |
| A-F6-2 | Body shows records (top 5, expandable) | BodyTrendsRenderTest.recordsCardListsTopFive | V7 |
| A-F6-3 | Weekly volume per muscle counts working sets only | BodyTrendsRenderTest.weeklyVolumeCountsWorkingSetsOnly + WeeklyVolumeQueryTest | V7 |
| A-F6-4 | Body has no session list | BodyTrendsRenderTest.bodyHasNoSessionList | V7 |
| A-F6-5 | History has no records list; PR count links to Body | HistoryRenderTest.historyHasNoRecordsList + prCountLinksToBody | V7 |
| A-F6-6 | No Room schema change | git diff --exit-code app/schemas | V7 |
| A-INV-1 | Metrics.commit is 72dp and Metrics.logFloorCommit 64dp | grep ui/theme/Metrics.kt + DockCommitRenderTest | all |
| A-INV-2 | Checker baselines do not rise | tools/preflight.sh static output vs tools/checker-baselines.toml | all |
| A-INV-3 | Both required checks green; no new smoke failures vs latest trunk | gh pr checks + smoke FAILED line diff | all |

## 9. Traceability

| Change | Sections | Packet | Acceptance |
|---|---|---|---|
| F1 One Start path | 3.1, 3.2, 4.1, 4.4, 5.1, 6.1 | V2 | A-F1-1..7 |
| F2 Plan week-only, Routines screen | 3.3, 4.2, 4.3 | V3 | A-F2-1..5 |
| F3 Inline rest | 3.4, 4.5, 5.2, 6.2, 6.3 | V4 | A-F3-1..8 |
| F4 Session edit mode | 1.2, 3.5, 4.6, 5.3, 6.4 | V5 | A-F4-1..6 |
| F5 Summary Up next | 3.6, 4.7 | V6 | A-F5-1..4 |
| F6 History vs Body | 3.7, 4.8, 4.9, 6.5 | V7 | A-F6-1..6 |
| R6 minor: Pause debounce 300ms | 4.10 | V1b | A-V1-7 |
| R6 minor: Resume to reach one minute | 4.10 | V1b | A-V1-8 |
| R6 minor: Paused label on live bar | 4.11 | V1b | A-V1-10 |
| R6 minor: per-row Plan add | 4.2 | V1a | A-V1-5 |
| R6 minor: PlanWeekRows tests (spoken rest, suggestFills, 56/72, min-height vs 4.2) | 2, 4.2 | V1a | A-V1-1..4 |
| R6 minor: TargetsSheet real dismiss | 4.12 | V1b | A-V1-11 |
| R6 minor: strings Suggested, today, Open this day | 4.2 | V1a | A-V1-6 |
| R6 minor: Leave running wording while paused | 4.10 | V1b | A-V1-9 |

## 10. Risks

1. V4 rest is the riskiest: it touches the largest screen file (ActiveWorkoutScreen.kt) and the rest timer has outside surfaces (service, overlay, lock widget, notification). CI cannot prove lock screen or overlay behaviour on a real phone; any claim of physical acceptance needs Allen on a device.
2. V2 removes two sheets with heavy test coverage (HomeStartSheetTest, StartOptions* tests, ConnectedWorkoutJourneyTest). Migrate tests in the same PR; do not delete a test without a replacement assertion.
3. V3 breaks 5 instrumented tests that assume routines live on Plan; they run in the non-blocking smoke job, so a regression could hide. The smoke diff rule (no new FAILED line) is the guard.
4. V5 edit: SessionDetailScreen's retained edit state (beginSetEdit, releaseSetSave, closeHistoricalEditor) is intricate; inline steppers must reuse it, not fork it.
5. V7 weekly volume query cost on long histories; no new index allowed without a Room decision.
6. Walkthrough harness (UxWalkthroughTest) is edited by V3 and V4 in the same step; keep edits in separate functions and rebase.
7. Copy marked ASSUMPTION ("Leave paused", "Rest done", "Up next: tomorrow") needs Allen's eye before V1b and V6 merge.

## 11. Open decisions for Allen (summary)

R1 (no Room change for v4, confirm), D1, D2, D3, D4, D5, D6, D7, D8, D9, D10, D11, D12, D13 (no "Edited" marker, since it needs a Room column), D14, D15 (Today one tap vs chooser), D16. Defaults are in the tables; packets proceed on defaults unless Allen says otherwise.
