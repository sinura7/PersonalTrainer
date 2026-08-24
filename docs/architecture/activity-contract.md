# Unified activity domain contract

- **Status:** Accepted — P5.2
- **Date:** 24 August 2026
- **Authority:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) P5.2
- **Does not reopen:** Room generation; production UI; Drive export

This packet specifies [ADR-007](ADR-007-activity-model.md) as a tested
domain contract *before* persistence or UI. Every FND-002 acceptance
case is representable. Export/import of the new types is P5.5.

## Decision

1. **`ActivitySession` is the envelope.** Strength-only, cardio-only,
   and mixed sessions are all legal. Mixed stores two or more typed
   blocks under one activity.
2. **Cardio is a typed block.** Duration, distance, effort, and
   intervals are columns / value objects. A cardio-only day has zero
   `StrengthSet` rows.
3. **Pace and speed are derived.** Contradictory claimed
   pace/speed/distance/time triples are rejected.
4. **Two completed activities can share one local date.** Each may
   link a different schedule occurrence.
5. **Records carry origin, source, and captured time.** Origin is
   `LIVE`, `BACKDATED`, or `IMPORTED`. The performed start is a
   [CapturedCivilTime](time-seams.md) four-tuple.
6. **One live activity at a time.** A second live start is rejected
   until the first is finished or discarded.
7. **Backdating is required.** Future local dates are rejected.
   Canceled editors write nothing.
8. **Templates do not rewrite history.** A session may snapshot a
   template id; editing the template leaves the session's blocks
   untouched.

## Finding coverage

FND-002 closed at P6.6: History, calendar, insights, and detail read
completed activities. FND-018's one-live rule is the contract here
and is proven across both live lanes; the Home agenda proof is P7.5.
