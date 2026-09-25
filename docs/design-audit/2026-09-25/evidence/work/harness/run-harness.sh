#!/bin/bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
cd /home/user/PersonalTrainer || exit 99
S=/tmp/claude-0/-home-user-PersonalTrainer/cf46fb84-1fb9-5154-8302-bc82a0b34c05/scratchpad
DEST=app/src/test/java/com/sinura/personaltrainer/ui/AuditRenderTest.kt
cp "$S/harness/AuditRenderTest.kt" "$DEST"
echo "=== harness run $(date -u +%FT%TZ) ===" | tee -a "$S/baseline/00-timeline.log"
( time sh tools/hang-watchdog.sh ./gradlew -PskipStaticChecks testDebugUnitTest \
    --tests 'com.sinura.personaltrainer.ui.AuditRenderTest' \
    --tests 'com.sinura.personaltrainer.ui.AuditFloorRenderTest' \
    --tests 'com.sinura.personaltrainer.ui.AuditShellRenderTest' ) > "$S/harness/run-1.log" 2>&1
rc=$?
echo "--- harness exit $rc --- $(date -u +%FT%TZ)" | tee -a "$S/baseline/00-timeline.log"
echo "frames: $(find app/build/screen-renders/audit-2026-09-25 -name '*.png' 2>/dev/null | wc -l)" | tee -a "$S/harness/run-1.log"
