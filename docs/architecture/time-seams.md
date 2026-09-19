# Platform-neutral time, ID, and quantity seams

- **Status:** Accepted — P5.1
- **Date:** 24 August 2026
- **Authority:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) P5.1
- **Does not reopen:** Room generation; activity persistence; Drive auth

This packet replaces `java.time` / `Locale` / `NumberFormat` inside
`domain/` with injected ports (ADR-003, ADR-011). Existing schedule
rules stay the ones signed in [SCHEDULE_SEMANTICS.md](../SCHEDULE_SEMANTICS.md).

## Decision

1. **Shared-target domain types do not import `java.time`,
   `java.text.NumberFormat`, `Locale`, Android, Room, or Compose.**
   `tools/check-domain-seams.py` ratchets that.
2. **Time is a four-tuple.** [CapturedCivilTime] holds UTC instant,
   IANA zone id, UTC offset seconds, and local epoch day. Display uses
   the captured local date. It does not re-derive the day from the
   device zone of the later read.
3. **DST is explicit.** A spring-forward gap is either shifted forward
   to the first valid local time or rejected. A fall-back overlap
   records the earlier or later offset. Tests cover both.
4. **IDs are injected.** [IdPort] lives in `domain/`. The JVM adapter
   stays `util/IdFactory`.
5. **Quantity math stays locale-free.** [WeightConverter.volumeDisplayWhole]
   is the single whole-number volume. Grouped printing is
   `util/QuantityFormat`.
6. **Rest timers stay `elapsedRealtime`.** They are not civil times
   (ADR-011 §8, ADR-012).
7. **The JVM adapter may use `java.time`.** `object JvmTime` in
   `util/JvmTimePort.kt`. Android UI may still format with platform locale
   APIs.
8. **The port is required, not defaulted.** Nothing in `domain/` may name
   `JvmTime`. Fifteen files carried `time: TimePort = JvmTime` until
   11 September 2026, which meant the port was declared and then defaulted
   past — and, because `JvmTime` calls `android.os.SystemClock` while `util`
   imports `TimePort` and `CivilDate` back, it also meant `domain` and `util`
   could not be compiled apart. `tools/check-domain-seams.py` now rejects an
   import of any internal package other than `domain` itself, so the whole
   class of reach-around is closed rather than the one instance of it.

## Finding coverage

FND-020 is opened by these seams. FND-039 closes only when write paths
persist the four-tuple (P6.2 / P7.1 / P8.1). This packet is the port,
not those writes.
