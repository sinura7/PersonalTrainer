# ADR-007 — Unified activity model, cardio, and one live session

- **Status:** Accepted
- **Date:** 24 August 2026
- **Supersedes:** Strength-only `WorkoutSession` / `SetLog` as the long-term
  model; the historical “editable finished sessions only, no backdated
  creation” limitation as a product constraint
- **Related:** FND-002, FND-008, FND-018; Phase 5 and Phase 6

## Context

The current domain can log a strength session, repair its sets, and store
more than one finished session on a date. It cannot represent cardio, mixed
blocks, a backdated *new* activity, or two independent *timed* planned
activities on one day. Fake exercises and schemaless metric maps would make
those gaps look closed.

## Decision

1. The durable record is an **`ActivitySession`** with:
   - stable ID;
   - active or completed status;
   - origin `LIVE`, `BACKDATED`, or `IMPORTED`;
   - source (Temper, later import providers);
   - title and notes;
   - performed start and end;
   - captured local date, IANA time zone, and UTC offset
     ([ADR-011](ADR-011-time-semantics.md));
   - optional template and schedule-occurrence links;
   - created/updated/revision metadata.
2. An activity contains an ordered list of typed blocks. A block is either:
   - **`StrengthBlock`** — historical exercise, load, equipment, and muscle
     snapshots, plus typed sets (weight kg, reps, RPE, warmup, completed at);
   - **`CardioBlock`** — activity type, indoor/outdoor, elapsed and moving
     seconds, optional distance, elevation, heart rate, energy, RPE, route
     reference, and typed intervals.
3. **Cardio is a first-class typed activity.** It is never a fake catalog
   exercise and never an unvalidated key/value metric map. Primary cardio
   metrics are typed columns or validated value objects.
4. Pace and speed are **derived**. Independently contradictory persisted
   pace/speed/distance/time triples are rejected.
5. Strength-only, cardio-only, and mixed sessions are all legal. Mixed
   sessions store two (or more) typed blocks under one activity. Summary
   never combines kg, distance, and minutes into a fictional score.
6. Templates are separate from sessions. A session may snapshot a template;
   editing a template never rewrites history.
7. **Multiple scheduled occurrences and multiple completed activities per
   local date are required.**
8. **One live activity at a time** is the explicit concurrency rule. A
   second start is blocked until the live activity is finished or discarded.
   Morning cardio and evening strength are sequential, not concurrent.
   Independent overlapping live timers are out of scope.
9. Backdated authoring is required. Canceled editors write nothing. Future
   dates are rejected. DST ambiguity is resolved explicitly at save. Source
   is `BACKDATED`.
10. This model is specified and tested as a domain contract (P5.2) *before*
    persistence or UI. Persistence is a new database generation
    ([ADR-010](ADR-010-schema-reset-migrations.md)), not a silent v2 patch.

## Consequences

- FND-018 is resolved by keeping one live session, not by inventing two
  foreground workouts.
- Import mappings land later as `IMPORTED` origins. They do not justify a
  schemaless bag.
- GPS/route capture is a later typed field (`routeRef`), not a Phase 6
  requirement. Manual and live elapsed cardio ship first.

## Review questions

- Can a cardio-only day exist with zero `SetLog` rows? Yes. Required.
- Can the user run live cardio and live lifting at once? No.
- Is backdating still “not built on purpose”? No. That constraint is
  superseded.
