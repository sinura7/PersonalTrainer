# The master action plan — 20 August 2026

The single end-to-end plan for turning PersonalTrainer into the self-training super-app:
every finding, every settled decision, the complete build sequence, and the full work
breakdown per phase. This document is the **reading layer**; the eleven packets in this
directory are the **execution layer** — each phase is executed by a fresh Claude Opus
session from [PROTOCOL.md](PROTOCOL.md) plus that phase's packet, and where this document
and a packet disagree on implementation detail, the packet wins.

Lineage, so nothing here floats free of evidence:

1. **The audit** (ten agents over the full checkout) mapped what exists against the
   owner's five-tab proposal → [HIERARCHY_PLAN.md](../HIERARCHY_PLAN.md) §1–§3.
2. **The adversarial critique** (five hostile attack agents) broke the audit's own plan:
   62 findings, 8 fatal → [REVISED_STRUCTURE.md](REVISED_STRUCTURE.md).
3. **The packets** (nine writer + nine verifier agents) rebuilt it as executable specs,
   every file:line claim re-verified against commit `2212628`.

Interactive version:
**https://claude.ai/code/artifact/0dbf304d-4761-4516-865e-af569f008099**

---

## 1. Where the app stands

Local-first Kotlin/Compose/Room strength tracker, single developer, single real user, one
phone whose training history cannot be re-created. The five-tab bar the owner proposed
(Home, Body, Routines, Library, History) **already ships verbatim**. Already built and
hardened: a transactional single-in-progress workout loop with drafts that survive
process death; a front/back body silhouette with tap-to-inspect muscle heat and a
five-rule recommendation engine; a weekly auto-planner; routines with a write-through
editor; a 37-exercise text-only catalog; history with a month calendar; validated JSON
backup with Drive sync; a fully tokenized dark design language ("Instrument") enforced by
eight static check scripts; 188 JVM domain tests.

The load-bearing weaknesses the plan exists to fix:

- **Sessions can strand**: a zero-set session can never be finished; an abandoned session
  blocks all new starts forever, never reaches History, and books wall-clock as duration.
- **The log is uncorrectable**: no edit, no delete, no repeat-last-session.
- **Nobody owns the week**: the plan is recomputed on every data change and silently
  reshuffles; users cannot pin "Push on Mondays"; the Schedule screen is orphaned behind
  an unlabeled tap.
- **The heat map lies at the margins**: heat is normalized to the window's own hottest
  muscle, compound lifts credit essentially one muscle, and the coach changes its advice
  when a display chip flips.
- **The catalog cannot grow**: seeding runs only on an empty table, so new exercises
  never reach the existing install; no equipment, no variations, no images.
- **Nothing verifies automatically**: CI has never executed once (account billing block);
  Android Studio on the owner's machine is the only compiler this code has ever seen.

## 2. What we are building

The end state, phrased against the owner's original asks:

- **Home** answers "what do I do right now": a masthead that names the day
  ("PUSH DAY · 4 LIFTS"), one hero that starts today's session in one tap, the week strip,
  one next-session module, a calendar jump — while a persistent **LiveSessionBar** owns
  the active workout on every screen so a session can never be lost or duplicated.
- **Body** is the single record of what training has done: the silhouette on honest
  absolute volume bands (weighted weekly sets, not relative heat), the training calendar,
  the month-grouped session log, PRs, and the coach's explanation cards — one scrolling
  surface.
- **Plan** (Routines, renamed) is where the week is owned: pin routines to days, rotation
  that shifts instead of skips when life eats a Monday, planner proposals you explicitly
  accept, and the routine list beneath.
- **Library** grows to 98 curated movements with equipment variations as first-class
  rows grouped into movement families, equipment filters, alias search, per-class
  defaults, and Compose-drawn thumbnails on every row.
- **History** lives inside Body, and the log becomes correctable: edit sets, delete
  sessions, repeat last session, undo set deletion.
- **The coach** stays a deterministic, offline rule engine — made honest (absolute bands,
  fixed 14-day basis), specific (names lifts you own), symmetric (can say "rest" and
  "deload"), and personal (goal + available equipment inputs) — surfaced where action
  happens: the start sheet and the in-workout add sheet.

## 3. The decision ledger

Settled across Phases 0–8. Phase 0 records D1–D5 in ROADMAP.md for the owner's
signature; the rest are fixed in the packets. **None may be reopened by an executor.**

