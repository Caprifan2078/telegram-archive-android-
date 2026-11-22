#!/usr/bin/env bash
set -euo pipefail
# Synthetic-only capture on disposable Android emulator, no user files.
rm -rf visual-audit
adb shell rm -rf /sdcard/Pictures/TelegramViewerVisualAudit/ || true
gradle --no-daemon --max-workers=2 connectedPasswordOnDemandQaAndroidTest \
  -PqaPasswordInstrumentation=true \
  '-Pandroid.testInstrumentationRunnerArguments.class=org.openarchive.telegram.QaVisualAuditScreenshotsTest'
mkdir -p visual-audit
adb pull /sdcard/Pictures/TelegramViewerVisualAudit/. visual-audit/
count=$(find visual-audit -maxdepth 1 -type f -name '*.png' | wc -l)
echo "Verified UI screenshot capture: $count / 11"
test "$count" -eq 11
