#!/usr/bin/env python3
"""Independent extraction check for ConversationPdfExporterTest synthetic fixture."""
from pathlib import Path
import re
import sys

text = Path(sys.argv[1]).read_text(encoding="utf-8")
messages = [int(x) for x in re.findall(r"QA_PDF_MESSAGE_(\d+)", text)]
checks = {
    "messages in order": messages == list(range(150)),
    "authors": text.count("Тестовый автор") == 150,
    "timestamps": text.count("2026-01-01 12:34") == 150,
    "message bodies": text.count("Синтетическое сообщение номер") == 150,
    "emoji without duplication": text.count("😀") == 150,
}
for name, ok in checks.items():
    print(f"{name}: {'PASS' if ok else 'FAIL'}")
if not all(checks.values()):
    raise SystemExit(1)
