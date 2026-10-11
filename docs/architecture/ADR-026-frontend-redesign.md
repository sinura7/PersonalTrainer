# ADR-026 — Frontend redesign and native evidence

- **Status:** Accepted
- **Date:** 16 September 2026
- **Amended:** 23 September 2026 — decision 8 by
  [ADR-032](ADR-032-jvm-evidence-lanes.md): JVM renders are the visual
  evidence; emulator goldens retire as baselines; 29 September 2026 —
  decision 3: effort is required on a working set (owner decision, P2a of
  the eight-asks plan of 29 September, `docs/owner-eight-plan-2026-09-29.md`
  once #444 lands); warm-ups and
  holds stay optional. Supersedes W1b's "Effort · optional" (#393).
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
   editable values, effort (optional until 29 September 2026; required on a
   working set since, `SetLogRules.requiresEffort`), latest saved set and a
   two-row action area.
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

## Amendment — 11 October 2026, compact finish receipt

The owner's approved connected flow concept carries the flat Home agenda and
workout entry into Summary. Replace the separate hero and metric cards with a
clear full workout title and a wrapping line of labeled recorded measures. Keep
the existing headline choice and canonical units: volume, bodyweight repetitions
or working sets. A mixed session retains its additional bodyweight repetitions;
a set-count headline is displayed once. Count-up ownership, settled spoken values
and reduced motion remain intact.

Show the lift breakdown as a flat illustrated ledger with complete names and
values. Exercise identity still resolves by its exact ID and catalog image key.
A hold keeps its recorded duration and set count without an empty weight metric;
this does not invent an aggregate hold duration. Full session remains the route to
all saved sets and correction. Notes use plain text, personal records retain their
existing event treatment, and Done and full-session actions remain pinned.

Loading, missing, read failure, confirmed saved work, Retry, retained Summary
refresh and exit navigation keep their existing rules. This is a presentation
change, with no calculation, write, schema, backup or public API change. Native
readability, unit/measure branches and the saved correction journey must pass;
browser concepts do not establish native or physical-phone acceptance.

## Amendment — 11 October 2026, compact History ledger

Carry the approved flat composition into History. The selected period has a
complete range title and wrapping, labeled session, day, set, active-time and
available record totals. Loading or failed progress does not display a successful
zero record count. Period choices use a flat radio group with a structural selected
underline: one line at normal phone text size, wrapping at larger sizes. Previous,
Next and Current remain quiet, reachable actions. Clearly labeled lifetime records and completed blocks use
quiet controls that wrap while preserving their full labels and touch targets.

Sessions, lifetime records and completed blocks use a flat ledger. Session rows
retain matching keyed exercise illustrations, complete names, captured dates,
exact kind/ID destinations and the separate Repeat action. Show session set/work
measures when present and retain actual active time; cardio does not acquire empty
strength measures, and holds do not acquire an invented weight or duration total.
The default Home row composition remains intact.

The selected period still governs totals, calendar, progress and chronology.
Immutable read projection, lifetime-reader ownership, loading/failure/Retry,
calendar restoration, units and existing calculation rules remain unchanged.
Verify rendered measures and reachable actions across the native display matrix
and preserve the saved-work journey. This changes presentation without changing
writes, schema, backup format, public API or physical-phone acceptance.
