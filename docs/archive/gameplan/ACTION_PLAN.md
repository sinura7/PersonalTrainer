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
4. **The second pass** (six independent auditors, ~1.1 M tokens, verified against HEAD and
   against Room/Robolectric/AOSP primary sources) overturned two decisions and fixed the
   defects → [SECOND_PASS.md](SECOND_PASS.md); its seven global decisions D-A…D-G are
   restated in §3 and appended to REVISED_STRUCTURE.md.

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
  absolute volume bands (weighted weekly sets, not relative heat), PRs, and the coach's
  explanation cards — one scrolling surface. Under D1's recommended four tabs the training
  calendar and the month-grouped session log stay in **History**, one tap away and gaining
  the same month grouping, day sheet and PR row in place; under the three-tab option they
  move into Body and the two scroll anchors carry you to them.
- **Plan** (Routines, renamed) is where the week is owned: pin routines to days, rotation
  that shifts instead of skips when life eats a Monday, planner proposals you explicitly
  accept, and the routine list beneath.
- **Library** grows to 98 curated movements with equipment variations as first-class
  rows grouped into movement families, equipment filters, alias search, per-class
  defaults, and Compose-drawn thumbnails on every row.
- **History** keeps its tab (four tabs) or lives inside Body (three tabs), and either way
  the log becomes correctable: edit sets and session notes, delete sessions, repeat last
  session, undo set deletion.
- **The coach** stays a deterministic, offline rule engine — made honest (absolute bands,
  fixed 14-day basis), specific (names lifts you own), symmetric (can say "rest" and
  "deload"), and personal (goal + available equipment inputs) — surfaced where action
  happens: the start sheet and the in-workout add sheet.

## 3. The decision ledger

Settled across Phases 0–8. Phase 0 records D1–D5 in ROADMAP.md for the owner's
signature; the rest are fixed in the packets. D-A through D-G are the second-pass global
decisions (21 Aug 2026) — the full record is [SECOND_PASS.md](SECOND_PASS.md), the compact
list is REVISED_STRUCTURE.md's amendments section. **None may be reopened by an
executor.**

**D1 — Information architecture.** Recommended: **four tabs — Home · Body · Plan ·
History** — plus the LiveSessionBar. Body absorbs the silhouette, the coach cards and the
PR row; History keeps its tab and gains the month grouping, the multi-session-day sheet
and the PR summary in place; Library becomes a pushed screen fed from Plan,
recommendation cards, and the muscle sheet. **The recommendation flipped from three tabs
to four on second-pass evidence (D-C):** post-6a the session log would sit below five
sections on the merged Body while Phase 6b deletes Home's Recent list in *both* branches,
so "what did I do last session" goes from one tap today to a tab change plus a long
scroll. Four tabs keeps every real win (month grouping, day sheet, PR row, Library
demotion, `isTabRoute`/`restoreState` shim deletion) and drops only the size-L merge.
**Three tabs — Home · Body · Plan — remains a legitimate option the owner may sign**;
both branches are specced to equal depth. If three tabs is signed, two mitigations are
MANDATORY and land in the same phases, not deferred: (i) a `section=sessions` scroll
anchor alongside `section=calendar` (Phase 6a), and (ii) a single **"Last session"** link
row on Home (Phase 6b) — a link row, not a recommendation surface, so the D3 surface map
is untouched. Keep-five is not offered — leaving the contradiction open means doing the
nav work twice.

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

**Second-pass decisions** (21 Aug 2026; evidence per entry in
[SECOND_PASS.md](SECOND_PASS.md)):

**D-A — Execution order changes; phase numbers do not.** Phase numbers are identifiers,
not sequence. The order is **0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b → 7 → 8** (§4). Phase 2
ships `tools/preflight.sh` and the Robolectric lane, so running it first keeps its own
verified gate literals true, gives Phase 1's gates a working domain-test lane instead of a
command that exits 2 on a cold clone, and gives Phase 1b's repository writes a lane they
otherwise lack. Cost: session hygiene reaches the owner ~1–2 executor-days later.

**D-B — Phases 1a and 1b are one phase.** "Phase 1 — Session hygiene": one branch
`claude/phase-1-session-hygiene`, one PR, one combined owner evening. Both packets go to
the same executor session, executed in order — 1A in full with its gate green, then 1B on
top. 1B's gate greps re-assert 1A's invariants, so the sequence self-verifies. Saves one
owner evening; no safety lost.

