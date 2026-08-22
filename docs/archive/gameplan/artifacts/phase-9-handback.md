# Phase 9 — the guided setup

> The app's front door. Not in the original game plan — it came out of a UX audit of the
> finished ten phases, which found that everything shipped assumed a lifter who already had
> routines and a pinned week, and a fresh install had neither.

## 1. What was wrong

The audit traced the cold-start path on the shipped code. It ran:

install → masthead reads **REST DAY** → the one filled button says "Suggest a week" → accept
the fills → start "Upper" → **a session containing zero lifts** → Add a lift → pick from 98
rows → Log 0 kg × 12 → **rejected**, with an error about warm-up mode.

Six steps, and four of them were defects. Every one is now closed, three of them before this
phase's own work began because the setup's output would have landed on them.

## 2. Before the feature: four fixes

| Fix | What it was |
|---|---|
| **The branch did not compile** | `ThisWeekCard` declared six required parameters; `HomeScreen` passed five. Introduced in Phase 6b by deleting the argument and leaving the parameter. It had been broken for three phases, each reporting preflight green. |
| **`check-required-args.py`** | The reason nothing caught it. `check-named-args` asks whether every name you passed exists; nothing asked whether you passed everything required. That is now the eleventh check. |
| **Bodyweight lifts could not be logged** | `SetLogRules` refused any working set at 0 kg, so 18 of the 98 catalog lifts were unrecordable — and told the user to log them as warm-ups, which would have excluded them from every total. Now loadType-aware. |
| **A fresh install said REST DAY** | The week derivation always returns seven days and fills every unpinned one with a rest day, so a new install had a non-null day whose `isRest` was true. The `day == null → READY TO TRAIN` branch could never fire. |

**A correction to the Phase 7 hand-back.** It told you a bodyweight-only set "logs 0 kg of
volume". That was wrong — `setVolumeKg` has always substituted a flat 40 kg stand-in, and
`WorkingVolumeAgreementTest` pins it. The real defect was narrower and worse: not mis-valued,
**unrecordable**. The document has been corrected, since it was handed over as the basis for a
decision.

## 3. The six questions

| # | Question | What it changes |
|---|---|---|
| 1 | How much lifting have you done? | Lifts per session, and the split |
| 2 | How many days a week? | The split, and the week's shape |
| 3 | Which days? | Which days get pinned |
| 4 | Where will you train? | Filters the catalog — here and everywhere else in the app |
| 5 | What are you training for? | Coach ordering; breaks split ties |
| 6 | Roughly what do you weigh? | Replaces the 40 kg stand-in in bodyweight-set volume. Skippable |

**Cut, and why.** *Height* — nothing in a strength app consumes one; it changes no lift, no
set, no rep. *Body type* — somatotype does not predict how anyone responds to training, and
shaping someone's first program on it would be inventing a reason. A question whose answer
changes nothing is a screen the lifter pays for and gets nothing back, which is the same
principle as "not overwhelming", applied to the setup itself.

**The split is never asked.** It is derived from experience, days and goal. Asking a new lifter
to choose between push/pull/legs and upper/lower is asking them to compare two things they have
no basis to compare — the opposite of guided. The rules, and the reasoning:

- 2–3 days → **full body**. Fewer sessions cannot cover the body in parts; splitting them means
  training chest once every ten days.
- A new lifter → never PPL. Six separate sessions to learn at once, for no benefit while the
  weights are still light enough to recover from easily.
- Bodyweight only → never PPL at 4+ days. You cannot build six distinct pushing lifts out of a
  living room; the sessions would come out short or padded with a fourth push-up variant.
- Strength at 5–6 days → **upper/lower**. Heavier work wants fewer, bigger sessions and more
  rest between them than a six-way split leaves room for.

## 4. How the generator works

It invents no training science. It composes four things that already shipped:

- `WeeklySchedulePlanner.slotKinds` — the order of sessions in a week, so setup and planner
  cannot disagree about what a four-day upper/lower looks like.
- The catalog's `movementKey` families — a slot asks for "a row" and the catalog answers with
  the best row this lifter can actually reach.
- `CatalogMeta.sortRank` — which lift in a family is best. The same order the Library shows.
- `AddDefaults` — sets, reps and rest.

The only judgment original to it is **which families make a session, and in what order**, which
is the part a lifter would otherwise be guessing at. Compounds first, isolation last.

**One change to `AddDefaults` was needed.** It sees how a lift is loaded and how many muscles it
credits, and by those two facts a barbell back squat and a Bulgarian split squat are the same
lift — both external, both compound, both 3 × 5 at 150 seconds. Which one is the *main* lift is
a property of the session, not the exercise. So `LiftRole` is passed in. `PRIMARY` is the
default and is exactly what shipped before, so a hand-added lift is unchanged; the generator
marks its opening two lifts primary and the rest accessory.

