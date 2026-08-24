# Temper

[![CI](https://github.com/sinura7/PersonalTrainer/actions/workflows/ci.yml/badge.svg)](https://github.com/sinura7/PersonalTrainer/actions/workflows/ci.yml)

Local-first strength tracker for Android, built for one person's training. The launcher
name is **Temper**. Package and `applicationId` stay `com.sinura.personaltrainer` so
installs and Room history keep their identity. Workouts stay on the device (Room).
Weights are stored in kilograms and can be shown as kg or lbs.

The current program — what is being built next, and the decisions that bind it — is
[docs/FOUNDATION_PROGRAM.md](docs/FOUNDATION_PROGRAM.md). This README describes the
shipping strength logger.

The Play Store is not required: build and install from Android Studio, or sideload a signed
APK and let Obtainium watch GitHub Releases.

## Run it

1. Clone the repo:
   ```bash
   git clone https://github.com/sinura7/PersonalTrainer.git
   ```
2. **File → Open** the folder that contains `settings.gradle.kts`.
3. Trust the project and wait for Gradle sync.
4. Plug in a phone (API 26+) or start an emulator, then press **Run ▶**.

That is the whole day-to-day loop. Debug builds use Android Studio's debug keystore and
need no configuration.

## Documentation

| | |
|---|---|
| [docs/FOUNDATION_PROGRAM.md](docs/FOUNDATION_PROGRAM.md) | **current program — packets, gates, signed decisions** |
| [docs/architecture/](docs/architecture/README.md) | accepted architecture decision records |
| [docs/foundation-audit/](docs/foundation-audit/README.md) | canonical current-state audit (23 August 2026) |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | project layout, running tests, what will bite you |
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

- **Home** — today's plan and one next-session act. A live session is resumed from the
  live bar, not from a Home Resume button.
- **Body** — muscle heat from real working sets over **This week** or **Last 30 days**,
  muscle detail, rule-based recommendations. The coach basis is a fixed trailing 14 days.
- **Plan** — the pinned week and the routines that fill it. Suggest / Replay / Tune /
  Lighter week are four different acts.
- **History** — finished sessions, calendar, personal records.
- **Library** (pushed) — search the lift list, filter by muscle, add custom exercises.
- **Logging** — weight + reps, optional RPE and warm-up, suggested next weight.
- **Rest timer** — foreground service + notification so rest keeps running when the app
  is minimized; 1:00 / 1:30 / 2:00 / custom presets; sound and vibration when rest ends.
  Exact completion on modern Android is a known defect (FND-001) being closed in the
  foundation program; the UI must not call an inexact fallback "reliable".
- **Progression** — the **top set** of the last finished session for that lift vs target
  reps. Hit target → add one increment; 1–2 reps short → hold; 3+ short → drop one
  increment. The increment is **2.5 kg** or **5 lbs** in the display unit
  (`IncrementTable`), never a converted "+5.5 lbs". A back-off set never lowers next
  session's suggestion. Bodyweight lifts are told to add a rep.
- **Settings** — kg/lbs display, rest sound/vibration and default rest, backup and
  restore, current app version.

**Backup, not sync.** Export/import a JSON file with no Google account, or make an
optional whole-file Google Drive **backup**. Restores are validated before anything is
written, and refuse to run while a workout is in progress. Drive does not merge two
phones. Android Auto Backup is currently enabled in the manifest; it is **not** the
supported recovery path and is scheduled to be disabled. Use Export to file.

Training works offline. A backup is only read when you ask for one. Cardio, backdated
new activities, timed two-a-day schedules, and measurable goals are the foundation
program's target, not shipping behavior.

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
data/local       Room entities, DAOs, TrainerDatabase (v2)
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

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- minSdk 26 (Android 8.0)
