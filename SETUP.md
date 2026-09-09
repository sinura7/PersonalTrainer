# Setup and release

Temper is a **sideload APK**. The Play Store is not required to install, update, back up, or restore. Training works offline. Google Drive is optional and only used when you sign in from Settings.

This is a standard single APK (`com.sinura.personaltrainer`). It is suitable for Obtainium.

## 1. Versioning

Bump both values at the top of `app/build.gradle.kts` before every release:

```kotlin
val appVersionCode = 1
val appVersionName = "1.0.0"
```

- `versionName` is the human version (`1.0.0`, `1.1.0`).
- `versionCode` is the integer Android and Obtainium use to decide that an APK is newer. Increase it by 1 every release.

The gym-floor ratchet is `tools/check-version-code.py`. Until the first
`v*` tag exists, the floor is 1 (`tools/released-version-code.txt` is
only that fallback). After a `v*` tag, git is source of truth: the
first `v*` may equal 1; every later `v*` must carry an `appVersionCode`
strictly above the previous `v*` tag. Do not write `debugLiveCode` into
the floor file. `debug-live-*` tags are not gym-floor releases.

Settings → About shows `Version <versionName> (<versionCode>)`.

## 2. Create a release keystore (once)

Create a `release/` folder in the repo root (gitignored). Then run this exact command from the repo root:

```bash
keytool -genkeypair -v \
  -keystore release/personal-trainer-release.keystore \
  -alias personaltrainer \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

`keytool` will ask for:

- A **keystore password**
- Your name / organization (any values are fine for personal use)
- A **key password** (press Enter to reuse the keystore password)

**Back up the keystore and both passwords.** If you lose them you cannot sign updates that Android will accept as the same app. Never commit the `.keystore` file.

## 3. Point Gradle at the keystore

```bash
cp keystore.properties.example keystore.properties
```

Edit `keystore.properties`:

```
storeFile=release/personal-trainer-release.keystore
storePassword=your-keystore-password
keyAlias=personaltrainer
keyPassword=your-key-password
```

`keystore.properties` and `release/` are gitignored. Debug builds keep using the default debug keystore even after this file exists.

If `keystore.properties` or the keystore file is missing, `assembleRelease` still works but the APK is **not** signed for distribution.

## 4. Google Drive OAuth (one-time)

Backup/restore talks to Drive with the `drive.file` scope. You must create an Android OAuth client that matches this package and the SHA-1 of the keystore that signed the installed APK.

### Create the Cloud project

1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Create a project (any name, e.g. `Temper`).
3. APIs & Services → Library → enable **Google Drive API**.

### OAuth consent screen

Google reorganised this area into **Google Auth Platform**; older guides
(and earlier versions of this file) call it APIs & Services → OAuth consent
screen. The settings below are now split across the Branding, Audience, and
Data Access tabs.

1. Google Auth Platform (formerly APIs & Services → OAuth consent screen).
2. User type: **External** — under the **Audience** tab.
3. App name: `Temper`. Support email: your Gmail. Under **Branding**.
4. Scopes: add `https://www.googleapis.com/auth/drive.file` under **Data Access**.
5. Test users: under **Audience**, add the Google account you will sign in with
   on the phone. Do this *before* the first sign-in attempt.
6. Then **publish the app** (Audience → Publish app).

Step 6 is not optional housekeeping. While the app sits in **Testing**, Google
expires the grant on a short cycle — about a week — and because the app holds no
refresh token (see below) that means the consent sheet returns roughly weekly,
forever. `drive.file` is not a restricted scope, so publishing needs no
verification submission and no security assessment. Publishing an External app
with one non-sensitive scope and one user is the supported way to stop the
re-consent loop.

### Android OAuth client

Create **one Android client per package and signing key**. Temper Debug and
gym-floor Temper are different packages signed by different keys, so they are
two clients.

1. APIs & Services → Credentials → Create credentials → **OAuth client ID**.
2. Application type: **Android**.
3. Package name: `com.sinura.personaltrainer` for gym-floor Temper, or
   `com.sinura.personaltrainer.debug` for Temper Debug.
