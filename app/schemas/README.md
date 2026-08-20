# Room schema history

Every file in this directory is a JSON snapshot of one `TrainerDatabase` version,
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
```

## Rules

1. Never edit these files by hand.
2. Never delete an old version's file — migrations are validated against it.
3. A schema change is not done until its `<version>.json` and its migration test are
   committed together.