**D-D — `movementKey` unifies on the family vocabulary** (Phase 7's). Phase 3's batch-1
catalog ships **family** keys from the start — `bench-press`, `row`, `squat`,
`romanian-deadlift` … — and nothing is re-keyed later. The 37-row batch-1 mapping is
normative in PHASE_3_SCHEMA_V2 (S6). Under the old pattern-key reading, Phase 7's own
`LibraryGroupingTest` ("the `bench-press` family has 8 members" — 4 batch-1 + 4 batch-2)
could never pass.

**D-E — `muscleKey` is `CanonicalMuscle.name.lowercase()` everywhere**: `chest, back,
shoulders, biceps, triceps, quadriceps, hamstrings, glutes, calves, core`. Phase 7's
`quads` spelling is corrected to `quadriceps` — `quads` is an alias that normalizes to
QUADRICEPS and would slip past a normalization-only check while being wrong in the column.
The shared invariant test is strengthened from "normalizes to a non-OTHER CanonicalMuscle"
to "`muscleKey == CanonicalMuscle.name.lowercase()` for some CanonicalMuscle", so drift is
a build failure.

**D-F — No phase gate may depend on a CI run.** CI has never executed (account billing
block), so "CI green on the PR" can never be satisfied. Every such gate line is
owner-machine output pasted into the PR; CI green survives only as an **additional** check
once the owner's standing, non-gating billing errand lands.

**D-G — Mandatory phase-start re-baseline.** Every count, line number and repo-state
assertion in a packet is a baseline as of audit commit `2212628`, not an oracle. The
executor's FIRST commit on a phase branch is a re-baseline report: current trunk tip,
actual domain-test count and test-class count, and every drifted packet literal with its
verified current value. Drift fully explained by merged prior phases or by the game plan's
own commits is EXPECTED and is not grounds to stop; stop only on unexplained mismatches.

**Engineering decisions fixed by the critique** (details in REVISED_STRUCTURE.md):

- **One-live-affordance rule**: while a session is in progress, the LiveSessionBar is
  the ONLY live-session affordance anywhere, counting docked chrome. Home's hero never
  says Resume; the rest strip dies.
- **Finish-side invariant**: every finish/discard routes through the shared
  `FinishWorkout`/`DiscardWorkout` use cases (zero-set guard, rest-timer stop, draft
  clear, one-shot navigation) — after Phase 1's 1A slice the repository methods have one
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

Value-first, with the test lane bought first, the risky substrate in the middle, and
content last. If the plan stops halfway, the owner has session hygiene and a pinned
week — not substrate and a picture book.

**Phase numbers are identifiers, not sequence (D-A).** The execution order is:

```
0 Decisions ──► 2 Test substrate ──► 1 Session hygiene ──► 3 Schema v2
   (signs D1/D2)   (preflight + lanes)   (1a + 1b, one PR)     (the migration)
                                                                     │
   8 Imagery ◄── 7 Catalog ◄── 6b Home ◄── 6a Tabs+Body ◄── 5 Heat/Coach ◄── 4 Plan tab
   (OPTIONAL)     (content)     (rework)     (IA landing)     (honesty)       (pinned week)
```

**Why Phase 2 runs before any code phase (D-A).** Phase 2 is the phase that *creates*
`tools/preflight.sh` and the Robolectric lane — the mechanical half of every later
phase's definition of done. Running it first (a) keeps its own verified gate literals
(188 tests / 24 classes / 23 files, confirmed accurate at HEAD) true instead of stale,
since any code phase landing first moves them; (b) gives Phase 1's gates a working
domain-test lane instead of `tools/run-domain-tests.sh`, which exits 2 on a cold clone
(`:20-24` needs a jar directory only Phase 2's bootstrap creates); and (c) gives Phase 1b's
repository writes — `restoreSet`, `repeatSession`, `deleteFinishedSession` — a Robolectric
lane they otherwise do not have. The cost is that session hygiene reaches the owner ~1–2
executor-days later; the buy is that the plan's largest unverified assumption (does this
toolchain run Robolectric at all?) is retired on the first evening, in the cheapest phase.

The remaining dependencies that force this order: Phase 1 (1a+1b) is schema-free
daily-pain work and needs only the test lane. Phase 3's migration gate is a
MigrationTestHelper suite, which needs Phase 2's Robolectric lane to exist. Phase 4 needs
`schedule_slots` (3) and the signed semantics (0). Phase 5 needs junction weights (3).
Phase 6a builds the IA landing on honest windows (5) and a Plan tab that exists (4); 6b
reworks Home around the persisted week (4), the coach module (5), and 6a's anchors and
tab bar. Phase 7's catalog rides the versioned seeder (3) and the navigation end-state
(6); Phase 8 draws on equipment + junction data (3, 7), is pure presentation, and is
**explicitly optional** — nothing depends on it.

**Totals** (from the packets' §9 blocks, with 1a+1b merged per D-B and Phase 6a costed on
its four-tab default per D-C): **ten PRs** — 0, 2, 1, 3, 4, 5, 6a, 6b, 7, 8 — at
**~26–37 executor-days and ~6–10 owner-days for the nine required phases (0 through 7)**;
the optional Phase 8 adds ~2–4 executor-days and ~0.5–1 owner-days, taking the full plan
to **~28–41 executor-days and ~6–11 owner-days**. (Under the three-tab option Phase 6a
roughly doubles, +2–2.5 executor-days and +0.5 owner-days.) The owner-days are real
evenings: two signatures, per-phase PR reviews, and one device checklist per landing —
the merge of 1a and 1b saves exactly one of them. The A1 DI refactor is deliberately not
on this path.

## 5. Phase-by-phase action plan

Each subsection: what gets built (the complete work breakdown, condensed to the
load-bearing detail), how it is verified, the owner's part, and what can go wrong.
Full literal specs — signatures, SQL, string tables, test names — live in the packet.

---

### Phase 0 — Decisions & doctrine · [packet](PHASE_0_DECISIONS.md) · 0.5–1 exec-days, 0.5–1 owner-days

**Docs only; the plan's single blocking checkpoint.** Nothing later executes until D1
and D2 carry the owner's initials.

Work breakdown:
1. **Nothing to commit.** `docs/gameplan/` — PROTOCOL.md, the eleven packets,
   ACTION_PLAN.md, SECOND_PASS.md — is *already* committed on the working branch. The
   old "commit the game plan" work item is closed; Phase 0's diff is confined to
   `docs/ROADMAP.md`, `docs/DESIGN_AUDIT.md`, `docs/DEVELOPMENT.md` and the new
   `docs/SCHEDULE_SEMANTICS.md`.
2. **ROADMAP.md restructure**: mark historical Phase 4 superseded (split value-first
   across the game plan); insert the game-plan phase table; re-point every
   known-open-items row to its owning game-plan phase; append the **Decisions** section
   skeleton (D1–D5 with Signed lines).
3. **DESIGN_AUDIT.md repairs**: close NAV-01 with the recorded IA decision; supersede
   the §7 per-routine equipment-override row; resolve the rest-overlay contradiction in
   ROADMAP's favor across all **six** places it appears (R-05, T-07, N-03, §10.2 banner,
   §16 build order at line 711, §17 acceptance line — the second pass found the sixth); append three §15 non-goals (LLM coach, head-level granularity,
   day/year windows).
