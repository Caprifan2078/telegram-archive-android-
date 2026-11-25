#!/usr/bin/env bash
set -uo pipefail
case "${1:-}" in
  import) classes=(DrawerAesZipRegressionTest IndependentAesZipRegressionTest EncryptedTelegramImportRegressionTest ArchivePasswordOnDemandRegressionTest ArchivePasswordUiRegressionTest ArchiveReadOnlySafPipeRegressionTest SevenZTelegramImportRegressionTest RarCompatibilityRegressionTest ArchiveEndToEndRegressionTest) ;;
  navigation) classes=(UiMenuTourRegressionTest UiChromeOverlapRegressionTest ChronologyHubRegressionTest ChatVisualConsistencyRegressionTest MultiConversationHomeRegressionTest HistoryEventSearchRegressionTest QaVisualAuditScreenshotsTest) ;;
  media) classes=(MediaFastScrollRegressionTest MediaInfiniteGalleryRegressionTest FloatingRoundReattachRegressionTest FloatingRoundLifecycleRegressionTest RoundPlayerActivityTest MediaPlaybackTest MediaUpgradeRegressionTest Qa00MediaLifecycleTest VoiceSeekUiRegressionTest) ;;
  protected) classes=(ProtectedArchiveSessionTest) ;;
  *) echo "Unknown audit suite" >&2; exit 2 ;;
esac
qualified=()
for class in "${classes[@]}"; do
  test -f "app/src/androidTest/java/org/openarchive/telegram/$class.java" || exit 3
  qualified+=("org.openarchive.telegram.$class")
done
selector=$(IFS=,; echo "${qualified[*]}")
result=0
gradle --no-daemon --max-workers=2 connectedProtectedArchiveQaAndroidTest -PqaProtectedArchiveInstrumentation=true "-Pandroid.testInstrumentationRunnerArguments.class=$selector" || result=$?
mkdir -p app/build/ui-audit
adb logcat -d -t 5000 > app/build/ui-audit/logcat.txt || true
if [[ "${1:-}" == navigation ]]; then
  adb pull /sdcard/Download/ app/build/ui-audit/ || true
fi
exit "$result"
