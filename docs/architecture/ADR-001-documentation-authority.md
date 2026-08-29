# ADR-001 — Documentation authority and program precedence

- **Status:** Accepted
- **Date:** 24 August 2026
- **Supersedes:** The claim in [ROADMAP.md](../ROADMAP.md) and [AUDIT.md](../AUDIT.md)
  that the historical roadmap is the source of truth for what is being built
- **Related:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md), P0.2

## Context

The tree holds several generations of law: a 19 August audit, a game-plan
archive, Jobs 1–6, a UX page pass, a 23 August foundation audit, and this
program. Agents and humans have treated later files as current while older
files still say `main`, “Drive sync”, “Room v3 won’t”, and “five tabs”. That
drift is FND-041 and FND-042.

## Decision

1. Authority is strictly ordered. A lower layer may explain a higher layer. It
   may not contradict it.
   1. **Shipping code, Gradle, the Android manifest, and generated Room
      schemas.** What the app does.
   2. **Accepted ADRs in this directory and [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md).**
      What the app is allowed to become, and in what order.
   3. **Operational runbooks:** [DEVELOPMENT.md](../DEVELOPMENT.md),
      [RECOVERY.md](../RECOVERY.md), [SETUP.md](../../SETUP.md),
      [MIGRATION_REHEARSAL.md](../MIGRATION_REHEARSAL.md) after its historical
      banner. How to build, recover, and migrate.
   4. **Dated audits:** [foundation-audit/](../foundation-audit/README.md) and
      the historical [AUDIT.md](../AUDIT.md). Observations, not instructions.
   5. **Archives and completed jobs:** `docs/archive/`, `docs/artifacts/`,
      `docs/ui-redesign/`, `docs/JOB*.md`, historical sections of
      [ROADMAP.md](../ROADMAP.md) and [UX_PAGE_PASS.md](../UX_PAGE_PASS.md).
      Context. Never current law.
2. Current-voice documents — the root README, SETUP, DEVELOPMENT, RECOVERY,
   UX_PAGE_PASS binding rules, this directory, the foundation program, and
   `.cursor/rules/owner-loop.mdc` — must use the runtime vocabulary:
   - the sitting branch is `trunk`;
   - Google Drive is optional **backup**, never sync;
   - Body heat windows are **Day**, **This week**, and **This month**;
   - the shipping IA is **Home · Body · Plan · History**, Library pushed;
   - user-controlled export/import is the recovery path, not Android Auto Backup;
   - Room schema `2.json` is committed; a later generation is a signed program
     step, not an accident.
3. Archives are not rewritten to look current. They stay labeled as archives.
4. `tools/check-doc-authority.py` is the mechanical guard for (2) and for
   relative markdown links in active documents. It does not phrase-check
   archives or dated audits.
5. When an ADR and the program disagree, the ADR wins and the program is
   defective until corrected in the same packet.

## Consequences

- Later packets update the foundation program’s packet status. They do not
  reopen Jobs 1–6 as live work.
- [ROADMAP.md](../ROADMAP.md) remains the historical record of how the
  strength logger was built. The banner at its top must point here.
- An agent that follows Job 6 or “Room v3 won’t” as current law is wrong.

## Review questions

- Which file may an executor treat as current law? The foundation program and
  accepted ADRs.
- May an archive be edited to hide a superseded “won’t”? No. Banner it or
  leave it.
- Is Android Auto Backup the documented recovery path? No.
