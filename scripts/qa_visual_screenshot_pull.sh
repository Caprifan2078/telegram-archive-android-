#!/usr/bin/env bash
# All screenshots are synthetic and exported through Android MediaStore Pictures,
# so they persist after Gradle uninstalls the instrumentation app.
set -euo pipefail
gradle --no-daemon --max-workers=2 connectedUiStructureQaAndroidTest \
  -PqaUiStructureInstrumentation=true \
  '-Pandroid.testInstrumentationRunnerArguments.class=org.openarchive.telegram.QaVisualAuditScreenshotsTest'
mkdir -p visual-audit
adb shell ls -lh /sdcard/Pictures/TelegramViewerVisualAudit/
adb pull /sdcard/Pictures/TelegramViewerVisualAudit/. visual-audit/
ls -lh visual-audit/
count=$(find visual-audit -maxdepth 1 -type f -name '*.png' | wc -l)
echo "Visual screenshots retrieved: $count/11"
test "$count" -eq 11
