#!/usr/bin/env bash
set -euo pipefail
if command -v 7zz >/dev/null; then seven=$(command -v 7zz); else seven=$(command -v 7z); fi
dest="$PWD/app/src/androidTest/assets/zip_codec_probe"
mkdir -p "$dest"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
python3 - <<'PY'
from pathlib import Path
Path("messages.html").write_text('<div class="history"><div class="message default" id="message101"><div class="body"><div class="from_name">Synthetic</div><div class="text">ZIP codec synthetic marker '+ 'sample sentence with varied numbers 1234567890. '*1800 +'</div></div></div></div>',encoding="utf-8")
PY
for method in Deflate Deflate64 BZip2 LZMA; do
  archive="$dest/$method.zip"
  rm -f "$archive"
  "$seven" a -tzip "-mm=$method" -mem=AES256 -pcodec-test-only "$archive" messages.html
  "$seven" t -pcodec-test-only "$archive"
  listing=$("$seven" l -slt -pcodec-test-only "$archive")
  printf '%s\n' "$listing"
  printf '%s\n' "$listing" | grep -E "^Method = .*AES-256.*$method([[:space:]]|:|$)"
done
