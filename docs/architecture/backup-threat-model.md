# Backup threat-model inventory

- **Status:** Accepted — P3.1 signed inventory
- **Date:** 24 August 2026
- **Authority:** [ADR-009](ADR-009-backup-privacy-sync.md)
- **Program:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) P3.1
- **Does not reopen:** whether Android Auto Backup stays on; whether Drive is
  backup or sync; whether user-controlled export is the recovery path

This file is the inventory later Phase 3 packets implement against. It records
what exists on disk today, which channels can copy it, and which packet owns
each remaining defect. It is not a new privacy decision.

## 1. Policy already decided

Restated from ADR-009. Later packets implement these lines. They do not vote
on them.

1. **User-controlled backup is the authoritative recovery path.** SAF export
   and optional Drive whole-file backup replace local state after preview and
   confirm. They are not synchronization.
2. **Implicit Android backup and device-to-device app-data transfer will be
   disabled** (`allowBackup=false` plus explicit exclusion rules). That is
   P3.5. The shipping manifest still has `allowBackup="true"`.
3. **Existing OS backups are not retroactively recalled.** They are luck, not
   a plan, and after P3.5 they are not a supported channel.
4. **App-layer plaintext export and Drive payloads expose fitness and
   bodyweight data.** Do not claim they are private. Do not claim Android
   Keystore encryption is portable to another device. Encryption is P3.6.
5. **Restore honesty** is P3.2–P3.4: preview authored counts, verified
   snapshots, journaled commit, start/restore serialization.
6. **No automatic telemetry.** A future diagnostic bundle is P12.1 and must
   not include workout names, weights, notes, bodyweight, emails, tokens,
   database or backup contents, or raw paths.

## 2. Assets

| Asset | Why it is sensitive |
|---|---|
| Finished set logs | Complete training history: loads, reps, RPE, times |
| Session names and notes | Identity and free-text that can name people or places |
| Custom exercises and notes | Authored library |
| Routines and schedule | Plan the owner actually uses |
| Bodyweight and weigh-in log | Health data |
| Training blocks | Authored milestones |
| Onboarding answers | Goal, place, equipment, training age |
| Drive account email | Google identity |
| OAuth access token | Session to the owner's Drive files created by this app |
| Safety and pre-migration snapshots | Older or deleted history the owner thought was gone |
| Live / unfinished session | Today's work, excluded from user backup on purpose |

Seeded built-in exercises are not authored data. They travel in every normal
export and must not be counted as “the user has history.”

## 3. Stores as of this packet

Application id: `com.sinura.personaltrainer` (debug:
`com.sinura.personaltrainer.debug`). None of these stores have app-layer
encryption.

| Store | On-disk location | Holds | In user backup JSON | Eligible for Auto Backup today |
|---|---|---|---|---|
| Room `TrainerDatabase` | `databases/personal_trainer.db` (+ WAL/SHM) | Exercises, muscles, routines, finished and live sessions, sets, schedule, seed meta | Finished sessions and the rest of the catalog/plan; **live sessions excluded** | Yes |
| DataStore `user_settings` | `files/datastore/user_settings.preferences_pb` | Unit, schedule, rest prefs, coach, bodyweight + log, blocks, onboarding, Drive email/folder, backup stamps, collision dismissals | Partial — see exclusions below | Yes |
| `rest_timer_state` | `shared_prefs/rest_timer_state.xml` | Live rest countdown and session id | No | Yes |
| `schema_marker` | `shared_prefs/schema_marker.xml` | Last opened schema version | No | Yes |
| Safety snapshots | `files/safety-snapshots/pre-restore-*.json` | Plaintext JSON of current state; keep newest 3 | N/A (they *are* backups) | Yes |
| Pre-migration v1 copy | `files/pre-migration/v1/personal_trainer.db` (+ WAL/SHM) | Byte copy taken once before Room v2 | No | Yes |
| Workout draft | In-process cache + Activity `SavedStateHandle` | Unlogged set entry | No | OS saved state only |
| Drive token | `DriveAuthClient` memory | Access token + email | No | No |
| Logcat | Not persisted by the app | `PT/<Component>` breadcrumbs | No | No |

