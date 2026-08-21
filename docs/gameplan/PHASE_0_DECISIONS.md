# Phase 0 — Decisions & doctrine

Docs only. No Kotlin file changes, no schema, no UI. Read `docs/gameplan/PROTOCOL.md`
first; this packet assumes it.

## 1. Mission

Record every decision the later phases build on, close the contradictions the doctrine
currently carries, and establish branch ground truth — so that eight fresh executor
sessions can each work from a self-consistent repo. This phase ends at the plan's only
**blocking** checkpoint: the owner signs the IA choice and the schedule semantics in
ROADMAP's new Decisions section. Phase 3 derives DDL from that signature; nothing later
executes without it.

## 2. Read first

1. `docs/gameplan/PROTOCOL.md` — the execution protocol; §2 branch truth and §4
   checkpoint rules govern this phase directly.
2. `docs/ROADMAP.md` — the file being restructured; read whole (156 lines).
3. `docs/HIERARCHY_PLAN.md` — §3 (the IA adjudication argument, lines 109–139) and §4
   (the superseded phase plan, line 140 onward); source of the decision text.
4. `docs/DESIGN_AUDIT.md` — the rows being amended: NAV-01 (line 449), §7 (line 478),
   R-05 (line 59), T-07 (line 342), N-03 (line 440), §10.2 (line 565), §15 (line 670),
   §16 build order (line 711, Wave 2 item 13), §17 (lines 735–739).
5. `docs/UI_REDESIGN.md` — §5 decision record (lines 99–136), §6 target IA (lines
   139–152: three tabs + LiveSessionBar), §9 (lines 174–193: what shipped, the
   no-compiler caveat).
6. `docs/DEVELOPMENT.md` — the checks (lines 59–66), the CI truth (lines 89–105), and
   the false trunk claim (line 144) this phase corrects.
7. `app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt:157-163` — the
   shipping five-tab bar (Home, Body, Routines, Library, History) the IA decision
   adjudicates.
8. `app/src/main/java/com/sinura/personaltrainer/domain/WeeklySchedulePlanner.kt` and
   `domain/ScheduleModels.kt` — the fully ephemeral planner the schedule-semantics spec
   replaces as the owner of "the week".
9. `app/src/main/java/com/sinura/personaltrainer/domain/DefaultExercises.kt` — the
   37-row seed catalog (the "batch 1" of Phase 3).

## 3. Binding doctrine

- **DESIGN_AUDIT** — NAV-01 (§6.12) is the open IA contradiction this phase closes; §7
  is the data-model table whose equipment-override row is superseded; §10.2/R-05/T-07/
  N-03/§17 carry the rest-overlay mandate that contradicts ROADMAP Phase 6's recorded
  not-to-build (`docs/ROADMAP.md:134`) — **ROADMAP wins**; §15 is the non-goals list
  gaining three entries.
- **UI_REDESIGN §6** — the recorded three-tab target IA (Today/Plan/Progress +
  LiveSessionBar) is the basis of the D1 recommendation; §5/§5.1/§8 (Direction B,
  Instrument, guardrails) bind the wording of everything this phase records — no
  decision text may contradict the token system or the accent-budget rule.
- **DIRECTION_B_INSTRUMENT** — the design language; decisions recorded here must not
  imply any surface it forbids (shadows, second accents, light theme).
- **REVISED GAME-PLAN STRUCTURE** (the binding brief this packet derives from) — phase
  list, shared pre-decided designs (schedule semantics, surface map, LiveSessionBar
  contract, band model, increment table), cut list, execution protocol. Items 2, 5, 8,
  10–12, 18, 19 constrain this packet's edits specifically.
- **Attack findings this packet must visibly satisfy:** the contradictions attack
  ("Phase 3.9's recording act is unspecified — no enumerated doc edits"; the
  rest-overlay contradiction; §15 non-goal gaps); the executor attack (branch trap —
  main is empty; the blocking checkpoint after the decisions phase); the tech attack
  finding 2 (rotation semantics must be signed **before** any DDL freezes); the scope
  attack (A1 is not a phase; the cut list).

## 4. Settled decisions

Stated as settled. Do not reopen any of them.

- **The recommended IA is four tabs — Home · Body · Plan · History — plus the
  LiveSessionBar** (recommendation flipped 21 Aug 2026 on second-pass evidence; see
  WI-4). The alternative on offer is three tabs (Home · Body · Plan, Body absorbing
  History), which remains a legitimate choice but carries two mandatory mitigations if
  signed. Keep-five is not offered. The owner circles one; either outcome closes
  NAV-01.
