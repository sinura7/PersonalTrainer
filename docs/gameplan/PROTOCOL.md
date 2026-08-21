# PROTOCOL — how every phase of the hierarchy game plan executes

**Status:** binding execution protocol. Committed 20 Aug 2026; amended 21 Aug 2026 by the
second-pass reconciliation (`docs/gameplan/SECOND_PASS.md`).
Every phase packet references this document; nothing in it is repeated in packets. Where a
packet and this protocol disagree, this protocol wins.

---

## 1. Who executes

Phases are executed by **fresh Claude Opus sessions**. Each session receives exactly two
documents: this `PROTOCOL.md` and its own phase packet (`docs/gameplan/PHASE_<n>_*.md`).
The session has no memory of the planning conversation that produced the packets. It may
read anything in the repo, but it must not assume anything that is not in the repo, its
packet, or this protocol. If a packet is ambiguous, the executor stops and asks the owner
rather than guessing — a wrong guess against the owner's only real training database is
worse than a stalled phase.

The owner is a non-engineer with Android Studio, the phone, and the release-signed install
holding the only real data. The executor has no device and no Android SDK; **Android
Studio on the owner's machine is the only thing that has ever compiled this app**
(`docs/DEVELOPMENT.md:105`). Every claim of "works on device" belongs to the owner, via
the packet's owner checklist — never to the executor.

## 2. Branch ground truth

Verified against the repo on 20 Aug 2026 and re-verified 21 Aug 2026:

- `main` holds **only** the initial commit (`1b7eb6a`). Nothing else has ever landed there.
- All real work — the entire app, plus `docs/gameplan/` itself — lives on the working
  branch **`claude/app-hierarchy-navigation-cjzigo`**, which is many commits ahead of
  `main`. **No commit count or tip SHA is recorded here on purpose: the branch tip moves,
  and a number written down here rots.** Re-check the tip, the commit count, and the
  ahead-of-`main` distance at phase start (see §6, "Mandatory phase-start re-baseline",
  D-G) with `git log --oneline -1` and `git rev-list --count main..HEAD`.
- `docs/DEVELOPMENT.md:144` ("Trunk-based: commit to `main`") is false in practice;
  Phase 0 corrects it.
- The correct test for "has Phase 0 merged?" is `grep -c "Signed:" docs/ROADMAP.md`
  returning `0` (not merged) or non-zero (merged). The presence of
  `docs/gameplan/PROTOCOL.md` is **not** that test — the game plan, this protocol
  included, is already committed on the working branch.

**Never branch a phase from `main` until Phase 0's checkpoint resolves this.** Phase 0
either merges `claude/app-hierarchy-navigation-cjzigo` into `main` (owner approves the PR)
or records branch-as-trunk in `docs/DEVELOPMENT.md`. The recorded outcome in ROADMAP's
Decisions section is the ground truth every later phase branches from.

## 3. Branch-per-phase, PR-per-phase

- One branch per phase, named **`claude/phase-<n>-<slug>`** (e.g.
  `claude/phase-3-schema-v2`). The `claude/` prefix is mandatory: CI triggers
  only on `main`, `claude/**`, `cursor/**` (`.github/workflows/ci.yml:13`); any other
  name gets zero verification.
- One PR per phase, into the trunk recorded by Phase 0. The owner reviews and merges.
- **Phase 1 is one phase built from two packets.** `PHASE_1A_SESSION_LIFECYCLE.md` and
  `PHASE_1B_LOG_REPAIR.md` share a single branch **`claude/phase-1-session-hygiene`**, a
  single PR, and a single owner evening (D-B). Both packets go to the **same** executor
  session, executed strictly in order: 1A in full, **its §7 acceptance gate green**, then
  1B's work items on top of it. 1B's gate greps re-assert 1A's invariants, so the sequence
  self-verifies. This is the **one** phase whose session receives **three** documents
  (this protocol + both packets) rather than the two described in §1.
- **No phase starts before the previous phase's PR is merged.** No stacked phases, no
  parallel phases. (The one exception: the A1 DI seam, §7, which is not a phase.)
