# ADR-003 — Android-first shipping platform and KMP gate

- **Status:** Accepted
- **Date:** 24 August 2026
- **Related:** [ADR-007](ADR-007-activity-model.md),
  [ADR-011](ADR-011-time-semantics.md), FND-020

## Context

Temper is a native Android Jetpack Compose app. A later Apple client is a
possible product, not a present one. Extracting Kotlin Multiplatform too early
would freeze the domain before cardio, occurrences, goals, and traces exist.

## Decision

1. **Android and Jetpack Compose remain the only shipping client** until the
   complete local Android product is accepted at the Phase 9 public-candidate
   gate.
2. New shared-target domain code introduced from Phase 5 onward must not
   import `java.time`, `java.text.NumberFormat`, `Locale`, Android, Room, or
   Compose. Time, IDs, and quantity seams are injected. UI-side locale
   formatting stays on the platform.
3. Existing `domain/` may keep `java.time` until the Phase 5 seam packet
   replaces those call sites. That is a scheduled migration, not permission
   to add more `java.time` to new shared-target types.
4. **Phase 10 does not start** until both are true:
   - the local Android milestone (Phase 9) is accepted;
   - an iOS client is an actual signed product decision, not a hypothetical.
5. Phase 10 moves only stable activity, time, schedule, goal, aggregate, and
   recommendation models plus their pure tests into `commonMain`. Compose,
   Room, DataStore, Android services, locale UI formatting, and native
   persistence remain platform-owned.
6. Phase 10’s exit is Android-consumer plus Apple-simulator compile/test
   parity. It does not require an iOS UI.
7. There is no React Native, Flutter, or shared-UI rewrite in this program.

## Consequences

- Packet authors do not “prepare for KMP” by weakening Android UX.
- FND-020 is closed by the Phase 5 seams plus, if and only if the gate
  fires, Phase 10 proof.
- An iOS sketch is not a reason to delay the Android recorder.

## Review questions

- Is KMP in scope for the next feature packet? No.
- May new cardio types import `java.time`? No. Use the injected clock/date
  ports from Phase 5.
- Does Apple UI ship in this program? No.