4. **D1 text**: the **four-tab recommendation** (Home · Body · Plan · History) and the
   three-tab option written so the owner circles one and signs (D-C). The three-tab
   option's text names its two mandatory mitigations — the `section=sessions` anchor and
   Home's "Last session" link row — so the owner signs the whole package, not half of it.
5. **D2 spec**: `SCHEDULE_SEMANTICS.md` — the slot model, eight derivation rules, and
   five worked examples over a named week (missed anchored day, missed unanchored day,
   week rollover, regeneration, routine deletion). One deliberate open question for the
   owner: rule 6 (no week carry-over) can be struck without changing the DDL.
6. **D3/D4/D5 texts** into the Decisions section; correct DEVELOPMENT.md's false
   trunk-based claim; resolve branch ground truth (merge to `main` recommended).

Verification: docs-only diff (`git diff --stat -- app/ tools/` empty); a battery of
literal greps (Decisions section present, ≥3 Signed lines, supersession banners asserted
in **ROADMAP.md and DESIGN_AUDIT.md** — HIERARCHY_PLAN's banner already exists and is not
re-asserted). No code, no tests — stated in the PR rather than faked. The gate no longer
asserts that `docs/gameplan/` is absent; it is committed (see D-G's repo-state
correction), and the "has Phase 0 merged?" test is `grep -c "Signed:" docs/ROADMAP.md`
returning `0`.

