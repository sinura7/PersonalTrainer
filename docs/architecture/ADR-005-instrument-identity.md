# ADR-005 — Instrument visual identity

- **Status:** Accepted
- **Date:** 24 August 2026
- **Related:** [DIRECTION_B_INSTRUMENT.md](../ui-redesign/DIRECTION_B_INSTRUMENT.md),
  FND-045

## Context

The shipping UI is a dark Instrument: semantic color roles, tabular numerals,
dense telemetry, one Volt action. A light theme and a second visual language
were considered and rejected.

## Decision

1. **Instrument is the only visual direction.** Dark-only. No light theme.
   Absence of a light theme is not a defect.
2. Color is consumed through semantic roles (`Surface*`, `Text*`, `Volt`,
   heat ramp). Raw hex/rgb/color literals in UI code remain forbidden.
3. Large numerals use the bundled tabular faces. System monospace is not a
   display face.
4. Each screen has **one dominant filled Volt action**. Secondary acts are
   quiet. Volt is not a brand wash.
5. Motion, spacing, shape, and haptics stay on the existing token layer.
   Reduced motion collapses token durations to zero (`LocalReducedMotion` /
   system animator scale). It does not invent a second theme.
6. Stock Material controls may be skinned only when golden or runtime
   evidence shows a visual or behavioral mismatch (FND-044). Taste alone is
   not enough.
7. A later IA evidence packet may move information. It may not replace
   Instrument with another design language.

## Consequences

- Theme packets do not add DayNight or dynamic color.
- New cardio and schedule surfaces reuse Instrument tokens. They do not
  introduce sport-app neons or a second type ramp.
- FND-045 is a permanent decision, protected here and verified in P9.2.

## Review questions

- Is a light theme required for Play or accessibility? No. Contrast and
  TalkBack are required; light theme is not.
- May a cardio screen use a different accent? No. Volt remains the one
  filled act.