**D1 — Information architecture.** Recommended: three tabs — **Home · Body · Plan** —
plus the LiveSessionBar. Body absorbs History's calendar and log; Library becomes a
pushed screen fed from Plan, recommendation cards, and the muscle sheet. Fallback
(owner's call): four tabs keeping History; every affected packet carries the fallback
branch. Keep-five is not offered — leaving the contradiction open means doing the nav
work twice.

**D2 — Schedule semantics.** The week is an ordered cycle of `schedule_slots`
(position, routineId?/focusKind?, optional weekday anchor). The *effective* week is a
pure derivation over (slots, completion history, today): anchors are preferences, a
missed anchored day **shifts forward, never skips**; satisfaction resets at week
rollover; regeneration proposes fills for empty days only and **nothing persists without
explicit Accept**; stored slots are never rewritten by derivation; routine deletion
cascades its slots and the derived week heals. Five worked examples ship for the owner's
signature (SCHEDULE_SEMANTICS.md) — Phase 3 derives the DDL from the signed spec.

**D3 — Recommendation surfaces.** Exactly four, nowhere else: Home = ONE next-session
module; Body = full explanation cards; the start-options sheet and the in-workout
add-exercise sheet = one pinned suggestion each.

**D4 — The cut list.** LLM/chat coach (the rule engine is the coach; offline-first
stands); muscle-head-level granularity (weighted primary/secondary credit is the honest
ceiling of set-log data); day and year heat windows; the per-routine equipment override
(a variant is its own catalog row); the FK re-pointing merge tool (collisions are
rename-or-keep-both — history FKs are never rewritten); the $1.5–4k line-art commission
(imagery is Compose-drawn; the commission survives only as a non-committal appendix);
A1-as-a-phase (opportunistic 2-day timebox, gates nothing); the rest overlay bubble.

**D5 — Branch ground truth.** `main` holds only the initial commit; everything lives on
`claude/app-hierarchy-navigation-cjzigo`. Phase 0 merges it to `main` (recommended) or
records branch-as-trunk; thereafter branch-per-phase, PR-per-phase, owner merges.

**Engineering decisions fixed by the critique** (details in REVISED_STRUCTURE.md):

- **One-live-affordance rule**: while a session is in progress, the LiveSessionBar is
  the ONLY live-session affordance anywhere, counting docked chrome. Home's hero never
  says Resume; the rest strip dies.
- **Finish-side invariant**: every finish/discard routes through the shared
  `FinishWorkout`/`DiscardWorkout` use cases (zero-set guard, rest-timer stop, draft
  clear, one-shot navigation) — after Phase 1a the repository methods have exactly one
  caller each, grep-enforced.
- **Nothing exists outside the exported Room schema JSON**: every table is an `@Entity`,
  every index declared — a raw-SQL index would crash-loop Room's open-time validation on
  a phone with no destructive fallback. Uniqueness of built-in names is a `nameKey`
  column + seed-invariant test + app-layer checks, never a DB UNIQUE constraint.
- **Backup co-evolves with schema, in the same phase, behind the same gate**: format v2
  carries every new table and field, decodes v1 files with derivation and defaults, and
  every restore ends with an idempotent catalog-reconciliation pass. `seed_meta` is
  never trusted across a restore.
- **The pre-open raw file copy is the only rollback**: `personal_trainer.db` + wal/shm
  copied in `Application.onCreate` before Room ever opens at v2 — v1 code refuses v2
  JSON and Room refuses downgrades, so this copy plus a fresh JSON export is the entire
  rollback story.
- **One DB-maintenance mutex** serializes seed, restore, and reconciliation; the seeder
  inserts built-ins unconditionally and never resolves name collisions.
- **Muscle resolution contract**: junction keys resolve through the alias index to a
  canonical muscle, unknown keys fall to the parent group (never OTHER);
  `muscleGroup` survives as display text; the heat calculator resolves catalog-first.
- **Band model**: a set credits each muscle by its junction weight; weekly weighted sets
  band at <4 untrained / 4–9 low / 10–20 productive / >20 high; the 30-day window shows
  the per-week average; the coach computes on a fixed trailing 14 days.
- **Increment table**: progression step comes from (loadType, display unit) — kg → 2.5 kg,
  lbs → 5 lb exactly (killing the "+5.5 lbs" defect); BODYWEIGHT lifts get rep-progression
  copy, never "+2.5 kg". One table feeds the calculator, the coach copy, and the stepper.

## 4. The build sequence and why

Value-first, with the risky substrate in the middle and content last. If the plan stops
halfway, the owner has session hygiene and a pinned week — not substrate and a picture
book.

```
0 Decisions ──► 1a Lifecycle ──► 1b Log repair ──► 2 Test substrate ──► 3 Schema v2
   (signs D1/D2)   (schema-free)     (schema-free)      (harness)          (the migration)
                                                                              │
   8 Imagery ◄── 7 Catalog ◄── 6b Home ◄── 6a Tabs+Body ◄── 5 Heat/Coach ◄── 4 Plan tab
   (presentation)  (content)     (rework)     (IA landing)     (honesty)       (pinned week)
```

The dependencies that force this order: 1a/1b are schema-free daily-pain fixes and need
nothing — they go first. Phase 3's migration gate is a MigrationTestHelper suite, which
needs Phase 2's Robolectric lane to exist. Phase 4 needs `schedule_slots` (3) and the
signed semantics (0). Phase 5 needs junction weights (3). Phase 6a builds the merged Body
on honest windows (5) and a Plan tab that exists (4); 6b reworks Home around the
persisted week (4), the coach module (5), and the merged Body anchor (6a). Phase 7's
catalog rides the versioned seeder (3) and the navigation end-state (6); Phase 8 draws on
equipment + junction data (3, 7) and is pure presentation.

**Totals** (from the packets): **~30–43 executor-days**, **~7–12 owner-days** — the
owner-days are real evenings: two signatures, per-phase PR reviews, and one device
checklist per landing. The A1 DI refactor is deliberately not on this path.

## 5. Phase-by-phase action plan

Each subsection: what gets built (the complete work breakdown, condensed to the
load-bearing detail), how it is verified, the owner's part, and what can go wrong.
Full literal specs — signatures, SQL, string tables, test names — live in the packet.

---

### Phase 0 — Decisions & doctrine · [packet](PHASE_0_DECISIONS.md) · 0.5–1 exec-days, 0.5–1 owner-days

**Docs only; the plan's single blocking checkpoint.** Nothing later executes until D1
and D2 carry the owner's initials.

Work breakdown:
1. Commit `docs/gameplan/` (PROTOCOL.md + packets) to the repo.
2. **ROADMAP.md restructure**: mark historical Phase 4 superseded (split value-first
   across the game plan); insert the game-plan phase table; re-point every
   known-open-items row to its owning game-plan phase; append the **Decisions** section
   skeleton (D1–D5 with Signed lines).
3. **DESIGN_AUDIT.md repairs**: close NAV-01 with the recorded IA decision; supersede
   the §7 per-routine equipment-override row; resolve the rest-overlay contradiction in
   ROADMAP's favor across all five places it appears (R-05, T-07, N-03, §10.2 banner,
   §17 acceptance line); append three §15 non-goals (LLM coach, head-level granularity,
   day/year windows).
