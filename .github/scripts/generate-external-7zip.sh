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
"$seven" a -tzip -mem=AES256 "-p пробел-Ё-123 ! " "$dest/aes256-unicode.zip" messages.html
"$seven" t "-p пробел-Ё-123 ! " "$dest/aes256-unicode.zip"
