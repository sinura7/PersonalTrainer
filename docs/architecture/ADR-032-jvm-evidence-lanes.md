# ADR-032 — JVM renders and JVM migration tests are the evidence lanes

- **Status:** Accepted
- **Date:** 23 September 2026 (owner decision of 22 September, whole-app audit)
- **Amends:** [ADR-026](ADR-026-frontend-redesign.md) decision 8;
  [ADR-027](ADR-027-workout-logging-redesign.md) decision 8 (the floor goldens
  are not re-recorded, and JVM renders become evidence rather than review-only
  artifacts); and [ADR-010](ADR-010-schema-reset-migrations.md) decision 7 as
  to which lane gates.
- **Does not supersede:** [ADR-024](ADR-024-hosted-jvm-check.md). The hosted
  emulator lane stays non-blocking; the local gate stays first.
- **Related:** [whole-app audit](../design-audit/2026-09-22/AUDIT.md),
  `app/schemas/README.md`

## Context

ADR-026 §8 made missing visual baselines a failure and gave native renders,
semantics checks and physical-device evidence distinct roles. In practice the
visual baselines were emulator goldens captured on 17 September. Five
live-workout redesign rounds later, 63 of them fail on `trunk` on every run, so
the hosted smoke lane is red for every PR, and a red that is always red proves
nothing.

No emulator exists in the cloud lane ([CLOUD-ENVIRONMENT.md](../CLOUD-ENVIRONMENT.md)).
Robolectric can draw the real Compose tree on the JVM with native graphics, and
`WorkoutFloorRenderTest` already writes floor frames that way.

ADR-010 §7 asks for JVM and device migration tests on every schema change.
Versions 6 and 7 shipped with neither JVM test, because Robolectric's
`MigrationTestHelper` reads schemas only from `app/src/debug/assets/`, and
those copies stopped at 5 (fixed by packet X2a).

## Decision

1. **Visual evidence is the JVM render set.** A visible packet's render test
   draws each changed screen with the real composables on the JVM
   (Robolectric, native graphics). **The matrix**, which other documents
   point to rather than restate: 360×640, 412 dp and landscape, each at font
   1.0, 1.6 and 2.0; and 600 dp for a screen whose layout changes at that
   width (F11 renders every tab at 600 dp). The test writes the frames under
   `app/build/screen-renders/<packet>/`, and it asserts what must stay
   reachable in each frame: the controls, the regions, the text. A frame that
   stops rendering, or a control that falls out of reach, fails the gate.
   The frames are reviewed, not pixel-pinned: the JVM renderer is not the
   phone's. The packet's author reviews them, and the ones that show the
   change go to the owner with the packet's report; CI does not keep them.
2. **Emulator goldens retire as acceptance baselines.** The 17 September
   captures are not refreshed and are not evidence for or against a packet.
   The hosted instrumented lane keeps running, non-blocking (ADR-024), for
   crashes and journeys; its golden failures need no explanation per PR.
3. **Physical-device evidence keeps its role.** The owner's phone check after
   a drop is still the acceptance for anything a JVM render cannot show:
   timing, touch feel, TalkBack speech, the system bars.
4. **Migrations gate on the JVM.** Every schema version needs its exported
   schema, a byte-identical copy in `app/src/debug/assets/`, and a
   `TemperMigration{N-1}To{N}Test` run by `testDebugUnitTest`.
   `TemperSchemaAssetsTest` fails when any of the three is missing. The device
   migration test is still written and extended with each version, and runs
   in the hosted lane as evidence, not as the gate.

## Consequences

- ADR-026's "missing required visual baselines fail" now means a missing or
  failing render test for a changed screen, not a missing emulator golden.
- The live-workout floor predates this record: `WorkoutFloorRenderTest`
  renders it at 360×800 with few assertions. Packet W3 brings it to the full
  matrix with reachability assertions, and F11 runs the matrix across every
  tab.
- The retired goldens and `domain/GoldenPageCatalog` stay until packet W3,
  which removes the golden checks from the hosted lane so that a new crash
  there is not hidden among failures that are always red.
- A schema bump without its JVM migration test fails `testDebugUnitTest`
  before review.

## Review questions

- Does a red instrumented smoke run block a PR? No (ADR-024), and golden
  failures in it are not evidence.
- Are JVM frames compared pixel by pixel? No. They are reviewed, and the test
  asserts reachability.
- Can a schema version ship with only the device test? No.
