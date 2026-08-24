# Recovery runbook

What to do when something is lost. Written to be readable on a phone, in a hurry, by
someone who has not looked at this repo in six months.

For a sideloaded app there is no Play Store to fall back on. Three things are
irreplaceable, and only one of them is in this repository:

| Thing | Where it lives | If lost |
|---|---|---|
| **Source code** | this repo, `trunk` branch | nothing lost — clone and build |
| **Training history** | the phone's Room database | restore from a backup file (below) |
| **Release keystore** | your own backup, **not** this repo | cannot update the installed app, ever |

---

## 1. "My phone died / I got a new phone"

Your training history is on the old phone. Recover it in this order:

1. **If you can still open the app on the old phone:** Settings → Backup & restore →
   **Export to file**. Save it somewhere off the phone (Drive, email it to yourself,
   a computer). This file is plain JSON and needs no Google account to restore.
2. **If the old phone is gone but you used Drive backup:** install the app on the new
   phone, Settings → Sign in with Google → View existing backups → restore the newest.
3. **If the old phone is gone and you never backed up:** the history is gone. Nothing in
   this repo can recover it — the database only ever existed on that device.

Install the app on the new phone first (see [SETUP.md](../SETUP.md)), then restore.

> Android Auto Backup is **not** the recovery path. The shipping manifest
> disables it (`allowBackup=false` plus exclusion rules). Existing OS
> copies taken before that change are not recalled and are not a
> supported channel. Export to file is the copy that counts. Google
> Drive, if you used it, is a whole-file **backup**, not a sync. The
> signed inventory is
> [backup-threat-model.md](architecture/backup-threat-model.md).

## 2. "My laptop died"

Nothing is lost as long as the keystore was backed up separately.

```bash
git clone https://github.com/sinura7/PersonalTrainer.git
```

Open in Android Studio, let Gradle sync, and you can build and run immediately. Debug
builds use Android Studio's own debug keystore and need nothing from you.

To build a **release** APK again you need the keystore and its passwords — restore them
per section 3, then follow [SETUP.md](../SETUP.md) §3.

## 3. "Where is the keystore, and what if I lost it?"

The keystore signs release APKs. Android will only install an update over an existing app
if the new APK is signed by **the same key**.

**Where yours should be:** `release/personal-trainer-release.keystore` locally
(gitignored), plus a copy in at least one place that is not your laptop — a password
manager attachment, an encrypted archive in cloud storage, or a USB drive in a drawer.
Store the **store password, key alias, and key password** with it. A keystore without its
passwords is as lost as no keystore.

> GitHub Actions secrets are **not** a backup. They are write-only: you can set
> `KEYSTORE_BASE64` but you can never read it back out.

**If it is genuinely lost:**

1. Export your training data from the phone first — Settings → **Export to file**. Do this
   before anything else.
2. Generate a new keystore ([SETUP.md](../SETUP.md) §2).
3. Register the new key's SHA-1 with the Google OAuth client, or Drive backup stops working
   ([SETUP.md](../SETUP.md) §4).
4. **Uninstall** the app on the phone, install the newly signed APK, and **import** the file
   from step 1.

The uninstall is unavoidable — that is exactly why the keystore is backed up.

## 4. "Drive backup stopped working"

Almost always the OAuth client no longer matches the APK's signing key. Symptom: sign-in
fails mentioning configuration or `DEVELOPER_ERROR`.

Re-check the SHA-1 registered in Google Cloud Console against the key that signed the
**installed** APK ([SETUP.md](../SETUP.md) §4). A debug build and a release build have
different SHA-1s and both must be registered if you use both.

This is also why local file export exists: **it never depends on Google**. If Drive is
broken and you need a backup right now, use Export to file.

## 5. "I need to know what shipped"

- `git tag` — every release is tagged `vX.Y.Z`.
- `app/schemas/` — one JSON per database version. Never delete these; migrations are
  validated against them.
- Settings → About on the phone shows the running version.

---

## The five-minute drill

Worth doing once, today, so you find out now rather than during an emergency:

1. Settings → **Export to file** → confirm the file exists and is not empty.
2. Confirm your keystore backup exists somewhere that is not your laptop, and that you can
   still open it: `keytool -list -keystore <path>`.
3. Confirm this repo has your latest commits pushed.

If all three pass, the worst case is inconvenience rather than loss.