4. **D1 text**: the three-tab recommendation and four-tab fallback written so the owner
   circles one and signs.
5. **D2 spec**: `SCHEDULE_SEMANTICS.md` — the slot model, eight derivation rules, and
   five worked examples over a named week (missed anchored day, missed unanchored day,
   week rollover, regeneration, routine deletion). One deliberate open question for the
   owner: rule 6 (no week carry-over) can be struck without changing the DDL.
6. **D3/D4/D5 texts** into the Decisions section; correct DEVELOPMENT.md's false
   trunk-based claim; resolve branch ground truth (merge to `main` recommended).

Verification: docs-only diff (`git diff --stat -- app/ tools/` empty); a battery of
literal greps (Decisions section present, ≥3 Signed lines, supersession banners in all
three files). No code, no tests — stated in the PR rather than faked.

Owner: read D1 and circle an option; read the five worked examples and sign D2 (decide
rule 6); skim D3/D4; choose D5; merge. **This is the plan's only blocking day.**

Risk: signing D2 casually. The schedule DDL is derived from it and frozen into the
migration — Phase 0 is where changing your mind is free.

---

### Phase 1a — Session lifecycle · [packet](PHASE_1A_SESSION_LIFECYCLE.md) · 3–4 exec-days, 0.5–1 owner-days

**Kill session limbo. Schema-free** (new `@Query` only — hard constraint).

Work breakdown:
1. **`FinishWorkout` / `DiscardWorkout` use cases** (land first, before any UI): extract
   the invariants currently private to `ActiveWorkoutViewModel` — zero-set guard,
   `restTimer.stop()`, draft-cache clearing, idempotent finish, sealed outcomes
   (`Finished/NothingLogged/SessionMissing/Failed`; `Discarded/Failed`). Registered in
   `AppContainer`; the ViewModel becomes a thin dispatcher preserving its exact error
   strings and one-shot exit events. **Finish-side invariant enforced by gate grep:**
   `finishSession`/`discardSession` each end the phase with exactly one caller.
2. **`observeSessionActivity` query**: total sets, working sets, `MAX(completedAt)` per
   session — the bar's data plus the staleness clock.
3. **The LiveSessionBar**: hosted in AppNav's scaffold above the tab bar; visible on
   every route except ActiveWorkout / Summary / StartWorkout; own nav-bar insets when
   the tab bar is hidden; 56dp row — volt rail, "In progress" kicker, session title,
   ticking elapsed clock, rest countdown in cyan while running, working-set cluster,
   overflow with **Finish** (only when ≥1 set) and **Discard…** (always, guarded with
   the existing dialog copy). Finish navigates to the summary exactly as the workout
   screen does; discard navigates nowhere.
4. **Stale-session nudge**: last activity = latest set `completedAt` (fallback
   `startedAt`); ≥4 h flips the bar kicker to "Left open · Nh" in amber. Evaluated
   in-app only — no WorkManager, no alarms. Zero-set stale sessions are discard-only.
5. **Home cleanup**: `RestRemainingStrip` deleted outright; the hero permanently stops
   relabeling to Resume (in-progress taps route to the StartWorkout screen's existing
   resume state). `ScheduleScreen.kt` is explicitly untouched (Phase 4 owns it).
6. **`LiveSessionRules`** pure domain object (staleness math, elapsed formatting) with
   its JVM test class.

Verification: the eight static checks + domain suite; four invariant greps with literal
expected output (single-caller rules, `RestRemainingStrip` gone). Gate wording, verbatim:
**"exactly one live-session affordance visible anywhere, counting docked chrome."**

Owner: 11-step device pass — bar on every tab and pushed screen, correct insets, resume/
finish/discard from the bar, zero-set discard-only, process-death survival, the 5-hour
clock trick for staleness.

Risk: a bar action bypassing the use cases would leave a live rest-timer notification
for a deleted session — the exact bug class this phase exists to kill; the grep gate is
the fence.

---

### Phase 1b — Log repair · [packet](PHASE_1B_LOG_REPAIR.md) · 2–3 exec-days, 0.5–1 owner-days

**Make the log correctable and reusable. Schema-free.** Honors ROADMAP's own decision
that editing history "deserves its own change."

Work breakdown:
1. **Editable finished sessions** — guard-by-guard disposition in `WorkoutRepository`
   (each line named in the packet): `updateSet`'s finished-guard **relaxed** (it already
   preserves `completedAt` and `setNumber`); `logSet`/notes/exercise-add guards **kept**;
   post-finish adds go through a new `addSetToFinishedSession` that timestamps inside
   `[startedAt, finishedAt]` via pure `FinishedSessionEdits` (monotone, total on
   degenerate windows) — no PR detection, no rest timer. Editable per set: weight, reps,
   RPE, warm-up flag, add/delete. Never editable: dates, timestamps, `durationMinutes`
   (its only computed writer stays `finishSession`, grep-enforced), notes, the exercise
   list. **Domain tests pin the two subtle invariants**: an added set heats the original
   training day, not today; an edited old set that becomes a PR carries the old date.
2. **Session delete** — new `deleteFinishedSession` (checked: finished only; FK CASCADE
   clears children), deliberately NOT `discardSession` (the 1a invariant grep still
   holds). SessionDetail overflow → two-step confirm naming the set count.
3. **Repeat-last-session** — pure `RepeatSessionPlan` (exercises + order from the source;
   target sets = what you actually did; target reps = last working set; weight always
   null — progression owns suggestions; logged sets never copied), then
   `repeatSession(sourceId)` through the single-in-progress transaction returning
   `Started/Blocked/Failed` — **never silently returns the existing session**. Blocked
   UX reuses the "Session in progress" resume dialog. Entry points: SessionDetail
   overflow + History row overflow.