That fix came from reading the rendered output, not from a test. Before it, every lift in a
generated leg day read 3 × 5 / 150s. After it, a beginner's upper day reads:

| # | Lift | Sets × Reps | Rest |
|---|---|---|---|
| 1 | Barbell Bench Press | 3 × 5 | 150s |
| 2 | Barbell Row | 3 × 5 | 150s |
| 3 | Overhead Press | 3 × 8 | 90s |
| 4 | Lat Pulldown | 3 × 12 | 60s |

## 5. Review the programming, not the code

**[`onboarding-programs-review.md`](onboarding-programs-review.md)** renders all 135 reachable
programs — every combination of experience, days, place and goal, with every lift, its
equipment, and its targets. Golden-file tested, so the document and the generator cannot drift.

**This is the artifact to actually read.** Which lifts go in a session at what sets and reps is
a training opinion, and you are a lifter — you will disagree with some of it. Disagreeing is
cheap right now and expensive once it is on a phone. To change a judgment call, edit
`RoutineGenerator.TEMPLATES`, run `tools/render-artifacts.sh`, and review the diff.

**Two coarsenesses I already know about**, both from `AddDefaults` being a two-axis table:

- A Nordic ham curl comes out at 3 × 12. It is bodyweight and not compound, so the table says
  twelve; almost nobody can do twelve.
- A weighted dip and a pull-up both open at 3 × 6 / 120s regardless of how strong you are.

Both are data refinements — a per-lift rep range in the catalog — rather than architecture. I
left them rather than special-casing, because a second targets system is how the app ends up
with two opinions about the same number.

## 6. Safety properties, and what they cost

- **Nothing is written until "Use this plan."** Backing out at any point leaves the app exactly
  as it was, and the preview shows real lifts before a single routine exists.
- **Re-running setup deletes nothing.** It is reachable from Settings → Your plan, and someone
  running it again may have months of history pointing at routines they still use. A second
  "Upper" is a mess they can see and fix in ten seconds; a deleted routine their last month of
  sessions points at is not recoverable from inside the app. The preview says so when it
  detects an existing program.
- **Preferences are written first.** If anything after them fails, the lifter still has an app
  that knows their days, goal and equipment — worse than the whole plan, far better than a
  questionnaire filled in for nothing.
- **The gate has three states.** `UNKNOWN` renders nothing, because DataStore reads are
  asynchronous and defaulting either way flashes the wrong screen on every cold start — on a
  first install, a flash of exactly the empty Home this phase exists to prevent.

## 7. Bugs found in my own work while building it

Recorded because each one looked fine and none was caught by a check:

- The 135-combination sweep found bodyweight-only sessions arriving **short** — two of six
  upper-body slots need equipment that does not exist in a living room, and a "six-lift session"
  was delivering four. Fixed with a per-kind fallback pool.
- `TrainingAge.entries.map { Choice(…) { viewModel.setExperience(it) } }` — the inner `it`
  refers to the trailing lambda, which takes no parameter. Unresolved reference.
- "I'll build my own" and the finish callback **raced**, and whichever landed second won, so
  choosing to build your own sometimes dropped you on Home instead of in the editor.
- `OnboardingScreen` is composed outside the app's Scaffold, so nothing handled the status-bar
  inset — the first thing a new install would have shown is a header under the clock.
- Three `runCatching` calls in coroutines would have swallowed cancellation; this codebase has
  `runCatchingCancellable` for exactly that.

## 8. Still outstanding — yours

1. **A real build.** Still true, and now more load-bearing than ever: the domain layer compiles
   and its 372 tests pass, but nothing in `ui/onboarding/`, `OnboardingApplier`, or the AppNav
   gate has been through a compiler.
2. **`app/schemas/…/2.json`** — Phase 3's Room schema export, still needs Gradle.
3. **The programs artifact.** §5. This is the one that needs your eye rather than a machine's.
4. **Device pass on the new path:** install fresh → six questions → preview → Use this plan →
   Home should show a real session today → start it → log a set. Then Settings → Your plan →
   Rebuild, and confirm your existing routines survive.

## 9. What this does not do

The **12-week block** is not built. The app has no mesocycle model, and adding one collides with
something already shipped: `ProgressionCalculator` decides next session's weight from what you
actually lifted, which is autoregulation, while a pre-written block is prescription. Ship both
naively and you have two systems answering "what do I do today" — the exact pathology the audit
named as the app's dominant flaw.

The clean resolution, for whenever you want it: **the block owns reps and volume; autoregulation
owns load.** The block says "3 × 8 this week, 3 × 6 next, week 7 is a deload"; the progression
engine says "you hit your reps, add 2.5 kg". They answer different questions and cannot
contradict each other. And store the *block*, not its output — writing 12 weeks of slots up
front fights `WeekDerivation`, which is already smart about missed sessions.

Phase 9 is complete and coherent without it.
