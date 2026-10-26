#!/usr/bin/env python3
"""Host regression for the production SQLite duplicate-ID publishing policy.

This tests SQL invariants, not Android UI or APK execution. All data synthetic.
"""
from pathlib import Path
import sqlite3

SOURCE = Path(__file__).resolve().parents[2] / "app/src/main/java/org/openarchive/telegram/MessageDb.java"
code = SOURCE.read_text(encoding="utf-8")
assert "SET message_id=-ABS(message_id) WHERE telegram_id=?" in code, (
    "Production duplicate-ID invalidation SQL is missing"
)
assert "FROM staged_telegram_ids WHERE message_id>0" in code, (
    "Production publish must exclude ambiguous Telegram IDs"
)

db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE staged_telegram_ids (message_id INTEGER PRIMARY KEY, telegram_id INTEGER NOT NULL UNIQUE);
CREATE TABLE telegram_ids (message_id INTEGER PRIMARY KEY, telegram_id INTEGER NOT NULL UNIQUE);
CREATE TABLE staged_reply_ids (message_id INTEGER PRIMARY KEY, target_telegram_id INTEGER NOT NULL);
CREATE TABLE message_replies (message_id INTEGER PRIMARY KEY, target_telegram_id INTEGER NOT NULL,
                              target_message_id INTEGER);
""")

def stage(message_id: int, telegram_id: int):
    db.execute("INSERT OR IGNORE INTO staged_telegram_ids(message_id,telegram_id) VALUES (?,?)",
               (message_id, telegram_id))
    if db.execute("SELECT changes()").fetchone()[0] == 0:
        db.execute("UPDATE staged_telegram_ids SET message_id=-ABS(message_id) WHERE telegram_id=?",
                   (telegram_id,))

# A duplicate in any later HTML part must invalidate the link even after 3+ copies.
for msg_id, original_id in ((1, 101), (2, 101), (3, 101), (4, 404), (5, 505)):
    stage(msg_id, original_id)
db.execute("""INSERT INTO telegram_ids(message_id,telegram_id)
              SELECT message_id,telegram_id FROM staged_telegram_ids WHERE message_id>0""")
db.executemany("INSERT INTO staged_reply_ids VALUES(?,?)", [(6, 101), (7, 404), (8, 505)])
db.execute("""INSERT INTO message_replies(message_id,target_telegram_id,target_message_id)
              SELECT r.message_id,r.target_telegram_id,t.message_id
              FROM staged_reply_ids r LEFT JOIN telegram_ids t
              ON t.telegram_id=r.target_telegram_id""")
assert db.execute("SELECT * FROM telegram_ids ORDER BY message_id").fetchall() == [(4, 404), (5, 505)]
assert db.execute("SELECT * FROM message_replies ORDER BY message_id").fetchall() == [
    (6, 101, None), (7, 404, 4), (8, 505, 5)
]
# Clearing staging must not leave collision markers between separate imports.
db.execute("DELETE FROM staged_telegram_ids")
stage(9, 101)
assert db.execute("SELECT * FROM staged_telegram_ids").fetchall() == [(9, 101)]
db.close()
print("PASS: ambiguous IDs do not resolve; unique links survive; staging can reset")
