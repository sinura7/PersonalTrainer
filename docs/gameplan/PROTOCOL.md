# PROTOCOL — how every phase of the hierarchy game plan executes

**Status:** binding execution protocol. Committed as `docs/gameplan/PROTOCOL.md` by Phase 0.
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

Verified against the repo on 20 Aug 2026:

- `main` holds **only** the initial commit (`1b7eb6a`).
- All real work — 53 commits, the entire app — lives on
  **`claude/app-hierarchy-navigation-cjzigo`** (tip `2212628` at protocol time).
- `docs/DEVELOPMENT.md:144` ("Trunk-based: commit to `main`") is false in practice;
  Phase 0 corrects it.

**Never branch a phase from `main` until Phase 0's checkpoint resolves this.** Phase 0
either merges `claude/app-hierarchy-navigation-cjzigo` into `main` (owner approves the PR)
or records branch-as-trunk in `docs/DEVELOPMENT.md`. The recorded outcome in ROADMAP's
Decisions section is the ground truth every later phase branches from.

## 3. Branch-per-phase, PR-per-phase

- One branch per phase, named **`claude/phase-<n>-<slug>`** (e.g.
  `claude/phase-1a-session-lifecycle`). The `claude/` prefix is mandatory: CI triggers
  only on `main`, `claude/**`, `cursor/**` (`.github/workflows/ci.yml:13`); any other
  name gets zero verification.
- One PR per phase, into the trunk recorded by Phase 0. The owner reviews and merges.
- **No phase starts before the previous phase's PR is merged.** No stacked phases, no
  parallel phases. (The one exception: the A1 DI seam, §7, which is not a phase.)
- Sub-slices inside a phase (where a packet defines them) are separate commits on the
  phase branch, in the packet's order, each leaving preflight green.

Note on CI: the GitHub Actions account block (`docs/DEVELOPMENT.md:95-105` — runner never
assigned, $0 spending limit) is a **standing owner errand** (add a card, make the repo
public, or stand up a self-hosted runner). It gates **nothing**. Executors do not attempt
to fix it and no phase's acceptance gate depends on a CI run.

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
  packets do not schedule more than one device pass per landing.

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
a **Phase 2 work item** with exactly these contents:

```bash
#!/usr/bin/env bash
# Preflight: all eight static checks + the domain tests. Green before every push.
set -u
cd "$(dirname "$0")/.."
fail=0
run() { echo "== $*"; "$@" || fail=1; }
run python3 tools/check-named-args.py app/src/main/java
run python3 tools/check-named-args.py app/src/test/java
run python3 tools/check-when-exhaustive.py app/src/main/java
run python3 tools/check-unused-imports.py app/src/main/java
run python3 tools/check-internal-imports.py app/src/main/java
run python3 tools/check-missing-imports.py
run python3 tools/check-design-tokens.py app/src/main/java
run python3 tools/check-screen-wiring.py app/src/main/java
run tools/syntax-check.sh app/src/main/java
if [ -d "${PT_JARS:-build/test-jars}" ]; then
  PT_JARS="${PT_JARS:-build/test-jars}" run tools/run-domain-tests.sh
else
  echo "!! FAIL: domain tests need a jar directory — see tools/README.md bootstrap"
  fail=1
fi
exit $fail
```

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
in-session. Until Phase 2 lands, phases run the eight checks individually and say so.

Additionally: **`check-when-exhaustive.py` and `check-screen-wiring.py` must be cited in
the phase's hand-back wherever an enum gains/loses a variant or a screen callback
changes** — that is the mechanical proof that no `when` or wiring survivor remains.

## 7. The phases, in execution order

Value-first: if the plan stops halfway, the owner has session hygiene and the pinned
week — not substrate and a picture book.

| Phase | One line | Executor-days | Owner-days |
|---|---|---|---|
| **0 — Decisions & doctrine** | Docs only: ROADMAP/DESIGN_AUDIT restructure, IA adjudication, schedule-semantics sign-off, surface map, cut list, branch ground truth. **BLOCKING checkpoint.** | 0.5–1 | 0.5–1 |
| **1a — Session lifecycle** | FinishWorkout/DiscardWorkout use cases, LiveSessionBar, zero-set discard-only policy, 4-hour stale nudge (in-app), RestRemainingStrip deleted, one-live-affordance gate. | 3–4 | 0.5–1 |
| **1b — Log repair** | Editable finished sessions (completedAt preserved), guarded session delete, repeat-last-session, delete-set undo. | 2–3 | 0.5 |
| **2 — Test substrate** | androidTest scaffold + deps, Robolectric JVM lane hosting MigrationTestHelper, smoke migration test vs `schemas/1.json`, `tools/preflight.sh`, connectedAndroidTest runbook. | 1–2 | 0.5 |
| **3 — Schema v2 migration** | ONE additive migration (equipment/loadType/movementKey/imageKey/nameKey, exercise_muscles, seed_meta, schedule_slots from D2), versioned seeding behind the maintenance mutex, batch-1 catalog (the 37) + review artifact, Backup v2 + round trip, pre-open raw DB copy, rehearsal runbook, junction-first heat. | 3–5 | 1–2 |
| **4 — Plan tab & the pinned week** | Reconciliation rules as pure Kotlin first (from D2), ScheduleRepository owns the persisted week (sixth insights source), planner demoted to proposing fills, `insights.weekPlan` rewired, Plan tab built inside the five-tab bar, pushed ScheduleScreen deleted, ThisWeekHomeCard extracted, planner fixes. | 4–6 | 1 |
| **5 — Honest heat & coach** | Absolute weekly-set bands, windows → THIS_WEEK + LAST_30_DAYS, coach on trailing 14 days, RPE, imbalance by weighted sets, per surface map. | 3–5 | 1 |
| **6a — Tab consolidation + Body absorbs History** | Tab bar per D1, Body gains calendar + month-grouped sessions + PRs, nine-site nav retarget checklist, `isTabRoute` shim deleted. Size L, honestly. | 3–5 | 1 |
| **6b — Home "Today" rework** | Masthead string table, week strip on the persisted week, ONE next-session module, start-options sheet replaces StartWorkout interstitial. Separate device pass from 6a. | 2–3 | 0.5–1 |
| **7 — Catalog to ~98 + Library UX** | Staged seed bumps, family grouping, equipment chips, canonical-key muscle filter end-to-end, skip-and-surface collisions, increment table. | 3–5 | staged review |
| **8 — Imagery** | Compose-drawn composed thumbnails (DrawScope + Heat tokens, no VectorDrawable XML), ≤2 MB APK delta. Line-art commission: non-committal appendix only. | 2–4 | 0.5 |

**A1 (DI seam) is NOT a phase.** It is an opportunistic refactor with a hard 2-day
timebox, done only if instrumented ViewModel tests are ever actually scheduled. No phase
gates on it; no packet exists for it.
