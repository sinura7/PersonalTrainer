# ADR-006 — Four-tab information architecture and evidence protocol

- **Status:** Accepted
- **Date:** 24 August 2026
- **Supersedes:** Five-tab shipping IA; three-tab UI_REDESIGN §6 as a live
  target. Historical D1 remains the *reason* four tabs shipped.
- **Related:** FND-031, FND-032, FND-033, FND-046; P0.3; P9.1

## Context

D1 signed Home · Body · Plan · History, Library pushed. That is the current
product. The agreed fitness platform adds cardio, two activities per day,
goals, and annual progress. Those features can be stuffed into four tabs or
used as an excuse for a fifth. A fifth tab is a common failure mode and is
rejected unless measured evidence demands it.

## Decision

1. **Starting IA, and the default until evidence says otherwise:**
   - Tabs: Home · Body · Plan · History.
   - Library is a pushed route, entered from Plan, recommendations, muscle
     detail, and in-workout add/swap.
   - Settings, editors, Active Workout, Summary, Session/Activity Detail,
     Exercise Detail, and the future Activity Composer are pushed routes.
   - The Live Session Bar is chrome, not a tab, and is the only live-session
     affordance.
2. **No fifth tab is planned.** Adding one requires a new signed ADR after
   the evidence protocol below fails for four tabs.
3. **No route or tab code changes in Phase 0.** This record freezes the
   default; it does not restyle the bar.
4. Canonical future tasks, used for later IA evidence:

   | ID | Task | Primary expected landing |
   |---|---|---|
   | T1 | Start today’s planned strength activity | Home, one filled act |
   | T2 | Record a cardio activity, live or manual | Home or start sheet → composer/live cardio |
   | T3 | Schedule morning cardio and evening strength on one day | Plan day sheet |
   | T4 | Find the latest completed activity | History |
   | T5 | Find a lift by the muscle it trains | Body or Library from Body/Plan |
   | T6 | Inspect annual progress | History or a pushed analytics route from Home/History |
   | T7 | Recover an empty week when routines already exist | Home / Plan replay, not a new tab |

5. **Reconsideration gate (P9.1).** Four tabs stay unless *all* of the
   following are true:
   - representative beginner and intermediate users, plus the owner, attempt
     every canonical task;
   - at least one canonical task has **unassisted completion below 80%** or a
     **majority first-click failure**;
   - a comparative prototype of the alternative is tested on the same tasks;
   - the alternative does **not** push any other canonical task below the
     same 80% / no-majority-first-click threshold.
6. An alternative that fixes T5 by burying T4, or fixes T6 by adding a fifth
   tab without passing (5), is rejected.
7. Goals are pushed from Home or Plan. Home shows at most one compact goal
   snapshot. Goals are not a tab.
8. Plan command vocabulary (Suggest / Replay / Tune / Lighter) remains four
   ideas. Evidence may change copy and disclosure, not collapse the ideas
   into one control.

## Consequences

- Feature packets place new surfaces on the existing four tabs or as pushed
  routes.
- Library discoverability (FND-032) is solved with contextual links and
  copy, not a tab.
- Body’s below-fold recommendations (FND-033) are an evidence question in
  P9.1 / P9.6, not a silent fifth surface.

## Review questions

- Is four tabs permanent? It is the permanent *default*, with a measurable
  gate. That is stronger than taste and weaker than dogma.
- May cardio get its own tab? Not without failing the gate and signing a
  new ADR.
- Does this packet change `AppNav.kt`? No.
