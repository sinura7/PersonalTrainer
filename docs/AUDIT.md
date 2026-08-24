# Audits

## Current foundation audit — 23 August 2026

The canonical, repository-contained review of the current app and agreed fitness-platform
target is [`foundation-audit/README.md`](foundation-audit/README.md). It includes:

- verified build, test, lint, and runtime evidence;
- every page, state, modal, component, and user journey;
- architecture and data-flow maps;
- target capability and public-product comparison;
- ranked findings with evidence and acceptance criteria.

The review below is preserved as historical context. Its private interactive report is not
the source of truth for the current tree.

## Historical audit — 19 August 2026

Full-scale review of the app across nine dimensions, with every critical and high-severity
bug claim independently re-verified against the code. 150 findings.

Full interactive report (ranked defects, per-dimension assessments, evidence with file and
line references):
**https://claude.ai/code/artifact/c40b989b-b6bd-482a-883d-83e19e5b6b9b**

Execution plan derived from it — 122 items across 10 workstreams:
**https://claude.ai/code/artifact/4c772739-8b6f-4564-ad85-e0605e5abff0**

> These links are private to the repo owner's Claude account. What is being
> built now is [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).
> [ROADMAP.md](ROADMAP.md) is the historical in-repo summary of the strength
> logger. Counts, grades, and “confirmed” statements below are preserved from
> that review and were not re-audited as part of the current package.

## Verdict at the time

**A disciplined, well-layered strength tracker with real craft in its foundations — and
three broken promises at its core.**

| Dimension | Grade |
|---|---|
| Data layer | B− |
| Backup & Drive sync | C+ |
| Rest timer | C− |
| Fitness domain logic | C− |
| Architecture | C+ |
| UI / UX & design | C− |
| Build & release | C+ |
| Testing | C+ |
| Premium product gap | C |
| **Overall** | **C+** |

Severity spread: 1 critical, 37 high, 76 medium, 36 low.

## The three broken promises

1. **The rest timer did not reliably ring in a pocket.** No `AlarmManager`, no wakelock —
   completion rode a Handler loop that stops when the CPU sleeps. *Phase 1a added an alarm
   path, but modern exact-alarm access remains unresolved; see foundation-audit FND-001.*
2. **Training history had no safety net that did not require Google.** Manual-only backup,
   a single OAuth-bound channel, and a restore that trusted the file blindly — a document
   of `{"version":1}` would have silently erased everything. *Fixed in Phase 1b.*
3. **The coaching gave wrong advice.** Progression read the chronologically last set, so a
   back-off set lowered the next session's suggested weight; the planner deleted the last
   lower-body day precisely when lower-body volume was low. *Fixed in Phase 1c.*

## What was already good

Foreign keys that genuinely protect history (CASCADE / RESTRICT / SET NULL each used
correctly); a restore that cannot half-destroy data; routine targets snapshotted into
sessions so editing a routine never rewrites the past; a rest timer keyed on
`elapsedRealtime`; Drive tokens held in memory only with minimal scope; a real domain test
suite; clean layering with a pure-Kotlin domain; and no secrets anywhere in git history.

## Method

Nine specialist reviews ran in parallel over the full checkout. Every critical- and
high-severity claim was then handed to an independent verifier instructed to refute it from
the code; one claim was downgraded on verification. The project's own
[DESIGN_AUDIT.md](DESIGN_AUDIT.md) was used as context and cross-checked — its five Wave-0
fixes were confirmed landed.