Owner: read D1 and circle an option; read the five worked examples and sign D2 (decide
rule 6); skim D3/D4; choose D5; merge. **This is the plan's only blocking day.** One
non-blocking extra is requested here: the **CI billing errand** (~30 min, any screen —
add a payment method / raise the $0 limit, make the repo public, or attach a self-hosted
runner). It gates nothing (D-F), but it removes the plan's single biggest bottleneck and
specifically saves a round-trip in Phase 3, whose `2.json` otherwise has to be fetched
off the owner's machine by hand.

Risk: signing D2 casually. The schedule DDL is derived from it and frozen into the
migration — Phase 0 is where changing your mind is free.

---

### Phase 2 — Test substrate · [packet](PHASE_2_TEST_SUBSTRATE.md) · 1–2 exec-days, 0.5 owner-days

**Runs FIRST among the code-touching phases (D-A).** Build the lanes Phase 3's migration
suite will run in, and the `preflight.sh` every later phase's gate calls. **Zero
`app/src/main` changes.** Running it here keeps its own gate literals (188 tests / 24
classes / 23 files) true rather than stale, gives Phase 1 a working domain-test lane, and
retires the plan's largest unverified assumption on the first evening.

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

6. **Host-OS constraint, recorded (second-pass finding).** The JVM/Robolectric lane
   **requires a macOS or Linux host.** Robolectric 4.14.1 uses NATIVE SQLite everywhere
   except Windows, where `SQLiteModeConfigurer.defaultValue()` hard-falls back to LEGACY
   (SQLite 3.7.10); LEGACY's `PRAGMA table_info` cannot express a composite primary key —
   exactly the shape of Phase 3's `exercise_muscles(exerciseId, muscleKey)` — so Room's
   open-time validation fails there for reasons unrelated to the migration under test. On
   a Windows host the emulator `connectedDebugAndroidTest` lane is the ONLY valid
   migration lane, and Phase 3's gate must be met there. Settle this before Phase 3 is
   planned, not inside it.

Owner: one evening — Gradle sync, `testDebugUnitTest` (first run downloads Robolectric's
android-all jar), an emulator `connectedDebugAndroidTest` (2 tests), `preflight.sh`,
paste outputs. **This first `testDebugUnitTest` is the single most important pre-start
verification in the plan**: it settles Robolectric compatibility, the host-OS question and
the toolchain assumptions at once — which is exactly why this phase now runs first.

---

### Phase 1 — Session hygiene · packets [1A](PHASE_1A_SESSION_LIFECYCLE.md) + [1B](PHASE_1B_LOG_REPAIR.md) · 5–7 exec-days, 0.5–1 owner-days

**Kill session limbo and make the log correctable and reusable. Schema-free throughout**
(new `@Query` only — hard constraint).

**One phase, two packets (D-B).** One branch `claude/phase-1-session-hygiene`, one PR,
one combined owner evening. Both packets go to the **same** executor session and run
strictly in order: 1A's work items in full with **its acceptance gate green first**, then
1B's on top of the same branch — no separate 1B branch, no separate 1B PR, no wait for a
1A merge. 1B's gate greps re-assert 1A's invariants, so the sequence self-verifies. The
phase starts only after the **Phase 2** PR merges (D-A), which is what gives 1B's
repository writes — `restoreSet`, `repeatSession`, `deleteFinishedSession` — a Robolectric
lane to be tested in at all.

#### 1A — session lifecycle

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

#### 1B — log repair

Honors ROADMAP's own decision that editing history "deserves its own change." Post-finish
**notes editing** rides along here (work item 1b-6): `updateSessionNotes`'s finished-guard
is relaxed on the same reasoning as `updateSet`'s — notes carry no timestamp, PR or heat
semantics, the method writes `notes` and nothing else, and the string already round-trips
through backup.

