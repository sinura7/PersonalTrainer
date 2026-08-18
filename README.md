# Personal Trainer

Local-first strength training tracker for Android. Weights are kilograms only. All routines, sets, and history persist in a Room database on the device.

## Open in Android Studio

1. Clone the repo:
   ```bash
   git clone https://github.com/sinura7/PersonalTrainer.git
   ```
2. In Android Studio choose **File → Open** and select the folder that contains `settings.gradle.kts`.
3. Trust the project and wait for Gradle sync.
4. Run on an API 26+ emulator or device.

## What it does

- **Home** — start or resume a workout, jump to routines/history, see lifts ready for +2.5 kg
- **Routines** — create, edit, reorder, and delete your own programs
- **Logging** — start a routine or a free workout, log weight (kg) + reps, optional RPE and warm-up
- **Rest timer** — starts after working sets (default 90s, or the routine rest target)
- **Progression** — last working set vs target reps:
  - hit target → suggest **+2.5 kg**
  - 1–2 reps short → keep the same weight
  - 3+ reps short → suggest **−2.5 kg**
- **History** — finished sessions with every set grouped by exercise and working volume

The first launch seeds a lift library. You can search it or create custom exercises when building a routine or mid-workout.

## Architecture

```
data/local     Room entities, DAOs, TrainerDatabase
data/repository
domain         models, ProgressionCalculator
ui/home, ui/routines, ui/workout, ui/history
```

ViewModels talk to repositories. No Hilt — `PersonalTrainerApp` holds an `AppContainer`.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- minSdk 26
