# ADR-025 — Goal may move thresholds, never rules

- **Status:** Accepted
- **Date:** 15 September 2026
- **Supersedes:** Nothing. Clarifies [CoachPreferences](../../app/src/main/java/com/sinura/personaltrainer/domain/CoachPreferences.kt)
  and reaffirms [ADR-008](ADR-008-deterministic-rules.md).
- **Related:** Packet 5 of the progression-engine program

## Context

`TrainingGoal` already reorders recommendation cards. `CoachPreferences`
said the goal must not author rules. The missing piece was whether a
goal may change the *numbers those rules read* when a lift is first
added — Muscle wanting more reps and less rest, Strength the reverse —
without becoming a second rule engine.

A goal that fired different in-set rules would be a different coach
for each setting. A goal that rewrote a stored routine would silently
edit a plan the user owns.

## Decision

1. **A goal may move thresholds on a newly added row.**
   [AddDefaults](../../app/src/main/java/com/sinura/personaltrainer/domain/AddDefaults.kt)
   takes `TrainingGoal` as a fourth axis. Muscle raises the landing rep
   window and shortens rest. Strength does the reverse. Athletic,
   Resilience, and General keep today's table.
2. **Every rule stays identical for every goal.**
   `SetMicroRecCalculator`, `ProgressionCalculator`, `RecommendationEngine`,
   and later `Coach.decide` do not branch on goal. They read the plan's
   targets. The goal reorders cards; it does not add or remove a card or
   an in-set reason code.
3. **New rows only.** A goal changed on a Tuesday never rewrites a routine
   or live session the user already wrote. `targetSets` / `targetReps` /
   `restSeconds` on stored entities are not back-filled.
4. **Generation keeps its own overlay.** [ProgramDose](../../app/src/main/java/com/sinura/personaltrainer/domain/ProgramDose.kt)
   still reads the General `AddDefaults` table and then applies age, days,
   and goal. Hand-added rows and generated weeks do not double-apply.

## Consequences

- The add-to-routine landing line and a live Add lift write the
  goal-shifted numbers. Editing those numbers afterwards is still the
  user's.
- A later packet that wants a rule to fire only for Strength is refused
  by this record, not by a comment.
- Schema stays at v5. No packet here writes goal onto a lift row.

## Review questions

- May Muscle change the in-set RPE ceiling for "another set"? No. That
  is a rule. The landing rep window is a threshold.
- May changing goal on Settings rewrite this week's pinned routines? No.
- May ProgramDose pass the goal into AddDefaults and also apply its own
  overlay? No. That would double-count.
