# Room schema history

Every file in this directory is a JSON snapshot of one `TrainerDatabase` version,
emitted by Room's annotation processor into `room.schemaLocation`
(wired in `app/build.gradle.kts`). **These files are committed on purpose.**

They are the only record of what schema actually shipped to the phone, and they are
the substrate for two things the training history depends on:

- hand-written `Migration` objects (never `fallbackToDestructiveMigration`), and
- `MigrationTestHelper`, which replays a real v(N) database and validates it against v(N+1).

## Generating the v1 baseline

The baseline JSON is produced by a build on a machine with the Android SDK — it cannot
be hand-authored, because Room derives an `identityHash` from the schema that must match
what the compiler generates:

```bash
./gradlew :app:kspDebugKotlin
git add app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/1.json
git commit -m "Commit Room v1 schema baseline"
```

Run this once, before any entity change. From then on, every version bump leaves its
own `<version>.json` here as part of the same commit as its migration.

## Rules

1. Never edit these files by hand.
2. Never delete an old version's file — migrations are validated against it.
3. A schema change is not done until its `<version>.json` and its migration test are
   committed together.
