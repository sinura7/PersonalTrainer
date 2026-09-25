# Common brief for every audit pass (X6, 25 September 2026)

You are one specialist pass in a max-effort whole-app audit of Temper, an
Android app (Kotlin, Jetpack Compose, Room v7, paused Supabase sync, Drive
backup, rest-timer foreground service) at `/home/user/PersonalTrainer`, HEAD
`1ad3b11` on `trunk` (merged as PR #412). The audit is a findings record; no
code is fixed during it. Your report feeds a lead who merges 19 reports, so
**follow the format exactly**.

## Hard rules

- Read-only on the repository. Never write, edit, create or delete anything
  under `/home/user/PersonalTrainer`. Write only your report file (path given
  in your brief) under the scratchpad
  `/tmp/claude-0/-home-user-PersonalTrainer/cf46fb84-1fb9-5154-8302-bc82a0b34c05/scratchpad/`.
- Never run `./gradlew`, `tools/preflight.sh`, `tools/verify.sh`,
  `tools/run-domain-tests.sh` or anything that starts Gradle or writes under
  `build/`. A baseline build is running in this checkout at the same time.
  Running a single Python checker (`python3 tools/check-*.py`) is fine; they
  only read and print.
- Never call any Supabase, GitHub-write, or network-changing tool. (B13b may
  use the GitHub MCP read tools for hosted run history; nobody else needs it.)
- Every claim must be **confirmed from code** (mark `C`) with `path:lines`, or
  marked `R` (needs runtime: only a phone/emulator can settle it). Do not
  present an `R` claim as fact. Do not invent line numbers; quote them from
  what you read.
- Be adversarial toward your own findings: before writing a P1 or P2, look for
  the code that would make it false (a guard, a caller that never hits the
  path, a test that pins it). Say what you checked.

## Read these first (they are the record you are extending)

1. `docs/design-audit/2026-09-22/AUDIT.md` — the previous whole-app audit:
   findings S-1…S-10, L-1…L-2, C-1…C-2, per-tab rows, live-workout and system
   rows, and the delivery order. Severity rubric is there (P1/P2/P3).
2. `docs/HANDOFF-NEXT.md` — "Done so far" lists every packet merged since
   (S0a, X2a, S0b, Q1, X1, T1a–T1c-2, W1a, W1b, R0, X3, W1c, X4, W1d, X2b,
   W2b-1, W2b-1b, W2b-1c, W2b-1d, W2a, X5, W2b-2, W2b-3) with what each did.
3. `docs/FRONTEND_REDESIGN.md` §"Order since the whole-app audit" — the packet
   table with statuses and PR numbers.
4. `docs/architecture/CURRENT_STRUCTURE.md` — what the code is; "Known debt,
   named" lists things a reader should not rediscover as new.
5. `docs/design-audit/2026-09-16/AUDIT.md` — D01–D17 visual findings (frontend
   passes only; know which are still open).
6. The ADRs named in your brief, under `docs/architecture/`.
7. `CLAUDE.md` — the owner is not a programmer; your "plain-language twin"
   column is written for them.

## Severity (verbatim from the 22 September record)

- **P1** = protect data or a first-run blocker.
- **P2** = next correctness or polish work.
- **P3** = worth doing, not urgent.

Type (from the foundation audit): **defect** (behaviour is wrong or
contradictory) · **validation gap** (plausible but unproven) · **architecture
debt** (structure will resist the target) · **target gap** (absent but
required by an accepted decision) · **opportunity** (useful, non-blocking).

## Finding IDs

Continue the 22 September families where they exist: sync `S-11`, `S-12`…;
coach `C-3`…; first launch `L-3`…. New areas use two-letter codes:
`BK-` backup/Drive/restore · `RT-` rest timer/alarms/service · `RM-`
reminders/WorkManager/updater · `DB-` Room/migrations/DAOs/repositories ·
`DM-` domain rules · `UI-` screens/ViewModels/navigation · `AX-`
accessibility/design system · `AR-` architecture/coroutines/performance ·
`TS-` tests/coverage · `BR-` build/CI/supply chain · `DC-` docs truth ·
`PV-` privacy/security. Number from 1 within your pass, prefixed with your
pass id in a `Pass` column so the lead can renumber without collisions
(e.g. `BK-1` from B5 and `BK-1` from B10a are distinguishable).

## Report skeleton (your file, in this order)

```
# <Pass id> — <scope in five words>

## 1. Prior findings in my scope
| Prior ID | Claim (short) | Status: Closed / Open / Regressed / Worse | Evidence (PR # or path:lines) |
(Every 22 Sep row and every still-open 16 Sep D-item that touches your scope. A known item may appear again in §2 ONLY if Regressed or Worse.)

## 2. Ranked findings (P1 and P2 only; at most 12 rows)
| ID | Pass | Sev | Type | C/R | Prior | Files | Claim | In plain terms | Probe or repro |
(Files: path:lines, comma-separated. Claim: one sentence, what the code does and why it is wrong. In plain terms: one sentence for the owner, no jargon. Probe: how a verifier can prove it — a JUnit test idea in one sentence, a command, or "read X".)

### Notes on §2 (optional, keyed by ID)
Longer evidence, code excerpts (≤ 15 lines each), the counter-evidence you looked for.

## 3. P3 findings (unlimited)
Same table.

## 4. Noticed, outside my scope
Bullets with path:lines and the pass that should own it (B1…B14). No severity.

## 5. Read ledger
| File | read / skimmed / not opened |
(Every file in your scope. Be honest; "not opened" is useful information.)

## 6. Prior-art check
One paragraph: which existing tests already pin the behaviour you audited (name them), and which docs you found contradicting the code (path:line, for B14).
```

Main report (§1–§2 with notes) ≤ 300 lines. Do not pad to reach 12 rows; do
not truncate a real P1 to stay under. Order §2 by severity then by user
impact.

## Owner / secondary rule on seams

Your brief lists seams with an **owner** and sometimes a **secondary**. The
owner writes the finding. The secondary writes one line "see B<n>" in §4
plus only what is new from its own angle. Do not write the same finding twice
under two IDs.

## What "confirmed from code" means here

You read the actual lines, followed the callers (grep for the function name
across `app/src/main` and `app/src/test`), and checked whether a test under
`app/src/test` or `app/src/androidTest` already asserts the behaviour. Name
that test in §6. A claim contradicted by a passing test is not a finding
unless the test is wrong (say so, with the test's path:lines).
