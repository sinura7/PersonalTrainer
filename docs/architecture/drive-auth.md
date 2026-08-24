# Drive authorization

- **Status:** Accepted — P4.5
- **Date:** 24 August 2026
- **Authority:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) P4.5
- **Does not reopen:** `drive.file` scope; Drive as whole-file backup;
  local recording without an account; compile/target 36

This packet removes the deprecated Google Sign-In remnants from Drive
backup. Authorization stays on `AuthorizationClient`. Local recording
stays Google-free.

## Signed matrix

| Surface | From | To |
|---|---|---|
| play-services-auth | 21.3.0 | **21.6.0** |
| Account email | `GoogleSignIn` / `toGoogleSignInAccount` | Drive About `user.emailAddress` |
| Sign-out | `Identity.getSignInClient().signOut()` | `AuthorizationClient.clearToken` + `revokeAccess` |

## Decisions

1. **No `com.google.android.gms.auth.api.signin` types** in production
   Kotlin. That package is the FND-028 remnant.
2. **Scope stays `drive.file`.** Do not add `openid`, `email`, or
   `profile`. Do not add Credential Manager. This is authorization for
   backup, not app sign-in.
3. **Email is a Drive About read.** `AuthorizationResult` does not
   carry account identity. `DriveRestClient.fetchAccountEmail` uses the
   same access token. If About fails, Settings still shows signed-in as
   "Google Drive" so a successful token is not lost.
4. **Sign-out revokes authorization.** `clearToken` drops the cached
   access token. `revokeAccess` disconnects the app. Local Drive
   session prefs are cleared either way.
5. **Local recording stays Google-free.** Workout, history, schedule,
   and backup-to-file do not import Play Services.

## Finding coverage

FND-028 is closed. FND-027 continues into P4.6 lint / supply-chain.