- Sub-slices inside a phase (where a packet defines them) are separate commits on the
  phase branch, in the packet's order, each leaving preflight green.

Note on CI: the GitHub Actions account block (`docs/DEVELOPMENT.md:95-105` — runner never
assigned, $0 spending limit) is a **standing owner errand** (add a card, make the repo
public, or stand up a self-hosted runner). It gates **nothing**. Executors do not attempt
to fix it and no phase's acceptance gate depends on a CI run.

CI has in fact **never executed** in this repo, so a gate line that says "CI green on the
PR" can never be satisfied. Four packets previously carried such lines; the second-pass
reconciliation rewrote every one of them into an **owner-machine gate** — the owner runs
the command locally and pastes the output into the PR — with CI green kept only as an
**additional** check to be re-run once the billing block clears (D-F). Correspondingly,
unblocking billing is now a **requested** (still non-gating) ~30-minute item on Phase 0's
owner checklist: working CI would remove the plan's main bottleneck (owner evenings are
the scarce resource), and Phase 3's `schemas/2.json` retrieval leans on a build that runs
somewhere other than the owner's living room.

## 4. Owner checkpoints

- Every phase ends with an owner checklist — numbered steps on the phone or in Android
  Studio, each with what the owner must observe. **A phase closes only on owner
  sign-off** of that checklist, recorded in the PR before merge.
- Phase 0's checkpoint is **BLOCKING for the whole plan**: the IA decision (D1) and the
  schedule-semantics spec (D2) must be signed in ROADMAP's Decisions section before any
  later phase's packet executes. Phase 3 derives DDL from D2; Phases 4 and 6a build on
  D1. An executor handed a later packet must first verify the signatures exist
  (`grep -n "Signed" docs/ROADMAP.md`) and stop if they do not.
- Estimates convention: every packet states **executor-days** and **owner-days**
  separately, as day ranges. S/M/L labels are banned. The owner-days are real evenings;
  packets do not schedule more than one device pass per landing. Estimates are
  **informational** — they gate nothing. A packet's own §9 governs where it differs from
  §7's table.

## 5. Packet template (contract)

Every packet has these sections, in this order: **1 Mission · 2 Read first · 3 Binding
doctrine · 4 Settled decisions · 5 Work items · 6 Out of scope · 7 Acceptance gate ·
8 Owner device checklist · 9 Estimates · 10 Hand-back.**

Rules: settled decisions are stated as settled and never reopened by the executor; work
items carry exact file paths and shapes (signatures, schemas, composable structure) so two
competent executors produce interchangeable code; acceptance gates are literal commands
plus expected output; doctrine is cited by ID and section (DESIGN_AUDIT IDs,
UI_REDESIGN §5.1 + §8, DIRECTION_B_INSTRUMENT rules).

## 6. Mechanical proof

**`tools/preflight.sh` must be green before every push, on every phase.** It chains the
eight static checks plus the domain tests. The script does not exist yet — creating it is
a **Phase 2 work item**, and **`PHASE_2_TEST_SUBSTRATE.md` WI-4 holds its literal
contents**. This protocol deliberately does **not** duplicate that script. WI-4's version
encodes a **per-check pass criterion** for each of the eight checks, which matters because
exit codes alone are not a sufficient judge: four of the checks
(`check-named-args.py`, `check-when-exhaustive.py`, `check-unused-imports.py`,
`syntax-check.sh`) **always exit 0** and report findings only on stdout, while only
`check-internal-imports.py`, `check-missing-imports.py`, `check-design-tokens.py`, and
`check-screen-wiring.py` exit by finding count. Any preflight that judges on exit code
alone silently passes real findings. Where this protocol and WI-4 appear to describe the
script differently, **WI-4 is the specification**; the executor transcribes it verbatim.

The eight static checks, by filename, as they exist in `tools/` today (the run commands
are recorded at `docs/DEVELOPMENT.md:59-66`):

