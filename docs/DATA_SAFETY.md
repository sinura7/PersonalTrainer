# Temper Data Safety narrative

**Status:** Current published posture (P12.2 / Phase 11 account sync)  
**Use:** Play Console Data safety form, or any store listing that asks the
same questions. Do not publish while a data-survival critical remains open.

| Question | Answer |
|---|---|
| Collects user data automatically? | No automatic analytics or crash upload. Optional Temper Account sync uploads finished workouts, schedule, routines, templates, custom exercises, bodyweight weigh-ins, measurable goals, coach/reminder/display prefs, and save posture (not live sessions or built-in catalog seed) after the user signs in and data changes while online |
| Shares data with other companies? | No ads or analytics partners. Optional Google Drive backup uses Google when the user turns it on. Temper Account uses the operator’s Supabase project (Auth + Postgres) |
| Required account? | No |
| Encrypted in transit? | Yes for Temper Account (HTTPS to Supabase). Optional Drive backup uses HTTPS; the backup file may also be password-protected |
| Users can request deletion? | Yes — delete sessions in-app; delete Temper Account in Settings → Account (removes cloud Auth user and synced server rows; local phone data stays); uninstall removes on-device data; user-managed Drive/export files are deleted by the user |
| Data collection is optional? | Local use collects nothing remotely. Temper Account is opt-in (first-launch chooser or Settings). Drive backup is opt-in. Diagnostics are user-triggered |
| Ads | Not used |
| Analytics | Not used |
| Crash logs sent automatically? | No. User-triggered redacted diagnostics only |
| Location | Not used. Cardio does not claim GPS |
| Health / fitness data | Stored on-device by default. Uploaded to Supabase when the user opts into Temper Account sync (finished workouts; schedule rules and occurrences; routines and routine exercises; activity templates with blocks/sets/intervals; custom exercises with muscle credits; bodyweight weigh-ins; measurable goals; coach, reminder, and display preferences; save posture; not live in-progress sessions; built-in catalog seed stays on-device). Exported when the user exports a backup or shares a file they created. Not E2EE on the server in v1 |
| Personal info (email) | Collected only for optional Temper Account (Supabase Auth). Not used for marketing |
| Photos / files | User-picked backup files via the Storage Access Framework |
| Contacts / SMS / microphone / camera | Not used |
| Device IDs for ads | Not used |
| Approximate location / precise location | Not used |
| Calendar | Not used |
| Financial / payment | Not used. Local recording is never subscription-gated |

Approximate / precise location, contacts, and photos are not requested.

Foreground service: rest timer only, so a rest can finish while the app is
in the background. The notification is the rest clock, not tracking.

Exact alarm: rest completion. Denied exact-alarm access degrades honestly.

Health declaration: training log, not medical advice.

Account creation: email + password via Supabase Auth when the user chooses
Temper Account. No Google Sign-In in v1.
