#!/usr/bin/env bash
# Run production TGS decoding against synthetic edge cases without Android SDK.
set -euo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
javac -encoding UTF-8 -d "$classes" \
  "$repo/app/src/main/java/org/openarchive/telegram/TgsPayload.java" \
  "$repo/qa/host_tests/TgsHostCheck.java"
java -Xmx32m -cp "$classes" org.openarchive.telegram.TgsHostCheck
