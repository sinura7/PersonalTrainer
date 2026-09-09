# Drive sign-in — the phone check

Ten steps, about ten minutes, one pass/fail each. Run it once after setting up
Cloud Console ([SETUP.md](../SETUP.md) §4), and again after any change to a
signing key, a package name, or the OAuth client.

This exists because the Play Services path had never been exercised on hardware.
[P4.5 evidence](foundation-program/evidence/P4.5-drive-auth.md) recorded the hole
plainly: *"Phone OAuth / Play Services UI was not exercised on this VM."* No JVM
or emulator lane can close it — there is no test in this repository that proves
sign-in works, and there cannot be one. The phone is the only instrument.

**If a step fails, get the log.** `adb logcat -s PT/DriveAuth PT/SettingsVM`
shows the Play Services status code behind the on-screen sentence (`10` is
`DEVELOPER_ERROR`, `4` is `SIGN_IN_REQUIRED`, `7` is `NETWORK_ERROR`). Turn off
Settings → Log → **Redact messages** first if you want the message text too.

**What this check is for.** Signing in is authorization for Drive backup, not a
login. Nothing here touches your training history: it stays in Room on the phone
whether you are signed in or not. What the check proves is that a complete copy
of that history can reach your own Google Drive, and that you can see it there.

---

## Before you touch the phone

Almost every failure is configuration, not code. Confirm all four, then start.

- [ ] **Google Drive API is enabled** on the Cloud project.
- [ ] **An Android OAuth client exists** whose package is exactly the package you
      installed — `com.sinura.personaltrainer.debug` for Temper Debug,
      `com.sinura.personaltrainer` for gym-floor Temper.
- [ ] **Its SHA-1 came from the APK you actually installed**, read with
      `keytool -printcert -jarfile PersonalTrainer-<version>-debug.apk`. A
      `./gradlew signingReport` on a laptop reports that machine's default debug
      keystore, which is a different identity from the drop's distribution signer.
- [ ] **`drive.file` is listed under Data Access**, and your Gmail is under
      Audience → Test users.

Any box unticked: stop and fix it. The phone will only tell you the same thing
more slowly.

---

## The ten steps

### 1 — Clean start
Phone on Wi-Fi. Temper → Settings → the **Google Drive** header.

**Pass:** one button, *Sign in with Google*.
If a *Signed in* row shows instead, tap Sign out first — that also exercises the
revoke path step 8 asks about.

### 2 — Offline guard
Airplane mode **on**. Tap *Sign in with Google*.

**Pass:** `Connect to the internet to use Google Drive.`, the button re-enables,
and **no consent sheet appears**. This proves the network check runs before
authorization rather than after a confusing Play Services failure.
**Fail:** a consent sheet, or any other message.

Airplane mode off.

### 3 — Configuration
Tap *Sign in with Google*.

**Pass:** a Google account chooser within a second or two.
**Fail — configuration:** `Google Drive sign-in isn't configured for this
install. Add an Android OAuth client for <package>…` That is `DEVELOPER_ERROR`,
and the message names the exact package to register. Stop; nothing after this
step can pass. Go back to the pre-flight list.
**Fail — no account:** `No Google account is available to this app.` Add your
Google account in Android Settings → Accounts, then retry.

### 4 — Decline path
Press Back to dismiss the chooser without picking an account.

**Pass:** `Google sign-in was cancelled.`, the spinner stops, and every backup
button re-enables.
**Fail:** the spinner never stops, or buttons stay disabled. That is the
stuck-busy regression the `pendingResolution` state flow exists to prevent,
returned.

### 5 — Scope
Tap *Sign in with Google* again and pick your account.

**Pass:** the consent screen asks for **exactly one** permission, worded like
*"See, edit, create and delete only the specific Google Drive files you use with
this app."*
**Fail:** it also asks for your name, email address, or profile. An identity
scope has leaked in, against
[drive-auth.md](architecture/drive-auth.md) decision 2. Do not approve — report it.

Approve.

### 6 — The account is the right account
This is the step that answers "is my data tied to my Gmail account".

**Pass:** the status reads *Signed in. Backups stay in your PersonalTrainer
Backups Drive folder.* **and** the row's subtitle is your actual Gmail address.
**Fail (soft):** the subtitle reads the literal words *"Google Drive"*. The token
worked but the Drive About read did not — Drive API not enabled, or a network
blip. **Do not read this as success.** Re-check pre-flight and retry.

### 7 — Rotation
Sign out. Tap *Sign in with Google*, and rotate the phone while the account
chooser is on screen. Complete sign-in.

**Pass:** it still completes and lands on the *Signed in* row.
**Fail:** spinner never stops. This is a regression of a bug this app already
shipped once and fixed — a consent raised during recomposition being dropped.

### 8 — Round trip, and the revoke question
Tap *Create backup now*. Then *Sign out*, then *Sign in with Google*, then
*View existing backups*.

**Pass:** the backup you just made is **still listed**.
**Fail:** `No backups in Drive yet.` Your files are not lost — but sign-out calls
`revokeAccess()`, and under `drive.file` the app can only see files it created.
If the per-file grants do not survive a revoke, the app creates a *second*
`PersonalTrainer Backups` folder and goes blind to the first. **This is the one
unknown only a real phone can settle.** Record the answer either way.

### 9 — Independent proof
On a computer, open [drive.google.com](https://drive.google.com) signed in as the
same account.

**Pass:** a folder `PersonalTrainer Backups` containing
`personal-trainer-backup-*.json` from step 8.

This is the only step that proves the data reached the account you meant without
trusting anything the app says. The file is an encrypted envelope — you need the
password to read it, but you can see and download it, from any browser, forever.
That property is what "tied to my Gmail account" is actually worth.

### 10 — Backup is not sync, and not a login
Open History with the app signed out.

**Pass:** your full training history is there, unchanged. Google is a
destination, never your identity. Signing in or out never touches training data.

---

## Bonus: does a cached grant survive a restart?

Worth 30 seconds while you are here, because it decides whether automatic backup
is even buildable. Force-stop Temper (Android Settings → Apps → Temper → Force
stop), reopen it, and tap *Create backup now*.

- **Uploads with no consent sheet** → a usable grant is cached across process
  death. A background/unattended backup is feasible.
- **Prompts for consent** → only a foreground backup can ever work, because
  authorization needs an Activity to show the sheet.

---

## Record the result

Write the outcome to `docs/foundation-program/evidence/owner-lane-drive-oauth.md`:
phone model, Android version, the package installed, the APK SHA-1 you registered,
pass/fail per step, and the answers to steps 8 and the bonus. Then point
[P4.5 evidence](foundation-program/evidence/P4.5-drive-auth.md) at it. That closes
the last open limitation on this surface.

## What still is not true after a full pass

A green run means sign-in works and a backup reaches your Drive. It does not mean
your data is safe automatically. Nothing in the app writes to Drive on its own —
the only Drive uploads are the three buttons in Settings, and the only nudge is a
caption after 14 days without a backup. Until that changes, "my data is saved"
depends on you tapping *Create backup now*.
