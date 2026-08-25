# Temper Data Safety narrative

**Status:** Current published posture (P12.2)  
**Use:** Play Console Data safety form, or any store listing that asks the
same questions. Do not publish while physical TalkBack or a data-survival
critical remains open.

| Question | Answer |
|---|---|
| Collects user data automatically? | No |
| Shares data with other companies? | No |
| Required account? | No |
| Encrypted in transit? | Only if the user chooses optional Drive backup; the file may also be password-protected |
| Users can request deletion? | Delete sessions in-app, restore an empty/other backup, or uninstall |
| Data collection is optional? | Local use collects nothing remotely. Drive backup is opt-in. Diagnostics are user-triggered |
| Ads | Not used |
| Analytics | Not used |
| Crash logs sent automatically? | No. User-triggered redacted diagnostics only |
| Location | Not used. Cardio does not claim GPS |
| Health / fitness data | Stored on-device. Exported only when the user exports a backup or shares a file they created |
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