1. `check-named-args.py` — named arguments vs. declarations
2. `check-when-exhaustive.py` — sealed/enum `when` coverage
3. `check-unused-imports.py` — dead imports
4. `check-internal-imports.py` — in-project names actually exist
5. `check-missing-imports.py` — names used but never imported
6. `check-design-tokens.py` — no raw colours/radii/elevation outside `ui/theme/`
7. `check-screen-wiring.py` — every screen callback is actually called
8. `syntax-check.sh` — parse-level Kotlin diagnostics

(`kotlin_source.py` is a shared library and `build-fonts.py` an asset builder; neither is
a check.) `tools/run-domain-tests.sh` needs a jar directory (`PT_JARS`) that a fresh
clone lacks; Phase 2 documents the bootstrap so the domain half of preflight is runnable
in-session. Under the execution order in §7, **Phase 2 runs first among the code phases**,
so `tools/preflight.sh` exists from Phase 2 onward and **every code phase runs it**.
Phase 0 is docs-only and runs no checks at all. No phase is left running the eight checks
by hand.

Additionally: **`check-when-exhaustive.py` and `check-screen-wiring.py` must be cited in
the phase's hand-back wherever an enum gains/loses a variant or a screen callback
changes** — that is the mechanical proof that no `when` or wiring survivor remains.

### Mandatory phase-start re-baseline (D-G)

**Every count, line number, file path, SHA, and repo-state assertion in a packet is a
baseline captured against audit commit `2212628`, not an oracle.** Phases land ahead of
you; the numbers move. Treating a packet literal as ground truth is how an executor
either stops on a healthy repo or writes a gate that can never go green.

**The rule: the executor's FIRST commit on a phase branch is a re-baseline report.** It is
a docs/PR-body commit, before any work item. It states:

1. The current trunk tip (`git log --oneline -1`) and which phases have merged since
   `2212628`.
2. The actual domain-test totals measured now — test count and test-class count — from a
   real run, not from the packet.
3. **Every packet literal that has drifted**, each with its verified current value: line
   numbers cited by the packet, file counts, seed counts, grep counts, gate-expected
   output strings.

Then the executor **proceeds**, using the re-baselined values in its gate commands and
hand-back.

**Expected drift vs. stop-worthy mismatch.** A mismatch that is **fully explained** by a
merged prior phase, or by the game plan's own commits, is **EXPECTED** — record it, adopt
the new value, continue. A mismatch with **no such explanation** — a file that should
exist and does not, a count that moved in a direction no merged phase could cause, a
symbol the packet says it introduced that is already present — means the packet's world
model is wrong. **Then, and only then, stop and ask the owner.** "The number differs" is
never by itself grounds to stop; "the number differs and nothing in the merge history
accounts for it" is.

*Worked example.* Phase 2's packet asserts `OK (188 tests)` across 24 test classes in 23
files. Phase 2 runs first among the code phases, so at its slot nothing has merged since
the baseline and that literal is expected to be **exactly true** — if the executor
measures 188, it records "no drift" and moves on; if it measures 191 with no merged phase
in between, that is unexplained and stop-worthy. Contrast a line-number citation such as
`docs/DEVELOPMENT.md:144`: an executor starting Phase 3 after Phase 1 merged may find that
line has moved by a few lines because Phase 1 edited the file above it. That is fully
explained by a merged prior phase — the executor records the new line number, cites it,
and continues without asking anyone.

## 7. The phases, in execution order

**Phase numbers are identifiers, not sequence.** The execution order is **0, 2, 1, 3, 4,
5, 6a, 6b, 7, 8** — ten PRs — and the table below is in that order (D-A). A phase's number
never changes, so "Phase 2" means the test-substrate packet wherever it appears in the
sequence.

Value-first, with one substrate exception: Phase 2 is pulled ahead of Phase 1 so that
every later phase has a working proof lane. If the plan stops after Phase 1, the owner has
session hygiene; the pinned week follows.

