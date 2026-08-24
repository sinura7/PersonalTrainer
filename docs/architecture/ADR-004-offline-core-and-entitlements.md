# ADR-004 — Offline local core and entitlement boundary

- **Status:** Accepted
- **Date:** 24 August 2026
- **Related:** [ADR-008](ADR-008-deterministic-rules.md),
  [ADR-009](ADR-009-backup-privacy-sync.md), FND-047, FND-048

## Context

The product began as a personal logger. Commercialization is a later option,
not a present business. Users who never create an account must still own a
complete training record.

## Decision

1. The following work **offline and without an account**, on a fresh install,
   in airplane mode:
   - recording strength and cardio, live and backdated;
   - history, templates, and repair;
   - schedules, occurrences, and reminders;
   - measurable goals;
   - deterministic recommendations and `RuleTrace`;
   - local export/import and recovery.
2. Those capabilities are **never subscription-gated**. An expired or absent
   entitlement must not hide, lock, degrade, or destroy local records.
3. Optional paid surfaces, when they exist, are limited to:
   - multi-device incremental sync;
   - advanced cloud analytics that are not required to read local history;
   - an API that explains an existing `RuleTrace`.
4. Entitlement checks wrap only those optional surfaces. They do not appear
   in core recording, history, goal, reminder, or export use cases.
5. A static boundary check in Phase 12 forbids billing, account, and network
   dependencies from those core use cases.
6. Signed-out behavior remains identical to today and creates no operational
   sync rows unless the user enrolls.

## Consequences

- “Log in to continue” on Home, Plan, History, or the workout screen is a
  defect.
- Drive sign-in may remain a Settings backup convenience. It is not an
  account for the product and not a requirement to export.
- Future billing packets cannot reopen this ADR by renaming a core screen.

## Review questions

- Can a user record, schedule, and export forever with no account? Yes.
- Can sync be disabled without disabling the recorder? Yes. Required.
- May goals become a Pro feature? No.