- **The LiveSessionBar contract** (implemented in Phase 1 — session hygiene — recorded
  now): visible on
  all tab routes and pushed routes EXCEPT ActiveWorkout, WorkoutSummary, StartWorkout;
  carries its own `navigationBarsPadding` when the tab bar is absent; shows elapsed
  time, working-set count, rest countdown; tap resumes; overflow = Finish (≥1 set) /
  Discard (always, guarded); zero-set state shows Discard only. **While it is visible,
  no other surface may show a live-session affordance** — Home's hero never relabels to
  Resume, and `RestRemainingStrip` (`ui/home/HomeScreen.kt:140`, defined at `:290`) is
  deleted.
- **Schedule model:** the week is an ordered cycle of `schedule_slots`
  (id, position, routineId? FK→routines ON DELETE CASCADE, focusKind?, anchorDay? 0–6,
  createdAt, updatedAt). `anchorDay` is 0=Monday … 6=Sunday (ISO order, independent of
  the display week-start preference). Anchors are preferences, not constraints; a
  missed anchored day shifts forward, never skips. Derivation is a pure function of
  (slots, completion history, today); stored slots are never rewritten by derivation.
  Phase 3 derives the DDL from the signed spec; Phase 4 implements it.
- **Recommendation surface map (final):** Home = ONE "next session" module (plan + top
  recommendation composed, one-line reason); Body = full explanation cards; the
  start-options sheet and the in-workout add-exercise sheet = action surfaces with one
  pinned suggestion each; **no other surfaces**. Home never shows two recommendation
  slots.
- **The cut list** (work item 6) is final: LLM coach, head-level granularity, day/year
  heat windows, per-routine equipment override, the FK re-pointing merge tool, the
  line-art commission (→ appendix), A1-as-a-phase, the rest overlay bubble.
- **Branch ground truth** is resolved this phase (PROTOCOL §2): merge
  `claude/app-hierarchy-navigation-cjzigo` → `main` with owner approval, or record
  branch-as-trunk. The recommendation is **merge to main** — it makes
  `DEVELOPMENT.md:144` true again and keeps CI's `main` trigger meaningful.
- **Backup v2 belongs to Phase 3** (same phase as the schema, same gate); the catalog's
  batch 1 is the existing ~37 rows upgraded in place. Recorded in the ROADMAP phase
  list; not re-litigated here.

## 5. Work items

All edits land on branch `claude/phase-0-decisions`, branched from
`claude/app-hierarchy-navigation-cjzigo`. WI-1 is a verification step; every other work
item is a doc edit. Per-item tests are the grep checks in §7.

### WI-1 — Verify the gameplan directory (do not re-create it)

**`docs/gameplan/` already exists and is committed.** The reconciliation of 21 Aug 2026
landed it (commits `6be86fa`..`4028c93`): fifteen files at HEAD — `PROTOCOL.md`,
`README.md`, `ACTION_PLAN.md`, `REVISED_STRUCTURE.md`, and all eleven phase packets
including this one. Do **not** create the directory, and do **not** re-commit
`PROTOCOL.md` or `PHASE_0_DECISIONS.md`; they are already tracked and their committed
wording is authoritative.

This work item is a verification step. Run it as the phase's first act, folded into the
mandatory phase-start re-baseline commit:

```bash
git rev-parse --short HEAD                       # record the actual trunk tip
ls docs/gameplan/                                # expect the full committed set (16 .md files; 17 after WI-5)
test -f docs/gameplan/PROTOCOL.md && echo PROTOCOL-OK
ls docs/gameplan/PHASE_*.md | wc -l              # expect: 11
grep -c "Signed:" docs/ROADMAP.md                # expect: 0 — the "has Phase 0 merged?" test
```

The last grep is the authoritative "has Phase 0 already merged?" test: `0` means the
Decisions section has no signatures yet and this phase has work to do. (The presence of
`PROTOCOL.md` is **not** that test — it exists at HEAD regardless.)

**The only new file this phase creates is `docs/gameplan/SCHEDULE_SEMANTICS.md` (WI-5).**
Every other work item edits a file that already exists.

Counts and line numbers throughout this packet are a baseline as of audit commit
`2212628`, not an oracle. Report every literal that has drifted, with its verified
current value, in the re-baseline commit. Drift fully explained by merged prior phases or
by the game plan's own commits is expected and is not grounds to stop.

### WI-2 — ROADMAP.md restructure

**(a)** Keep the heading `## Phase 4 — Schema v2 · **next**` (line 89) in place, and
replace its body (lines 91–97) with:

