#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/ui-audit
result=0
gradle --no-daemon --max-workers=2 connectedProtectedArchiveQaAndroidTest -PqaProtectedArchiveInstrumentation=true -PqaLocalModel=true -Pandroid.testInstrumentationRunnerArguments.class=org.openarchive.telegram.RealLocalModelSmokeTest || result=$?
adb logcat -d -t 10000 > app/build/ui-audit/real-model-logcat.txt || true
if [[ "$result" != 0 ]]; then
  echo "Native model diagnostic (synthetic data only)"
  adb logcat -b crash -d -t 2000 || true
  grep -E "ModelSmoke|Fatal signal|FATAL EXCEPTION|OutOfMemory|lowmemorykiller|lmkd|LiteRT|litertlm" app/build/ui-audit/real-model-logcat.txt | tail -n 150 || true
  exit "$result"
fi
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
cases=[]
for p in pathlib.Path("app/build/outputs/androidTest-results").rglob("TEST-*.xml"):
    cases.extend(c for c in ET.parse(p).getroot().iter("testcase") if c.get("classname")=="org.openarchive.telegram.RealLocalModelSmokeTest")
assert len(cases)==1, "Real inference test did not execute exactly once"
assert not any(cases[0].find(t) is not None for t in ("failure","error","skipped")), "Real inference did not pass"
print("Actual Qwen download and native chat inference: PASS")
PY
