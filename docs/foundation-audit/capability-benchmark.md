# Capability gap and public-product benchmark

## 1. Method

This is desk research dated 23 August 2026. It uses public help, legal, and marketing
materials. No competitor app was installed; claims such as “fast,” “simple,” “AI,” and
“premium” are vendor positioning unless explicitly described as a documented workflow.

The purpose is to establish a product bar, not to copy a competitor’s information
architecture.

## 2. Agreed product definition

Temper should serve a deliberate mix:

- intermediate users who mainly need a fast, trustworthy recorder;
- beginners who benefit from a questionnaire and transparent guidelines;
- people who combine lifting and cardio;
- one self-coached person per app installation in the initial product.

Core principles:

- no account required;
- local records and export remain user-owned;
- optional account/cloud sync later;
- native Android first, eventual iOS;
- deterministic offline recommendations;
- future API may explain or propose adjustments but is not required for recording;
- multiple timed activities can occur on one day;
- reminders are explicit, respectful, actionable, and user-controlled;
- core local recording is not held behind a subscription.

## 3. Current-versus-target capability matrix

| Capability | Temper today | Agreed target | Classification |
|---|---|---|---|
| Strength live logging | Weight, reps, RPE, warm-up, notes, PRs, rest | Preserve and refine | Strong |
| Strength templates | Routines with targets and schedule pins | Preserve | Strong |
| Cardio | No activity model | First-class typed recording | Critical target gap |
| Mixed workout | Strength exercises only | Ordered strength/cardio blocks when useful | Critical target gap |
| Multiple activities/day | Multiple finished strength sessions possible; one live session | Independent morning/evening occurrences | High target gap |
| Manual/backdated entry | Finished set repair only | Create past activity with time zone/source | High target gap |
| Schedule | Recurring strength cycle and derived week | Recurrences plus dated/timed occurrences | Partial |
| Missed-session handling | Silently shifts within week; resets next week | One prompt to move/push/adapt/skip | Mismatch |
| Workout reminders | None | Start, Snooze, Move, Rest day/Skip | Critical target gap |
| Rest timer | Full UI and notification model | Preserve after reliability fix | Critical defect |
| Beginner guidance | Questionnaire, generated block | Extend to activity times/cardio intent | Strong base |
| Recommendations | Transparent local rule engine | Preserve; add structured explanation trace | Strong base |
| Measurable goals | Goal enum influences generation/order | Actual target, period, progress, pause | High target gap |
| Day progress | Home and session | Unified daily agenda and completion | Partial |
| Week progress | Schedule, heat, adherence implied | Explicit adherence and active-minute goals | Partial |
| Month progress | Calendar and history grouping | Cross-month trends | Partial |
| Year/all-time progress | Raw history and per-lift lifetime | Dedicated comparable horizons | High target gap |
| Bodyweight | Optional current/log and block comparison | Optional measurable goal/history | Partial |
| Data export | Restorable JSON | Preserve stable round trip | Strong |
| Backup | Local file and manual Drive | Preserve | Strong |
| Multi-device sync | None | Optional incremental/encrypted sync | High target gap |
| Account | None | Optional only | Aligned |
| Offline core | Yes | Non-negotiable | Strong |
| Privacy controls | Local by default, no telemetry | Explicit Auto Backup/export/API consent | Partial |
| iOS portability | JVM-pure domain | Platform-neutral shared rules, native UI | Partial |
| Subscription | None | Cloud/advanced value only | Future extension |

## 4. Recording benchmark

| Product | Strength recording | Cardio recording | Offline/account boundary |
|---|---|---|---|
| Strong | Templates/empty workout, previous values, set types, RPE, supersets, notes, rest | Time/distance exercise metrics; no documented native GPS recorder | Cloud account/sync; complete phone-offline contract not clearly documented |
| Hevy | Routine/empty starts, previous values, RPE, set types, supersets, timer, PRs | Distance/time/pace exercises; Strava export | Cloud profile; watch workflows document offline use |
| Fitbod | Generated editable workout, exertion, timer, plates, circuits | Duration cardio in workout plus integrations | Previously generated workout can complete offline |
| Caliber | Strong keyboard and next/complete flow, supersets, automatic timer | Duration, distance, calories, pace; integrations | Explicit offline mode; cloud account |
| Strava | New strength detail is primarily created/edited around a recorded activity | Mature GPS, sensors, routes, sport types | Recording can wait for upload; account-centric cloud |
| Apple Fitness/Workout | Session-level strength, not deep set/load tracking | Deep GPS, heart rate, pace, intervals, many modalities | Device-local Health foundation; optional encrypted iCloud |
| Garmin Connect ecosystem | Device-dependent rep/set/load capture and structured workouts | Deep sensor/GPS/multisport recording | Watch records offline; Connect account sync |