Work breakdown:
1. **Editable finished sessions** — guard-by-guard disposition in `WorkoutRepository`
   (each line named in the packet): `updateSet`'s finished-guard **relaxed** (it already
   preserves `completedAt` and `setNumber`); `updateSessionNotes`'s finished-guard
   **relaxed** too (work item 1b-6, above); `logSet`/exercise-add guards **kept**;
   post-finish adds go through a new `addSetToFinishedSession` that timestamps inside
   `[startedAt, finishedAt]` via pure `FinishedSessionEdits` (monotone, total on
   degenerate windows) — no PR detection, no rest timer. Editable per set: weight, reps,
   RPE, warm-up flag, add/delete; per session: `notes`. Never editable: dates,
   timestamps, `durationMinutes` (its only computed writer stays `finishSession`,
   grep-enforced), an existing set's `completedAt`, the exercise list. **Domain tests pin the two subtle invariants**: an added set heats the original
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
completedAt write sites). **The repository writes this phase adds — `restoreSet`,
`repeatSession`, `deleteFinishedSession` — get Robolectric coverage in the lane Phase 2
built; before D-A's reorder they had no test lane at all.**

Owner: 10-step pass — edit an old set and watch this week's heat NOT light up; PR
chronology stays on the old date; repeat copies structure but zero sets; backup
round-trips edited history.

**Recorded NON-GOAL — backdated / manual session entry.** "I trained yesterday and forgot
to log it" is not solved here and is not smuggled in: `date`, `startedAt` and `finishedAt`
stay non-editable. Real backdating needs an editable session date plus its own decisions
about heat attribution and PR chronology.

