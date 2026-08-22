# Hierarchy plan — 20 August 2026

The owner asked for a five-tab hierarchy (Home, Body, Routines, Library, History), a
Home reworked into an overview page, a Body page with a heat-mapped silhouette and an AI
trainer, a Routines page that also allocates days, a ~100-exercise library with images and
equipment variations, and a History fed by an unmistakable start/finish loop — and asked
for this to be **audited and critiqued, not implemented blindly**.

This document is that audit: five code readers swept every screen and the docs, five
specialist critics (information architecture, gym-floor UX, content strategy, coaching,
sequencing) judged the proposal against what they found. [ROADMAP.md](ROADMAP.md) remains
the source of truth for what is being built; nothing below is committed until the
decisions in §3 are recorded there.

Interactive report:
**https://claude.ai/code/artifact/767dd64c-177f-447b-9efa-74200edd5f3e**

> The link is private to the repo owner's Claude account. This file is the in-repo copy
> and carries the same content.

---

## 1. The punchline

**The proposed hierarchy already ships.** The bottom bar is literally Home, Body,
Routines, Library, History, in that order (`AppNav.kt:157-163`). Start/finish already
works end-to-end and cannot silently lose a workout: one transactional in-progress
session, drafts that survive process death, an idempotent finish that lands on a summary
and files the session into History. The Body tab already draws a front/back silhouette
with tappable muscle heat, per-muscle detail, and rule-based "hit this next" cards. A
weekly planner already exists. Most of what the proposal asks for is therefore not new
structure — it is **depth in the right places**, and much of that depth is already
scoped as Phase 4.

The audit found the proposal right about four things, wrong about four, and silent about
the two defects that matter most.

**Right:**

1. **Plan-building belongs in Routines.** Today the weekly plan is architecturally
   orphaned — a pushed screen reachable only via an unlabeled tap on Home's hero card and
   a Settings row, with no link to or from the Routines tab. The single worst IA defect
   in the app; the owner found it by feel.
2. **The schedule must be consistent.** Today's week is planner-inferred, never
   persisted, and can silently reshuffle when a workout finishes or a routine changes.
   "Allocate routines to days" has no schema, no UI, and no owning phase — genuinely new
   scope, and the best product judgment in the proposal.
3. **The library needs variations, images, and scale.** 37 seeded exercises, name +
   free-text muscle group only, no equipment, no imagery — and the seed runs only on an
   empty table, so *any* catalog growth before versioned seeding never reaches the
   existing install. Phase 4 already specifies the fix verbatim.
4. **Deep underneath, simple on the surface.** Already the recorded bar
   (DESIGN_AUDIT.md:5, the Instrument language). Settled doctrine, not new direction.

**Wrong:**

1. **Home as "primarily informational" hurts the primary user.** Mid-rest with 90
   seconds on the clock, Home's only job is *resume in one tap*; between sessions it is
   *start today's workout in one tap*. Strong, Hevy and Boostcamp all converge on
   home-as-launcher. The overview appetite is real but is served by a glanceable
   masthead, a week strip, and links into Body/History — not by demoting the start hero.
2. **"Front head of the triceps" is false precision.** Set logs record exercise ×
   weight × reps; head-level loading is not derivable from that, and a map that claims it
   will be caught lying by any anatomy-literate lifter within a week. Today compound
   lifts credit exactly **one** muscle group — fix crediting before adding resolution.
   The honest ceiling: weighted primary/secondary credit now, and later a sub-group split
   for delts and back only.
3. **Day and year heat windows are not training decisions.** "Day" is the session log;
   "year" under the current normalization is a meaningless smear. Worse, heat is
   normalized to the window's own hottest muscle (`MuscleLoadCalculator.kt:87-90`), so
   one light arm day in an empty week paints biceps "High", and the recommendation
   engine changes its advice when the display window changes. The decision-relevant
   horizons are **this week** and **~30 days**, on an absolute sets-per-muscle-per-week
   scale — a domain-model fix that must precede any new window chips.
4. **An LLM "AI personal trainer" breaches two recorded decisions** — AI chat is an
   explicit non-goal (DESIGN_AUDIT §15) and the README promises training works offline
   with network as backup only. It would also add almost no signal: everything
   trustworthy from set logs (volume, recency, imbalance, progression) is already
   computable by rules, and what an LLM could phrase well (fatigue, soreness, injury)
   uses data the app does not collect. The funded path is making the existing rule
   engine honest and specific.

