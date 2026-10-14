# Temper UX development handoff

Development stopped on 14 October 2026 at the owner's request. The UX programme is unfinished. This folder preserves the implemented work so another developer can continue without relying on the chat.

## Current state

- **Integrated source:** `trunk`, `eea668ce5105a5f4c4058b2d4aad35991416a174`.
- **Unmerged release preparation:** `codex/ux-core-debug-126`, `158f492b552e839c7f5ee1cd27e1ef464980d7ff`; [PR 505](https://github.com/sinura7/PersonalTrainer/pull/505) is now draft.
- **Latest verified signed delivery:** [Temper Debug 125](https://github.com/sinura7/PersonalTrainer/releases/tag/debug-live-2026-10-12), source `587e9577ee30f1962fa0a6db1a6acd2aad0b642c`.
- **Debug 126:** not merged, not published, and its complete local gate did not finish. Do not distribute the local APK as a verified release.
- **Progress:** P01–P10 have bounded development/integration closure. P11–P19 development and P20 physical acceptance remain. The reconciled checklist records bounded automated evidence for **45 of 96 required criteria**; this is not a usability or effort-completion percentage.

## Read in this order

1. [Original plan compared with completed work and remaining obligations](PLAN-RECONCILIATION.md). This is the detailed packet-by-packet continuation account.
2. [Every acceptance criterion and its remaining closure task](ACCEPTANCE-CHECKLIST.md). All 97 items are retained: 45 required items with bounded evidence, 48 required Open items, 3 required partially covered items and 1 optional item.
3. [Implemented work summary](IMPLEMENTATION-AND-REMAINING.md) and [developer continuation and verification](DEVELOPER-HANDOFF.md).
4. In the restored repository: `AGENTS.md`, `docs/FOUNDATION_PROGRAM.md`, applicable accepted `docs/architecture/` decisions, `docs/DEVELOPMENT.md`, `SETUP.md`.
5. Existing UX records: `docs/ux-context/README.md`, `docs/ux-program/UX-Backlog.json`, and `docs/ux-context/studies/q01/p01-*.json` through `p10-*.json`. [SOURCE-INDEX.md](SOURCE-INDEX.md) links their exact source snapshots.

## GitHub and offline evidence

This directory is the GitHub-readable handoff. Its Markdown and JSON files contain the stop-state account, original-plan comparison and every acceptance item. [SOURCE-INDEX.md](SOURCE-INDEX.md) provides immutable links to the source backlog, all ten studies and governing repository documentation. No local computer access is needed to read those GitHub files.

The large evidence archives, complete offline Git bundle, local transfer manifest and machine-specific run logs remain in the separate offline handoff package on the original development computer. They are not attachments in this PR. Older receipts name local archive paths; those paths do not imply that the archive was uploaded to GitHub. Another developer can read the source and tracked evidence through the source index, and obtain the offline package from the owner when raw local artifacts are needed.

No signing private keys, account tokens, owner workout database, APK binaries or large archives are included in this documentation change. The existing distribution workflow retains its signing identity.

## Cleanup performed

- Stopped the incomplete Debug 126 local verification run; its preflight passed, but the Android gate is incomplete.
- Cancelled GitHub runs `38641525755` and `38641515310`; cancellation is not a product-test failure or a passing gate.
- Converted PR 505 to draft and retained its branch for recovery.
- Stopped task-owned preview servers on ports 8881, 8882 and 8883.
- Shut down the confirmed disposable `temper-tests-api29` emulator on port 5554.
- Preserved source, reviews, original failure evidence and existing recovery archives. No merge, deployment, dependency upgrade or product change was performed during handoff.

Use this handoff as the stop-state record. Older documentation saying Debug 126 is being prepared describes the state before the owner's stop request.

## Continuation boundary

Continue with the unfinished Drop A release packet, then P11–P19 and P20. Retain working P01–P10 implementation. Some acceptance obligations owned by earlier packets remain open; their exact IDs and closure tasks appear in the checklist. Integrated packet labels do not override those open obligations. No product development or release was resumed while strengthening these handoff documents.
