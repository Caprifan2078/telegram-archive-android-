#!/usr/bin/env bash
# Copy screenshots while instrumentation is running; Gradle uninstalls the test app
# at completion, which would erase Android/data/.../files.
set -euo pipefail
mkdir -p visual-audit
source_dir=/sdcard/Android/data/org.openarchive.telegram.qa.uichat20261118/files/visual-audit
(
  for i in $(seq 1 420); do
    adb pull "$source_dir/." visual-audit/ >/dev/null 2>&1 || true
    sleep 0.5
  done
) &
watcher=$!
set +e
gradle --no-daemon --max-workers=2 connectedUiReviewQaAndroidTest \
  -PqaUiReviewInstrumentation=true \
  '-Pandroid.testInstrumentationRunnerArguments.class=org.openarchive.telegram.QaVisualAuditScreenshotsTest'
result=$?
set -e
adb pull "$source_dir/." visual-audit/ >/dev/null 2>&1 || true
kill "$watcher" >/dev/null 2>&1 || true
wait "$watcher" >/dev/null 2>&1 || true
ls -lh visual-audit/
count=$(find visual-audit -maxdepth 1 -type f -name '*.png' | wc -l)
echo "Visual screenshots retrieved: $count/11"
test "$result" -eq 0
test "$count" -eq 11
