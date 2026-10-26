#!/usr/bin/env python3
"""Verify *production* Java search plans against real offline SQLite FTS4 unicode61.

Synthetic Russian strings only. JDK 17 and Python's built-in sqlite3 needed.
"""
from pathlib import Path
import sqlite3
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = root / "app/src/main/java/org/openarchive/telegram"
with tempfile.TemporaryDirectory(prefix="fts-yo-") as classes:
    subprocess.run([
        "javac", "-encoding", "UTF-8", "-d", classes,
        str(source / "FuzzyWordPolicy.java"),
        str(source / "OfflineSynonymSearch.java"),
        str(root / "qa/host_tests/YoSearchHostCheck.java"),
    ], check=True)
    raw = subprocess.check_output([
        "java", "-Xmx64m", "-cp", classes, "org.openarchive.telegram.YoSearchHostCheck",
    ], text=True, encoding="utf-8")
plans = dict(line.split("\t", 1) for line in raw.strip().splitlines())
expected = {
    "ёлка": {"ёлка", "елка"},
    "елка": {"ёлка", "елка"},
    "ёжик": {"ёжик", "ежик"},
    "ежик": {"ёжик", "ежик"},
    "всё": {"всё", "все"},
    "все": {"всё", "все"},
    "ё-опечатка": {"ёлочка"},
}
db = sqlite3.connect(":memory:")
db.execute("CREATE VIRTUAL TABLE messages_fts USING fts4(author,body,date_text,tokenize=unicode61)")
for name in {"ёлка", "елка", "ёжик", "ежик", "всё", "все", "ёлочка"}:
    db.execute("INSERT INTO messages_fts(author,body,date_text) VALUES(?,?,?)",
               ("Синтетический автор", name, "2026-01-01"))
for term, desired in expected.items():
    expr = plans[term]
    try:
        hits = {row[0] for row in db.execute(
            "SELECT body FROM messages_fts WHERE messages_fts MATCH ?", (expr,)
        )}
    except sqlite3.OperationalError as exc:
        raise AssertionError(f"FTS query rejected for {term!r}: {expr}") from exc
    assert hits == desired, f"{term!r}: expected {desired}, got {hits} with {expr}"
print("PASS: production Java FTS plans find both е/ё spellings (7 queries, synthetic corpus)")
