#!/usr/bin/env python3
"""Execute actual Java-generated FTS4 MATCH plans against synthetic SQLite only."""
import sqlite3, subprocess, tempfile, pathlib, time, os
ROOT=pathlib.Path(__file__).resolve().parents[2]
TEST=ROOT/'qa'/'host_tests'
JAVA=ROOT/'app/src/main/java/org/openarchive/telegram/OfflineSynonymSearch.java'
with tempfile.TemporaryDirectory(prefix='ta048_java_') as out:
 subprocess.run(['javac','-encoding','UTF-8','-d',out,str(JAVA),str(ROOT/'app/src/main/java/org/openarchive/telegram/FuzzyWordPolicy.java'),str(TEST/'OfflineSearchPlans.java')],check=True)
 txt=subprocess.check_output(['java','-cp',out,'org.openarchive.telegram.OfflineSearchPlans'],text=True)
 plans=dict(line.split('\t',1) for line in txt.splitlines())
 with sqlite3.connect(':memory:') as d:
  d.executescript('''CREATE TABLE messages(id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT);
  CREATE VIRTUAL TABLE messages_fts USING fts4(author,body,date_text,content='messages',tokenize=unicode61);''')
  msgs=[
   (1,'Я','2021-01-01','Спасибо, Никита, ты мне помог!'),
   (2,'Я','2021-01-02','Купил машину сегодня.'),
   (3,'Я','2021-01-03','Я купил катану.'),
   (4,'Друг','2021-01-04','Руководитель компании прислал письмо.'),
   (5,'Я','2021-01-05','Я благодарю Никиту за помощь.'),
   (6,'Друг','2021-01-06','Катану показал мастер без покупки.'),
  ]
  d.executemany('INSERT INTO messages VALUES(?,?,?,?)',msgs)
  d.execute("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')")
  expected={'0':[1,5], '1':[2], '2':[], '3':[], '4':[3,6]}
  for key,expr in plans.items():
   rows=[x[0] for x in d.execute('''SELECT id FROM messages WHERE id IN
     (SELECT docid FROM messages_fts WHERE messages_fts MATCH ?) ORDER BY id''',(expr,))]
   assert rows==expected[key],f'FTS4 query={key}, got {rows}, expected={expected[key]}; expression={expr}'
   print('PASS plan',key,'matched',rows)
  # FTS must not infer intent from adjacent messages: joined words in one message only.
  d.execute('INSERT INTO messages VALUES (7,?,?,?)',('Я','2021-02-01','Купил кое-что.'))
  d.execute('INSERT INTO messages VALUES (8,?,?,?)',('Я','2021-02-02','Мне нравится эта катана.'))
  d.execute("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')")
  got=[x[0] for x in d.execute('SELECT docid FROM messages_fts WHERE messages_fts MATCH ? ORDER BY docid',(plans['4'],))]
  assert got==[3,6],f'unjustified cross-message inference: {got}'
  print('PASS no cross-message semantic leap (one result is negative wording, labelled for review)')
print('PASS SQLite FTS4 grammar, literal evidence and broad opt-in')
