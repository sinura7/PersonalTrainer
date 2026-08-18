# Personal Trainer

Android app for following strength, cardio, and mobility workouts. Open this repository in Android Studio, sync Gradle, and run it on an emulator or device.

## Open in Android Studio

1. Clone the repo:
   ```bash
   git clone https://github.com/sinura7/PersonalTrainer.git
   ```
2. In Android Studio choose **File → Open** and select the `PersonalTrainer` folder (the one that contains `settings.gradle.kts`).
3. Trust the project and wait for Gradle sync.
4. Create or start an emulator (API 26+), then click **Run**.

Android Studio will use the included Gradle wrapper (`gradlew`). You do not need a system-wide Gradle install.

## What’s in the app

- **Home** — weekly goal, last session, and a featured workout
- **Workouts** — filterable library (full body, strength, cardio, mobility)
- **Session** — step through each exercise, then save the completed workout
- **Progress** — session history
- **Profile** — name, goal, and weekly target

Sample programs and exercises live in `app/src/main/java/com/sinura/personaltrainer/data/SampleData.kt`. History is kept in memory for this first upload so the project opens and runs without a backend or database.

## Project layout

```
app/src/main/java/com/sinura/personaltrainer/
  data/           models, sample workouts, repository
  ui/home         dashboard
  ui/workouts     library + detail
  ui/session      guided workout
  ui/progress     history
  ui/profile      settings
  ui/navigation   bottom tabs
```

## Requirements

- Android Studio Ladybug or newer
- JDK 17 (bundled with Android Studio)
- Android SDK 35
- minSdk 26
