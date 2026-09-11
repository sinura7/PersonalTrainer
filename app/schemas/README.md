# Room schema history

`TrainerDatabase` (legacy, `personal_trainer.db`) and `TemperDatabase`
(foundation generation, `temper.db`) each have their own folder.
`TrainerDatabase` v1/v2 stay the historical migration substrate.
`TemperDatabase` started at version 1 and is now at version **4**. It is a
new generation, not a `TrainerDatabase` v2→v3 patch.

| Version | What it added | Migration |
|---|---|---|
| 1 | The foundation cutover baseline | — |
| 2 | Bodyweight entries, training blocks | `MIGRATION_TEMPER_1_2` |
| 3 | Schedule rules, occurrences, missed-work decisions, reminder deliveries | `MIGRATION_TEMPER_2_3` |
| 4 | Measurable goals; bodyweight zone and offset columns | `MIGRATION_TEMPER_3_4` |

Every file in this directory is a JSON snapshot of one database version,
emitted by Room's annotation processor into `room.schemaLocation`
(wired in `app/build.gradle.kts`). **These files are committed on purpose.**

They are the only record of what schema actually shipped to the phone, and they are
the substrate for two things the training history depends on:

- hand-written `Migration` objects (never `fallbackToDestructiveMigration`), and
- `MigrationTestHelper`, which replays a real v(N) database and validates it against v(N+1).

## The v1 baseline

Captured 20 August 2026 from a real build:
`com.sinura.personaltrainer.data.local.TrainerDatabase/1.json`, six entities,
`identityHash` `6d58ad40d5c03785ab29aaf61157f369`.

It could not be hand-authored — Room derives that hash from the schema and the compiler has
to agree with it. Every version bump from here leaves its own `<version>.json` beside it, as
part of the same commit as its migration:

```bash
./gradlew :app:kspDebugKotlin
git add app/schemas/
# Robolectric reads debug assets, not this folder. Release APKs do not include the copy.
cp app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/*.json \
   app/src/debug/assets/com.sinura.personaltrainer.data.local.TrainerDatabase/
cp app/schemas/com.sinura.personaltrainer.data.local.TemperDatabase/*.json \
   app/src/debug/assets/com.sinura.personaltrainer.data.local.TemperDatabase/
git add app/src/debug/assets/
```

## Rules

1. Never edit these files by hand.
2. Never delete an old version's file — migrations are validated against it.
3. A schema change is not done until its `<version>.json` and its migration test are
   committed together.
4. After a new `<version>.json` is generated, copy it into the matching
   `app/src/debug/assets/com.sinura.personaltrainer.data.local.<Database>/`
   folder. AGP 8 does not package `sourceSets.test.assets.srcDir("schemas")`
   into the Robolectric APK. Debug assets are what `MigrationTestHelper`
   sees on the JVM. They do not ship in a release APK.