| Order | Phase | One line | Executor-days | Owner-days |
|---|---|---|---|---|
| 1st | **0 — Decisions & doctrine** | Docs only: ROADMAP/DESIGN_AUDIT restructure, IA adjudication, schedule-semantics sign-off, surface map, cut list, branch ground truth. **BLOCKING checkpoint.** | 0.5–1 | 0.5–1 |
| 2nd | **2 — Test substrate** | androidTest scaffold + deps, Robolectric JVM lane hosting MigrationTestHelper, smoke migration test vs `schemas/1.json`, `tools/preflight.sh`, connectedAndroidTest runbook. | 1–2 | 0.5 |
| 3rd | **1 — Session hygiene (packets 1A + 1B)** | One branch `claude/phase-1-session-hygiene`, one PR, one owner evening. **1A:** FinishWorkout/DiscardWorkout use cases, LiveSessionBar, zero-set discard-only policy, 4-hour stale nudge (in-app), RestRemainingStrip deleted, one-live-affordance gate. **1B (on top of a green 1A gate):** editable finished sessions (completedAt preserved), guarded session delete, repeat-last-session, delete-set undo. | 5–7 | 1–1.5 |
| 4th | **3 — Schema v2 migration** | ONE additive migration (equipment/loadType/movementKey/imageKey/nameKey, exercise_muscles, seed_meta, schedule_slots from D2), versioned seeding behind the maintenance mutex, batch-1 catalog (the 37, keyed on the Phase 7 family vocabulary from the start) + review artifact, Backup v2 + round trip, pre-open raw DB copy, rehearsal runbook, junction-first heat. | 3–5 | 1–2 |
| 5th | **4 — Plan tab & the pinned week** | Reconciliation rules as pure Kotlin first (from D2), ScheduleRepository owns the persisted week (sixth insights source), planner demoted to proposing fills, `insights.weekPlan` rewired, Plan tab built inside whichever tab bar D1 settles on, pushed ScheduleScreen deleted, ThisWeekHomeCard extracted, planner fixes. | 4–6 | 0.5–1 |
| 6th | **5 — Honest heat & coach** | Absolute weekly-set bands, windows → THIS_WEEK + LAST_30_DAYS, coach on trailing 14 days, RPE, imbalance by weighted sets, per surface map. | 4–5 | 0.5–1 |
| 7th | **6a — Tab consolidation + Body absorbs History** | Tab bar per D1, Body gains calendar + month-grouped sessions + PRs, nine-site nav retarget checklist, `isTabRoute` shim deleted. Size L, honestly. | 4–5 | 1–1.5 |
| 8th | **6b — Home "Today" rework** | Masthead string table, week strip on the persisted week, ONE next-session module, start-options sheet replaces StartWorkout interstitial. Separate device pass from 6a. | 2–3 | 0.5–1 |
| 9th | **7 — Catalog to ~98 + Library UX** | Staged seed bumps, family grouping on the movementKey vocabulary, equipment chips, canonical-key muscle filter end-to-end, skip-and-surface collisions, increment table. | 4–5 | staged review |
| 10th | **8 — Imagery** *(optional — deferrable indefinitely; nothing depends on it)* | Compose-drawn composed thumbnails (DrawScope + Heat tokens, no VectorDrawable XML), ≤2 MB APK delta. Line-art commission: non-committal appendix only. | 2–4 | 0.5–1 |

**Why 2 precedes 1:** Phase 2 ships `tools/preflight.sh` and the Robolectric lane, so
running it first keeps its own gate literals true rather than stale, gives Phase 1's gates
a domain-test lane that actually runs on a cold clone (`tools/run-domain-tests.sh:20-24`
exits 2 without the jar directory Phase 2's bootstrap creates), and gives Phase 1's
repository writes (`restoreSet`, `repeatSession`, `deleteFinishedSession`) a Robolectric
lane they otherwise lack. The cost is that session hygiene reaches the owner roughly one
to two executor-days later.

**A1 (DI seam) is NOT a phase.** It is an opportunistic refactor with a hard 2-day
timebox, done only if instrumented ViewModel tests are ever actually scheduled. No phase
gates on it; no packet exists for it.