4. SHA-1: paste the fingerprint of the key that signed **that** install (see below).
5. Create.

Repeat for the other package if you use both.

No `google-services.json` is required. The app does not embed a client secret.
An Android OAuth client is matched by package name plus signing SHA-1 at Google's
end, so there is nothing to paste into the APK and nothing to get wrong in code.

### What the app stores, and what it does not

The app keeps **no refresh token and no access token on disk**. Only your account
email and the Drive folder id are persisted. Every Drive action re-runs
authorization, which normally returns a token with no visible prompt once the
grant is recorded. Two consequences worth knowing before you debug anything:

- "Signed in" in Settings is a remembered email address, not a live session. It
  can survive a grant that Google has since expired.
- If a consent sheet appears more often than once, that is the Testing-status
  expiry above, not a bug in the app.

### SHA-1 fingerprints

**Temper Debug** (the `debug-live-*` pre-release Obtainium installs): the signer
is the debug distribution keystore from §6, not any machine's
`~/.android/debug.keystore`, and not anything in a fresh clone —
`debug-signing/` is gitignored, and `DEBUG_KEYSTORE_BASE64` is write-only.
Read the SHA-1 off the APK that was installed:

```bash
apksigner verify --print-certs PersonalTrainer-<version>-debug.apk
```

`apksigner` ships in `$ANDROID_HOME/build-tools/<version>/`. Use it, not
`keytool -printcert -jarfile`: AGP signs with APK Signature Scheme v2/v3 and no
v1 JAR signature, so the `keytool` form prints **nothing at all** for these APKs
— silently, with a zero exit. That is a wasted afternoon, not an error message.

Cross-check the `SHA-256` line against the `DEBUG_CERT_SHA256` repository
variable (§6) before registering: if they differ, the APK you have was signed by
a throwaway runner key and its SHA-1 is worthless.

The current stable debug distribution signer, measured from
`debug-live-2026-09-09-3` (versionCode 25, the first drop on the stable key):

```
SHA-1:   7A:78:2C:6F:2F:FE:6C:6B:49:29:52:A4:6B:B7:09:2C:38:04:C8:39
SHA-256: B2:6E:A6:4C:9E:3E:2C:C4:40:63:DE:E3:D1:89:2E:85:57:CE:A2:D2:5E:71:DE:05:AB:CD:75:4D:E9:12:C3:36
```

That is a certificate digest, not a secret — it is derivable by anyone holding
the public APK. It is recorded here so the Cloud Console client can be rebuilt
without re-deriving it, and it changes only if the debug keystore is replaced.

A local `assembleDebug` without `debug-keystore.properties` is signed by that
machine's default debug keystore instead (`./gradlew signingReport`, `SHA1` under
`Variant: debug`), which is a different identity: register it separately if you
sideload local builds, and expect such a build not to update an Obtainium drop.

**Release** (after `keystore.properties` exists):

```bash
./gradlew signingReport
```

Use the `SHA1` under `Variant: release`. Or:

```bash
keytool -list -v \
  -keystore release/personal-trainer-release.keystore \
  -alias personaltrainer
```

Copy the SHA-1 as hex with colons, for example `A1:B2:C3:...`.

If Drive sign-in says it is not configured, the installed APK’s SHA-1 is missing from Cloud Console.

### If sign-in does not work

Read the symptom, not the guess. Each row below has one likely cause.

| What the phone says | What it means | Fix |
|---|---|---|
| "Google Drive sign-in isn’t configured for this install…" | `DEVELOPER_ERROR`. The package name or SHA-1 in Cloud Console does not match the installed APK. The message names the package to register. | §4 Android OAuth client, with the SHA-1 read off the installed APK |
| Signed in, but the row reads the literal words **"Google Drive"** instead of your address | The token worked; the Drive About read did not. Usually the Drive API is not enabled. | Enable **Google Drive API** (§4) |
| "Drive access was denied." on Create backup | Drive returned 403. Most often the Drive API is off, not a scope problem. | Enable **Google Drive API** (§4) |
| "No Google account is available to this app." | `SIGN_IN_REQUIRED`. There is no usable Google account on the device. | Android Settings → Accounts → add your Google account |
| "Google sign-in was cancelled." without you cancelling | The consent closed without granting, or the 5-minute wait elapsed. | Retry; if it repeats, check the two rows above |
| "Connect to the internet to use Google Drive." | The pre-authorization network check refused before any Google UI. | Get on Wi-Fi |