**Silent on the two defects that matter most:**

1. **Sessions can strand in limbo.** A zero-set session can *never* be finished — only
   discarded from inside the workout screen, or left in-progress forever
   (`ActiveWorkoutViewModel.kt:660-663`). An abandoned session persists indefinitely,
   silently blocks every new start, never appears in History, and books the full
   wall-clock gap as duration if finished late. *This* — not a bigger button — is the
   truth behind the owner's start/finish worry.
2. **The log cannot be corrected or reused.** A typo'd 225-instead-of-125 is permanent,
   an accidental session cannot be deleted, and "repeat last session" — the single
   highest-frequency real-world start path — does not exist.

---

## 2. Page-by-page verdict

| Page | Today | Verdict on the proposal |
|---|---|---|
| **Home** | Start-centric status page; hero card with a mis-tap trap (card body opens Schedule, button starts workout); duplicates Body/History/Schedule at lower fidelity; only door to Settings | Keep the launcher heart, add the overview around it: masthead that answers the day ("PUSH DAY · 4 LIFTS"), week strip, calendar jump, up to two recommendations, live in-progress telemetry. Kill the dual-affordance hero. |
| **Body** | Front/back silhouette, 10 muscle groups, 3 windows (7D/14D/this week), per-muscle sheet, 5-rule recommendations | Substantially built. Fix heat semantics (absolute weekly set bands), decouple coaching from the display window, then windows become This week + 30 days. Head-level metrics: never. |
| **Routines** | List + editor only; day allocation does not exist anywhere; planner auto-infers the week on a detached screen | The proposal's best idea. Becomes **Plan**: pinned routine→day schedule (rotation-first with weekday anchors so a missed day shifts rather than skips), planner fills only unpinned days, plan persisted — never silently rewritten. |
| **Library** | 37 exercises, no equipment/images/families; seed never re-runs; picker degrades linearly with size | Right ask, already Phase 4. Variation = its own exercise row (own history/PRs), grouped by a `movementKey` family; ~98 curated movements, not a cartesian product; composed vector thumbnails give 100 % image coverage in <1 MB on day one, line art later. |
| **History** | Month calendar + flat all-time list; finished sessions immutable; in-progress sessions invisible | Start/finish already works; the gaps are edit-after-finish, delete, **repeat last session**, month grouping, and surfacing the in-progress session. |

---

## 3. The decision only the owner can make

There is a live, unresolved contradiction inside the project's own doctrine. The app
ships five tabs; DESIGN_AUDIT NAV-01 says "five tabs is a lot for a logging app";
UI_REDESIGN §6 records a three-tab target IA (Today / Plan / Progress + a persistent
LiveSessionBar) that Phase 5 deliberately deferred as "the largest remaining item." The
proposal re-asserts five tabs against that recorded direction — silently keeping five is
a decision, and it must be written down either way.

**Recommendation: three tabs — Home · Body · Plan — plus the LiveSessionBar.** This *is*
the recorded IA, with one change in the owner's favor: the reflection tab keeps the name
**Body** and leads with the silhouette. History does not disappear — its calendar and
session log move *under* the body map, making Body the single strong "what has my
training done" surface instead of two thin ones (Body is a dead end on first launch
today; History is "a receipt, not a story"). Library stops being a tab — its real job is
feeding routines and the mid-workout picker — and lives on as a pushed screen entered
from Plan, from recommendation cards, and from the muscle detail sheet. Every pushed
route survives unchanged.

The **LiveSessionBar** — a docked strip above the tab bar on every tab whenever a
session is in progress (elapsed, sets, rest countdown; tap to resume; overflow to
finish/discard) — is the answer to "where does the active workout live so it can never
be lost." It is chrome, not a tab, and it deletes Home's easy-to-miss
headline-plus-relabeled-button representation.

**Fallback if the owner overrules:** four tabs (Home · Body · Plan · History), Library
still demoted. Either outcome closes NAV-01 and UI_REDESIGN §6 with a recorded decision;
the worst outcome is leaving the contradiction open and doing the nav work twice.

---

## 4. The recommended plan