### Recording conclusion

No reviewed product clearly combines all of:

- deep live set logging;
- first-class cardio;
- multiple independent activities/day;
- optional login;
- local-first operation;
- explainable offline planning.

That intersection is a credible Temper position. It is also architecturally demanding; cardio
must not be faked as an exercise row.

## 5. Planning, goals, and progress benchmark

| Product | Planning/reminders | Goals/progress | Notable lesson |
|---|---|---|---|
| Strong | Reusable templates; scheduling details are weakly documented | Workout-count target, records, history; charts in paid tier | Excellent self-directed recorder, limited guidance |
| Hevy | Routine weekday widget; calendar is mainly retrospective | Streaks and exercise graphs across longer paid horizons | Social/cloud model is not Temper’s privacy model |
| Fitbod | Adaptive workouts and selected-day previews | Weekly goal, streak, recovery and reports | Generation reduces setup but can hide reasoning |
| Caliber | Coach-programmed strength/cardio/habits; self-service scheduling still evolving | Broad charts, habits, strength score | Input-flow optimization is worth emulating |
| Strava | Training calendar/log; specialized plans | Metric goals over week/month/year and combined sports | Flexible metric + activity + period grammar |
| Apple | Custom Workouts, Plans, Stacks, reminders, multiple sessions/day | Rings, awards, trends; goals can pause | Humane pause and on-device privacy are strong models |
| Garmin | Device calendar, structured workouts, adaptive coach | Records, badges, coach progress, long-range charts | Hardware dependence should not be required by Temper |

## 6. Product principles to adopt

### 6.1 Set a logging-friction budget

A working strength set should keep:

- previous result visible;
- one obvious completion action;
- automatic but dismissible rest;
- direct typing and tactile adjustment;
- in-session correction.

Measure taps and completion time. Do not call a flow “fast” without observing it.

### 6.2 Model the day, not only the workout

A day contains zero-to-many scheduled occurrences and zero-to-many completed sessions.
A session may contain ordered typed blocks. Morning cardio and evening lifting are two
occurrences, not one overloaded routine or one fake exercise.

### 6.3 Make local-first observable

Recording, history, templates, goals, reminders, and local recommendations should work in
airplane mode. Sync should have its own optional state; “not signed in” is not degraded app
health.

### 6.4 Use humane goal grammar

Initial target families:

- sessions per week;
- active minutes per week;
- lift load/repetition by date;
- cardio duration/distance by period;
- optional bodyweight by date.

Each goal needs:

- metric;
- activity scope;
- period/deadline;
- progress;
- pause;
- edit;
- complete/abandon reason.

Avoid streaks that punish illness, travel, rest, or deliberate rescheduling.

### 6.5 Make reminders actionable

Only notify from explicit schedule/goal intent. Actions should include:

- Start;
- Snooze;
- Move;
- Skip or Rest day.

Use one missed-session check-in. Do not send indefinite engagement notifications or
guilt-based copy.

### 6.6 Keep recommendations deterministic

The local engine should expose:

- action;
- rule identifier;
- evidence window;
- inputs used;
- threshold;
- alternatives;
- override.

A future API can improve wording, not become the hidden authority for loads or schedule
rewrites.

### 6.7 Show comparable horizons

Recommended product horizons:

- current session/day;
- current week;
- rolling four weeks;
- rolling twelve weeks;
- year;
- all time.

Equivalent windows should be compared. Do not normalize two periods differently and label
the result as progress.

### 6.8 Treat accessibility as premium execution

TalkBack, scalable type, large targets, contrast, non-color cues, reduced motion, timer
alternatives, and keyboard/switch access are part of product quality before decorative
themes.

### 6.9 Preserve round-trip ownership