Failures are logged under the tags `PT/DriveAuth` and `PT/SettingsVM`:

```bash
adb logcat -s PT/DriveAuth PT/SettingsVM
```

That is the fastest way to tell a configuration problem from a cancelled tap.
Message text is redacted by default, but the exception is always logged in full
and the Play Services `ApiException` carries the status code — `10` is
`DEVELOPER_ERROR`, `4` is `SIGN_IN_REQUIRED`, `7` is `NETWORK_ERROR`. For the
free-text messages too, turn off Settings → Log → **Redact messages** first; it
re-arms on the next app start.

The full phone check — ten steps, about ten minutes, with a pass/fail at each —
is [docs/DRIVE_SIGNIN_CHECK.md](docs/DRIVE_SIGNIN_CHECK.md). Run it once after
setting up Cloud Console.

## 5. GitHub Release + Obtainium

Each update is three steps.

### 1. Bump version

Edit `appVersionCode` and `appVersionName` in `app/build.gradle.kts`. Commit that change.

### 2. Build the signed release APK

```bash
./gradlew assembleRelease
```

Gradle writes a standard single APK (no ABI/density splits). Upload this file:

```
app/build/outputs/apk/release/PersonalTrainer-1.0.0.apk
```

The name follows `PersonalTrainer-<versionName>.apk`. A `*-release.apk` sibling may also appear; use the version-only filename for GitHub and Obtainium. Confirm the file is signed:

```bash
jarsigner -verify -verbose -certs \
  app/build/outputs/apk/release/PersonalTrainer-1.0.0.apk
```

`apksigner verify --print-certs` works the same if you have Android build-tools on your PATH.

### 3. Create a GitHub Release and upload the APK

1. GitHub → this repo → Releases → **Draft a new release**.
2. Tag: `v1.0.0` (match `versionName`).
3. Title: `1.0.0`.
4. Attach `PersonalTrainer-1.0.0.apk` as a release asset.
5. Publish.

### Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add App → paste this repo URL: `https://github.com/sinura7/PersonalTrainer`
3. Source: GitHub. Track the latest release. Prefer the asset whose name starts with `PersonalTrainer-` and ends with `.apk`.
4. Allow unknown sources / install from Obtainium when Android asks.

Obtainium compares `versionCode` inside the APK (and the release tag). Upload one standard APK per release. Do not publish an AAB or split APKs.

Sideload without Obtainium: download the same APK from the GitHub Release and open it on the phone.

## 6. Phone check (Obtainium, not Studio)

Android Studio is not the install path. Cursor lands on `trunk`. The phone
gets **Temper Debug** from a GitHub **pre-release**.

Gym-floor **Temper** (`com.sinura.personaltrainer`) stays on the phone.
New chrome is judged on **Temper Debug** (`com.sinura.personaltrainer.debug`)
— a second icon and a second database. Do not uninstall release to make room.

### Temper Debug (live test)

1. After a packet is on `trunk` and the JVM gate is green, bump
   `debugLiveCode` in `app/build.gradle.kts` (Obtainium will not offer an
   update if versionCode stays the same). Build `./gradlew assembleDebug`.
2. Tag `debug-live-YYYY-MM-DD` on that commit. A second drop the same
   day is `debug-live-YYYY-MM-DD-2`.
3. Publish a **pre-release** named `Temper Debug — live test <debugLiveCode>`
   and attach `PersonalTrainer-<version>-debug.apk`.
4. Obtainium: this repo URL, **include pre-releases**, prefer the asset
   whose name ends with `-debug.apk`. Pull down to refresh.

Do not point the gym-floor Obtainium entry at a `*-debug.apk`.

### The Temper Debug signer

