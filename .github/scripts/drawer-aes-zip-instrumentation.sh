#!/usr/bin/env bash
# One shell process: android-emulator-runner executes each YAML script line
# separately, so inline result=0 / exit "$result" loses its exit status.
set -uo pipefail
result=0
gradle --no-daemon --max-workers=2 connectedDrawerZipQaAndroidTest -PqaDrawerZipInstrumentation=true '-Pandroid.testInstrumentationRunnerArguments.class=org.openarchive.telegram.DrawerAesZipRegressionTest,org.openarchive.telegram.IndependentAesZipRegressionTest,org.openarchive.telegram.EncryptedTelegramImportRegressionTest,org.openarchive.telegram.ArchivePasswordOnDemandRegressionTest,org.openarchive.telegram.ArchivePasswordUiRegressionTest,org.openarchive.telegram.UiMenuTourRegressionTest,org.openarchive.telegram.UiChromeOverlapRegressionTest,org.openarchive.telegram.MediaFastScrollRegressionTest,org.openarchive.telegram.MediaInfiniteGalleryRegressionTest,org.openarchive.telegram.ChronologyHubRegressionTest,org.openarchive.telegram.FloatingRoundReattachRegressionTest,org.openarchive.telegram.ChatVisualConsistencyRegressionTest,org.openarchive.telegram.MultiConversationHomeRegressionTest' || result=$?
mkdir -p app/build/ui-audit
adb pull /sdcard/Download/ app/build/ui-audit/ || true
exit "$result"
