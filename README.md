# Personal Trainer

Local-first strength tracker for Android. Workouts stay on the device (Room). Weights are stored in kilograms and can be shown as kg or lbs.

The Play Store is not required. Install the signed APK yourself or let Obtainium watch GitHub Releases.

## Open in Android Studio

1. Clone the repo:
   ```bash
   git clone https://github.com/sinura7/PersonalTrainer.git
   ```
2. **File → Open** the folder that contains `settings.gradle.kts`.
3. Trust the project and wait for Gradle sync.
4. Run on an API 26+ emulator or phone.

Debug builds use the default debug keystore. Release signing, OAuth, and updates are in [SETUP.md](SETUP.md).

## What it does

- **Home** — start or resume a workout, jump to routines / library / history, training balance, lifts ready to progress
- **Body map** — heat from real working sets (7 / 14 / this week), muscle detail, rule-based recommendations
- **Weekly schedule** — suggested training days from heat, routines, and your day/split prefs; start a day as a routine or focused free workout
- **Routines** — create, edit, reorder, and delete programs
- **Library** — search the lift list, filter by muscle, add custom exercises
- **Logging** — weight + reps, optional RPE and warm-up, suggested next weight
- **Rest timer** — foreground service + notification so rest keeps running when the app is minimized; 1:00 / 1:30 / 2:00 / custom presets; sound and vibration when rest ends
- **Progression** — last working set vs target reps:
  - hit target → suggest **+2.5 kg**
  - 1–2 reps short → keep the same weight
  - 3+ reps short → suggest **−2.5 kg**
- **History** — finished sessions with sets grouped by exercise
- **Settings** — kg/lbs display, rest sound/vibration and default rest, optional Google Drive backup/restore, current app version

Training works offline. Drive is only used when you back up or restore.

## Version and updates

Bump `appVersionCode` and `appVersionName` at the top of `app/build.gradle.kts`, then follow [SETUP.md](SETUP.md):

1. Bump version
2. Build the signed release APK (`PersonalTrainer-<version>.apk`)
3. Create a GitHub Release and attach that APK

Obtainium can install and update from those releases. One standard APK per release — no Play Store, no app bundle, no split APKs.

## Architecture

```
data/local       Room entities, DAOs, TrainerDatabase
data/repository
data/backup      Drive JSON backup / restore
domain           models, units, muscle heat, recommendations, weekly schedule, ProgressionCalculator
timer            foreground rest service, notification, alerts
ui/home, ui/progress, ui/schedule, ui/routines, ui/workout, ui/history, ui/library, ui/settings
```

ViewModels talk to repositories. No Hilt — `PersonalTrainerApp` holds an `AppContainer`.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- minSdk 26