Combined verification and owner pass: the two packets' gates run back-to-back on the one
branch (1A's first and green, then 1B's, whose greps re-assert 1A's invariants); the owner
runs both device checklists in one sitting and signs once. All gate output is
owner-machine output pasted into the PR — no gate depends on a CI run (D-F).

---

### Phase 3 — Schema v2 migration · [packet](PHASE_3_SCHEMA_V2.md) · 3–5 exec-days, 1–2 owner-days

**The highest-risk phase: the app's first-ever migration, run exactly once against the
only real dataset.** Everything downstream waits on these tables.

Work breakdown:
1. **v2 entities + `MIGRATION_1_2`** — five new `exercises` columns (`equipment`,
   `loadType`, `movementKey`, `imageKey`, `nameKey` + plain declared index; annotation
   defaults and migration SQL must agree byte-for-byte with the generated `2.json`, the
   packet's S1 rule); three new `@Entity` tables — `exercise_muscles(exerciseId,
   muscleKey, weight)` PK-composite FK-CASCADE (`muscleKey` is
   `CanonicalMuscle.name.lowercase()` and nothing else — D-E), `seed_meta(catalogVersion,
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
   existing error channels. **`normalizeKey` must be exposed, not re-implemented**: the
   `nameKey` rule is "the same function as `MuscleNormalizer.normalizeKey`", which is
   `private` at `CanonicalMuscle.kt:161` — this phase makes it public (or adds a public
   `nameKeyOf`). Skipping that grows a second normalizer and enforces uniqueness against
   the wrong string.
4. **Batch-1 catalog: the 37, upgraded in place** — `movementKey` ships **family** keys
   from the start (D-D: `bench-press`, `row`, `squat`, `romanian-deadlift` …, 23 families
   across these 37 rows), the same vocabulary Phase 7 groups by, so nothing is ever
   re-keyed later. The packet carries the complete normative data table (id, equipment,
   loadType, movementKey, primary at 1.0, weighted
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

**Mid-phase owner round-trip (blocking; budget for it).** The executor environment has no
Android SDK and CI cannot be relied on (D-F), so after the v2 entities compile by
inspection and *before* the migration SQL can be finished, the executor stops and hands
the owner a request: pull the branch, run `./gradlew :app:assembleDebug`, and return
`app/schemas/…TrainerDatabase/2.json` plus the CREATE/INDEX statements it contains. The
executor then diffs those against the hand-written `MIGRATION_1_2` (rule S1:
byte-for-byte). ~15 minutes of owner time, but a calendar gap — raise it early, not at
the end.

Verification: preflight + `testDebugUnitTest` green on the owner's machine, pasted into
the PR (never a CI run — D-F); `2.json` committed and identical to the generated artifact; ~45 named tests across migration/safety-net/seed/backup/heat; the
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
   — callers show a shared resume-or-discard dialog routed through Phase 1's use
   cases. The `when`-exhaustiveness checker proves no caller misses the new outcome.
4. **The Plan tab, inside the five-tab bar** — Routines renamed Plan (route string
   deliberately unchanged); layout: header (Plan · Tune · New · Settings gear — the
   named second Settings home) → 7-cell week strip (today's volt marker, day letter,
   date, name, logged tick) → suggestion flow ("Suggest a week" → inline proposals →
   explicit Accept/Dismiss) → routines list verbatim. All pin management lives in the
   day sheet (tap a cell): Start / Swap routine / Unpin on pinned days; Pin-a-routine /
   Pin-a-focus on open days; past days informational.
5. **Demolition, once** — pushed ScheduleScreen deleted; `ThisWeekHomeCard` extracted to
   `ui/home/ThisWeekCard.kt` (gains the empty-week state: headline "No plan yet", with
   **"Suggest a week" as its primary action** and "Start a workout" demoted beneath it —
   this phase deliberately deletes the auto-generated ghost week, so the empty state is
   what the owner sees on the first launch after the update and recovery from it must be
   one tap from Home, not a tab hunt followed by a button hunt); Home's hero-card body tap →
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

Recorded follow-up (owner decision, not built here): **the Deload rule has no
affordance.** The coach can now say "schedule a lighter week", but the app offers no way
to *act* on it beyond training less — named on the PR for the owner rather than quietly
built.

Owner: 13-step pass — two chips only; untrained muscles dark early in the week; advice
invariant under chip flips; the band-truthfulness judgment call ("does Productive match
how I train?") which may trigger one threshold-tuning round.

---

### Phase 6a — Tab consolidation + Body absorbs History · [packet](PHASE_6A_TAB_CONSOLIDATION.md) · 2–2.5 exec-days (four tabs) / 4–5 (three tabs), 0.5–1.5 owner-days

**The IA landing.** Safe now because the bar owns resume (Phase 1), Plan owns the week
(4), honest heat owns the windows (5). **Two branches, specced to equal depth; the
executor runs the one D1 signed and never blends them** — Branch A (four tabs, D1's
recommended default per D-C) or Branch B (three tabs). Branch A is roughly half the
executor time because it drops the size-L merge while keeping the identical feature set.

Work breakdown:
1. **The tab bar** — **Branch A (four tabs, the default)**: Home · Body · Plan ·
   History; `Route.History`, `HistoryScreen` and `HistoryViewModel` all survive, the
   merge work is dropped, and month grouping, the multi-session-day sheet and the PR row
   land on History **in place**. **Branch B (three tabs)**: Home · Body · Plan;
   `Route.History` deleted. In **both** branches tab matching becomes plain pattern
   equality — the `isTabRoute` query-param shim and the `restoreState=false` Library
   hacks die either way.
2. **The merged Body screen** — ONE LazyColumn, never nested scrolling: window picker →
   silhouette → per-muscle rows → month calendar (moved file) → coach cards →
   month-grouped session list with sticky month headers → PR summary. Grouping and the
   PR summary are new pure domain code (`groupSessionsByMonth`, `prSummary` — e1RM-first
   with weight fallback, one row per exercise, recency-ordered). The content list is
   1:1 with items so 6b's calendar anchor is a true scroll index (comment records it).
   Multi-session days open a chooser sheet instead of silently taking the first session.
   **Branch B ships TWO scroll anchors, not one** (D-C mitigation (i)): `section=calendar`
   *and* `section=sessions`, the latter resolving to the first month header so "show me my
   log" lands on the log rather than five sections above it. Leaving `sessions` unwired is
   an incomplete Branch B; Branch A needs no anchors at all.
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
**Inherits 6a's branch; never re-opens it.** Exactly three things here are
branch-dependent — the calendar-jump mechanism, the "Last session" row, and the device
steps that name a screen. Everything else — masthead, week strip, next-session module,
StartOptionsSheet, interstitial demolition, hero, demolitions — is identical in both.

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
4. **Calendar jump** — **Branch B**: `Route.Progress` gains an optional `?section=`
   pattern; the chip navigates with `section=calendar` and Body one-shot-scrolls to the
   calendar index; plain tab taps never scroll. **Branch A (default)**: the chip is a
   plain History tab jump; no pattern change — one chip and one callback.
   **Branch B additionally builds the mandatory "Last session" link row on Home** (D-C
   mitigation (ii)): one tertiary row, no card, reading the most recent finished session's
   title and date from state already on screen, tapping through to SessionDetail (and to
   `section=sessions` when the log is empty). It is a **link row, not a recommendation
   surface** — no reason line, no ranking, no engine input — so the D3 surface map is
   untouched. Branch A does not build it: History is one tap away and its session list is
   the second thing on that screen.
5. **StartWorkout interstitial dies** — its logic becomes `StartOptionsSheet` hosted by
   Home and Body: today's slot pinned with a TODAY kicker → the Phase-5 suggestion row
   (moves in) → routines → free workout; in-progress state shows Go-to-session + guarded
   Discard through the 1a use cases. Every former call site enumerated and retargeted;
   `Route.StartWorkout` deleted.
6. **`LIVE_BAR_HIDDEN_ROUTES` amendment (both branches)** — this phase deletes
   `Route.StartWorkout`, which Phase 1 put in that list; removing it from the list is an
   explicit work item here, in both branches. (Second-pass finding: no packet owned it.)
7. **Hero final form + demolitions** — the whole card is one action (start today via
   `StartTrainingDay`; rest/no-slot days open the sheet); a separate "This week ›" row
   goes to Plan (the mis-tap trap dies); the duplicate heat card and the Recent
   list are deleted in **both** branches (Body owns the heat card; the session log is
   owned by History under Branch A and by Body under Branch B, where the "Last session"
   row above is the one link that survives); Ready-to-progress rows deep-link the named
   lift.

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
3. **The muscle-filter contract flip, end-to-end** — `muscleKey` is exactly
   `CanonicalMuscle.name.lowercase()` (D-E), so this phase's tail rows use
   **`quadriceps`**, never `quads` (an alias that normalizes correctly and is still wrong
   in the column; the invariant test asserts exact equality, so writing it fails the
   build). Family grouping reads the same `movementKey` vocabulary Phase 3 already
   shipped (D-D) — a mismatch is reported to the owner, never silently re-keyed on either
   side. The Library route param becomes the canonical muscle enum name; filtering is junction-based (secondary credits match
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

Recorded follow-up (owner decision, ships either way): **bodyweight is never stored.**
This phase adds 20+ `BODYWEIGHT`/`BODYWEIGHT_PLUS` rows and the rep-progression copy that
goes with them, but the app holds the owner's bodyweight nowhere — so a bodyweight-only
set logs 0 kg of volume and a weighted dip computes from the added load alone. Two
dispositions go on the PR to sign: **A (recommended)** an owned follow-up — a device-local
`bodyweight_kg` preference read by the volume/e1RM math, a small phase of its own because
it changes computed history; **B** an explicit non-goal — bodyweight lifts are sets and
reps forever, volume and e1RM blank rather than zero. What is not acceptable is shipping
the rows and the copy with the arithmetic unexplained.

Owner: staged — skim two review artifacts at batch scrutiny, then an 11-step device
pass (family expansion, "ohp"/"rdl"/"100%" searches, chip AND-filtering,
secondary-credit filtering, class defaults, "+5 lbs", swap flows, collision row).

---

### Phase 8 — Imagery · [packet](PHASE_8_IMAGERY.md) · 2–4 exec-days, 0.5–1 owner-days · **OPTIONAL**

**Every exercise gets an image, at zero asset cost.** Pure presentation; last; and
**explicitly optional** — nothing in the plan depends on it. `imageKey == null` means
"compose the thumb", which is a complete shipping state, so deferring this phase
indefinitely costs the owner nothing. Being optional changes nothing about how it is run
if it does run.

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
   Phase 3; phases 1 and 7 carry explicit no-schema hard constraints.
2. **Backup discipline**: any phase that adds persisted user state must carry it in the
   backup document, validate it, and round-trip-test it (Phase 3 does this for
   everything the plan adds; Phase 7's dismissal preference is the one recorded
   device-local exception).
3. **State-graph discipline**: every start routes through `StartTrainingDay`; every
   finish/discard through Phase 1's `FinishWorkout`/`DiscardWorkout` use cases; navigation events are one-shot
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
7. **Re-baseline discipline (D-G)**: every count, line number and repo-state assertion in
   a packet is a baseline as of audit commit `2212628`, not an oracle. The executor's
   FIRST commit on a phase branch is a re-baseline report — current trunk tip, actual
   domain-test and test-class counts, and every drifted literal with its verified current
   value. Drift fully explained by merged prior phases or by the game plan's own commits
   is EXPECTED and is not grounds to stop; stop only on unexplained mismatches.
8. **No-CI-gates discipline (D-F)**: CI has never executed in this repo, so no phase gate
   may depend on a CI run. Every gate is a command the owner runs on their own machine
   with the output pasted into the PR; CI green is an **additional** check to re-run once
   the owner's standing, non-gating billing errand lands.

## 7. Running it with Opus

One fresh session per phase, two documents per session, phases strictly in **execution**
order — `0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b → 7 → 8` (D-A; phase numbers are identifiers, not
sequence). Phase 1 is the one exception to "two documents": it is one phase built from two
packets (D-B), so its session receives three — the protocol plus 1A and 1B.

> Read docs/gameplan/PROTOCOL.md and docs/gameplan/PHASE_2_TEST_SUBSTRATE.md in
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
4. **State-graph regressions** (Phases 1/4/6): the hardening from the app's Phase 2
   history must survive verbatim; the invariant greps and the device passes
   (process death, deep links, notification resume) are the fence.
5. **Pin/planner reconciliation subtleties** (Phase 4): the reason the derivation is
   pure, the semantics are owner-signed with worked examples, and the examples are the
   tests.
6. **Verification stays manual until the owner's standing errand lands** (CI billing):
   no gate may depend on a CI run (D-F), so every gate is expressed as commands + named
   tests + device checklists that work without CI; the day CI turns on, both jobs run
   with zero further changes.
7. **The CI errand is the plan's most under-priced lever.** ~30 minutes of owner time
   (payment method / public repo / self-hosted runner) against a plan whose scarce
   resource is owner evenings — it would remove the single biggest bottleneck and
   specifically kill Phase 3's hand-fetch of `schemas/2.json`. It is *requested* at
   Phase 0 and still gates nothing; the risk is that it stays undone for the whole plan
   and every phase pays the manual-verification tax.
8. **Host OS for the JVM migration lane** (Phases 2/3, new in the second pass):
   Robolectric 4.14.1 falls back to LEGACY SQLite (3.7.10) on **Windows**, whose
   `PRAGMA table_info` cannot express a composite primary key — exactly
   `exercise_muscles(exerciseId, muscleKey)` — so Room's open-time validation fails there
   for reasons unrelated to the migration. Mitigation: run the JVM lane on macOS or
   Linux; on a Windows host the emulator `connectedDebugAndroidTest` lane is the only
   valid migration lane, and Phase 3's gate must be met there. Settle it in Phase 2, not
   inside Phase 3.
9. **Estimate risk**: phases 4 and 6a are the likely overruns (six-flow combine and the
   `when` ripple; the merged-screen construction — the latter only under the three-tab
   branch). Both packets flag their overrun points and both are split-committed so
   partial progress is reviewable.
10. **Packet literals keep drifting** as phases merge and as the game plan's own doc
   commits land. Mitigated, not eliminated, by the mandatory phase-start re-baseline
   (D-G): expected drift is reported and execution continues; only unexplained drift
   stops a phase.

## 9. What "done" looks like

Four tabs and a bar — three, if that is the option you signed. You open the app: the
masthead says `PUSH DAY · 4 LIFTS`, the hero starts it in one tap, the strip shows the
week you pinned. Mid-rest, the bar carries the clock on every screen; finishing lands on
the summary and the session files itself. Body shows honest bands over the silhouette,
your records — and a coach that names the lift you actually own, holds you at RPE 9, and
tells you to rest when everything is productive; your calendar and your whole log,
grouped by month, are one tap away in History (or in Body itself under three tabs). Plan
holds your week and heals when you delete a routine. The Library is ~98 movements in families with images drawn from
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
| [REVISED_STRUCTURE.md](REVISED_STRUCTURE.md) | the binding brief: what the critique changed and why (+ the second-pass amendments D-A…D-G, appended) |
| [SECOND_PASS.md](SECOND_PASS.md) | the second-pass due-diligence record: verdict, decisions changed, defects fixed, what held, new risk |
| [PHASE_0_DECISIONS.md](PHASE_0_DECISIONS.md) … [PHASE_8_IMAGERY.md](PHASE_8_IMAGERY.md) | the eleven executor packets |
| [../HIERARCHY_PLAN.md](../HIERARCHY_PLAN.md) | the audit of record (§4 superseded by this directory) |
| [../ROADMAP.md](../ROADMAP.md) | source of truth; Phase 0 records the Decisions section there |