> **Superseded 20 Aug 2026, same day.** This section was adversarially attacked from
> five angles (62 findings, 8 fatal — including two schema designs below that could not
> work as written) and rebuilt as the executable game plan in
> [archive/gameplan/](archive/gameplan/README.md), which wins wherever they differ. The section is kept
> as the audit's original recommendation; the phase-by-phase spec packets in
> `docs/archive/gameplan/` are what gets executed.

Phases keep the roadmap's numbering and land in order, each behind a verifiable gate.

**Phase 3.9 — Decisions (docs only, S).** Adjudicate the IA (§3) and record it. Add to
the not-to-build list: LLM/chat coach, muscle-head granularity, day/year heat windows,
the DESIGN_AUDIT §7 per-routine equipment override (superseded by variation-as-row).
Resolve the rest-overlay contradiction in favor of ROADMAP. Gate: docs self-consistent,
owner sign-off.

**Phase 3.95 — A place for tests to run (S).** CI has never executed once (account-level
spending block); Phase 4's gate is a MigrationTestHelper suite with nowhere to run.
Fix the billing block or commit a local instrumented-runner script. Gate: one green run.

**Phase 4 — Schema v2, as scoped, plus the fields that prevent a v3 (L).** Everything
already listed (equipment, loadType, imageKey, canonical muscles with secondary credit,
versioned catalog seeding, UNIQUE constraints, A1 DI seam, editable finished sessions,
imbalance-by-sets, RPE consumption, planner fixes) **plus**: `movementKey` (variation
families), an `exercise_muscles` junction table with open string keys and contribution
weights (so finer taxonomy later is a data change, not a migration),
`seed_meta.catalogVersion`, a `routine_schedule` table (routineId × day) so pinning
rides this same reviewed migration, a partial unique index on `lower(name)` for
built-ins only, and the two limbo policies (zero-set sessions get a one-tap
"nothing logged — discard?"; sessions open past ~4 h get a finish-or-discard nudge with
duration capped at last-set time). One migration, first ever, against the owner's only
real dataset: auto-backup before migrating, and test v1→v2 against a copy of the real
database. Gate: migration suite green; upgrade-in-place on the real phone with zero
loss; a seed bump proven to reach the existing install.

**Phase 4b — The catalog (M code + M content).** ~98 curated movements with equipment,
family, weighted muscle credits, aliases, and popularity rank (≈800 hand-authored data
points — the bulk of the effort is editorial, and errors feed the heat map silently, so
it needs a review pass plus a domain test asserting invariants). Curated, not cartesian:
the big six families get 3–4 equipment variants, isolation lifts 1–2. Ships **with** the
picker UX that keeps 100 rows usable one-handed: family grouping, equipment filter
chips, recency/popularity ordering, wildcard-escaped search. Custom-name collisions get
a user-confirmed merge (one transaction re-pointing history FKs — the most dangerous
write ever added; transactional, tested, preceded by auto-backup), never a silent skip
or merge. Gate: new entries arrive on upgrade; zero duplicate names; picker verified on
device.

**Phase 4c — Imagery as content, decoupled from schema (L, mostly content).** Day one:
composed vector thumbnails — an equipment glyph over a mini silhouette with the primary
muscle lit on the existing magma ramp — ~20 vectors, <1 MB, 100 % coverage,
automatically on-language with the dark Instrument aesthetic. Later: a commissioned
single-weight line-art pass (work-for-hire, ~$1.5–4k for 100), hard ceiling 5 MB. No
photo sets: licensing friction and a fatal aesthetic clash with the design language.
Nothing downstream waits on art. Gate: every row renders an image or the fallback;
APK delta within budget.

**Phase 4.5 — Plan (M–L).** The genuinely new scope, on the Phase-4 table: pin "Push on
Mondays"; the planner demotes to filling unpinned days and never moves a pin;
regeneration is explicit and confirmed; rotation-first semantics with weekday anchors so
a missed Monday shifts the week instead of skipping Push. Schedule's surface moves into
the Plan tab; Settings gets a second home so it stops being Home-only. Write the
pin/planner reconciliation rules as pure-domain code with tests **before** UI. Gate:
domain tests prove pins survive regeneration, data changes, and routine deletion.

