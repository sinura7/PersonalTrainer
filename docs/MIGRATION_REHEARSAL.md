# Phase 3 — migration rehearsal and real-phone upgrade

> **Banner (24 Aug 2026).** The v1 → v2 migration has shipped.
> [`2.json`](../app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/2.json)
> is committed (`identityHash` `3eedd530…`). Do not hand-edit it. Step 0
> below is historical — do not treat “schema for version 2 does not exist
> yet” as current. A later database generation is a signed Phase 5 cutover
> ([ADR-010](architecture/ADR-010-schema-reset-migrations.md)), not a
> casual v3. Current program: [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).

The v1 → v2 migration runs **exactly once**, on a phone holding training history that cannot be
re-created, with no destructive fallback and no way to downgrade. This document is how it was
rehearsed against real data before it touched that phone.

Two constraints shape everything below:

- **The phone's database cannot be pulled.** The sideloaded build is release-signed and
  non-debuggable, so `adb` cannot reach its files. The owner's SAF JSON export, restored into a
  v1 emulator build, is the only faithful rehearsal with real data.
- **The rollback copy dies with an uninstall.** `files/pre-migration/v1/` lives in app-private
  storage. That is why step 1 of the real-phone upgrade takes a fresh JSON export the owner keeps
  off the phone — it is the copy that survives anything.

---

## Step 0 — generate and commit `2.json` (blocking, owner's machine)

The executor environment has no Android SDK and cannot run Gradle, so the exported Room schema
for version 2 does not exist yet. Nothing downstream can be verified without it: Room's schema
JSON is what `MigrationTestHelper` validates the migration against, and it is the byte-level
arbiter for whether the hand-written `MIGRATION_1_2` SQL matches what Room will expect at open.

```
git checkout claude/app-hierarchy-navigation-cjzigo
./gradlew :app:assembleDebug
git add app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/2.json
git commit -m "Add the generated v2 Room schema"
```

Then, before anything else: open `2.json` and diff its `createSql` for every entity against the
statements in `app/src/main/java/com/sinura/personaltrainer/data/local/Migrations.kt`. They must
be equivalent — same columns, same types, same `DEFAULT` clauses, same indexes. A difference in
so much as a quoted default is a permanent crash loop on open, because Room validates the live
schema against its own expectation and there is no fallback to catch it.

Run the suites once `2.json` is in place:

```
./gradlew testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest   # the migration lane of record on this project
```

> **Why the emulator lane is the gate.** The owner's host is Windows, where Robolectric falls
> back to legacy SQLite (3.7.10) whose `PRAGMA table_info` cannot express composite primary keys.
> `exercise_muscles` has one. A `Migration1To2Test` failure naming its primary keys on Windows is
> the harness, not the migration. The JVM lane's result is informational here; the emulator's is
> the one that counts.

---

## Rehearsal (owner, development machine, ~half a day)

1. **Phone:** Settings → Backup → *Export to file* → save the JSON → transfer to the computer.
   Note the restore-summary counts the app shows.
2. `git checkout schema-v1-rehearsal && ./gradlew assembleDebug`
3. Start an API-34 emulator.
   `adb install app/build/outputs/apk/debug/PersonalTrainer-1.0.0-debug.apk`
4. `adb push <backup>.json /sdcard/Download/`
5. Emulator app: Settings → *Restore from file* → pick the JSON → confirm.
   **Record all of this — it is the baseline everything after is compared against:**
   - the summary line ("N workouts, M sets, X exercises, Y routines")
   - the most recent session's name and date in History
   - one known lift's last-session sets and weights
   - one PR value on Progress
   - a screenshot of the Body tab
6. `git checkout claude/app-hierarchy-navigation-cjzigo && ./gradlew assembleDebug`
7. `adb install -r app/build/outputs/apk/debug/PersonalTrainer-1.0.0-debug.apk`
   — an **upgrade in place**. Do not uninstall; uninstalling is the one action that makes this
   rehearsal test nothing.
8. Launch and verify, in order:
   - the app opens, no crash, no unusual delay
   - History shows the same N workouts and the same most-recent session
   - the known lift's sets and weights are unchanged
   - the PR value is unchanged
   - Library lists 37 built-ins plus your customs. If you once named a custom after a built-in,
     you will now see both — that is expected and flagged; a later update lets you tidy it.
   - the Body tab renders. **The heat distribution may have shifted** — that is the junction
     switch, and judging it is the point of this step. Compare against step 5's screenshot and
     ask whether it better matches how you actually train.
9. Settings → *Export to file* on the emulator. Open the JSON in a text editor: `"version": 2`,
   and an `exerciseMuscles` array is present.
10. Settings → *Restore from file* with the **original step-1 (v1) JSON** onto this v2 install.
    Run step 8's checks again — this is the v1-document upgrade path against a live database.
11. Report every observation in the PR thread.

---

## Real-phone upgrade (owner, after PR review + rehearsal green, ~1 hour)

1. Phone: take a **fresh** Settings → *Export to file* and copy it off the phone. The pre-v2 raw
   copy is app-private and does not survive an uninstall; this JSON is your independent copy.
2. Build the release APK per `SETUP.md` (`./gradlew assembleRelease` with `keystore.properties`)
   from the approved branch, and sideload it over the existing install. **Do not uninstall.**
3. Run the checklist below on the phone.
4. Use the app for one full workout before the PR is merged.

### Phone checklist

1. Before installing: export a backup and confirm the file is a plausible size.
2. Install the Phase-3 APK **over** the existing app.
3. The app opens normally — no crash, no delay beyond about a second.
4. History: the workout count and most recent session (name + date) are exactly as before.
5. Open a session you remember: a known lift shows the same sets, weights, reps.
6. Progress: a PR you know (your best bench, say) shows the same number.
7. Library: 37 built-ins plus every custom you created, names and notes intact.
8. Body: the silhouette renders. The heat pattern may look different — it now weights secondary
   muscles per lift. Does it better match how you train? Note anything that looks wrong; that
   feedback is what tunes the catalog.
9. Settings → Export to file completes without error.
10. Do one full real workout: start, log, rest timer, finish.
11. Report 3–10 pass/fail in the PR. The phase closes on this sign-off.

---

## Rollback

The pre-v2 raw copy of `personal_trainer.db` (plus `-wal` / `-shm`) at `files/pre-migration/v1/`
on the phone, together with the JSON export taken before the upgrade, are the **only** rollback
artifacts. v1 builds refuse v2 JSON by design, and Room refuses to open a downgraded database.
The raw copy dies with an uninstall.
