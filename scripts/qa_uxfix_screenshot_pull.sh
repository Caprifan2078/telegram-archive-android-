#!/usr/bin/env bash
set -euo pipefail
gradle --no-daemon --max-workers=2 connectedUxFixQaAndroidTest \
  -PqaUxFixInstrumentation=true \
  '-Pandroid.testInstrumentationRunnerArguments.class=org.openarchive.telegram.QaVisualAuditScreenshotsTest'
mkdir -p visual-audit
adb pull /sdcard/Pictures/TelegramViewerVisualAudit/. visual-audit/
count=$(find visual-audit -maxdepth 1 -type f -name '*.png' | wc -l)
echo "UX fix screenshots: $count/11"
test "$count" -eq 11
