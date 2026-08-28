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

1. APIs & Services → OAuth consent screen.
2. User type: **External**.
3. App name: `Temper`. Support email: your Gmail.
4. Scopes: add `https://www.googleapis.com/auth/drive.file` (or finish the wizard and add it under Data Access).
5. Test users: add the Google account you will sign in with on the phone.
6. Publishing status can stay in **Testing** for personal use.

### Android OAuth client

Create **one Android client per signing key**. Debug installs and release installs have different SHA-1 values.

1. APIs & Services → Credentials → Create credentials → **OAuth client ID**.
2. Application type: **Android**.
3. Package name: `com.sinura.personaltrainer`
4. SHA-1: paste the fingerprint for that install (see below).
5. Create.

Repeat for the other SHA-1 if you use both a debug-signed Temper Debug
APK and a signed release APK.

No `google-services.json` is required. The app does not embed a client secret.

### SHA-1 fingerprints

**Debug** (`assembleDebug` / Temper Debug Obtainium drop):

```bash
./gradlew signingReport
```

Use the `SHA1` under `Variant: debug`. Or:

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

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

1. After a packet is on `trunk` and the JVM gate is green, build
   `./gradlew assembleDebug`.
2. Tag `debug-live-YYYY-MM-DD` on that commit. A second drop the same
   day is `debug-live-YYYY-MM-DD-2`.
3. Publish a **pre-release** named `Temper Debug — live test` and attach
   `PersonalTrainer-<version>-debug.apk`.
4. Obtainium: this repo URL, **include pre-releases**, prefer the asset
   whose name ends with `-debug.apk`.

Do not point the gym-floor Obtainium entry at a `*-debug.apk`.

### Gym-floor Temper (signed)

Obtainium watches GitHub Releases for a signed `PersonalTrainer-<version>.apk`.
That file is the gym-floor update path. Cut it by bumping `appVersionCode`
and `appVersionName`, then pushing tag `vX.Y.Z` (must match `appVersionName`).
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
4. Optional: Settings → Backup & restore → Sign in with Google, then Create backup now.

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
