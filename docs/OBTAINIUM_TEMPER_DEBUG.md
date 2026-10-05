# Obtainium — Temper Debug

Temper Debug (`com.sinura.personaltrainer.debug`) updates from GitHub **pre-releases**
that carry `PersonalTrainer-*-debug.apk`. Gym-floor Temper uses signed **releases**
without `-debug` in the name — use a **separate** Obtainium entry for that APK.

## Add the app (manual — GitHub source)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. **Add app** → URL: `https://github.com/sinura7/PersonalTrainer`
3. Source: **GitHub** (should auto-detect).
4. **Include prereleases:** ON (required — live tests are pre-releases, not `v*` releases).
5. **Filter APKs by regular expression:** `-debug\.apk$`
   (or `PersonalTrainer-.+-debug\.apk` if multiple assets appear).
6. App name: **Temper Debug** (helps find it in the list).
7. Save → pull down to refresh → **Update**.

Obtainium compares **`versionCode` inside the APK**. That number is `debugLiveCode` from
`app/build.gradle.kts` on the tagged commit (e.g. `119`), not gym-floor `appVersionCode`.

## Failed host lookup: `api.github.com`

Obtainium’s **GitHub** source always talks to **`api.github.com`** first. If the phone
**cannot resolve that hostname** (DNS), you see errors like:

`Failed host lookup: api.github.com`

That is a **network/DNS** problem on the device or network — not a wrong Temper URL.
`github.com` in a browser may still work while `api.github.com` does not (different names).

### Fix DNS on the phone (try in order)

1. **Private DNS (Samsung One UI):** Settings → Connections → More connection settings →
   **Private DNS** → **Automatic** (or Off). Custom “AdGuard / NextDNS / family filter”
   hosts often block or break `api.github.com`.
2. Turn **VPN off** and retry (including “secure DNS” inside the VPN app).
3. Switch **Wi‑Fi ↔ mobile data** and retry Obtainium refresh.
4. Restart the phone after changing Private DNS.

### Obtainium workarounds (when GitHub API DNS stays broken)

**A) Direct APK URL (most reliable)** — bypasses `api.github.com` entirely:

1. On a PC, open [Releases](https://github.com/sinura7/PersonalTrainer/releases) and pick
   the newest **Temper Debug** pre-release (tag `debug-live-…`).
2. Long-press / copy link on **`PersonalTrainer-1.0.0-debug.apk`** (or the `-debug.apk` asset).
3. Obtainium → **Add app** → source **Direct download** / **APK link** (wording varies by version).
4. Paste that `https://github.com/sinura7/PersonalTrainer/releases/download/…/PersonalTrainer-….apk` URL.
5. After each new live drop, update the URL once (or switch back to GitHub source when DNS is fixed).

Example shape (tag changes each drop):

`https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-10-05-3/PersonalTrainer-1.0.0-debug.apk`

**B) GitHub proxy in Obtainium** (some versions): app → **Advanced** → **GitHub proxy prefix**
or global **Networks** proxy. Obtainium wiki documents prefixes such as `ghproxy.com` that
front GitHub API requests when your network blocks the API host. Use only if you trust the proxy.

**C) GitHub token** (helps rate limits, not DNS): Settings → Networks → personal access token
(`public_repo` read). Still requires `api.github.com` to resolve.

## GitHub token (when API works but updates are flaky)

Obtainium calls **`api.github.com`**. Without a token, GitHub rate-limits anonymous
requests (~60/hour per IP). Heavy refresh often surfaces as connection abort / errno 103.

1. Obtainium → **Settings** → **Networks** → GitHub token (classic PAT, **`public_repo`** read).
2. Retry refresh.

## If updates still fail

| Symptom | What to try |
|--------|-------------|
| **Failed host lookup `api.github.com`** | Private DNS → Automatic; VPN off; Direct APK URL in Obtainium (above) |
| Connection abort / errno 103 | Token + network switch; or Direct APK / in-app **Update now** |
| “Could not find a suitable release” | **Include prereleases** ON; APK filter `-debug\.apk$` |
| Install blocked | Allow installs from Obtainium; use signed `debug-live` APKs only |
| Wrong app updated | Do not use gym-floor `PersonalTrainer-1.0.0.apk` for Temper Debug |

## In-app update (Temper Debug)

Temper Debug checks GitHub when you open the app:

- **Required** dialog when a newer live code exists (Debug **119+** also tries
  `github.com/releases.atom` if `api.github.com` fails DNS).
- **Update now** downloads from `github.com/releases/download/…` (not the API host).

If both Obtainium and in-app fail, fix **Private DNS** first, then use the **Direct APK URL**
in Obtainium for the current tag.

Optional builder token: `github.properties.example` → `github.properties` with
`GITHUB_API_TOKEN` (in-app checks only; Obtainium token is separate).

## Phone check after a drop

1. Pre-release `debug-live-YYYY-MM-DD[-N]` with `PersonalTrainer-*-debug.apk` on GitHub.
2. Obtainium refresh **or** in-app **Update now**.
3. Settings → About shows `+debug.N` matching the live code.