User-backup JSON exclusions: in-progress sessions and their sets; `seed_meta`;
`REST_LAST_PRESET`; `REST_ALARM_ELIGIBLE`; Drive email, folder id, and
backup/restore stamps; rest-timer runtime state.

## 4. Channels

### 4.1 Implicit OS backup (shipping, to be closed)

- Manifest: `android:allowBackup="true"`.
- `android:fullBackupContent` and `android:dataExtractionRules` are unset.
- `res/xml/backup_rules.xml` is `<full-backup-content />` and is **not**
  referenced. Lint already flags it unused.
- Effective inclusion is the Android default: Room, DataStore, both
  SharedPreferences files, safety snapshots, and the pre-migration copy.
- The OS transport is OS-encrypted. Temper does not version or validate an
  OS-restored tree. A Play/D2D restore can therefore land mixed or stale
  files the app will treat as authoritative Room + preferences.

P3.5 implements `allowBackup=false` plus explicit legacy and API-31+
exclusion rules covering every row in §3. Upgrade-in-place must not erase
local data.

### 4.2 SAF export / import

- Settings → Export to file / Import file.
- Format: pretty-printed plaintext JSON (`BackupJson`, `version = 2`,
  `app = "personal-trainer"`).
- No Google account. Written to a user-chosen URI; no app-private temp file.
- Anyone who can read that file can read bodyweight and the full finished
  history.

### 4.3 Optional Google Drive backup

- Scope: `https://www.googleapis.com/auth/drive.file` only.
- Folder name: `PersonalTrainer Backups`. File prefix:
  `personal-trainer-backup-`.
- Same plaintext JSON as SAF. App properties: `app=personal-trainer`,
  `kind=backup`.
- Token is memory-only and cleared on sign-out. Email is stored in
  DataStore so Settings can show who is signed in.

### 4.4 Restore commit (shipping)

Order today: refuse if a workout is live → decode → validate → authored
compare (no write) → confirm → maintenance lock → recover any open
journal → **verified** safety snapshot (failure aborts) → journal
staged/wiping → wipe Room → journal room → write preferences → journal
prefs → catalog reconcile → clear journal.

Settings lists retained snapshots by authored counts and date. Export,
restore-through-preview, and delete are offered. Raw private paths are
not shown.

Start and repeat share that lock. Process start finishes an interrupted
restore before catalog seed. Catalog-only authored compare is P3.2.

## 5. Threats

Each row is a fact about today plus the decided response. “Accepted until”
means the risk stays until that packet lands, not that it is approved
forever.

