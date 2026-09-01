# ADR-006 — Four-tab information architecture and evidence protocol

- **Status:** Accepted (superseded in part by
  [ADR-014](ADR-014-settings-tab.md): Settings is a fifth tab. Library
  remains pushed. [ADR-016](ADR-016-settings-home-trim.md) removes the
  Goals UI. [ADR-017](ADR-017-home-week-board.md) removes Tune as a
  Plan command. The reconsideration gate still binds a sixth tab.)
- **Date:** 24 August 2026
- **Supersedes:** Five-tab shipping IA (Library on the bar); three-tab
  UI_REDESIGN §6 as a live target. Historical D1 remains the *reason*
  Library is not a tab.
- **Related:** FND-031, FND-032, FND-033, FND-046; P0.3; P9.1;
  [ADR-014](ADR-014-settings-tab.md); [ADR-015](ADR-015-plan-day-blocks.md)

## Context

D1 signed Home · Body · Plan · History, Library pushed. That default
rejected stuffing cardio, goals, or the catalog onto the bar.
[ADR-014](ADR-014-settings-tab.md) later placed **Settings** on the bar
because the owner asked for a dedicated Settings space. Library stays
pushed. Goals UI is gone ([ADR-016](ADR-016-settings-home-trim.md)). A
sixth tab is still a common failure mode and is rejected unless the gate
below fails.

## Decision

1. **Starting IA, and the default until evidence says otherwise:**
   - Tabs: Home · Body · Plan · History · Settings
     ([ADR-014](ADR-014-settings-tab.md)).
   - Library is a pushed route, entered from Plan, recommendations, muscle
     detail, and in-workout add/swap.
   - Editors, Active Workout, Summary, Session/Activity Detail,
     Exercise Detail, Plan day, and the Activity Composer are
     pushed routes.
   - The Live Session Bar is chrome, not a tab, and is the only live-session
     affordance.
2. **No sixth tab is planned.** Adding one — or promoting Library onto
   the bar — requires a new signed ADR after the evidence protocol
   below fails for these five tabs.
3. **No route or tab code changes in Phase 0.** This record freezes the
   default; it does not restyle the bar.
4. Canonical future tasks, used for later IA evidence:

   | ID | Task | Primary expected landing |
   |---|---|---|
   | T1 | Start today’s planned strength activity | Home planned row, then confirm ([ADR-021](ADR-021-home-start-and-day-add.md)); filled Volt is Start a workout |
   | T2 | Record a cardio activity, live or manual | Home or start sheet → composer/live cardio |
   | T3 | Schedule cardio and a workout on one day | Plan day page ([ADR-015](ADR-015-plan-day-blocks.md)) |
   | T4 | Find the latest completed activity | History |
   | T5 | Find a lift by the muscle it trains | Body or Library from Body/Plan |
   | T6 | Inspect annual progress | History or a pushed analytics route from Home/History |
   | T7 | Recover an empty week when routines already exist | Home / Plan replay, not a new tab |
   | T8 | Recover history via file export or restore | Settings tab ([ADR-014](ADR-014-settings-tab.md)) |

5. **Reconsideration gate (P9.1).** Five tabs stay unless *all* of the
   following are true:
   - representative beginner and intermediate users, plus the owner, attempt
     every canonical task;
   - at least one canonical task has **unassisted completion below 80%** or a
     **majority first-click failure**;
   - a comparative prototype of the alternative is tested on the same tasks;
   - the alternative does **not** push any other canonical task below the
     same 80% / no-majority-first-click threshold.
6. An alternative that fixes T5 by burying T4, or fixes T6 by adding a sixth
   tab without passing (5), is rejected.
7. Goals UI is removed ([ADR-016](ADR-016-settings-home-trim.md)). Room
   goal tables stay. Goals are not a tab and are not a pushed route.
8. Plan command vocabulary (Suggest / Replay / Lighter) remains three
   ideas plus **Add session** as the fill act
   ([ADR-017](ADR-017-home-week-board.md)). Tune is gone (days / split /
   week-start live on Settings). Evidence may change copy and
   disclosure, not collapse Suggest into Replay.

## Consequences

- Feature packets place new surfaces on the existing five tabs or as pushed
  routes.
- Library discoverability (FND-032) is solved with contextual links and
  copy, not a tab.
- Body’s below-fold recommendations (FND-033) are an evidence question in
  P9.1 / P9.6, not a silent fifth surface.

## Review questions

- Are five tabs permanent? Five is the shipping IA. A sixth tab still
  needs the measurable gate. That is stronger than taste and weaker than
  dogma.
- May cardio get its own tab? Not without failing the gate and signing a
  new ADR.
- Does this packet change `AppNav.kt`? No. Settings-as-tab is ADR-014.