Android installs an update only over the same signing key, and each hosted
runner generates its own debug keystore, so two automated drops could never
update each other. `debug-live.yml` therefore restores **one** debug
distribution keystore from repository secrets and signs every drop with it.
Without the secrets it still builds, but the pre-release is marked
**THROWAWAY SIGNER** and cannot update an existing Temper Debug.

One-time setup:

1. Create the keystore once and back it up exactly like the release one (§2):

   ```bash
   keytool -genkeypair -v \
     -keystore debug-signing/temper-debug.keystore \
     -alias temperdebug \
     -keyalg RSA \
     -keysize 2048 \
     -validity 10000
   ```

   `debug-signing/` and `debug-keystore.properties` are gitignored
   (`debug-keystore.properties.example` is the template for local drops).
2. Repository secrets: `DEBUG_KEYSTORE_BASE64`
   (`base64 -w0 debug-signing/temper-debug.keystore`), `DEBUG_KEYSTORE_PASSWORD`,
   `DEBUG_KEY_ALIAS`, `DEBUG_KEY_PASSWORD`. All four or none.
3. Repository **variable** (not a secret) `DEBUG_CERT_SHA256`: the certificate's
   SHA-256 digest (`keytool -list -v -keystore debug-signing/temper-debug.keystore
   -alias temperdebug`, the `SHA256:` line; colons optional). With it set, a drop
   signed by anything else fails before it is published.
4. Register the keystore's SHA-1 for `com.sinura.personaltrainer.debug` in Cloud
   Console (§4).

Moving an installed Temper Debug onto the stable key: if the phone's current
Temper Debug was signed by a runner-generated key, the first stable drop will
not install over it. Export a backup from Temper Debug first, uninstall it,
install the stable drop from Obtainium, then import the backup. Gym-floor Temper
is untouched by any of this.

### Gym-floor Temper (signed)

Obtainium watches GitHub Releases for a signed `PersonalTrainer-<version>.apk`.
That file is the gym-floor update path. Cut it by bumping `appVersionCode`
and `appVersionName`, then pushing tag `vX.Y.Z` (must match `appVersionName`).
The first `v*` may keep `appVersionCode` at 1. Every later `v*` must bump
the code above the previous `v*` tag — the release workflow calls
`tools/check-version-code.py --tag-release` for that, and does not ask you
to edit `tools/released-version-code.txt` in the same commit.
The [release workflow](.github/workflows/release.yml) publishes the APK when
the four `KEYSTORE_*` repository secrets are set. Without those secrets the
tag still builds, but the APK is unsigned and will not update an existing
Temper install.

This is not Play. `versionCode` stays at 1 until a signed public artifact is cut.

## 7. First device install

1. Obtainium → this repo. Temper Debug: include pre-releases, `*-debug.apk`.
   Gym-floor Temper: signed `PersonalTrainer-<version>.apk`.
2. Allow installs from Obtainium when Android asks.
3. Open Settings → About and confirm the version.
4. Optional: Settings → Google Drive → Sign in with Google, then Create backup now.
5. Optional: turn on **Back up after each workout** in the same section. It asks
   for the backup password once and keeps it sealed on the phone; every finished
   workout then goes to Drive as the usual protected envelope, with no prompt.

Core training (routines, logging, history, units, library) does not need Google or a network. Backup/restore replaces local data from a Drive JSON file you created earlier.

## 8. Rest timer on Samsung / Android 13+

The rest timer is a **foreground service** with an ongoing notification. It keeps counting if you leave the workout screen, switch apps, or lock the phone. Finishing or discarding a workout stops the service.

### Notifications (Android 13+)

The first time you open an active workout, Android asks for notification permission. Allow it so remaining time stays visible in the shade and you get the “Rest done” alert.

Settings → Apps → Temper → Notifications → Rest timer / Rest complete should stay on.

### Battery (Samsung and other OEMs)

Aggressive battery savers can still pause background work. For reliable rest between sets:

1. Settings → Apps → Temper → Battery
2. Choose **Unrestricted** (not Optimized or Restricted)

On some Samsung builds the path is Settings → Battery → Background usage limits, then remove Temper from sleeping / deep-sleeping apps.

You do not need a lock-screen overlay. The notification chronometer is the always-visible indicator.