> Superseded 20 Aug 2026. The monolithic Phase 4 bundled the migration with behaviour
> changes this roadmap itself said deserved their own change (see line 83–85 on editable
> sessions). It is split and re-ordered value-first across the game plan below: the
> migration core is game-plan Phase 3; the test substrate and session hygiene ship first
> (Phases 2 then 1); the
> catalog and imagery ship last (Phases 7/8). Execution rules: `docs/gameplan/PROTOCOL.md`.

**(b)** After the Phase 6 section (line 134, before the `---` at line 136), insert a new
section:

> ## The game plan — 20 Aug 2026
>
> Phase numbers below are game-plan numbers, independent of the historical phases above.
> Full packets live in `docs/gameplan/`; the execution protocol is
> `docs/gameplan/PROTOCOL.md`. Phases land in order; each closes only on owner sign-off.

followed by the eleven-row phase table from PROTOCOL §7 verbatim (Phase 0 … Phase 8 with
one-liners and day estimates), and the line:

> **A1 (the DI seam) is not a phase.** It is an opportunistic refactor, hard 2-day
> timebox, undertaken only if instrumented ViewModel tests are ever scheduled. Nothing
> gates on it. (Supersedes the "Do it with Phase 4" note at Phase 2's outstanding item.)

**(c)** Update the known-open-items table (lines 142–156). Add above the table: "Phase
numbers refer to the game plan." Then re-point the rows:

| Row (current text at line) | New phase cell |
|---|---|
| No instrumented tests (144) | 2 |
| A1 ViewModels untestable (145) | opportunistic — not a phase |
| Finished sessions cannot be edited (146) | 1 |
| Imbalance advice compares tonnage (148) | 5 |
| Progression increment "+5.5 lbs" (149) | 7 |
| RPE read by nothing (150) | 5 |
| Planner assigns past days (151) | 4 |
| `arrangeKinds` back-to-back (152) | 4 |
| Toolchain stale (153) | later (platform) |
| Exercise imagery + equipment field (154) | 3 (field) / 7 (catalog) / 8 (imagery) |
| Sound design; plate calculator; font-scale (155) | later (platform) |
| No scheduled auto-backup (156) | later (platform) |

**(d)** At the end of ROADMAP.md, append the Decisions section skeleton:

> ## Decisions
>
> Signed decisions that later phases build on. A decision is binding once the owner's
> initials and date appear on its Signed line.
>
> ### D1 — Information architecture  *(text: WI-4)*
> ### D2 — Schedule semantics  *(spec: docs/gameplan/SCHEDULE_SEMANTICS.md)*
> ### D3 — Recommendation surfaces  *(text: WI-6)*
> ### D4 — Cut list  *(text: WI-7)*
> ### D5 — Branch ground truth  *(text: WI-8)*
> ### D6 — Second-pass amendments  *(text: WI-9)*

The italic pointers are placeholders for this packet's flow only: WI-4 through WI-9
insert the actual decision text under these headings, and each pointer is deleted when
its text lands. The committed ROADMAP contains the headings and the decision text, not
the "(text: WI-n)" annotations (D2's pointer is replaced by the spec-reference line WI-5
specifies).

**(e) — already landed; no edit.** The `docs/HIERARCHY_PLAN.md` §4 supersession banner
went in with commit `6be86fa` (it sits directly under the §4 heading) and **its committed
wording is authoritative** — do not add a second banner and do not reword the existing
one. This phase does not touch `docs/HIERARCHY_PLAN.md`.

**Test:** `grep -n "game plan" docs/ROADMAP.md` hits the new section; `grep -n "Phase 4" docs/ROADMAP.md` shows the superseded body; no row of the known-items table still says a bare "4".

### WI-3 — DESIGN_AUDIT.md amendments

**(a) Close NAV-01** (line 449). Amend the Issue cell to end with:

> **Closed 20 Aug 2026 — adjudicated by the decision recorded in ROADMAP.md § Decisions
> D1, which the owner signs by circling one option (four tabs Home · Body · Plan · History,
> recommended; or three tabs Home · Body · Plan with Body absorbing History). Either option
> demotes Library to a pushed screen and lands the LiveSessionBar as chrome. Do not restate
> the chosen option here — D1 is the single record of it. The Home/Workout/Program/You
> grouping suggested in this row is superseded either way.**

**(b) Supersede the §7 RoutineExercise row** (line 478). Replace the sentence
"`RoutineExercise` should store equipment override (same lift on machine vs barbell is a
different card)." with:

