# Temper

[![CI](https://github.com/sinura7/PersonalTrainer/actions/workflows/ci.yml/badge.svg)](https://github.com/sinura7/PersonalTrainer/actions/workflows/ci.yml)

Local-first strength tracker for Android, built for one person's training. The launcher
name is **Temper**. Package and `applicationId` stay `com.sinura.personaltrainer` so
installs and Room history keep their identity. Workouts stay on the device (Room).
Weights are stored in kilograms and can be shown as kg or lbs.

The current program — what is being built next, and the decisions that bind it — is
[docs/FOUNDATION_PROGRAM.md](docs/FOUNDATION_PROGRAM.md). This README describes the
local fitness beta on the debug install.

The Play Store is not required. Day-to-day on the phone is **Obtainium**:
Temper Debug from a GitHub pre-release (`debug-live-*`,
`PersonalTrainer-*-debug.apk`), gym-floor Temper from a signed
`PersonalTrainer-<version>.apk`. Android Studio is not the install path.

## Run it

**Phone (owner).** Install [Obtainium](https://github.com/ImranR98/Obtainium).
Add `https://github.com/sinura7/PersonalTrainer`. Include pre-releases.
Prefer `PersonalTrainer-*-debug.apk` for **Temper Debug**. Keep a separate
entry on the signed `PersonalTrainer-<version>.apk` for gym-floor **Temper**.
Do not mix those two apps.

**Cursor.** Clone, JVM-gate (`./gradlew testDebugUnitTest assembleDebug`),
squash-merge to `trunk`. Then cut a `debug-live-*` pre-release so Obtainium
can update Temper Debug. Details: [SETUP.md](SETUP.md) §6.

Debug and release are **separate apps**. Debug is `com.sinura.personaltrainer.debug`.
Release stays `com.sinura.personaltrainer`. Different signing keys, different
databases, different icons.

## Documentation

| | |
|---|---|
| [docs/FOUNDATION_PROGRAM.md](docs/FOUNDATION_PROGRAM.md) | **current program — packets, gates, signed decisions** |
| [docs/architecture/](docs/architecture/README.md) | accepted architecture decision records |
| [docs/architecture/backup-threat-model.md](docs/architecture/backup-threat-model.md) | signed backup/privacy inventory (P3.1) |
| [docs/foundation-audit/](docs/foundation-audit/README.md) | canonical current-state audit (23 August 2026) |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | project layout, running tests, what will bite you |
| [docs/PRIVACY.md](docs/PRIVACY.md) | what Temper stores, backup, diagnostics, no default telemetry |
| [docs/DATA_SAFETY.md](docs/DATA_SAFETY.md) | Play Data safety answers |
| [docs/SUPPORT.md](docs/SUPPORT.md) | how to report a defect without sending a database |
| [docs/COMMERCIAL_BOUNDARY.md](docs/COMMERCIAL_BOUNDARY.md) | local core is never paywalled |
| [SETUP.md](SETUP.md) | release keystore, Google Drive OAuth, building a signed APK |
| [docs/RECOVERY.md](docs/RECOVERY.md) | **new phone, dead laptop, lost keystore — read before you need it** |
| [docs/ROADMAP.md](docs/ROADMAP.md) | *historical* — how the strength logger was built |
| [docs/AUDIT.md](docs/AUDIT.md) | pointer to the current audit, plus the 19 August review |
| [docs/HIERARCHY_PLAN.md](docs/HIERARCHY_PLAN.md) | *historical* audit of a five-tab proposal; shipping IA is four tabs |
| [docs/SCHEDULE_SEMANTICS.md](docs/SCHEDULE_SEMANTICS.md) | current v2 schedule-slot derivation; target missed-work policy is ADR-012 |
| [docs/MIGRATION_REHEARSAL.md](docs/MIGRATION_REHEARSAL.md) | *historical* v1→v2 runbook; `2.json` is committed |
| [docs/artifacts/](docs/artifacts/) | generated review artifacts; the tests fail if these drift from the code |
| [docs/archive/gameplan/](docs/archive/gameplan/README.md) | *archived* — the phase packets the strength logger was built from |
| [docs/DESIGN_AUDIT.md](docs/DESIGN_AUDIT.md) | *museum* — early product/design bar |

## What it does

Four tabs — **Home · Body · Plan · History**. Library is a pushed route, not a tab.

- **Home** — today's plan and one next-session act. Last session and days
  since come from all-time summaries. A live session is resumed from the
  live bar, not from a Home Resume button. Library and Goals are links, not tabs.
- **Body** — muscle heat from real working sets over **This week** or **Last 30 days**,
  muscle detail, rule-based recommendations above the map. The coach basis is a
  fixed trailing 14 days. An empty heat window is not “never trained.”
- **Plan** — the pinned week and the routines that fill it. Suggest / Replay / Tune /
  Lighter week are four different acts.
- **History** — finished sessions, calendar, personal records, and
  comparable Week / Month / Year / All time totals.
- **Library** (pushed) — search the lift list, filter by muscle, add custom exercises.
- **Goals** (pushed) — typed targets. Pause is first-class. No punitive streaks.
- **Activities** — strength, cardio, or mixed; live or backdated. One live
  activity at a time. Two timed items can sit on one day.
- **Logging** — weight + reps, optional RPE and warm-up, suggested next weight.
- **Rest timer** — foreground service + notification so rest keeps running when the app
  is minimized; 1:00 / 1:30 / 2:00 / custom presets; sound and vibration when rest ends.
  Exact completion uses `SCHEDULE_EXACT_ALARM` when the system grant is present. Without
  it the app uses an inexact wakeup and does not call that reliable.
- **Progression** — the **top set** of the last finished session for that lift vs target
  reps. Hit target → add one increment; 1–2 reps short → hold; 3+ short → drop one
  increment. The increment is **2.5 kg** or **5 lbs** in the display unit
  (`IncrementTable`), never a converted "+5.5 lbs". A back-off set never lowers next
  session's suggestion. Bodyweight lifts are told to add a rep.
- **Settings** — kg/lbs display, rest sound/vibration and default rest, backup and
  restore, user-triggered diagnostics, current app version.

**Backup, not sync.** Export/import a file with no Google account, or make an
optional whole-file Google Drive **backup**. The default export is a
password-protected envelope; plaintext is an advanced warned choice. Restores
are validated before anything is written, and refuse to run while a workout is
in progress. Drive does not merge two phones. Android Auto Backup is
**disabled** in the shipping manifest. It is not the recovery path. Existing OS
copies are not recalled. Use Export to file.

Training works offline. A backup is only read when you ask for one. Cardio,
backdated activities, timed two-a-day schedules, and measurable goals ship
on this debug build. Phase 10 (KMP) and Phase 11 (sync) stay gated.

## Version and updates

Bump `appVersionCode` and `appVersionName` at the top of `app/build.gradle.kts`, then either:

**Locally** (see [SETUP.md](SETUP.md)) — build the signed APK, create a GitHub Release,
attach `PersonalTrainer-<version>.apk`.

**Or by tag** — push `vX.Y.Z` and the [release workflow](.github/workflows/release.yml)
builds, signs, verifies and publishes it, provided the `KEYSTORE_*` secrets are configured.

Obtainium installs and updates from those releases. One standard APK per release — no Play
Store, no app bundle, no split APKs.

## Architecture

```
data/local       Room entities, DAOs, TemperDatabase (v4, frozen)
data/repository  the only classes that touch DAOs
data/backup      backup document, JSON codec, validator, Drive backup client
domain           pure Kotlin — models, units, muscle heat, recommendations, planner, progression
insights         TrainingInsightsSource — one analytics pipeline
timer            rest timer store, controller, foreground service, wakeup alarm, notifications
workout          in-progress workout draft (memory + saved state)
ui/home, ui/progress (Body), ui/plan, ui/history, ui/library,
ui/routines, ui/workout, ui/summary, ui/settings, ui/onboarding
```

ViewModels talk to repositories, never to DAOs. No Hilt — ViewModels take `AppDependencies`
in the constructor; `AppContainer` is the production graph. `domain/` has no `android.*`
imports, which is why most of the test suite runs on the JVM in seconds. More in
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). The target activity model and the one
authorized database cutover are [ADR-007](docs/architecture/ADR-007-activity-model.md)
and [ADR-010](docs/architecture/ADR-010-schema-reset-migrations.md).

## Requirements

- JDK 17
- Android SDK 35
- minSdk 26 (Android 8.0)
- Obtainium on the phone (Temper Debug from `debug-live-*` pre-releases)
