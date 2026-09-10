# Architecture decisions

This directory is the signed decision set for Temper. It is current law, not a
museum.

Authority order is defined in [ADR-001](ADR-001-documentation-authority.md).
The executable program that consumes these decisions is
[FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md).

A decision is binding when its status is **Accepted**. Changing one requires a
new ADR that names what it supersedes. Silent contradiction of an accepted ADR
is a defect.

## Record format

Every ADR uses the same headings:

- **Status** — Accepted, Superseded, or Rejected
- **Date** — the day it became binding
- **Supersedes / Related** — earlier constraints and sibling decisions
- **Context** — the problem that forced a choice
- **Decision** — numbered, testable statements
- **Consequences** — what later packets may and must not do
- **Review questions** — the ambiguities this record closes

No ADR may leave a schema, scheduling, privacy, or entitlement choice as TBD.

## Index

| ID | Title | Closes |
|---|---|---|
| [ADR-001](ADR-001-documentation-authority.md) | Documentation authority and program precedence | FND-041, FND-042 |
| [ADR-002](ADR-002-execution-protocol.md) | One-packet execution protocol | process |
| [ADR-003](ADR-003-shipping-platform.md) | Android-first shipping platform and KMP gate | FND-020 |
| [ADR-004](ADR-004-offline-core-and-entitlements.md) | Offline local core and entitlement boundary | FND-047, FND-048 |
| [ADR-005](ADR-005-instrument-identity.md) | Instrument visual identity | FND-045 |
| [ADR-006](ADR-006-information-architecture.md) | IA and evidence protocol (Library stays pushed) | FND-031, FND-032, FND-046 |
| [ADR-007](ADR-007-activity-model.md) | Unified activity model, cardio, and one live session | FND-002, FND-008, FND-018 |
| [ADR-008](ADR-008-deterministic-rules.md) | Deterministic rules and explanation API | FND-047 |
| [ADR-009](ADR-009-backup-privacy-sync.md) | Backup, privacy, Drive, and sync gate | FND-011, FND-012, FND-030 |
| [ADR-010](ADR-010-schema-reset-migrations.md) | Schema evolution, one-time reset, migration ban | FND-019, Room v3 supersession |
| [ADR-011](ADR-011-time-semantics.md) | Captured time and travel policy | FND-039 |
| [ADR-012](ADR-012-rest-and-reminders.md) | Exact rest, reminders, and missed-work policy | FND-001, FND-007, FND-017 |
| [ADR-013](ADR-013-finding-dispositions.md) | Finding dispositions and superseded doctrine | FND-037 and the full map |
| [ADR-014](ADR-014-settings-tab.md) | Settings is the fifth tab | FND-031 Settings gear |
| [ADR-015](ADR-015-plan-day-blocks.md) | Plan is a day-block schedule | T3 Plan day page |
| [ADR-016](ADR-016-settings-home-trim.md) | Settings / Home trim | Display, check-in, Goals UI gone |
| [ADR-017](ADR-017-home-week-board.md) | Home week board and Plan day fill | Occurrence days, Add session Volt |
| [ADR-018](ADR-018-home-start-confirm.md) | Home start confirm | Confirm-then-start; workout over aux |
| [ADR-019](ADR-019-move-to-today.md) | Move a leftover session to today | Do it today; Still open |
| [ADR-020](ADR-020-warmup-extras.md) | Warm-up extras, untimed board, same-day extra | Golf/lower/upper/shoulder packs; Home Add extra; Up/Down; hide clocks |
| [ADR-021](ADR-021-home-start-and-day-add.md) | Home start, day add, skip leftover, editor Save | Row starts planned; Volt is Start a workout; + under Today; skip Still open; §7 amended 10 Sep 2026 — Save leaves only when its writes landed |
| [ADR-022](ADR-022-keyed-catalog-stills.md) | Keyed catalog stills | One WebP per built-in lift; `imageKey` written at catalog v7 |
| [ADR-023](ADR-023-palette-and-reduced-motion.md) | Palette collisions stay; reduced motion finishes the gate | F14 / G6; amends ADR-005 §5 |
| [ADR-024](ADR-024-hosted-jvm-check.md) | The deterministic hosted job may gate `trunk`; the emulator may not | Amends ADR-002 §6 for one named job; the local gate is unchanged |

## Supporting records

| Record | Role |
|---|---|
| [backup-threat-model.md](backup-threat-model.md) | P3.1 signed inventory of stores, channels, and threats. Implements ADR-009; does not reopen it. |
| [sdk36-compatibility.md](sdk36-compatibility.md) | P4.1 reviewed pass for compile/target 36. AGP/Gradle are the official pair; Compose, Room, and Robolectric stay for P4.2–P4.4. |
| [core-toolchain.md](core-toolchain.md) | P4.2 signed Core / Lifecycle / Activity / coroutines / serialization / Robolectric / AndroidX Test matrix. |
| [compose-toolchain.md](compose-toolchain.md) | P4.3 signed Compose BOM / Material / Navigation / compiler matrix. |
| [persistence-toolchain.md](persistence-toolchain.md) | P4.4 signed Room / DataStore matrix. Schema v1/v2 hashes stay. |
| [drive-auth.md](drive-auth.md) | P4.5 Drive authorization: AuthorizationClient + drive.file; Google Sign-In gone. |
| [lint-policy.md](lint-policy.md) | P4.6 local lint waivers and supply-chain checksum ledger. |
| [time-seams.md](time-seams.md) | P5.1 platform-neutral time, ID, and quantity ports. `java.time` stays out of `domain/`. |
| [activity-contract.md](activity-contract.md) | P5.2 unified `ActivitySession` contract. Tests before persistence/UI. |
| [foundation-generation.md](foundation-generation.md) | P5.3–P5.7 `TemperDatabase`, export v3, signed reset, freeze. |
| [completed-training-convergence.md](completed-training-convergence.md) | 2026-09-06 R18 capability matrix for strength sessions and typed activities, the shared read contract, use cases to extract, parity tests, and the R17 measurement plan. No schema change. |

## Permanent refusals that remain in force

These are not superseded by the foundation program:

- `fallbackToDestructiveMigration` ([ADR-010](ADR-010-schema-reset-migrations.md))
- A sixth tab, or Library as a tab, without a new signed decision ([ADR-006](ADR-006-information-architecture.md), [ADR-014](ADR-014-settings-tab.md), [ADR-016](ADR-016-settings-home-trim.md), [ADR-017](ADR-017-home-week-board.md), [ADR-018](ADR-018-home-start-confirm.md), [ADR-019](ADR-019-move-to-today.md), [ADR-020](ADR-020-warmup-extras.md), [ADR-021](ADR-021-home-start-and-day-add.md), [ADR-022](ADR-022-keyed-catalog-stills.md), [ADR-023](ADR-023-palette-and-reduced-motion.md))
- An LLM or chat coach that authors loads, plans, or records ([ADR-008](ADR-008-deterministic-rules.md))
- Package or Drive-folder rename
- GitHub-hosted runners as the project test lane ([ADR-002](ADR-002-execution-protocol.md)),
  with one named exception: the deterministic *Tests, lint, debug build* job may be a
  required check on `trunk` ([ADR-024](ADR-024-hosted-jvm-check.md)). The emulator lane
  may not, ever, without a new signed decision.
- Subscription-gating the local core ([ADR-004](ADR-004-offline-core-and-entitlements.md))
