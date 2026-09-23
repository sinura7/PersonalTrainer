# ADR-028 — Save posture and Temper Account sync target

- **Status:** Accepted
- **Date:** 2026-09-21
- **Amended:** 23 September 2026 — §5 is read through
  [ADR-031](ADR-031-trusted-server-sync-lane.md) (trusted-server lane, paused).
  §2: the chooser is a real gate (touch, Back,
  TalkBack, scroll) and the permission walk waits until Settings is left
  (whole-app audit Q1)
- **Supersedes / Related:** [ADR-004](ADR-004-offline-core-and-entitlements.md),
  [ADR-009](ADR-009-backup-privacy-sync.md), [sync-personal-build.md](sync-personal-build.md)

## Context

Temper Account (Supabase) and Google Drive backup serve different owners and
must not be conflated. Training must never require sign-in. Owners still need a
clear, once-per-install choice of how they want data saved, and a Settings path
to change that choice later.

## Decision

1. **Two save lanes, both optional for training.** Temper Account is optional
   cloud sync when signed in. Local use keeps Room on the phone as gym-floor
   source of truth; Google Drive remains optional whole-file **backup** for
   local users ([ADR-009]).
2. **First launch (after the process cold-start intro).** Before the permission
   walk, the app shows a full-screen chooser: Temper Account (sign in / create
   via Settings → Account) or continue on this phone, with an optional path into
   Settings → Backup for Drive. The choice is persisted (`save_posture_chosen`);
   the chooser does not repeat until the owner changes posture in Settings.
   *Amended 23 Sep 2026 (whole-app audit Q1):* the chooser is a real gate over
   the Home drawn beneath it — it is the touch target for its whole area, owns
   Back (which leaves the app; nothing is chosen, so it asks again next time),
   is announced as its own pane with the app beneath hidden from TalkBack, and
   scrolls so every choice is reachable in landscape and at large text. After
   Account or Drive, the permission walk waits until the owner is on a tab other
   than Settings, so its dialogs never cover the sign-in or backup form.
3. **Upgrades.** Installs that already used permissions, plan setup, Drive, or
   Account are migrated to a chosen posture without re-showing the chooser.
4. **Settings → How you save.** Owners can switch posture and open Account or
   Backup from one subpage. Home, Plan, History, and live workouts are never
   gated ([ADR-004]).
5. **Temper Account sync target (signed-in users).** Long term, account details,
   settings, workouts, and history are fully cloud-backed with local Room as
   cache. **Packet 1 ships posture + UX only.** Replication today matches
   [sync-personal-build.md](sync-personal-build.md) scope (`SyncEntityType`).
   The lane that reaches this target is trusted-server and currently paused;
   its rules are [ADR-031](ADR-031-trusted-server-sync-lane.md)'s.

## Consequences

- Later packets expand `SyncEntityType` and worker coverage; they do not remove
  Drive backup or gate training on Account.
- Privacy and Data Safety stay honest about partial sync until follow-up packets
  land.

## Follow-up sync entities

| Area | Examples | Notes |
|------|----------|--------|
| Library | Custom exercises + junction credits | **Packet 2** — built-in catalog seed still local |
| Body | Bodyweight weigh-in log | **Packet 2** — training blocks still local |
| Goals | Measurable goals, pause intervals | **Packet 3** — `measurable_goals` |
| Coach / generator | Coach prefs (goal, emphasis, equipment, age, place, focus, heat window) | **Packet 3** — `coach_prefs`; training blocks stay backup/local |
| Reminders | Reminder prefs, day alarms | **Packet 3** — `reminder_prefs`; device-local pending occurrence / permission flags stay local |
| Display | Weight unit, clock format, check-in weekday | **Packet 3** — `display_prefs`; RPE helper dismissed stays device-local |
| Account profile | Save posture choice | **Packet 3** — `account_profiles`; display name / avatar deferred (no UI) |

## Review questions

- Is training gated on Account? **No** ([ADR-004]).
- Is Drive sync? **No** — backup only ([ADR-009]).
- Does choosing local block Account later? **No** — Settings can switch.
