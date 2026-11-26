#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/ui-audit
result=0
model=${1:-qwen06}
case "$model" in qwen06|qwen17|qwen3508|huihui352|qwen25unc15|qwen06q4|qwen2515) ;; *) echo 'Unknown benchmark model'; exit 2;; esac
extra=()
if [[ "$model" == qwen25unc15 ]]; then
  [[ "${PROBE_REVISION:-}" =~ ^[a-f0-9]{40}$ && "${PROBE_SHA:-}" =~ ^[a-f0-9]{64}$ ]] || exit 3
  extra+=("-Pandroid.testInstrumentationRunnerArguments.probeRevision=$PROBE_REVISION" "-Pandroid.testInstrumentationRunnerArguments.probeSha=$PROBE_SHA")
fi
gradle --no-daemon --max-workers=2 connectedProtectedArchiveQaAndroidTest -PqaProtectedArchiveInstrumentation=true -PqaLocalModel=true -Pandroid.testInstrumentationRunnerArguments.class=org.openarchive.telegram.RealLocalModelSmokeTest -Pandroid.testInstrumentationRunnerArguments.analystModel="$model" "${extra[@]}" || result=$?
adb logcat -d -t 10000 > app/build/ui-audit/real-model-logcat.txt || true
grep -E "ModelSmoke:" app/build/ui-audit/real-model-logcat.txt | tail -n 60 || true
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
print("Actual pinned model download and native chat inference: PASS")
PY
