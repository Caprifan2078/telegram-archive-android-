#!/usr/bin/env bash
set -euo pipefail
# Synthetic visual fixtures only; no actual user messages or photos.
gradle --no-daemon --max-workers=2 connectedCleanChatQaAndroidTest \
  -PqaCleanChatInstrumentation=true \
  '-Pandroid.testInstrumentationRunnerArguments.class=org.openarchive.telegram.QaVisualAuditScreenshotsTest'
mkdir -p visual-audit
adb shell ls /sdcard/Pictures/TelegramViewerVisualAudit/
adb pull /sdcard/Pictures/TelegramViewerVisualAudit/. visual-audit/
count=$(find visual-audit -maxdepth 1 -type f -name '*.png' | wc -l)
echo "Synthetic screenshots collected: $count / 11"
test "$count" -eq 11
