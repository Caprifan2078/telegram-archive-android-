#!/usr/bin/env bash
set -uo pipefail
result=0
gradle --no-daemon --max-workers=2 connectedProtectedArchiveQaAndroidTest -PqaProtectedArchiveInstrumentation=true -PqaLocalModel=true -Pandroid.testInstrumentationRunnerArguments.class=org.openarchive.telegram.AnalystBackgroundSearchUiTest,org.openarchive.telegram.FullChatModelScanIntegrationTest "$@" || result=$?
mkdir -p app/build/ui-audit
adb logcat -d -s AnalystJob:I ActivityTaskManager:W > app/build/ui-audit/background-diagnostic.txt || true
cat app/build/ui-audit/background-diagnostic.txt || true
exit "$result"