4. **Delete-set undo** — `deleteSet` returns the removed row; `restoreSet` re-inserts
   with original id/timestamp and renumbers; the confirm dialog is deleted in favor of
   immediate delete + snackbar Undo, in the active workout AND session detail. The
   was-latest rest-timer stop behavior is preserved; undo does not restart the timer.
5. **`SetEditSheet`** — modal sheet with the standard entry panel, RPE chips, warm-up
   toggle, Save, and a Danger delete text action.

Verification: full check suite + three new domain test classes
(`FinishedSessionEditsTest`, `EditedSessionAttributionTest`, `RepeatSessionPlanTest`);
four invariant greps (durationMinutes writers, discard separation, dialog gone,
completedAt write sites).

Owner: 10-step pass — edit an old set and watch this week's heat NOT light up; PR
chronology stays on the old date; repeat copies structure but zero sets; backup
round-trips edited history.

---

### Phase 2 — Test substrate · [packet](PHASE_2_TEST_SUBSTRATE.md) · 1–2 exec-days, 0.5 owner-days

**Build the lanes Phase 3's migration suite will run in. Zero `app/src/main` changes.**

Work breakdown:
1. **Gradle wiring**: version-catalog entries (Robolectric 4.14.1, androidx.test,
   room-testing riding Room 2.6.1); `testInstrumentationRunner`; test source set gets
   the schema assets + `isIncludeAndroidResources`.
2. **Robolectric JVM lane** (primary): `SchemaV1BaselineTest` — MigrationTestHelper
   opens the committed v1 schema JSON and asserts all six tables. Lives OUTSIDE
   `domain/` so the jar-based domain lane never sees Android imports. The honesty
   statement ships in its KDoc: Robolectric's SQLite is not the phone's — green here is
   necessary, never sufficient.
