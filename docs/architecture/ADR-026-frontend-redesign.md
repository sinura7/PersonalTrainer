# ADR-026 — Frontend redesign and native evidence

- **Status:** Accepted
- **Date:** 16 September 2026
- **Amended:** 23 September 2026 — decision 8 by
  [ADR-032](ADR-032-jvm-evidence-lanes.md): JVM renders are the visual
  evidence; emulator goldens retire as baselines
- **Supersedes / Related:** ADR-002 §3 branch prefix only; ADR-005/023 visual
  identity retained; ADR-017 day-picker geometry may adapt; ADR-022 Body
  registration refined without replacing keyed catalog stills; ADR-024 gates
  retained. Refines the current Foundation Program's frontend work.

## Context

The owner supplied twelve screenshots and explicitly approved the comprehensive
frontend redesign plan. The audit found composition, state-clarity and full-screen
evidence gaps despite substantial functional tests. Source constraints requiring
a 112 dp workout hero, a blank 56 dp context rail, Log after target completion,
or totals-only History filtering describe the previous design, not the new target.

## Decision

1. Execute [FRONTEND_REDESIGN.md](../FRONTEND_REDESIGN.md) in one packet at a time,
   with independent/adversarial review and integrated verification. Short-lived
   branches use `codex/`; `trunk` remains the sole long-lived branch.
2. Preserve Instrument, offline functionality, five destinations, canonical IDs,
   calculation rules, captured-time semantics, persistence and backup formats.
3. Recompose workout around compact exercise identity, explicit set type,
   editable values, optional effort, latest saved set and a two-row action area.
   Unapplied suggestions are visibly and semantically distinct from selections.
4. Completed planned work offers Next exercise (next unfinished, wrapping), then
   Finish workout. Extra sets remain explicit. No automatic advancement.
5. History has one selected period/range for totals, calendar, progress and list.
   Lifetime records and blocks remain available in clearly labeled secondary views.
6. The day picker may scroll to preserve touch targets. Navigation may reflow
   3 + 2 when all five full labels cannot fit; destinations and order stay fixed.
7. Body base, heat masks, highlights and hit testing use registered geometry.
   Known exercise image keys still resolve first, with the existing fallbacks.
8. Missing required visual baselines fail. Native renders, semantics/interaction
   checks and physical-device evidence have distinct acceptance roles. Hosted
   emulator checks remain nonblocking; the deterministic/local gates stay intact.
   *Amended 23 September 2026 by [ADR-032](ADR-032-jvm-evidence-lanes.md):* the
   required baselines are the JVM render set; the 17 September emulator goldens
   are retired.
9. Home retains ADR-021's current start sheet and no Add row. The approved plan's
   instruction to preserve the existing recurrence choice does not resurrect the
   obsolete Home add flow removed on 13 September; Plan remains its current author.

## Consequences

Old presentation assertions must be updated to test the approved behavior, not
bypassed. No schema reset, new backend, sixth tab or signing change is authorized
by visual work. Obtainium milestones need increasing codes and stable signatures.
Physical checks remain explicit outstanding acceptance until actually performed.

## Review questions

- Can an HTML concept close a native UI packet? No.
- Does Next exercise automatically advance after logging? No; it requires a tap.
- Can History display all sessions beneath Month-selected totals? No.
- Can a generated test APK overwrite the owner's Obtainium app? No.
- Does this authorize parallel implementation packets? No.