Export must have stable identifiers, timestamps, time zones, units, source, and schema
version—and the app must be able to import what it exports. A compliance-only CSV that
cannot restore a user’s app is not enough.

## 7. Patterns to avoid

- Account creation before the first workout.
- Paywalling raw local history, export, or recovery.
- Modeling cardio as a lifting exercise.
- Forcing one activity per day.
- Cloud-only coaching described as offline.
- Silent plan rewrites.
- Generic or punitive reminders.
- Public/social defaults for fitness data.
- Hardware dependency for core recording.
- “AI” that cannot identify the rule, evidence, or alternative.
- Premium visuals without declared accessibility support.

## 8. Recommended commercial boundary

Keep available without subscription:

- unlimited local recording;
- local history;
- core templates;
- measurable goals;
- schedule/reminders;
- deterministic local recommendations;
- rest timer;
- export/import.

Plausible later subscription value:

- encrypted multi-device sync;
- cross-platform continuity;
- advanced comparative analytics;
- optional server-assisted explanations;
- future shared coaching workflows.

The subscription should add service value, not rent access to data the user entered.

## 9. Source register

Official or vendor-maintained materials, accessed 23 August 2026:

- Strong:
  [first workout](https://help.strongapp.io/article/229-my-first-workout),
  [account/cloud](https://help.strongapp.io/article/143-strong-account),
  [privacy](https://help.strongapp.io/article/232-privacy-policy)
- Hevy:
  [feature guide](https://help.hevyapp.com/hc/en-us/articles/33106320824727-Everything-You-Need-to-Know-About-the-Hevy-App-2025-Features-Guide),
  [program library](https://help.hevyapp.com/hc/en-us/articles/36011518408983-How-to-Access-and-Use-Hevy-s-Routine-and-Program-Library),
  [calendar/streak](https://help.hevyapp.com/hc/en-us/articles/35380117933207-Track-Your-Workout-Consistency-with-the-Calendar-and-Streak-Features)
- Fitbod:
  [My Plan](https://help.fitbod.me/hc/en-us/articles/34336407191191-My-Plan),
  [algorithm](https://help.fitbod.me/hc/en-us/articles/16254175592215-Fitbod-s-Algorithm-Q-A),
  [cardio](https://help.fitbod.me/hc/en-us/articles/360006427673-Cardio-Recommendations)
- Caliber:
  [offline mode](https://feedback.caliberstrong.com/announcements/caliber-422-improved-offline-mode-bug-fixes),
  [input flow](https://feedback.caliberstrong.com/announcements/caliber-540-custom-keyboard-automatic-rest-timer-default-exercise-settings-sundaymonday-start-date-g),
  [export and plans](https://feedback.caliberstrong.com/announcements/caliber-570-export-workout-data-plans-section-rebuild-dark-mode)
- Strava:
  [strength training](https://support.strava.com/en-us/articles/15401547-strength-training),
  [activity recording](https://support.strava.com/en-us/articles/15402137-recording-an-activity),
  [goals](https://support.strava.com/en-us/articles/15401694-goals-on-the-strava-app),
  [data export](https://support.strava.com/en-us/articles/15401919-exporting-your-data-and-bulk-export)
- Apple:
  [Fitness+](https://support.apple.com/en-us/108761),
  [custom plans](https://support.apple.com/guide/fitness-plus/create-a-custom-plan-apdf222051d8/ios),
  [activity trends](https://support.apple.com/guide/iphone/see-your-activity-summary-iph4c34a8a95/ios),
  [Health storage](https://support.apple.com/en-us/108779)
- Garmin:
  [strength manual](https://www8.garmin.com/manuals/webhelp/GUID-EECCAC99-90D6-4AB1-9A3A-EC433D3365E2/EN-US/GUID-573EC4B6-D45B-46E7-BE37-FB542CBB4FC1.html),
  [training calendar](https://www8.garmin.com/manuals/webhelp/GUID-25E3235D-44D2-4384-A591-DD1D71BEBCB1/EN-US/GUID-F5EB9C7C-A74E-4A26-BFB5-1E6DA4399067.html),
  [Garmin Connect privacy](https://www.garmin.com/en-US/privacy/connect/policy/)

Public material changes frequently. Revalidate commercial tiers and current workflows before
using this benchmark for pricing or launch claims.