> ~~`RoutineExercise` should store equipment override~~ **Superseded 20 Aug 2026 (D4
> cut list):** an equipment variant is its own catalog row with its own history and PRs,
> grouped by `movementKey`; no per-routine override column will exist.

**(c) Rest-overlay resolution — ROADMAP wins.** ROADMAP Phase 6 (line 134) records the
overlay bubble as not-to-build; this document still mandates it in six places. Amend:

- **R-05** (line 59): append to the Current state cell: "**Superseded — see §10.2
  banner; the notification + last-5s ticks are the home-screen presence.**"
- **T-07** (line 342): append to the Issue cell: "**Superseded — see §10.2 banner.**"
- **N-03** (line 440): append to the Issue cell: "**Superseded — no overlay permission
  row will be added; see §10.2 banner.**"
- **§10.2** (line 565): insert directly under the heading:

  > **Superseded 20 Aug 2026.** ROADMAP Phase 6 records the decision **not** to build
  > the overlay bubble, and D4 reaffirms it. The FGS notification (non-negative, fixed
  > under A-03) plus the last-5s ticks are the glanceable rest surface. This section is
  > retained as the analysis that informed the decision; do not implement it.

- **§17** (line 739): replace "**see the clock on the launcher** (overlay or at least a
  non-negative notification)" with "**see the clock in the notification shade**
  (non-negative, always)".
- **§16 build order** (line 711, Wave 2 item 13): the line reads "Optional overlay
  bubble + permission row (R-05)". Same strike-through-and-annotate treatment as the
  rows above — replace it with:

  > 13. ~~Optional overlay bubble + permission row (R-05)~~ **Cut 20 Aug 2026 (D4); see
  >     §10.2 banner. Nothing replaces it in this wave.**

**(d) §15 non-goals additions.** Extend the §15 bullet list — it ends at line 683 with
"Gym-level machine brand models"; append the three new bullets there, before the closing
paragraph at line 685 ("Wear and a plate calculator will matter. …"):

> - LLM / chat "AI trainer" — reaffirmed 20 Aug 2026 (D4). The coach is the rule
>   engine, made honest and specific; it works offline.
> - Muscle-head-level granularity ("front head of the triceps"). Weighted
>   primary/secondary credit is the honest ceiling of set-log data; a later sub-group
>   split for delts and back only is the recorded maybe.
> - Day and year heat windows. The decision-relevant horizons are This week and Last
>   30 days, on absolute weekly-set bands (game-plan Phase 5).

**Test:** `grep -c "Superseded" docs/DESIGN_AUDIT.md` ≥ 5; `grep -n "LLM" docs/DESIGN_AUDIT.md` hits §15.

### WI-4 — D1: the IA decision text

Insert under ROADMAP § Decisions D1, verbatim:

> The app ships five tabs (`AppNav.kt:157-163`: Home, Body, Routines, Library, History).
> DESIGN_AUDIT NAV-01 calls five a lot; UI_REDESIGN §6 records a three-tab target that
> Phase 5 deferred. This decision closes the contradiction. **Circle one option and
> sign.**
>
> **What both options give you, whichever you circle.** Library stops being a tab and
> lives on as a pushed screen — entered from Plan, from recommendation cards, and from
> the muscle detail sheet; every pushed route survives. Routines folds into the new
> **Plan** tab, so the week and the routines that fill it are one place instead of a tab
> plus an orphaned screen in Settings. The **LiveSessionBar** (a docked strip whenever a
> session is live — elapsed, sets, rest countdown, tap to resume) lands as chrome, not a
> tab, and becomes the **only** live-session affordance anywhere. And the history work
> lands either way: sessions grouped by month, a sheet when a day holds more than one
> session, and a personal-records row.
>
> **Option A (recommended): four tabs — Home · Body · Plan · History.** History keeps
> its tab. The calendar and the session log stay exactly one tap away, Body stays
> silhouette-first, and Phase 6a is the tab-bar rewrite plus the nav retargets — Body
> does not absorb History. What it costs you: a fourth tab in the bar, and "what has my
> training done" is answered in two related places rather than one.
>
> **Option B: three tabs — Home · Body · Plan — plus the LiveSessionBar.** This is the
> recorded target IA of UI_REDESIGN §6: the reflection tab keeps the name **Body**,
> leads with the silhouette, and *absorbs* History — the calendar and session log move
> under the body map, making Body the single "what has my training done" surface instead
> of two thin ones. What it costs you: the largest single piece of nav work in the plan,
> and the reach described below.
>
> **Why the recommendation flipped to four tabs (new evidence, 21 Aug 2026).** When the
> merged Body screen was specced in full, the session log came out **five sections deep**
> — window picker, silhouette, muscle rows, calendar, coach cards, and only then the
> session list, with personal records under it. At the same time the Home rebuild deletes
> Home's "Recent" list in **both** branches, and the only scroll anchor built into the
> merged screen targets the **calendar**, not the session list. Net effect if you circle
> B: "what did I do last session" goes from one tap today to a tab change plus a long
> scroll. Four tabs keeps every win listed above and drops only the large merge — and it
> is closer to what you originally asked for.
>
> **Three tabs is still a legitimate choice.** If you circle B, two mitigations become
> mandatory and are built in the same phases, not deferred: (i) a `section=sessions`
> scroll anchor on Body alongside the `section=calendar` one, so anything that means
> "show me my log" lands on the log; and (ii) a single **"Last session"** link row on
> Home. That row is a link, not a recommendation surface, so the D3 surface map is
> untouched by it.
>
> Either choice closes NAV-01. There is no keep-five option: leaving the contradiction
> open means doing the nav work twice.
>
> **Chosen option: ____   Signed: ____ (initials, date)**

