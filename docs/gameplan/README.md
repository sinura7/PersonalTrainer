# The game plan — 20 August 2026

Executable successor to [HIERARCHY_PLAN.md](../HIERARCHY_PLAN.md) §4. That plan was
adversarially attacked from five angles (technical viability, integration seams, scope
realism, internal contradictions, executor readiness — 62 findings, 8 rated fatal);
this directory is the rebuilt result: one spec packet per phase, each written against
the actual code and then independently verified line-by-line, so that a fresh executor
session with no memory of the planning can run a phase from its packet alone.

**Where this directory and HIERARCHY_PLAN.md §4 disagree, this directory wins.**
[REVISED_STRUCTURE.md](REVISED_STRUCTURE.md) records what the critique changed and why;
[PROTOCOL.md](PROTOCOL.md) is the binding execution protocol every phase follows.

## How to execute a phase

Give a fresh Claude Opus session exactly two documents: `PROTOCOL.md` and the phase's
packet. Phases run strictly in order; each ends in one PR that the owner reviews,
checks on the phone against the packet's owner checklist, and merges. **Phase 0's
checkpoint blocks everything** — the IA decision and the schedule semantics get signed
there before any code phase starts.

## The phases

| Phase | Packet | Delivers | Executor-days | Owner-days |
|---|---|---|---|---|
| 0 | [PHASE_0_DECISIONS.md](PHASE_0_DECISIONS.md) | Doc edits: IA decision recorded, schedule semantics signed, cut list, doctrine repairs | 0.5–1 | 0.5 |
| 1a | [PHASE_1A_SESSION_LIFECYCLE.md](PHASE_1A_SESSION_LIFECYCLE.md) | Finish/Discard use cases, LiveSessionBar, zero-set + stale-session policies, one-live-affordance rule | 3–4 | 0.5–1 |
| 1b | [PHASE_1B_LOG_REPAIR.md](PHASE_1B_LOG_REPAIR.md) | Editable finished sessions, session delete, repeat-last-session, delete-set undo | 2–3 | 0.5–1 |
| 2 | [PHASE_2_TEST_SUBSTRATE.md](PHASE_2_TEST_SUBSTRATE.md) | Robolectric migration lane, androidTest scaffold, `tools/preflight.sh` | 1–2 | 0.5 |
| 3 | [PHASE_3_SCHEMA_V2.md](PHASE_3_SCHEMA_V2.md) | The one reviewed migration: v2 schema, versioned seeding, backup v2, pre-open safety net, batch-1 catalog, junction heat | 3–5 | 1–2 |
| 4 | [PHASE_4_PLAN_TAB.md](PHASE_4_PLAN_TAB.md) | Pinned week (domain rules first), ScheduleRepository, Plan tab, ScheduleScreen deleted | 4–6 | 1 |
| 5 | [PHASE_5_HEAT_COACH.md](PHASE_5_HEAT_COACH.md) | Absolute set-count bands, This week + 30 days, decoupled coach, RPE, do-less rules, named lifts | 3–5 | 0.5–1 |
| 6a | [PHASE_6A_TAB_CONSOLIDATION.md](PHASE_6A_TAB_CONSOLIDATION.md) | Tab bar per recorded IA, Body absorbs History, nine-site nav retarget | 3–5 | 1 |
| 6b | [PHASE_6B_HOME_TODAY.md](PHASE_6B_HOME_TODAY.md) | Home "Today": masthead table, week strip, one next-session module, start-options sheet | 2–3 | 0.5–1 |
| 7 | [PHASE_7_CATALOG.md](PHASE_7_CATALOG.md) | Catalog to ~98 in staged seed bumps, family/equipment picker UX, canonical-key filters, increment table | 3–5 | staged review |
| 8 | [PHASE_8_IMAGERY.md](PHASE_8_IMAGERY.md) | Compose-drawn exercise thumbnails, 100 % coverage, ≤2 MB budget | 2–4 | 0.5 |

The A1 DI seam is deliberately **not** a phase — opportunistic, 2-day timebox, gates
nothing.

## What the self-critique reversed (the short list)

- **Value order inverted**: session hygiene (the daily-pain fixes) now ships first;
  catalog and imagery ship last. The half-plan outcome is hygiene + pinning, not
  substrate + pictures.
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
- **LiveSessionBar pulled forward** to Phase 1a — it is the surface the limbo policies
  need, and it becomes the *only* live-session affordance (Home's hero never shows
  Resume again; the rest strip dies).

## Provenance

Produced by three multi-agent rounds against the full checkout: a ten-agent audit
(five code readers, five product critics), a five-critic adversarial attack on the
resulting plan, and nine writer+verifier pairs for the packets — ~4.2 M tokens of
independent review. Every file:line claim in the packets was re-verified against
commit `2212628` by an agent instructed to refute it.
