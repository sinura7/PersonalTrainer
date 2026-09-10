#!/bin/sh
# tools/hang-watchdog.sh — run a Gradle command, and thread-dump it if it wedges.
#
# Why this exists: a CI unit-test run sat for 31m10s and was killed by the job's
# timeout-minutes. Cancelling a job skips its `if: always()` steps, so nothing was
# uploaded — no HTML report, no per-class XML, no stacks. Thirty minutes of runner
# time bought zero evidence. The hang has not reproduced reliably on a developer
# box, so the instrument has to live where the bug actually happens.
#
# How it decides: a wedged JVM burns no CPU, so this samples the Gradle test
# worker's utime+stime out of /proc instead of guessing a wall-clock limit. A slow
# or contended machine keeps making progress and is never flagged; only a worker
# that has genuinely stopped computing is.
#
# Everything is observed from OUTSIDE the worker on purpose. Adding a println to
# the suspect write path made the hang vanish for ten consecutive runs, so any
# in-JVM instrumentation is as likely to hide this bug as to catch it.
#
# usage: sh tools/hang-watchdog.sh ./gradlew testDebugUnitTest --stacktrace
#
# env:
#   PT_HANG_SAMPLE      seconds between CPU samples            (default 30)
#   PT_HANG_IDLE_TICKS  ticks below which a sample reads idle  (default 200, ~2 CPU-seconds)
#   PT_HANG_STRIKES     consecutive idle samples to call it    (default 3)
#   PT_HANG_MIN_ALIVE   never judge a worker younger than this (default 60 seconds)
#   PT_HANG_DUMP_DIR    where the dumps land                   (default build/hang-dumps)
#
# Exits with the wrapped command's status, or 42 when it killed a wedged worker.
set -u

SAMPLE="${PT_HANG_SAMPLE:-30}"
IDLE_TICKS="${PT_HANG_IDLE_TICKS:-200}"
STRIKES_MAX="${PT_HANG_STRIKES:-3}"
MIN_ALIVE="${PT_HANG_MIN_ALIVE:-60}"
DUMP_DIR="${PT_HANG_DUMP_DIR:-build/hang-dumps}"

[ "$#" -gt 0 ] || { echo "hang-watchdog: nothing to run" >&2; exit 2; }

# No JDK tools, or no procfs, means no watching. Run the command anyway rather
# than failing a build over a missing diagnostic.
if ! command -v jps >/dev/null 2>&1 || ! command -v jstack >/dev/null 2>&1 || [ ! -d /proc/self ]; then
    echo "hang-watchdog: jps/jstack or /proc unavailable — running unwatched" >&2
    exec "$@"
fi

# /proc/<pid>/stat's comm field is parenthesised and may contain spaces, so cut
# through the last ')' first; utime and stime are then fields 12 and 13.
cpu_ticks() {
    sed 's/^.*) //' "/proc/$1/stat" 2>/dev/null | awk '{ print $12 + $13 }'
}

workers() {
    jps -l 2>/dev/null | grep 'GradleWorkerMain' | cut -d' ' -f1
}

dump() {
    _pid="$1"
    mkdir -p "$DUMP_DIR"
    _n=1
    while [ "$_n" -le 3 ]; do
        _file="$DUMP_DIR/jstack-$_pid-$_n.txt"
        jstack -l "$_pid" > "$_file" 2>&1
        echo "=== hang-watchdog: thread dump $_n of 3 for worker $_pid ==="
        cat "$_file"
        _n=$((_n + 1))
        [ "$_n" -le 3 ] && sleep 10
    done
}

"$@" &
CMD_PID=$!

WATCHED=""       # pid of the worker being tracked
PREV=0
STRIKES=0
SEEN=0

while kill -0 "$CMD_PID" 2>/dev/null; do
    sleep "$SAMPLE"

    if [ -z "$WATCHED" ] || ! kill -0 "$WATCHED" 2>/dev/null; then
        WATCHED="$(workers | head -1)"
        [ -n "$WATCHED" ] || continue
        SEEN="$(date +%s)"
        PREV="$(cpu_ticks "$WATCHED")"
        STRIKES=0
        continue
    fi

    CUR="$(cpu_ticks "$WATCHED")"
    [ -n "$CUR" ] || continue
    DELTA=$((CUR - PREV))
    PREV="$CUR"
    ALIVE=$(( $(date +%s) - SEEN ))

    if [ "$ALIVE" -le "$MIN_ALIVE" ] || [ "$DELTA" -ge "$IDLE_TICKS" ]; then
        STRIKES=0
        continue
    fi

    STRIKES=$((STRIKES + 1))
    echo "hang-watchdog: worker $WATCHED idle (${DELTA} ticks in ${SAMPLE}s), strike $STRIKES/$STRIKES_MAX" >&2
    [ "$STRIKES" -lt "$STRIKES_MAX" ] && continue

    echo "hang-watchdog: worker $WATCHED has made no progress for $((SAMPLE * STRIKES_MAX))s — dumping and killing it" >&2
    dump "$WATCHED"
    echo "hang-watchdog: per-class results written before the hang are in app/build/test-results/" >&2
    kill -9 "$WATCHED" 2>/dev/null
    wait "$CMD_PID" 2>/dev/null
    exit 42
done

wait "$CMD_PID"
exit $?
