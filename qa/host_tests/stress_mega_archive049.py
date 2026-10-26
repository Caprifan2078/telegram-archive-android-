#!/usr/bin/env python3
"""Synthetic million-message FTS4/recovery stress, no actual Telegram content or gigabytes of media.

This is a HOST SQLite benchmark: it does not replace Android low-RAM or Jsoup instrumentation.
Media total is logical metadata only. HTML source partitions are simulated with part numbers.
"""
import argparse, os, sqlite3, tempfile, time, resource, json, pathlib, random
p=argparse.ArgumentParser()
p.add_argument('--messages',type=int,default=1000000)
p.add_argument('--parts',type=int,default=100)
p.add_argument('--media-gb',type=int,default=6)
a=p.parse_args()
assert 0<a.messages<=2000000 and 0<a.parts<=1000 and 0<=a.media_gb<=30
start=time.monotonic(); fd,path=tempfile.mkstemp(prefix='ta049-stress-',suffix='.sqlite');os.close(fd)
c=None
try:
 c=sqlite3.connect(path); c.executescript('''PRAGMA journal_mode=DELETE;
 PRAGMA synchronous=NORMAL;
 CREATE TABLE messages(id INTEGER PRIMARY KEY, source_part INT NOT NULL, author TEXT, date_text TEXT, body TEXT);
 CREATE TABLE attachments(message_id INTEGER, path TEXT, kind TEXT, logical_bytes INT);
 CREATE INDEX ix_attach_message ON attachments(message_id);
 CREATE VIRTUAL TABLE messages_fts USING fts4(author,body,date_text,content='messages',tokenize=unicode61);
 ''')
 print(f'BEGIN messages={a.messages:,} simulated HTML parts={a.parts}, logical media={a.media_gb} GiB',flush=True)
 def rows():
  for i in range(1,a.messages+1):
   part=min(a.parts,(i-1)*a.parts//a.messages+1)
   if i==1:body='Никита, спасибо за помощь, мне было очень плохо'
   elif i==a.messages:body='Я купил катану и книгу, вот наш последний день'
   elif i%16001==0:body='Мы обсуждали алгоритм сортировки, искусственный интеллект и компьютер'
   elif i%7999==0:body='Нужен ремонт машины, не покупаю ничего сегодня'
   else:body=f'Обычное письмо о встрече и планах за этот день номер {i%20480} число'
   yield i,part,'Никита' if i%2 else 'Друг',f'{2010+i%17:04d}-08-23',body
 cur=c.cursor();batch=[]
 for row in rows():
  batch.append(row)
  if len(batch)>=10000:
   cur.executemany('INSERT INTO messages VALUES(?,?,?,?,?)',batch);batch.clear()
 if batch:cur.executemany('INSERT INTO messages VALUES(?,?,?,?,?)',batch)
 # Synthetic metadata only: do not allocate 6GiB of actual files or claim media decoding.
 media_rows= min(200000,max(1,a.messages//4))
 logical_total=a.media_gb*1024**3
 cur.executemany('INSERT INTO attachments VALUES(?,?,?,?)',
   ((1+(i*a.messages)//media_rows,f'videos/part_{i%a.parts+1}/item_{i:06d}.mp4',
    'video' if i%3==0 else 'image',logical_total//media_rows) for i in range(media_rows)))
 c.commit();insert_seconds=time.monotonic()-start
 print(f'LOAD: {insert_seconds:.2f}s, {a.messages:,} messages; media references={media_rows:,}',flush=True)
 t=time.monotonic();c.execute("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')");c.commit();fts_secs=time.monotonic()-t
 print(f'FTS rebuilt in {fts_secs:.2f}s',flush=True)
 t=time.monotonic()
 results={}
 for name,q in [
  ('oldest_gratitude','(body:спасибо OR body:благодар*) AND body:никита'),
  ('newest_sword','body:катану AND (body:купил* OR body:покупа*)'),
  ('middle_computer','body:компьютер* AND body:алгоритм*'),
  ('impossible','body:стегозавр AND body:невероятнокрасивая')]:
  hits=c.execute('SELECT docid FROM messages_fts WHERE messages_fts MATCH ? ORDER BY docid LIMIT 30',(q,)).fetchall()
  results[name]=[x[0] for x in hits]
 assert results['oldest_gratitude']==[1],results
 assert results['newest_sword']==[a.messages],results
 assert results['middle_computer'] and not results['impossible'],results
 query_secs=time.monotonic()-t
 print(f'Queries in {query_secs:.3f}s',flush=True)
 assert c.execute('SELECT COUNT(*),MIN(id),MAX(id) FROM messages').fetchone()==(a.messages,1,a.messages)
 print('count/min/max IDs passed',flush=True)
 assert c.execute('SELECT MIN(source_part),MAX(source_part),COUNT(DISTINCT source_part) FROM messages').fetchone()==(1,a.parts,a.parts)
 print('all 100 parts passed',flush=True)
 assert c.execute('SELECT COUNT(*) FROM attachments').fetchone()[0]==media_rows
 print('media metadata count passed',flush=True)
 # Simulated interrupted transaction must not leave partially imported records.
 first_count=c.execute('SELECT COUNT(*) FROM messages').fetchone()[0]
 try:
  c.execute('BEGIN');c.execute('INSERT INTO messages VALUES(?,?,?,?,?)',(a.messages+1,a.parts,'X','2026-10-01','partial'))
  raise OSError('synthetic interruption')
 except OSError:c.rollback()
 assert c.execute('SELECT COUNT(*) FROM messages').fetchone()[0]==first_count
 print('atomic rollback passed',flush=True)
 if a.messages <=500000:
  assert c.execute('PRAGMA quick_check').fetchone()[0]=='ok'
  print('SQLite quick_check passed',flush=True)
 else:
  print('SQLite deep integrity skipped at 1M due excessive runtime; dedicated check required',flush=True)
 print('Integrity/rollback passed',flush=True)
 c.execute('CREATE VIRTUAL TABLE temp.search_vocab USING fts4aux(main,messages_fts)')
 typo=c.execute("SELECT term FROM temp.search_vocab WHERE col='*' AND term >= 'пл' AND term<'пм' LIMIT 650").fetchall()
 db_bytes=os.path.getsize(path)
 max_rss_kb=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
 result={'result':'PASS','messages':a.messages,'parts':a.parts,'logical_media_gib':a.media_gb,
  'media_metadata_rows':media_rows,'real_media_decode_tested':False,'html_parser_tested':False,
  'insert_seconds':round(insert_seconds,2),'rebuild_fts_seconds':round(fts_secs,2),
  'search_seconds':round(query_secs,3),'db_mib':round(db_bytes/1024**2,1),
  'max_host_process_rss_mib':round(max_rss_kb/1024,1),
  'tests':['oldest_search','newest_search','middle_search','no_false_hit','part_count','no_duplicate_ids','media_references','atomic_rollback','sqlite_integrity' if a.messages<=500000 else 'integrity_skipped_at_1M','fts4aux']}
 print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
 out=pathlib.Path(__file__).resolve().parents[2]/'MEGA_STRESS_RESULTS.json'
 out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
finally:
 if c is not None:c.close()
 try:os.remove(path)
 except OSError:pass
