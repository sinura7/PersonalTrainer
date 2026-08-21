# The game plan — 20 August 2026

Executable successor to [HIERARCHY_PLAN.md](../HIERARCHY_PLAN.md) §4. That plan was
adversarially attacked from five angles (technical viability, integration seams, scope
realism, internal contradictions, executor readiness — 62 findings, 8 rated fatal);
this directory is the rebuilt result: one spec packet per phase, each written against
the actual code and then independently verified line-by-line, so that a fresh executor
session with no memory of the planning can run a phase from its packet alone.

**Where this directory and HIERARCHY_PLAN.md §4 disagree, this directory wins.**
[ACTION_PLAN.md](ACTION_PLAN.md) is the consolidated master plan — read that first;
[REVISED_STRUCTURE.md](REVISED_STRUCTURE.md) records what the critique changed and why
(its appended amendments carry the second-pass decisions D-A…D-G);
[SECOND_PASS.md](SECOND_PASS.md) is the second-pass due-diligence record — verdict,
decisions changed, defects fixed, what held, the new risk;
[PROTOCOL.md](PROTOCOL.md) is the binding execution protocol every phase follows.

Interactive playbook:
**https://claude.ai/code/artifact/b6f120ae-9185-46aa-84cf-69439cb42752**

> Private to the repo owner's Claude account, like the audit's links. This directory is
> the source of truth; the playbook is its overview.

## How to execute a phase

Give a fresh Claude Opus session exactly two documents: `PROTOCOL.md` and the phase's
packet. Phases run strictly in **execution order** — `0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b →
7 → 8`; **phase numbers are identifiers, not sequence** (D-A). Each phase ends in one PR
that the owner reviews, checks on the phone against the packet's owner checklist, and
merges. **Phase 0's checkpoint blocks everything** — the IA decision and the schedule
semantics get signed there before any code phase starts. **Phase 1 is the one exception
to "two documents"**: it is a single phase built from two packets (D-B), so its session
receives three — the protocol plus 1A and 1B.

## The phases

In execution order (D-A). Ten PRs; **~26–37 executor-days and ~6–10 owner-days for
phases 0 through 7**, plus ~2–4 / ~0.5–1 if the optional Phase 8 runs.

| # | Phase | Packet | Delivers | Executor-days | Owner-days |
|---|---|---|---|---|---|
| 1 | 0 | [PHASE_0_DECISIONS.md](PHASE_0_DECISIONS.md) | Doc edits: IA decision recorded (four tabs recommended), schedule semantics signed, cut list, doctrine repairs | 0.5–1 | 0.5–1 |
| 2 | 2 | [PHASE_2_TEST_SUBSTRATE.md](PHASE_2_TEST_SUBSTRATE.md) | Robolectric migration lane (macOS/Linux host), androidTest scaffold, `tools/preflight.sh` | 1–2 | 0.5 |
| 3 | 1 | [PHASE_1A_SESSION_LIFECYCLE.md](PHASE_1A_SESSION_LIFECYCLE.md) + [PHASE_1B_LOG_REPAIR.md](PHASE_1B_LOG_REPAIR.md) | **One phase, one branch, one PR (D-B):** Finish/Discard use cases, LiveSessionBar, zero-set + stale policies, one-live-affordance rule; then editable finished sessions + notes, session delete, repeat-last-session, delete-set undo | 5–7 | 0.5–1 |
| 4 | 3 | [PHASE_3_SCHEMA_V2.md](PHASE_3_SCHEMA_V2.md) | The one reviewed migration: v2 schema, versioned seeding, backup v2, pre-open safety net, batch-1 catalog (family movementKeys), junction heat | 3–5 | 1–2 |
| 5 | 4 | [PHASE_4_PLAN_TAB.md](PHASE_4_PLAN_TAB.md) | Pinned week (domain rules first), ScheduleRepository, Plan tab, ScheduleScreen deleted | 4–6 | 0.5–1 |
| 6 | 5 | [PHASE_5_HEAT_COACH.md](PHASE_5_HEAT_COACH.md) | Absolute set-count bands, This week + 30 days, decoupled coach, RPE, do-less rules, named lifts | 4–5 | 0.5–1 |
| 7 | 6a | [PHASE_6A_TAB_CONSOLIDATION.md](PHASE_6A_TAB_CONSOLIDATION.md) | Tab bar per the signed IA (Branch A four tabs / Branch B three tabs), month grouping + day sheet + PR row, nine-site nav retarget | 2–2.5 (A) / 4–5 (B) | 0.5–1.5 |
| 8 | 6b | [PHASE_6B_HOME_TODAY.md](PHASE_6B_HOME_TODAY.md) | Home "Today": masthead table, week strip, one next-session module, start-options sheet | 2–3 | 0.5–1 |
| 9 | 7 | [PHASE_7_CATALOG.md](PHASE_7_CATALOG.md) | Catalog to ~98 in staged seed bumps, family/equipment picker UX, canonical-key filters, increment table | 4–5 | ~1, staged |
| 10 | 8 | [PHASE_8_IMAGERY.md](PHASE_8_IMAGERY.md) | **OPTIONAL** — Compose-drawn exercise thumbnails, 100 % coverage, ≤2 MB budget | 2–4 | 0.5–1 |

