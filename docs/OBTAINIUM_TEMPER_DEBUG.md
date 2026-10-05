# Obtainium — Temper Debug

Temper Debug (`com.sinura.personaltrainer.debug`) updates from GitHub **pre-releases**
that carry `PersonalTrainer-*-debug.apk`. Gym-floor Temper uses signed **releases**
without `-debug` in the name — use a **separate** Obtainium entry for that APK.

## Add the app (manual)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. **Add app** → URL: `https://github.com/sinura7/PersonalTrainer`
3. Source: **GitHub** (should auto-detect).
4. **Include prereleases:** ON (required — live tests are pre-releases, not `v*` releases).
5. **Filter APKs by regular expression:** `-debug\.apk$`
   (or `PersonalTrainer-.+-debug\.apk` if multiple assets appear).
6. App name: **Temper Debug** (helps find it in the list).
7. Save → pull down to refresh → **Update**.

Obtainium compares **`versionCode` inside the APK**. That number is `debugLiveCode` from
`app/build.gradle.kts` on the tagged commit (e.g. `118`), not gym-floor `appVersionCode`.

## GitHub token (strongly recommended)

Obtainium calls **`api.github.com`**. Without a token, GitHub rate-limits anonymous
requests (~60/hour per IP). Heavy refresh, many apps, or mobile networks often surface as:

`SocketException: Software caused connection abort` / `ECONNABORTED` / failed connection
to `api.github.com`.

**On the phone:**

1. Obtainium → **Settings** → **Networks** (or **GitHub** section, depending on version).
2. Add a **GitHub personal access token** (classic is fine) with **`public_repo`** read scope only.
3. Retry the Temper Debug update.

Create a token: GitHub → Settings → Developer settings → Personal access tokens.

## If updates still fail

| Symptom | What to try |
|--------|-------------|
| Connection abort / errno 103 to `api.github.com` | Add GitHub token in Obtainium; switch Wi‑Fi ↔ mobile data; disable VPN/private DNS temporarily; set battery **Unrestricted** for Obtainium |
| “Could not find a suitable release” | Turn **Include prereleases** ON; set APK filter to `-debug\.apk$`; confirm a pre-release exists on [Releases](https://github.com/sinura7/PersonalTrainer/releases) |
| Install blocked | Allow **Install unknown apps** for Obtainium; Temper Debug must keep the **same signing key** as the installed build (Obtainium drops from `debug-live.yml`, not a local Studio Run) |
| Wrong app updated | You pointed at gym-floor `PersonalTrainer-1.0.0.apk` — use the **`-debug.apk`** asset only |

## In-app update (same GitHub drops)

Temper Debug also checks GitHub when you open the app:

- A **required** dialog appears when a newer live code is published.
- **Home** still shows an update banner.
- This path downloads the same release asset and opens Android’s install sheet.

If Obtainium errors persist, use **Update now** inside Temper Debug after a live drop
is published — it uses the same APK URL, with retries on flaky mobile links.

Optional: copy `github.properties.example` → `github.properties` on the machine that
**builds** Temper Debug and set `GITHUB_API_TOKEN` so in-app checks share the same
rate-limit headroom (CI can stay empty).

## Phone check after a drop

1. Confirm pre-release tag `debug-live-YYYY-MM-DD[-N]` on GitHub with `PersonalTrainer-*-debug.apk`.
2. Obtainium: refresh → version should rise (e.g. 117 → 118).
3. Open Temper Debug: required update dialog or banner should match that live code.