### WI-5 — D2: the schedule-semantics spec

Create `docs/gameplan/SCHEDULE_SEMANTICS.md` with the following content, and put under
ROADMAP § Decisions D2 the line: "Spec: `docs/gameplan/SCHEDULE_SEMANTICS.md`. Phase 3
derives the `schedule_slots` DDL from this signed spec; Phase 4 implements the
derivation. **Signed: ____ (initials, date)**".

> # Schedule semantics — signed spec
>
> ## The model
>
> The week is an **ordered cycle of slots**, persisted in `schedule_slots(id, position,
> routineId? FK→routines ON DELETE CASCADE, focusKind?, anchorDay? 0–6, createdAt,
> updatedAt)`. A slot is a **routine slot** (routineId set), a **focus-only slot**
> (focusKind set, routineId null — the planner may propose a routine for it), or, by
> convention, absent — days with no slot are rest. `anchorDay`: 0=Monday … 6=Sunday
> (ISO order, independent of the week-start display preference).
>
> ## Derivation rules
>
> The effective week is a **pure function** of (stored slots, completion history,
> today). It never writes; stored slots are input-only.
>
> 1. A slot is **satisfied** this week when a finished session traces to it (started
>    via its pin) or matches its routine within the current week. "Next up" is the
>    first unsatisfied slot in cycle order.
> 2. A satisfied slot renders on the day its session finished.
> 3. An anchored, unsatisfied slot renders on its anchor day when that day is still
>    reachable (≥ today, not taken by an earlier slot). A **missed anchored day shifts
>    forward** to the next open day — never skipped.
> 4. An unanchored, unsatisfied slot renders on the earliest open day ≥ today that
>    preserves cycle order.
> 5. Cycle order beats anchors: if an earlier slot must occupy a later slot's anchor
>    day, the anchored slot shifts forward.
> 6. Satisfaction resets at the week boundary: the cycle restarts each week; unfinished
>    slots do **not** carry over as debt. *(If you want carry-over instead, strike this
>    rule and initial the margin — the DDL is unaffected; only the derivation changes.)*
> 7. Planner regeneration proposes fills for **empty (focus-only) slots only**; an
>    accepted fill persists into that slot's routineId. User-created slots are never
>    touched by regeneration.
> 8. Deleting a routine cascades its slots away; the derived week heals (Phase 4's
>    reconciliation test proves it). A pin whose routine still exists but has zero
>    exercises keeps its slot, and starting it surfaces an explicit error — never a
>    silent free workout.
>
> ## Worked examples
>
> Week of **Mon 24 – Sun 30 Aug 2026**. Stored slots: **slot 1** Push (anchor Mon),
> **slot 2** Pull (no anchor), **slot 3** Legs (anchor Fri). Baseline derived week on
> Monday morning, nothing trained: **Push Mon · Pull Tue · Legs Fri**, rest otherwise.
>
> **1 — Missed anchored day.** Monday passes with no session. Tuesday's derived week:
> **Push Tue** (shifted, rule 3) **· Pull Wed · Legs Fri** (anchor still reachable).
> Stored slots unchanged, byte for byte.
>
> **2 — Missed unanchored day.** Push finished Mon. Nothing Tue or Wed. Thursday:
> next up is still Pull → **Pull Thu · Legs Fri**. If nothing happens until Friday:
> **Pull Fri** (cycle order takes Legs' anchor day, rule 5) **· Legs Sat** (shifted).
>
> **3 — Week rollover.** The week ends with Push (done Tue 25) and Pull (done Thu 27)
> finished; Legs never happened. Monday 31 Aug: satisfaction resets (rule 6) — the
> derived week is the baseline again: **Push Mon 31 · Pull Tue 1 · Legs Fri 4**. Last
> week's unfinished Legs is not owed.
>
> **4 — Regeneration.** Stored: slot 1 Push (anchor Mon), slot 2 focus-only "PULL"
> (routineId null), slot 3 Legs (anchor Fri). Derived week shows **Push Mon ·
> [Pull-focus, proposed: Pull] Tue · Legs Fri**. Tapping regenerate re-proposes for
> slot 2 only; accepting writes Pull into slot 2's routineId (persisted — the week
> stops reshuffling). Slots 1 and 3 are untouched, including their `updatedAt`.
>
> **5 — Routine deletion.** The Pull routine is deleted. CASCADE removes slot 2.
> Stored: slots 1 and 3. Derived week: **Push Mon · Legs Fri**; next up after Push is
> Legs. No orphan, no error. (Contrast: emptying Pull to zero exercises without
> deleting it keeps slot 2, and starting it shows the explicit error state — rule 8.)

