#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  backup) classes=(BackupDryRunRegressionTest BackupIntegrityAndroid15RegressionTest BackupSafReadbackRegressionTest FullBackupCustomAssetSafetyRegressionTest FullBackupSourceAccessRegressionTest) ;;
  recovery) classes=(PortableRestoreRegressionTest RestoreRecoveryTest ImportRollbackRegressionTest DuplicateTelegramIdSafetyTest) ;;
  viewer) classes=(SearchLifecycleRegressionTest PerformanceRegressionTest AttachmentPreviewScreenTest) ;;
  *) exit 2 ;;
esac
qualified=()
for class in "${classes[@]}"; do
  test -f "app/src/androidTest/java/org/openarchive/telegram/$class.java"
  qualified+=("org.openarchive.telegram.$class")
done
selector=$(IFS=,; echo "${qualified[*]}")
mkdir -p app/build/extended-audit
result=0
gradle --no-daemon --max-workers=2 connectedProtectedArchiveQaAndroidTest -PqaProtectedArchiveInstrumentation=true "-Pandroid.testInstrumentationRunnerArguments.class=$selector" || result=$?
adb logcat -d -t 5000 > app/build/extended-audit/logcat.txt || true
if [[ "$result" != 0 ]]; then exit "$result"; fi
python3 - "$selector" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
expected = set(sys.argv[1].split(","))
counts = dict.fromkeys(expected, 0)
failed = []
for path in pathlib.Path("app/build/outputs/androidTest-results").rglob("TEST-*.xml"):
    for case in ET.parse(path).getroot().iter("testcase"):
        name = case.get("classname")
        if name not in counts:
            continue
        if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
            failed.append(name + "." + str(case.get("name")))
        else:
            counts[name] += 1
lines = [f"{name}: {count} passed" for name, count in sorted(counts.items())]
lines.append(f"Total passed: {sum(counts.values())}")
lines.extend("Not passed: " + name for name in failed)
report = "\n".join(lines) + "\n"
pathlib.Path("app/build/extended-audit/results.txt").write_text(report, encoding="utf-8")
print(report)
if failed or any(count == 0 for count in counts.values()):
    sys.exit("Extended audit failed or a selected class did not execute")
PY
