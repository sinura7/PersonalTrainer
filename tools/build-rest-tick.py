#!/usr/bin/env python3
"""Write res/raw/rest_tick.wav — the click for the last five seconds of rest.

Generated, not recorded, so the asset is reproducible from this file alone
(the fonts are built the same way, tools/build-fonts.py). Deterministic:
no randomness, no dependency beyond the standard library.

The click: 40 ms of a 1.8 kHz sine under an exponential decay with an 8 ms
time constant and a 1 ms attack, at 60% of full scale. Short enough that
five of them one second apart read as a count, not a tone; the completion
cue (rest_done.ogg) stays the only sustained sound the timer makes.

Usage:  python3 tools/build-rest-tick.py [path]
"""
from __future__ import annotations

import math
import struct
import sys
import wave

RATE = 44_100
DURATION_S = 0.040
FREQUENCY_HZ = 1_800.0
DECAY_S = 0.008
ATTACK_S = 0.001
PEAK = 0.60
OUT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw/rest_tick.wav"


def sample(index: int) -> int:
    t = index / RATE
    attack = min(1.0, t / ATTACK_S)
    envelope = attack * math.exp(-t / DECAY_S)
    value = PEAK * envelope * math.sin(2.0 * math.pi * FREQUENCY_HZ * t)
    return int(round(value * 32767.0))


def main() -> int:
    frames = int(RATE * DURATION_S)
    data = b"".join(struct.pack("<h", sample(i)) for i in range(frames))
    with wave.open(OUT, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(data)
    print(f"{OUT}: {frames} frames, {len(data) + 44} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
