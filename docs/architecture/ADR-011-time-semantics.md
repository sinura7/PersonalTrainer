# ADR-011 — Captured time and travel policy

- **Status:** Accepted
- **Date:** 24 August 2026
- **Related:** FND-039, FND-020; P5.1, P6.2, P7.1, P8.1

## Context

Everything currently reads `ZoneId.systemDefault()` at display and often at
write. That makes yesterday’s session jump days when the owner travels, and
it makes DST gaps and overlaps undefined. Annual progress and two-a-day
schedules cannot be honest without a captured zone.

## Decision

1. Every new durable activity, occurrence, goal period, and bodyweight entry
   stores:
   - a UTC instant (or equivalent epoch millis) for the performed/effective
     time;
   - the **IANA time-zone ID** captured at write;
   - the **UTC offset** captured at write;
   - the **local calendar date** in that zone, used for day/week/month/year
     attribution.
2. Display uses the captured local date for “which day did this belong to.”
   It does not re-derive the day from the current device zone.
3. **Backdated authoring** lets the user set local date, time, and zone.
   Default zone is the device zone at authoring. Travel does not rewrite
   older rows.
4. **Schedule rules** have an explicit zone policy:
   - **Follow-device** (default): the occurrence’s local time is interpreted
     in the device zone on the day it is generated;
   - **Fixed-zone**: the rule names an IANA zone and keeps it.
   Generated occurrences still persist the captured zone and offset they
   were generated with, so history does not drift.
5. DST gap (spring-forward missing local time) and overlap (fall-back
   repeated local time) are resolved explicitly in the composer and in
   occurrence generation. The chosen offset is persisted. Tests cover both.
6. Week boundaries use the user’s week-start preference plus the captured
   local date. They do not use the device zone of the read.
7. New shared-target domain code does not import `java.time`. It talks to
   injected clock, date, and zone ports ([ADR-003](ADR-003-shipping-platform.md)).
   Android UI may format with platform locale APIs.
8. Rest-timer deadlines remain `elapsedRealtime`-based. They are not civil
   times and are not stored as time zones
   ([ADR-012](ADR-012-rest-and-reminders.md)).

## Consequences

- Home, History, heat, goals, and projections attribute a backdated Tokyo
  session to the Tokyo local date even if the phone is later in New York.
- “Today” on Home is the device’s current local date. That is a *now*
  question, not a rewrite of captured history.
- FND-039 closes only when write paths persist the four-tuple in (1).

## Review questions

- If I log in London and open History in Chicago, does the session move
  days? No.
- If I schedule 07:00 follow-device cardio and I fly overnight, which 07:00
  fires? The 07:00 in the device zone on that local date.
- Are rest timers scheduled in civil time? No.