**Phase 5.5 — The IA lands: Today-shaped Home + LiveSessionBar (M).** Consolidate the
tab bar per the §3 decision. Home keeps its single filled Start/Resume hero (whole card
= one action; a separate labeled row opens the plan), gains the masthead, week strip,
calendar jump, second recommendation slot, and loses the duplicate heat card and Recent
list to Body. LiveSessionBar ships here with finish/discard in its overflow.
Ready-to-progress rows deep-link the named lift instead of the generic picker. "Repeat
last session" lands on History rows and SessionDetail. Finish salience judged on device
before restyling — one-tap-no-dialog stays (confirm destruction, never completion).
Untangle ThisWeekHomeCard out of ScheduleScreen.kt first; re-audit every cross-tab
navigation target when History and Library stop being tabs. Gate: token check at zero
violations; exactly one filled start control on Home; scripted device pass over tab
switching, deep links, process death, and notification resume.

**Phase 5.6 — Honest heat, honest coach (M).** Domain first: absolute weekly
sets-per-muscle bands (untrained / low / productive / high) replace relative-max
normalization; the coach computes from a fixed window regardless of the display chip;
windows become This week + Last 30 days. Then the sanctioned "AI trainer": the rule
engine names specific lifts the user owns (with 4c imagery), reads RPE, takes goals and
available equipment as inputs, gains symmetric "do less" rules (deload/rest — today four
of five rules only ever say *more*), and adopts a codified voice: imperative, data-cited,
one sentence plus one reason line, no praise, no chat. Recommendations surface where
action happens: the start picker and the in-workout add-exercise sheet, with Body as the
explanation surface. Gate: domain tests per rule; advice invariant under display-window
changes.

**Later, unchanged:** Phase 6 platform items. An optional LLM tier only ever as an
opt-in, user-keyed, **narrator** over rule-engine numbers — never a decider, never
default-on, never chat UI. On-device small models: not worth it at current quality/size.

---

## 5. Cut, with reasons to record

- **Muscle-head-level metrics** ("front head of the triceps") — not derivable from set
  logs at honest confidence; decorative precision that erodes trust in the whole map.
  Ceiling: weighted primary/secondary credit; later, front/side/rear delts and
  lats/upper/lower back only.
- **LLM chat coach** — breaches offline-first doctrine and a recorded non-goal for
  near-zero informational gain; the design language is sentence-averse by constitution.
- **Day and year heat windows** — the calendar already answers "what did I hit that
  day"; year-scale relative heat is meaningless. Replaced by This week + 30 days on
  absolute bands.
- **Per-routine equipment override** (DESIGN_AUDIT §7) — two identity axes for the same
  fact; variation-as-row plus "swap to sibling variant" covers it.
- **Anatomical silhouette repaint before catalog imagery** — art budget belongs on the
  catalog first; the schematic figure is recorded as deliberate v1.

## 6. Top risks

1. **The migration** — the app's first ever, against the owner's daily-driver phone,
   with a test harness that currently has nowhere to run. Sequence 3.95 first; backup
   before migrating; rehearse on a copy of the real database.
2. **Content production dwarfs code** — ~800 authored data points feeding the heat map
   silently; a wrong mapping corrupts the Body tab's truthfulness. Review pass +
   invariant tests are part of the phase, not optional.
3. **Design regression by enthusiasm** — the Home rework can casually undo Phase 5's
   deliberate constraints (one accent, one filled start, no completion confirms). The
   token script catches raw values but not accent-budget or one-spine violations; those
   are review discipline.
4. **Planner/pin reconciliation** — if the ephemeral planner keeps ownership of the
   week, pins get silently reshuffled and the "inconsistent schedule" complaint returns
   under a new name. Domain-tested rules before UI.
5. **State-graph regressions** — the Phase 2 hardening (one-shot navigation, single
   in-progress invariant, draft persistence, consume-once deep links) must survive the
   IA work verbatim; every new start surface routes through StartTrainingDay.

## 7. Method

Five parallel code readers (navigation/Home, Body/Schedule, Routines/Library,
workout/History, docs) mapped the app with file:line evidence; five critics
(information architecture, gym-floor UX, library content strategy, AI coaching, delivery
sequencing) then judged the proposal against the combined map. ~966 k tokens of
independent review; findings above are the convergent subset, and every load-bearing
claim was verified against the code.
