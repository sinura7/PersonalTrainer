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
| [ADR-006](ADR-006-information-architecture.md) | Four-tab IA and evidence protocol | FND-031, FND-032, FND-046 |
| [ADR-007](ADR-007-activity-model.md) | Unified activity model, cardio, and one live session | FND-002, FND-008, FND-018 |
| [ADR-008](ADR-008-deterministic-rules.md) | Deterministic rules and explanation API | FND-047 |
| [ADR-009](ADR-009-backup-privacy-sync.md) | Backup, privacy, Drive, and sync gate | FND-011, FND-012, FND-030 |
| [ADR-010](ADR-010-schema-reset-migrations.md) | Schema evolution, one-time reset, migration ban | FND-019, Room v3 supersession |
| [ADR-011](ADR-011-time-semantics.md) | Captured time and travel policy | FND-039 |
| [ADR-012](ADR-012-rest-and-reminders.md) | Exact rest, reminders, and missed-work policy | FND-001, FND-007, FND-017 |
| [ADR-013](ADR-013-finding-dispositions.md) | Finding dispositions and superseded doctrine | FND-037 and the full map |

## Permanent refusals that remain in force

These are not superseded by the foundation program:

- `fallbackToDestructiveMigration` ([ADR-010](ADR-010-schema-reset-migrations.md))
- A fifth tab without a new signed decision ([ADR-006](ADR-006-information-architecture.md))
- An LLM or chat coach that authors loads, plans, or records ([ADR-008](ADR-008-deterministic-rules.md))
- Package or Drive-folder rename
- GitHub-hosted runners as the project test lane ([ADR-002](ADR-002-execution-protocol.md))
- Subscription-gating the local core ([ADR-004](ADR-004-offline-core-and-entitlements.md))