3. **androidTest scaffold** (truth lane): `InstrumentationSmokeTest` +
   `SchemaV1BaselineDeviceTest`; a verbatim runbook in DEVELOPMENT.md —
   `connectedDebugAndroidTest` against an **emulator, never the phone** (the debug test
   APK shares the release applicationId; uninstalling the real app would delete the
   owner's history).
4. **`tools/preflight.sh`** — the mechanical half of every later phase's definition of
   done: jar bootstrap (Gradle module cache → wrapper dists → distribution lib/, Kotlin
   jars pinned to 2.x), all eight static checks with per-check pass criteria (four judged
   by exit code, three by summary line, syntax-check by its output), then the domain
   tests, honest Gradle fallback, loud failure when no lane exists. The exact script was
   executed in an SDK-less clone before being committed to the packet.
5. **ci.yml**: one non-blocking `instrumented-smoke` emulator job, written blind by
   design (CI still can't run — the billing fix is a standing owner errand that gates
   nothing); YAML validity is the in-phase gate.

Owner: one evening — Gradle sync, `testDebugUnitTest` (first run downloads Robolectric's
android-all jar), an emulator `connectedDebugAndroidTest` (2 tests), `preflight.sh`,
paste outputs.

---

### Phase 3 — Schema v2 migration · [packet](PHASE_3_SCHEMA_V2.md) · 3–5 exec-days, 1–2 owner-days

**The highest-risk phase: the app's first-ever migration, run exactly once against the
only real dataset.** Everything downstream waits on these tables.

Work breakdown:
1. **v2 entities + `MIGRATION_1_2`** — five new `exercises` columns (`equipment`,
   `loadType`, `movementKey`, `imageKey`, `nameKey` + plain declared index; annotation
   defaults and migration SQL must agree byte-for-byte with the generated `2.json`, the
   packet's S1 rule); three new `@Entity` tables — `exercise_muscles(exerciseId,
   muscleKey, weight)` PK-composite FK-CASCADE, `seed_meta(catalogVersion,
   pendingCollisions)`, `schedule_slots` with the D2-signed DDL. The migration performs
   **no data transform** beyond the `nameKey` backfill — junction rows, catalog upgrades
   and collision detection run in Kotlin at first startup, behind the mutex, where a
   failure is a logged no-op instead of a bricked open. Four migration tests
   (empty, populated-with-history, FK integrity, index parity with the declared schema).
2. **Pre-open safety net** — `PreMigrationSnapshot.ensure(context)` as the first
   statement of `Application.onCreate`: raw copy of the DB + wal/shm before any
   container/DB touch, gated on a schema marker, exactly one copy kept forever, copy
   failure retries next launch and never blocks the app. **This is the only rollback.**
3. **Versioned seeding** — `CATALOG_VERSION = 2`; upsert-by-id (update built-ins in
   place preserving notes; insert new; never delete; never re-slug — the 37 v1 ids are
   frozen and pinned by test); built-in junction rows replaced wholesale per pass;
   collisions inserted-and-flagged into `seed_meta.pendingCollisions` (UI is Phase 7);
   all of seed/restore/reconcile behind one mutex with the version check inside the
   lock. `createCustom`/`updateCustom` gain the duplicate-name check surfaced through
   existing error channels.
4. **Batch-1 catalog: the 37, upgraded in place** — the packet carries the complete
   normative data table (id, equipment, loadType, movementKey, primary at 1.0, weighted
   secondaries — e.g. deadlift: glutes 1.0 / hamstrings 0.5 / back 0.5; bench: chest
   1.0 / triceps 0.5 / shoulders 0.25) to be transcribed, not invented. A generated
   family-grouped **review artifact** diffs v2 against v1 for the owner's sign-off;
   seven invariant tests (weights, primaries, unique nameKeys, frozen slugs, canonical
   keys, closed movement vocabulary, golden-file artifact parity).
5. **Backup format v2** — document gains the new exercise fields, `exerciseMuscles`,
   `scheduleSlots`; validator rules for every new shape (enum membership, weight
   bounds, anchorDay range, dangling ids, slot with neither routine nor focus);
   `decode` accepts v1 and returns a fully-populated v2 document (junction rows derived
   from `muscleGroup` for v1 files); `replaceWith` delete/insert order includes the new
   tables and resets `seed_meta`; **every restore ends with the idempotent
   reconciliation pass inside the maintenance lock**; `hasLocalData` counts schedule
   slots. Round-trip tests: v2→v2 lossless; v1-file→v2 rebuilds the catalog.
6. **Heat switches to junction credits** — resolution order: catalog junction →
   embedded junction → embedded muscleGroup derivation → catalog derivation → OTHER;
   window-max normalization deliberately untouched (bands are Phase 5). A named test
   fixes the exact before/after per-muscle numbers for a known history — the executable
   half of the owner's heat-diff review.
7. **The rehearsal runbook, verbatim** — the phone's DB cannot be pulled (release-signed,
   non-debuggable), so: owner's SAF JSON export → v1 APK on an emulator (tagged
   merge-base) → restore → install v2 over it → verify counts, a known lift, a PR, the
   heat picture → export (v2) → restore the original v1 file onto the v2 install →
   verify again. Then the real-phone upgrade: fresh JSON export off the phone first,
   sideload over the existing install, re-verify, one full real workout before the PR
   closes.

Verification: preflight + `testDebugUnitTest` green; `2.json` committed and identical to
CI's artifact; ~45 named tests across migration/safety-net/seed/backup/heat; the
owner-side gate (rehearsal + catalog review + heat-diff judgment + real-phone upgrade).

Risks: this PR is the one where rubber-stamping can cost history — the owner reviews the
migration and `replaceWith` ordering line-by-line; the long pole is making the entity
annotations, migration SQL, and generated schema agree byte-for-byte.

---

### Phase 4 — Plan tab & the pinned week · [packet](PHASE_4_PLAN_TAB.md) · 4–6 exec-days, 0.5–1 owner-days

**Give the week an owner.** The proposal's best idea, built domain-first.

Work breakdown:
1. **`WeekDerivation` engine (pure Kotlin, FIRST)** — implements the signed D2 rules:
   satisfaction by matching (chronological sessions each satisfy at most one slot, by
   routineId or focus compatibility), placement in numbered rule order (satisfied days
   display on their session's day; anchored-and-reachable on the anchor; missed anchors
   shift forward; unanchored even-spread; never place before today). The five signed
   worked examples become five named domain tests, plus five satisfaction-rule tests.
   Planner demoted: `plan()` gains `pinnedSlots`, proposes fills for open days ≥ today
   only; `arrangeKinds` gets the greedy same-family-adjacency fix; both recorded planner
   defects (past-day assignment, adjacency) close here with tests.
2. **`ScheduleRepository` + the sixth insights source** — slot CRUD over Phase 3's DAO
   (pin appends position; acceptFills is one transaction); joins `TrainingInsightsSource`
   via a wrapping combine; `insights.weekPlan` is rebuilt from the derived week — **pinned
   truth only, no invented ghost week** — so Home, Plan, and the hero read the same
   object from the first day pins exist. Stated owner-visible consequence: until you pin
   or accept fills, the week is honestly empty.
3. **`StartTrainingDay` contract** — pure `decideStart` decision core: a pinned day whose
   routine is deleted/emptied returns an explicit `Failed` with named copy (never a
   silent free workout); an in-progress session returns `Blocked` (never a silent resume)
   — callers show a shared resume-or-discard dialog routed through the Phase-1a use
   cases. The `when`-exhaustiveness checker proves no caller misses the new outcome.
4. **The Plan tab, inside the five-tab bar** — Routines renamed Plan (route string
   deliberately unchanged); layout: header (Plan · Tune · New · Settings gear — the
   named second Settings home) → 7-cell week strip (today's volt marker, day letter,
   date, name, logged tick) → suggestion flow ("Suggest a week" → inline proposals →
   explicit Accept/Dismiss) → routines list verbatim. All pin management lives in the
   day sheet (tap a cell): Start / Swap routine / Unpin on pinned days; Pin-a-routine /
   Pin-a-focus on open days; past days informational.
5. **Demolition, once** — pushed ScheduleScreen deleted; `ThisWeekHomeCard` extracted to
   `ui/home/ThisWeekCard.kt` (gains the empty-week state); Home's hero-card body tap →
   Plan tab; Settings' "This week's plan" row removed; every AppNav change enumerated;
   five proof greps must return nothing.

Verification: preflight; 20 named domain tests; check-when-exhaustive/check-screen-wiring
cited; the five demolition greps.

Owner: 12-step pass — pin, suggest+accept, force-stop and confirm nothing reshuffles,
delete a pinned routine and watch the week heal, dead-pin error copy, blocked-start
dialog both paths, backup round-trip of pins.

Risk: satisfaction/placement edge cases — that is why the engine is pure and the signed
examples are the tests.

---

### Phase 5 — Honest heat & coach · [packet](PHASE_5_HEAT_COACH.md) · 4–5 exec-days, 0.5–1 owner-days

**Stop the map lying; make the coach worth obeying.**

Work breakdown:
1. **Band model** — weighted weekly sets per muscle (a set credits each muscle by its
   junction weight); bands <4 / 4–10 / 10–20 / >20; `heat` stays a 0..1 fraction so the
   ramp and every consumer survive, but becomes an absolute piecewise-linear function
   with literal anchors (LOW sweeps the ramp, PRODUCTIVE sits flat at Heat3, HIGH ramps
   to Heat4); `normalizeHeat` and `HeatBand.fromHeat` deleted.
2. **Window surgery** — `HeatWindow` becomes CURRENT_WEEK ("THIS WEEK") +
   LAST_30_DAYS ("30 DAYS", per-week average ×7/30); 7D/14D deleted with the complete
   break list enumerated (screen labels, source default, Home fallback copy, six test
   files retargeted); the window choice becomes a persisted preference with tolerant
   decode. Zero-hit grep for the deleted members + when-exhaustive are the proof.
3. **Coach decoupling** — recommendations compute from a fixed trailing-14-day
   `CoachBasis` built in the same compute chain; a basis failure degrades to the
   recommendations flag, never blanks the map. Named test: identical history ⇒
   byte-identical advice under both display windows.
4. **Rule upgrades** — imbalance on weighted weekly sets (tonnage constant deleted);
   RPE finally read (top-set RPE ≥9 across the last two sessions converts an INCREASE
   into an explicit HOLD with its own strip copy, and drops the lift from
   ready-to-progress); two symmetric do-less rules — **Rest** (everything at productive
   volume → "nothing needs adding") and **Deload** (three weeks rising volume + flat
   top-lift e1RMs → "schedule a lighter week"); **named owned lifts** (B-03: every
   muscle card resolves one lift from your routines/recent history, filtered by
   available equipment, deep-linking its detail page); goal + available-equipment
   preferences (Settings "Coaching" section) modulate rank.
5. **Voice spec** — the full literal copy-template table (kicker/title/reason per card
   type); no praise, no first person, no exclamation marks, never quotes the display
   window; enforced by a test that drives every card type and greps the output.
6. **Surfaces + mid-workout swap/remove** — one pinned SUGGESTED row in the start
   screen (marked for its 6b move) and one in the add-exercise sheet; the never-called
   `removeExerciseFromSession` finally gets its UI: current-lift overflow with Swap/
   Remove, only while the lift has zero logged sets, guards extracted pure and tested.

Verification: preflight; ~14 new/rewritten domain test classes; three literal greps
(deleted windows, deleted constants, swap-remove now called).

Owner: 13-step pass — two chips only; untrained muscles dark early in the week; advice
invariant under chip flips; the band-truthfulness judgment call ("does Productive match
how I train?") which may trigger one threshold-tuning round.

---

### Phase 6a — Tab consolidation + Body absorbs History · [packet](PHASE_6A_TAB_CONSOLIDATION.md) · 4–5 exec-days, 1–1.5 owner-days

**The IA landing. Size L, honestly.** Safe now because the bar owns resume (1a), Plan
owns the week (4), honest heat owns the windows (5).

Work breakdown:
1. **The tab bar** — PRIMARY: Home · Body · Plan; `Route.History` deleted; tab matching
   becomes plain pattern equality (the `isTabRoute` query-param shim and the
   `restoreState=false` Library hacks die). FALLBACK (if D1 chose four tabs): History
   keeps its tab; the merge work is dropped but month grouping, the multi-session-day
   sheet, and the PR row land on History in place — the packet forbids blending
   branches.
2. **The merged Body screen** — ONE LazyColumn, never nested scrolling: window picker →
   silhouette → per-muscle rows → month calendar (moved file) → coach cards →
   month-grouped session list with sticky month headers → PR summary. Grouping and the
   PR summary are new pure domain code (`groupSessionsByMonth`, `prSummary` — e1RM-first
   with weight fallback, one row per exercise, recency-ordered). The content list is
   1:1 with items so 6b's calendar anchor is a true scroll index (comment records it).
   Multi-session days open a chooser sheet instead of silently taking the first session.
3. **Library becomes pushed-only** — back-arrow header; three remaining entry points
   (Plan header action, recommendation cards, muscle sheet).
4. **The nine-site retarget table** — every cross-tab navigation site enumerated with
   file:line and required end state (Home's Recent header → Body; OPEN_ROUTINES →
   Plan; Library pushes; History empty-state start → the merged session section; the
   Home-stays-start-destination constraint; the notification deep link re-proven on
   device). The PR reproduces the table with every row checked.

Verification: preflight; `SessionMonthGroupingTest` + `PrSummaryTest`; three
zero-output greps (isTabRoute, restoreState=false, HistoryScreen); when-exhaustive
proves no `Route.History` survivor.

Owner: 10-step pass — one continuous scroll, sticky month headers, calendar day taps,
multi-session sheet, deep-link consume-once retest, don't-keep-activities restore.

---

### Phase 6b — Home "Today" rework · [packet](PHASE_6B_HOME_TODAY.md) · 2–3 exec-days, 0.5–1 owner-days

**Home answers "what do I do right now."** Separate landing, separate device pass.

Work breakdown:
1. **The masthead string table — all seven states, literal**: "Workout in progress" is
   deleted as a masthead state (the bar owns live; the masthead always names the day);
   `"REST DAY"`, `"RECOVERY DAY"`, `"PUSH DAY · 4 LIFTS"` (count from routines already
   in the ViewModel — dropped, never guessed, when unresolvable), `"READY TO TRAIN"`,
   `"TRAINED TODAY"`. Pure `MastheadCopy` function, one test per state.
2. **Week strip** — the SAME composable as Plan's, extracted shared, reading the
   persisted week; any tap → Plan.
3. **ONE next-session module** — focus + up to three named lifts + one reason line
   (engine's top recommendation when it names this session, else the day's reason);
   replaces every other recommendation slot on Home.
4. **Calendar jump** — `Route.Progress` gains an optional `?section=` pattern; the chip
   navigates with `section=calendar` and Body one-shot-scrolls to the calendar index;
   plain tab taps never scroll. (Four-tab fallback: the chip is a plain History tab
   jump; no pattern change.)
5. **StartWorkout interstitial dies** — its logic becomes `StartOptionsSheet` hosted by
   Home and Body: today's slot pinned with a TODAY kicker → the Phase-5 suggestion row
   (moves in) → routines → free workout; in-progress state shows Go-to-session + guarded
   Discard through the 1a use cases. Every former call site enumerated and retargeted;
   `Route.StartWorkout` deleted.
6. **Hero final form + demolitions** — the whole card is one action (start today via
   `StartTrainingDay`; rest/no-slot days open the sheet); a separate "This week ›" row
   goes to Plan (the mis-tap trap dies); the duplicate heat card and Recent list are
   deleted (Body owns them); Ready-to-progress rows deep-link the named lift.

Verification: preflight; `MastheadCopyTest` + `NextSessionReasonTest`; three
zero-output greps (`Route.StartWorkout`, the demolished composables, "Workout in
progress").

Owner: 10-step pass — masthead states, one-tap start, sheet in both states, strip
parity with Plan, the calendar jump, `TRAINED TODAY` after finishing.

---

### Phase 7 — Catalog to ~98 + Library UX · [packet](PHASE_7_CATALOG.md) · 4–5 exec-days, ~1 owner-day staged

**Content lands last, on a finished substrate.**

Work breakdown:
1. **The tail: 61 authored rows in two seed bumps** — batch 2 (33 upper-body) and
   batch 3 (28 lower/core), each a `CATALOG_VERSION` bump with a regenerated review
   artifact; every row fully specified in the packet (id, family, equipment, loadType,
   primary, weighted secondaries, aliases, rank) for transcription. Buckets land the
   totals at 98 (Chest 12 · Back 15 · Hinge 5 · Shoulders 11 · Biceps 8 · Triceps 8 ·
   Quads 12 · Hamstrings 7 · Glutes 6 · Calves 4 · Core 10). The invariant test extends
   to all 98 and pins that no batch-1 id changed.
2. **Library & picker at scale** — family grouping by `movementKey` (expandable family
   rows with equipment badges; flat when filtered/searching); equipment chip row
   AND-combined with muscle chips; ordering (Library by curated rank; picker by
   last-logged recency then rank, via one new recency query); LIKE search escaped
   (`LikeEscaper`) and alias-aware ("ohp" finds Overhead Press, "rdl" both RDLs).
   `sortRank`/`searchTerms` are code-side catalog metadata — **no schema change in this
   phase, hard rule**.
3. **The muscle-filter contract flip, end-to-end** — the Library route param becomes the
   canonical muscle enum name; filtering is junction-based (secondary credits match
   too); the free-text `catalogLabel` hop in recommendation dispatch is deleted; every
   `Library.create` call site retargeted.
4. **Skip-and-surface collisions** — a derived "NEEDS ATTENTION" Library section for
   customs whose nameKey matches a built-in: **Rename mine** or **Keep both** — the PR
   states verbatim that the FK re-pointing merge tool is cut.
5. **Per-loadType add defaults** — the eight-row table (compound EXTERNAL 3×5·150s …
   BODYWEIGHT isolation 3×12·60s) replaces every hardcoded 3×5/90.
6. **The increment table** — one object, three readers changed together: the
   progression calculator (null step for BODYWEIGHT ⇒ rep hints), the coach copy
   (per-lift step labels; lbs users finally see "+5 lbs"), and the weight stepper.
7. **Swap equipment** — same-movement sibling swap in the routine editor (targets and
   position preserved) and pinned "Same movement" section in the Phase-5 in-workout
   swap sheet (zero-logged-sets rule unchanged).

Verification: preflight; ~15 named test classes; grep proofs (`catalogLabel` out of
dispatch, `INCREMENT_KG` gone); both review artifacts committed.

Owner: staged — skim two review artifacts at batch scrutiny, then an 11-step device
pass (family expansion, "ohp"/"rdl"/"100%" searches, chip AND-filtering,
secondary-credit filtering, class defaults, "+5 lbs", swap flows, collision row).

---

### Phase 8 — Imagery · [packet](PHASE_8_IMAGERY.md) · 2–4 exec-days, 0.5–1 owner-days

**Every exercise gets an image, at zero asset cost.** Pure presentation; last.

Work breakdown:
1. **`ExerciseThumb`** — Compose DrawScope composition only (VectorDrawable XML
   forbidden): the mini body figure (extracted shared from the Body map with a
   low-detail mode; Body tab stays pixel-identical) with the primary muscle lit in a
   **fixed** Heat token (identity, not live state; secondaries at 0.4 alpha), front/back
   view derived from the primary's hotspot side, plus one of **nine equipment glyphs**
   (each specified as unit-square stroke geometry: barbell, dumbbell, stack, cable,
   smith, kettlebell, band, bodyweight figure, other-diamond) as a corner badge.
   `imageKey == null` → composed; non-null → reserved hook, still composed for now.
2. **Four wired surfaces** — picker rows and library rows (40dp, replacing the
   initial-letter placeholder), exercise-detail header (56dp), in-workout lift chips
   (20dp glyph only — a silhouette is illegible at that size; the chip component gains
   a leading slot).
3. **A preview gallery** for the owner's aesthetic verdict (mirroring the existing
   ThemeGallery pattern) — glyph identity is settled; coordinates tune freely.
4. **APK budget measured, not asserted** — release build before/after; gate ≤2 MB,
   investigation flag at ~200 KB (nothing here should add assets at all).

Verification: preflight (the two new exhaustive `when`s are exactly what
check-when-exhaustive exists for; the thumb code contains zero raw colors for
check-design-tokens); `ExerciseThumbLogicTest`.

Owner: 10-step pass — badge legibility at arm's length, front/back correctness, Body
tab unchanged, the glyph verdict round.

**Appendix (non-committal, recorded):** commissioned line art remains a someday option;
`imageKey` is the hook; nothing schedules it and the phase ships complete without it.

---

## 6. Cross-cutting engineering rules

These hold in every phase; the packets restate them locally.

1. **Schema discipline**: Room's exported schema JSON is the arbiter — nothing exists
   outside it; no `fallbackToDestructiveMigration`, ever; schema changes only in
   Phase 3; phases 1a/1b/7 carry explicit no-schema hard constraints.
2. **Backup discipline**: any phase that adds persisted user state must carry it in the
   backup document, validate it, and round-trip-test it (Phase 3 does this for
   everything the plan adds; Phase 7's dismissal preference is the one recorded
   device-local exception).
3. **State-graph discipline**: every start routes through `StartTrainingDay`; every
   finish/discard through the Phase-1a use cases; navigation events are one-shot
   StateFlows, never captured callbacks; consume-once deep links stay consume-once
   (re-proven on device at every nav-touching phase).
4. **Design-system discipline**: tokens only (mechanically policed); the volt accent
   budget; one heat ramp, one encoding; confirm destruction, never completion; the
   one-live-affordance rule. The coach speaks in the codified voice — imperative,
   data-cited, no praise, no chat.
5. **Verification discipline**: `tools/preflight.sh` green before every push; every
   enum/callback change cites check-when-exhaustive / check-screen-wiring output in the
   PR; migration-class changes run in both lanes (Robolectric + emulator); every phase
   ends with literal-command gates, named tests, and an owner device checklist — and
   closes only on owner sign-off.
6. **Estimates discipline**: executor-days and owner-days, always split; S/M/L labels
   are banned.

## 7. Running it with Opus

One fresh session per phase, two documents per session, phases strictly in order:

> Read docs/gameplan/PROTOCOL.md and docs/gameplan/PHASE_1A_SESSION_LIFECYCLE.md in
> full, then execute the phase exactly per the protocol: verify the Phase 0 signatures
> exist in ROADMAP.md first, create the phase branch, implement the work items in order
> with preflight green on every commit, and finish with the packet's hand-back report.
> Do not reopen settled decisions; if the packet is ambiguous anywhere, stop and ask me.

Swap the packet filename per phase. The session may read the whole repo; it must not
guess past its packet. You review the PR, run the packet's numbered device checklist on
the phone, and merge; no phase starts before the previous PR merges. Every packet ends
with a hand-back format so you always know exactly what was done, what to check, and
what the next phase is.

## 8. Risk register

1. **The migration against the only real dataset** (Phase 3). Mitigations, all in the
   packet: pre-open raw copy, fresh off-phone JSON export, emulator rehearsal with the
   owner's real data, migration tests in two lanes, no data transforms inside the
   migration, line-by-line owner review of the migration and restore ordering.
2. **Content accuracy feeding the heat map silently** (Phases 3/7): ~800 authored data
   points. Mitigations: normative tables transcribed not invented, invariant tests,
   generated review artifacts, batch staging, and the owner's heat-diff sign-off.
3. **Design regression by enthusiasm** (Phases 4/6): the token checker catches raw
   values but not accent-budget or one-spine violations — those are named review items
   in every UI phase's gate.
4. **State-graph regressions** (Phases 1a/4/6): the hardening from the app's Phase 2
   history must survive verbatim; the invariant greps and the device passes
   (process death, deep links, notification resume) are the fence.
5. **Pin/planner reconciliation subtleties** (Phase 4): the reason the derivation is
   pure, the semantics are owner-signed with worked examples, and the examples are the
   tests.
6. **Verification stays manual until the owner's standing errand lands** (CI billing):
   every gate is therefore expressed as commands + named tests + device checklists that
   work without CI; the day CI turns on, both jobs run with zero further changes.
7. **Estimate risk**: phases 4 and 6a are the likely overruns (six-flow combine and the
   `when` ripple; the merged-screen construction). Both packets flag their overrun
   points and both are split-committed so partial progress is reviewable.

## 9. What "done" looks like

Three tabs and a bar. You open the app: the masthead says `PUSH DAY · 4 LIFTS`, the hero
starts it in one tap, the strip shows the week you pinned. Mid-rest, the bar carries the
clock on every screen; finishing lands on the summary and the session files itself. Body
shows honest bands over the silhouette, your calendar, your whole log grouped by month,
your records — and a coach that names the lift you actually own, holds you at RPE 9,
and tells you to rest when everything is productive. Plan holds your week and heals when
you delete a routine. The Library is ~98 movements in families with images drawn from
the app's own design system, your customs intact beside them. A typo in an old session
takes four taps to fix and cannot move your PRs off their real dates. And underneath:
two test lanes, a versioned seeder, a co-evolved backup format, one migration that was
rehearsed on your real data before it ever touched your phone, and a rollback copy that
was taken before Room opened the database.

## 10. Document map

| Document | Role |
|---|---|
| [ACTION_PLAN.md](ACTION_PLAN.md) | this file — the reading layer |
| [PROTOCOL.md](PROTOCOL.md) | binding execution protocol (branches, PRs, gates, preflight) |
| [README.md](README.md) | index + the critique-reversal summary |
| [REVISED_STRUCTURE.md](REVISED_STRUCTURE.md) | the binding brief: what the critique changed and why |
| [PHASE_0_DECISIONS.md](PHASE_0_DECISIONS.md) … [PHASE_8_IMAGERY.md](PHASE_8_IMAGERY.md) | the eleven executor packets |
| [../HIERARCHY_PLAN.md](../HIERARCHY_PLAN.md) | the audit of record (§4 superseded by this directory) |
| [../ROADMAP.md](../ROADMAP.md) | source of truth; Phase 0 records the Decisions section there |
