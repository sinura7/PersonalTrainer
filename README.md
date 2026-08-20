# Personal Trainer

[![CI](https://github.com/sinura7/PersonalTrainer/actions/workflows/ci.yml/badge.svg)](https://github.com/sinura7/PersonalTrainer/actions/workflows/ci.yml)

Local-first strength tracker for Android, built for one person's training. Workouts stay on
the device (Room). Weights are stored in kilograms and can be shown as kg or lbs.

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
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | project layout, running tests, what will bite you |
| [SETUP.md](SETUP.md) | release keystore, Google Drive OAuth, building a signed APK |
| [docs/RECOVERY.md](docs/RECOVERY.md) | **new phone, dead laptop, lost keystore — read before you need it** |
| [docs/ROADMAP.md](docs/ROADMAP.md) | what is built, what is next |
| [docs/AUDIT.md](docs/AUDIT.md) | the code review this roadmap came from |
| [docs/HIERARCHY_PLAN.md](docs/HIERARCHY_PLAN.md) | audit of the five-tab hierarchy proposal and the recommended plan |
| [docs/DESIGN_AUDIT.md](docs/DESIGN_AUDIT.md) | the product/design bar, screen by screen |

## What it does

- **Home** — start or resume a workout, jump to routines / library / history, training balance, lifts ready to progress
- **Body map** — heat from real working sets (7 / 14 / this week), muscle detail, rule-based recommendations
- **Weekly schedule** — suggested training days from heat, routines, and your day/split prefs; start a day as a routine or focused free workout
- **Routines** — create, edit, reorder, and delete programs
- **Library** — search the lift list, filter by muscle, add custom exercises
- **Logging** — weight + reps, optional RPE and warm-up, suggested next weight
- **Rest timer** — foreground service + notification so rest keeps running when the app is minimized; 1:00 / 1:30 / 2:00 / custom presets; sound and vibration when rest ends
- **Progression** — the **top set** of your last session for that lift vs target reps:
  - hit target → suggest **+2.5 kg**
  - 1–2 reps short → keep the same weight
  - 3+ reps short → suggest **−2.5 kg**

  Judging the heaviest set means a back-off set never lowers next session's suggestion.
- **History** — finished sessions with sets grouped by exercise
- **Settings** — kg/lbs display, rest sound/vibration and default rest, backup and restore, current app version
- **Backup** — export/import a JSON file (no Google account needed) or sync with your own
  Google Drive. Restores are validated before anything is written, and refuse to run while
  a workout is in progress

Training works offline. A backup is only read when you ask for one.

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
data/local       Room entities, DAOs, TrainerDatabase
data/repository  the only classes that touch DAOs
data/backup      backup document, JSON codec, validator, Drive client
domain           pure Kotlin — models, units, muscle heat, recommendations, planner, progression
timer            rest timer store, controller, foreground service, wakeup alarm, notifications
workout          in-progress workout draft (memory + saved state)
ui/home, ui/progress, ui/schedule, ui/routines, ui/workout, ui/history, ui/library, ui/settings
```

ViewModels talk to repositories, never to DAOs. No Hilt — `PersonalTrainerApp` holds an
`AppContainer`. `domain/` has no `android.*` imports, which is why most of the test suite
runs on the JVM in seconds. More in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- minSdk 26 (Android 8.0)