| # | Threat | Current exposure | Decided response | Packet |
|---|---|---|---|---|
| T1 | **Device theft, screen locked** | Room/DataStore are app-private. A locked device is the platform's lock. No app-layer DB encryption. | Keep relying on the platform lock for at-rest on-device files. Do not invent device-bound encryption for portable backups. | none — accepted platform posture |
| T2 | **Unlocked or shared device** | Any app or person with the unlocked phone can open Temper and read history, or Export to file. | Product stays single-user, no in-app lock screen in Phase 3. Do not pretend otherwise. | none — out of scope |
| T3 | **Android Auto Backup / D2D transfer** | Default inclusion copies the whole §3 tree, including safety and pre-migration snapshots that can retain deleted history. Temper will treat an OS-restored DB as real. | Disable the channel. Document that already-taken OS copies are not recalled. | P3.5 |
| T4 | **File leak of a SAF/Drive JSON** | Plaintext. Complete finished history + bodyweight if those rows exist. | Warn. Offer a portable authenticated encrypted envelope. Keep legacy plaintext import. Plaintext export becomes an advanced choice. | P3.6 |
| T5 | **Drive account or `drive.file` folder compromise** | Attacker with the Google account can read/replace files this app created. `drive.file` cannot list the rest of Drive. | Keep `drive.file`. Say **backup**, never sync. Encryption (P3.6) reduces payload value. | P3.6; P12.2 copy |
| T6 | **Catalog-only or bodyweight-blind restore** | A file that is only the seeded catalog passes the empty guard and can wipe authored sessions. Local bodyweight/blocks do not count as “has data.” | Authored-data counts on both sides. Catalog-only cannot wipe history through the normal path. | P3.2 |
| T7 | **Safety snapshot fails, restore continues** | Closed: a verified snapshot is a restore precondition. Failure aborts before Room is touched. Settings lists, exports, restores through preview, and deletes retained copies. Paths stay off the screen. | Verified snapshot is a restore precondition. Failure aborts. Snapshots are listable/exportable/restorable from Settings. | P3.3 |
| T8 | **Start versus restore race** | Closed: start, repeat, and restore share the maintenance lock. A start that arrives while a restore journal is open is refused. | One coordinator serializes start/repeat and restore. | P3.4 |
| T9 | **Process death mid-restore** | Closed: staged/wiping/room/prefs journal. Process start finishes Room-committed work. Failure copy names the committed state. | Journaled phases. Success means the committed state. Failure states are recoverable and named. | P3.4 |
| T10 | **Scale / whole-document encode** | `createSnapshot` / `BackupJson.encode` load the finished DB in memory. | Measure on a 500-session / 15,000-set fixture before any streaming rewrite. | P3.7, later P8.5 |
| T11 | **Support / diagnostics leak** | `AppLog` is local. No field bundle exists yet. | Default remains no telemetry. Any future bundle is redacted (ADR-009 §16). | P12.1 |
| T12 | **Commercial privacy copy drift** | No published privacy policy or Play Data Safety narrative. | P12.2 must match this inventory and ADR-009, not the shipping Auto Backup default. | P12.2 |

## 6. Honest claims

Current-voice documents may say:

- Export to file is the copy that counts.
- Drive is optional whole-file **backup**, not sync.
- Auto Backup is enabled in the shipping manifest and is **not** the
  supported recovery path.
- Export and Drive JSON are plaintext today.
- Restore refuses while a workout is live.
- A verified safety copy is required before restore teardown. Copies
  are listable from Settings. Failure aborts the restore.
- Start and restore share one lock. An interrupted restore is finished
  from the journal on the next launch.

They must not say:

- Drive syncs two phones.
- Auto Backup is how you move to a new phone.
- The JSON is private or encrypted.

## 7. Finding coverage

| Finding | This inventory | Remaining implementation |
|---|---|---|
| FND-011 | Threat model covers device theft (T1–T2), Auto Backup (T3), file leak (T4), and Drive (T5) | P3.5 manifest; P3.6 envelope |
| FND-014B | T6 records the catalog-only / authored-count hole | P3.2 |
| FND-014A | T7 is closed: verified snapshot, Settings recovery | done — P3.3 |
| FND-014C | T8–T9 are closed: one lock, journaled recover | done — P3.4 |
| FND-038 | T10 records the whole-document encode | P3.7 measure, P8.5 if budgets fail |
| FND-030 | T12 records missing commercial privacy copy | P12.2 |
| FND-012 | Incremental sync remains gated by ADR-009 §14 | Phase 11 after its start gate |

P3.1 closes the **inventory** half of FND-011. It does not close the finding.

## 8. Review questions

- May a later packet leave Auto Backup on because “OS encryption is enough”?
  No.
- Is Drive the recovery path if the owner never exported a file? Only if they
  used Drive backup. Auto Backup is still not a plan.
- Does a locked-device theft require app-layer Room encryption in Phase 3?
  No.
- Can this file authorize incremental sync? No.