### WI-6 — D3: the recommendation-surface map

Insert under ROADMAP § Decisions D3, verbatim:

> Recommendations appear on exactly four surfaces, and nowhere else. **Home:** one
> "next session" module — today's plan and the top recommendation composed into a
> single card with a one-line reason; never two recommendation slots. **Body:** the
> full explanation cards. **The start-options sheet** and **the in-workout add-exercise
> sheet:** action surfaces, one pinned suggestion each. Phases 5 and 6b both conform to
> this map; any executor adding a fifth surface is wrong.

### WI-7 — D4: the cut list

Insert under ROADMAP § Decisions D4, verbatim:

> Cut, recorded 20 Aug 2026. None of these may reappear in a packet without a new
> signed decision here.
>
> - **LLM / chat coach** — breaches DESIGN_AUDIT §15 and offline-first; the rule engine
>   is the coach.
> - **Muscle-head-level granularity** — not derivable from set logs; weighted
>   primary/secondary credit is the ceiling.
> - **Day and year heat windows** — windows are This week + Last 30 days (Phase 5).
> - **Per-routine equipment override** — a variant is its own catalog row
>   (`movementKey` family); DESIGN_AUDIT §7's row is superseded.
> - **The FK re-pointing custom-merge tool** — collisions are skip-and-surface: the
>   seeder always inserts the built-in and flags the collision; a "needs attention" row
>   in Library lets the owner rename their custom or keep both. History FKs are never
>   rewritten.
> - **The line-art commission ($1.5–4k)** — imagery is Compose-drawn composed
>   thumbnails (Phase 8); the commission survives only as a non-committal appendix
>   there.
> - **A1 as a phase** — opportunistic, 2-day timebox, gates nothing.
> - **The rest overlay bubble** — reaffirming ROADMAP Phase 6; DESIGN_AUDIT §10.2 now
>   carries the superseding banner.

### WI-8 — D5: branch ground truth + DEVELOPMENT.md correction

Insert under ROADMAP § Decisions D5:

> `main` holds only the initial commit; the entire app history lives on
> `claude/app-hierarchy-navigation-cjzigo`, which is many dozens of commits ahead and
> still growing (the executor states the exact count from `git rev-list --count` in the
> PR body rather than freezing a number in this doc). Resolution (owner chooses at the
> checkpoint): **(recommended)** merge that branch into `main` via this phase's PR, or
> record branch-as-trunk here. Thereafter: branch-per-phase `claude/phase-<n>-<slug>`,
> one PR per phase, owner merges, no phase starts before the previous PR lands
> (`docs/gameplan/PROTOCOL.md` §2–§3). **Chosen: ____   Signed: ____**