The A1 DI seam is deliberately **not** a phase — opportunistic, 2-day timebox, gates
nothing.

## What the self-critique reversed (the short list)

- **Value order inverted**: session hygiene (the daily-pain fixes) ships early and
  catalog and imagery ship last. The half-plan outcome is hygiene + pinning, not
  substrate + pictures. (Second pass: the test substrate now runs one phase *ahead* of
  hygiene — D-A — because it is what gives hygiene a test lane at all.)
- **Two fatal schema designs replaced**: the partial unique index (inexpressible in
  Room; would crash-loop the app after its first migration) became a `nameKey` column
  with seeder-enforced uniqueness; the `routineId × weekday` table (couldn't express
  rotation) became ordered `schedule_slots` derived from semantics the owner signs in
  Phase 0 — before the DDL freezes.
- **Backup format v2 added**: schema v2 without it silently strips every new field on
  the next restore and can permanently gut the catalog. Round-trip tests gate Phase 3.
- **"Auto-backup before migrating" made real**: a raw file copy before Room opens at
  v2 — the JSON path runs after migration and cannot do the job; this copy is the only
  rollback path.
- **The FK re-pointing merge tool is cut**: name collisions are rename-or-keep-both;
  no history foreign key is ever rewritten.
- **The line-art commission is cut** to a non-committal appendix; imagery is
  Compose-drawn composition, ~0 MB.
- **LiveSessionBar pulled forward** to Phase 1 — it is the surface the limbo policies
  need, and it becomes the *only* live-session affordance (Home's hero never shows
  Resume again; the rest strip dies).

## Second pass

A six-auditor due-diligence round (21 Aug 2026, ~1.1 M tokens, verified against HEAD and
against Room/Robolectric/AOSP primary sources) then attacked this directory itself. The
engineering core held; **two decisions were overturned**: the **execution order** — Phase 2
now runs before Phase 1, and phase numbers are identifiers rather than sequence (D-A) —
and **D1's recommended default, which flipped from three tabs to four** (Home · Body ·
Plan · History), with three tabs still signable behind two mandatory mitigations (D-C).
Five more global decisions came out of it: 1a+1b merge into one phase, the `movementKey`
and `muscleKey` vocabularies unify, no gate may depend on CI, and every phase opens with a
re-baseline commit. The record — verdict, the defects fixed, what held under attack, the
new Windows/Robolectric risk — is [SECOND_PASS.md](SECOND_PASS.md).

## Provenance

Produced by three multi-agent rounds against the full checkout: a ten-agent audit
(five code readers, five product critics), a five-critic adversarial attack on the
resulting plan, and nine writer+verifier pairs for the packets — ~4.2 M tokens of
independent review. Every file:line claim in the packets was re-verified against
commit `2212628` by an agent instructed to refute it — and then re-verified again against
HEAD by the second pass ([SECOND_PASS.md](SECOND_PASS.md), six auditors, ~1.1 M tokens).
