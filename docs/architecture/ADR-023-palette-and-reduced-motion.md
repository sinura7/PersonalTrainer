# ADR-023 — Palette collisions stay; reduced motion finishes the gate

- **Status:** Accepted
- **Date:** 3 September 2026
- **Related:** [ADR-005](ADR-005-instrument-identity.md) §5;
  FND-045; G6 / F14
- **Does not supersede:** ADR-005. Instrument stays the only visual
  direction. This record answers the palette question ADR-005 left
  open and names the remaining reduced-motion sites.

## Context

Three Instrument collisions survive on the shipping palette:

1. **Volt and PrGold** collapse for a red-green-blind (deutan) reader.
2. **Warn (`#FFB020`) and PrGold (`#FFC53D`)** sit next to each other
   for everyone.
3. **Heat3** (`#E25A50`) reads as **Danger** (`#FF6B6B`).

Shifting Warn toward orange would retune a gym-floor palette the owner
already trains under. The heat ramp already carries intensity in
luminance. Records already carry a trophy and the words "personal
record". Warn sites already carry a clock, a kicker, or copy.

Separately, ADR-005 §5 says reduced motion collapses token durations
to zero. Six of thirteen animating files honoured that; the summary
count-up, record stagger, status banner, rest sweep, and eight
`animateItem()` lists did not.

## Decision

1. **Keep Volt, PrGold, Warn, Danger, and the heat ramp as they are.**
   Do not shift Warn toward orange. Do not invent a second gold.
2. **Every site that uses those tokens must carry a non-colour
   channel** — icon, kicker, copy, or (on the heat ramp) luminance.
   Colour is never the only signal.
3. **Reduced motion collapses animation durations to zero** at the
   remaining sites: summary count-up and record entrance, status and
   record banners, rest sweep/pulse, and list placement. Dwell times
   (how long a banner stays readable) are not animations and stay.

## Consequences

- `Color.kt` hex values do not move in a polish packet.
- A later signed retune may still shift Warn; it needs a new ADR.
- `Motion` owns dwell, pulse, and tick constants. Call sites do not
  invent millisecond literals for those.

## Review questions

- Must a deutan reader tell Volt from gold by hue? No. The filled
  button versus the trophy-and-copy record is the distinction.
- Does reduced motion hide records? No. They appear at rest, without
  the stagger or the scale-in.