And in `docs/DEVELOPMENT.md`, replace the whole opening paragraph of the Committing
section (lines 144–145 — both the "Trunk-based: commit to `main`…" sentence and the
"Branch only when work spans several sessions or you want CI to vet it before it
lands." sentence, which contradicts branch-per-phase) with:

> Branch-per-phase: work lands on `claude/phase-<n>-<slug>` branches, one PR per phase,
> merged by the owner — see `docs/gameplan/PROTOCOL.md`. (`main` was empty of app code
> until 20 Aug 2026; do not trust older claims of trunk-based flow.)

### WI-9 — D6: the second-pass amendments (informational)

Insert under ROADMAP § Decisions D6, verbatim:

> Recorded 21 Aug 2026, after the second-pass adversarial audit (six independent
> auditors, every finding evidence-verified against the repo). These amend **how** the
> game plan executes; they do not change what it builds, and nothing here reopens D1–D5.
>
> - **Execution order changes; phase numbers do not.** Phase numbers are identifiers,
>   not sequence. The order is **0 (Decisions) → 2 (Test substrate) → 1 (Session
>   hygiene) → 3 (Schema v2) → 4 (Plan tab) → 5 (Heat & coach) → 6a (Tabs + Body) →
>   6b (Home) → 7 (Catalog) → 8 (Imagery, optional)**. Phase 2 is the phase that ships
>   `tools/preflight.sh` and the Robolectric lane, so running it first (a) keeps Phase
>   2's verified gate literals true rather than stale, (b) gives Phase 1's gates a
>   working domain-test lane instead of a command that exits non-zero on a cold clone,
>   and (c) gives Phase 1's repository writes — restore-set, repeat-session,
>   delete-finished-session — a Robolectric lane they otherwise lack. The cost is that
>   session hygiene reaches your phone roughly one to two executor-days later.
> - **Phases 1a and 1b merge into one phase: "Phase 1 — Session hygiene."** One branch
>   `claude/phase-1-session-hygiene`, one PR, one combined owner evening. Both packets
>   go to the same executor session and are executed in order — 1a in full with its gate
>   green, then 1b on top. 1b's gate greps re-assert 1a's invariants, so the sequence
>   self-verifies. Saves one owner evening; no safety is lost.
> - **The full second-pass findings and the rest of this reconciliation:**
>   `docs/gameplan/SECOND_PASS.md`.
>
> **No signature required; recorded for the record.**

## 6. Out of scope

- **Any Kotlin, Gradle, schema, or resource change.** This phase touches `docs/` only.
- Implementing the LiveSessionBar, the Plan tab, or any tab change (Phases 1/4/6a).
- Writing `tools/preflight.sh` (Phase 2 owns it; PROTOCOL §6 specifies it).
- Deriving or writing any `schedule_slots` DDL (Phase 3, from signed D2).
- Touching `docs/HIERARCHY_PLAN.md` at all — its §4 supersession banner already landed
  with commit `6be86fa` and its committed wording is authoritative (WI-2e is a no-op).
- Authoring later phase packets, fixing the GitHub Actions billing block, or touching
  `docs/UI_REDESIGN.md` (its §6 remains the recorded target; D1 adjudicates it without
  editing it).

## 7. Acceptance gate

Run from the repo root on `claude/phase-0-decisions`; expected results in comments.

```bash
git diff --stat claude/app-hierarchy-navigation-cjzigo...HEAD -- app/ tools/ .github/
# expect: empty output (docs-only phase)

ls docs/gameplan/SCHEDULE_SEMANTICS.md
# expect: the path echoed back — this is the ONLY new file this phase creates.
# (`ls docs/gameplan/` itself lists the full committed set — 16 .md files at HEAD,
# 16 with SCHEDULE_SEMANTICS.md. Do not assert a three-file directory; it never was one.)

grep -n "## Decisions" docs/ROADMAP.md            # expect: exactly one hit
grep -c "Signed:" docs/ROADMAP.md                  # expect: >= 3 (D1, D2/D5 signature lines)
grep -n "game plan" docs/ROADMAP.md | head -1      # expect: the new section heading
grep -c "Superseded 20 Aug 2026" docs/ROADMAP.md      # expect: >= 1 (WI-2a)
grep -c "Superseded 20 Aug 2026" docs/DESIGN_AUDIT.md  # expect: >= 3 (WI-3a/3b/3c)
# These two files are the ones this phase actually edits; both read 0 before the phase,
# so the gate can detect a skipped edit. docs/HIERARCHY_PLAN.md already carries its §4
# banner from commit 6be86fa (1 hit pre-phase) — it is NOT part of this gate, because a
# grep that passes before the work is done proves nothing.
grep -n "clock in the notification shade" docs/DESIGN_AUDIT.md   # expect: one hit in §17
grep -n "Worked examples" docs/gameplan/SCHEDULE_SEMANTICS.md    # expect: one hit
grep -rn "overlay bubble" docs/ROADMAP.md
# expect: exactly two hits — Phase 6's "Recorded decisions **not** to build" line and
# D4's cut-list bullet; both record the bubble as not-built. (ROADMAP's other "overlay"
# mention, "elevation overlay" in the Phase 5 section, is Material theming — unrelated
# and left alone.)
```

**Domain tests: none.** This phase adds no code and no tests; the domain-test list is
empty by design. The eight static checks are not run (no source change) — record that
in the PR description rather than faking a green.

The phase then waits at the **blocking checkpoint** (§8). It closes only when D1, D2,
and D5 carry the owner's initials and the PR is merged per D5's chosen ground truth.

## 8. Owner device checklist

You are signing the two decisions everything else builds on. Steps 1–2 are on the
phone; steps 3–6 and 8 are reading the PR (GitHub on any screen); step 7 is a standing
errand you can do any time — it gates nothing.

1. Open the app. Look at the bottom bar: five tabs — Home, Body, Routines, Library,
   History. This is what D1 changes. Note which tabs you actually touch in a week.
2. Tap Home's weekly card, then find the same week via Settings → schedule. That
   orphaned screen is what the Plan tab replaces.
3. In the PR, read **D1** in ROADMAP.md. Circle Option A or B, add initials + date on
   the Signed line. (**A — four tabs, Home · Body · Plan · History — is the
   recommendation**, and it is what you originally asked for; B is the three-tab merge,
   which is still a legitimate choice but puts the session log five sections down a
   single Body screen. Read D1's "why the recommendation flipped" paragraph before you
   circle.)
4. Read **`docs/gameplan/SCHEDULE_SEMANTICS.md`** — especially the five worked
   examples. For each, ask: "is this what I'd expect my week to do?" Pay attention to
   example 3 (a missed Legs day does *not* carry into next week — strike rule 6 and
   initial the margin if you want carry-over instead). Sign D2.
5. Skim **D3** (where recommendations appear) and **D4** (what is cut). These are
   informational; flag anything you disagree with as a PR comment now — they are much
   cheaper to change today than mid-phase.
6. Decide **D5**: merging the working branch into `main` is recommended. Record the
   choice, sign, and merge the PR.
7. **The CI billing errand — requested, but explicitly NOT a gate.** About 30 minutes,
   at your convenience, on any screen: GitHub Actions has never run for this repo
   because of an account billing block, so *no* phase gate depends on CI (gates are
   owner-machine output pasted into the PR; CI green is an additional check once this is
   done). Any one of these clears it — add a payment method / raise the $0 spending
   limit, make the repo public, or attach a self-hosted runner; see `docs/DEVELOPMENT.md`
   § "Continuous integration". Doing it removes the plan's single biggest bottleneck,
   and it specifically saves you a round-trip in Phase 3, whose schema `2.json` otherwise
   has to be fetched off your machine by hand. Not doing it blocks nothing.
8. Observe after merge: `docs/ROADMAP.md` on the trunk shows the game-plan table and
   your signatures. Later phases will refuse to start without them.

## 9. Estimates

- **Executor:** 0.5–1 day (the edits are enumerated; the schedule spec is the only
  writing of substance).
- **Owner:** 0.5–1 day, and it is the plan's only blocking day: two real decisions
  (D1, D2) plus a merge. Until this closes, zero later packets may execute.

## 10. Hand-back

The completion report to the owner must contain:

1. The PR link and the list of files changed (must be docs-only; say so explicitly).
2. D1 as chosen (three-tab or four-tab) and its one-sentence consequence for Phase 6a's
   scope.
3. D2 as signed, noting whether rule 6 (no week carry-over) survived or was struck —
   Phase 3's DDL and Phase 4's derivation both read this.
4. D5 as chosen (merge-to-main or branch-as-trunk) and the exact trunk name later
   phases branch from.
5. Confirmation that the acceptance-gate greps all passed, quoted verbatim.
6. Anything the owner flagged on D3/D4 during review, and whether it changed the
   recorded text (if it did, the revised text in full).
7. The reminder that the next phase is **2 — Test substrate** on branch
   `claude/phase-2-test-substrate`, and that its packet may now be executed. (Phase 2
   runs before Phase 1 under D6; phase numbers are identifiers, not sequence. Phase 1 —
   Session hygiene, the merged 1a+1b packets on the single branch
   `claude/phase-1-session-hygiene` — follows it.)
8. D1 restated as chosen, in one line, at the top of the report: **four tabs** or
   **three tabs**. If three tabs was chosen, say explicitly that PHASE_6A's and
   PHASE_6B's three-tab mitigations are now **mandatory, not optional** — the
   `section=sessions` scroll anchor alongside `section=calendar`, and the "Last session"
   link row on Home — and that both are in scope for their phases' gates.
