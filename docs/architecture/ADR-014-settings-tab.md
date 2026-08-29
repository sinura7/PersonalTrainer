# ADR-014 — Settings is the fifth tab

- **Status:** Accepted
- **Date:** 29 August 2026
- **Supersedes:** [ADR-006](ADR-006-information-architecture.md) on the
  “no fifth tab” and “Settings is a pushed route” lines only
- **Related:** FND-031, FND-046; owner request 29 August 2026

## Context

ADR-006 signed Home · Body · Plan · History, with Library pushed and
Settings reached from a gear on Home and Plan. That default rejected a
fifth tab used as an excuse for cardio, goals, or a catalog.

The owner asked for Settings as its own tab: a dedicated space, not a
header glyph on other pages. That is the documented escape from
ADR-006 — a new signed ADR — not a silent fifth tab. The fifth tab is
**Settings**. It is not Library. It is not Goals.

Historical files that said “five tabs” meant Library on the bar. That
IA is still refused.

## Decision

1. **Shipping IA:** Home · Body · Plan · History · Settings.
2. **Library remains a pushed route**, entered from Plan, recommendations,
   muscle detail, and in-workout add/swap. It is not a tab.
3. **Goals remain a pushed route** from Home or Plan. Goals are not a tab.
4. **Settings is a tab destination.** The tab bar stays visible on it.
   The Settings header has no Back arrow. Export remains the page’s Volt
   act. Backup is never the Volt gym act.
5. **Header gears are gone.** Home’s masthead and Plan’s header do not
   offer Settings. The only Settings entry is the fifth tab.
6. **A sixth tab is not planned.** Adding one — or promoting Library or
   Goals onto the bar — requires a new signed ADR. ADR-006’s
   reconsideration gate still binds that case.
7. Canonical recovery (export / restore) lands on the Settings tab, not
   a header glyph.

   | ID | Task | Primary expected landing |
   |---|---|---|
   | T8 | Recover history via file export or restore | Settings tab |

## Consequences

- Feature packets place new surfaces on these five tabs or as pushed
  routes. They do not add a sixth tab.
- Library discoverability stays contextual links and copy, not a tab.
- Instrument (ADR-005) is unchanged. This packet does not restyle the bar.
- Current-voice vocabulary is **Home · Body · Plan · History · Settings**,
  Library pushed.

## Review questions

- Is Settings a pushed route? No. It is the fifth tab.
- May Library return to the bar? Not without a new ADR.
- May Goals become a tab? Not without a new ADR.
- Do Home and Plan keep a settings gear? No.
