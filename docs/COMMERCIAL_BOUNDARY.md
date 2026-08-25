# Temper commercial boundary

**Status:** Current published posture (P12.2 / FND-047 / FND-048)  
**Related:** [ADR-004](architecture/ADR-004-offline-core-and-entitlements.md),
[ADR-008](architecture/ADR-008-deterministic-rules.md)

## Always free, always signed-out

These capabilities must work with no account and must never be locked
behind a subscription, trial, or paywall:

- live and backdated strength, cardio, and mixed recording
- history, calendars, and session detail
- templates, routines, and the pinned week
- reminders and the rest timer
- measurable goals
- deterministic local recommendations and their `RuleTrace`
- file export and restore

`tools/check-commercial-boundary.py` fails the local preflight if billing,
ads, analytics, or account SDKs appear in those packages.

## What may later be entitlement-gated

Only if a later signed decision exists:

- optional encrypted incremental sync enrollment
- a future remote explanation service that *explains* an existing
  `RuleTrace` and does not change loads, plans, goals, or records

Expired entitlement, if that product ever exists, pauses the network
service. Local history and queued local work stay.

## What does not exist today

- Kotlin Multiplatform extraction (Phase 10; [ADR-003](architecture/ADR-003-shipping-platform.md) gate unfired)
- Incremental sync (Phase 11; [ADR-009](architecture/ADR-009-backup-privacy-sync.md) §14 unfired)
- Play Billing
- A remote recommendation API

Do not advertise those products.
