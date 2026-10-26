#!/usr/bin/env python3
"""Synthetic-only offline SQLite FTS4 smoke/stress. Does not read user chats."""
import os,sqlite3,tempfile,time
start=time.monotonic()
f=tempfile.NamedTemporaryFile(prefix='ta047-fts-',suffix='.db',delete=False)
f.close()
try:
 c=sqlite3.connect(f.name)
 c.executescript("""
 PRAGMA journal_mode=WAL;
 CREATE TABLE messages(id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT,body_fold TEXT,author_fold TEXT);
 CREATE VIRTUAL TABLE messages_fts USING fts4(author,body,date_text,content='messages',tokenize=unicode61);
 """)
 rows=200001
 c.executemany("INSERT INTO messages VALUES(?,?,?,?,?,?)", ((i,'Боб','2022-06-13',
  ('Купили ПК для работы' if i==1 else 'Компания прислала письмо' if i==2 else 'Обычное сообщение о дне и планах'),
  '', '') for i in range(1,rows+1)))
 c.commit()
 t=time.monotonic();c.execute("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')");c.commit()
 index_secs=time.monotonic()-t
 expr='(body:компьютер* OR body:комп OR body:компа OR body:компе OR body:компу OR body:компом OR body:пк OR body:pc)'
 t=time.monotonic()
 hits=c.execute('''SELECT id FROM messages WHERE messages.id IN
 (SELECT docid FROM messages_fts WHERE messages_fts MATCH ?) ORDER BY id DESC LIMIT 36''',(expr,)).fetchall()
 query_secs=time.monotonic()-t
 assert hits==[(1,)],f'Unexpected matches: {hits}'
 first=c.execute('''SELECT id FROM messages WHERE messages.id IN
 (SELECT docid FROM messages_fts WHERE messages_fts MATCH ?) ORDER BY id ASC LIMIT 1''',(expr,)).fetchone()
 assert first==(1,)
 # Ensure the original raw query cannot be injected through bound MATCH parameter.
 try:
  c.execute('SELECT docid FROM messages_fts WHERE messages_fts MATCH ?',('body:unclosed(',)).fetchall()
 except sqlite3.OperationalError:pass
 else: raise AssertionError('Malformed FTS expression unexpectedly accepted')
 print(f'PASS {rows} synthetic messages, index rebuild {index_secs:.3f}s, lookup {query_secs:.4f}s, oldest message found')
 print('PASS slang "комп" did not match unrelated "компания"')
 print(f'Total elapsed {time.monotonic()-start:.2f}s (desktop SQLite; not Android benchmark)')
finally:
 try:c.close()
 except Exception:pass
 for path in [f.name,f.name+'-wal',f.name+'-shm']:
  try:os.unlink(path)
  except FileNotFoundError:pass
