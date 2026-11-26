#!/usr/bin/env bash
set -euo pipefail
if command -v 7zz >/dev/null; then seven=$(command -v 7zz); else seven=$(command -v 7z); fi
dest="$PWD/app/src/androidTest/assets/external7zip"
mkdir -p "$dest"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
printf '%s' '<div class="history"><div class="message default" id="message101"><div class="body"><div class="from_name">Synthetic</div><div class="text">External 7zip synthetic message</div></div></div></div>' > "$tmp/messages.html"
cd "$tmp"
"$seven" a -tzip -mem=AES256 -pqa-7zip-password "$dest/aes256.zip" messages.html
"$seven" a -tzip -mem=ZipCrypto -pqa-7zip-password "$dest/zipcrypto.zip" messages.html
"$seven" a -t7z -mhe=on -pqa-7zip-password "$dest/headers.7z" messages.html
for f in "$dest"/aes256.zip "$dest"/zipcrypto.zip "$dest"/headers.7z; do "$seven" t -pqa-7zip-password "$f"; done
"$seven" a -tzip -mem=AES256 "-p Space-123 ! " "$dest/aes256-spaces.zip" messages.html
"$seven" t "-p Space-123 ! " "$dest/aes256-spaces.zip"

"$seven" a -t7z -mhe=on "-p пробел-Ё-123 ! " "$dest/unicode.7z" messages.html
"$seven" t "-p пробел-Ё-123 ! " "$dest/unicode.7z"

# Match the reported export layout, with synthetic payloads only.
mkdir -p "$tmp/layout"
cd "$tmp/layout"
python3 - <<'PY'
from pathlib import Path
for directory, count in {"css":1,"images":42,"js":1,"photos":42,"round_video_messages":834,"stickers":15,"video_files":21,"voice_messages":1}.items():
    root=Path(directory);root.mkdir()
    for i in range(count):
        (root/("synthetic%04d.txt"%i)).write_bytes(("fixture %s %d\n"%(directory,i)).encode())
for i in range(1,21):
    name="messages.html" if i==1 else "messages%d.html"%i
    Path(name).write_text('<div class="history"><div class="message default" id="message%d"><div class="body"><div class="from_name">Synthetic</div><div class="text">Synthetic page %d</div></div></div></div>'%(i,i),encoding="utf-8")
PY
for config in solid-header solid-data nonsolid-header; do
    solid=on;header=on
    if [[ "$config" == solid-data ]]; then header=off; fi
    if [[ "$config" == nonsolid-header ]]; then solid=off; fi
    archive="$dest/layout-$config.7z"
    "$seven" a -t7z "-ms=$solid" "-mhe=$header" -pqa-layout-secret "$archive" .
    "$seven" t -pqa-layout-secret "$archive"
    "$seven" l -slt -pqa-layout-secret "$archive" | grep -E '^(Solid|Blocks|Files|Folders) ='
done
