#!/usr/bin/env python3
"""Reconstruct only the synthetic instrumentation fixture; reject dropped log chunks."""
import base64
from pathlib import Path
import re
import sys

lines = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
begins = [i for i, line in enumerate(lines) if line.startswith("BEGIN ")]
if len(begins) != 1:
    raise SystemExit("Expected exactly one fixture")
start = begins[0]
size = int(lines[start].split()[1])
parts = {}
finished = False
for line in lines[start + 1:]:
    if line == "END":
        finished = True
        break
    match = re.fullmatch(r"PART (\d+) ([A-Za-z0-9+/=]+)", line)
    if match:
        offset = int(match.group(1))
        if offset in parts:
            raise SystemExit("Duplicate fixture chunk")
        parts[offset] = match.group(2)
if not finished or sorted(parts) != list(range(0, size, 2000)):
    raise SystemExit("Synthetic fixture is incomplete")
encoded = "".join(parts[offset] for offset in sorted(parts))
if len(encoded) != size:
    raise SystemExit("Synthetic fixture has an incorrect length")
pdf = base64.b64decode(encoded, validate=True)
if not pdf.startswith(b"%PDF-"):
    raise SystemExit("Fixture is not PDF")
Path(sys.argv[2]).write_bytes(pdf)
print(f"Synthetic fixture recovered: {len(pdf)} bytes, {len(parts)} chunks")
