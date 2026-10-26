#!/usr/bin/env python3
"""Synthetic-only source ID -> reply linkage, strict Java URL handling and migration safety."""
import sqlite3,subprocess,tempfile,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='ta050_reply_') as temp:
    folder=pathlib.Path(temp)
    test=folder/'Reply050Host.java'
    test.write_text('''package org.openarchive.telegram;
public class Reply050Host {
 public static void main(String[] a){
  eq(101,TelegramReplyPolicy.sourceMessageId("message101"));
  eq(-1,TelegramReplyPolicy.sourceMessageId("message0"));
  eq(-1,TelegramReplyPolicy.sourceMessageId("message9223372036854775808"));
  eq(303,TelegramReplyPolicy.replyTargetFromHref("messages2.html#go_to_message303"));
  eq(101,TelegramReplyPolicy.replyTargetFromHref("#message101"));
  eq(-1,TelegramReplyPolicy.replyTargetFromHref("https://evil.test/#go_to_message303"));
  eq(-1,TelegramReplyPolicy.replyTargetFromHref("../messages2.html#message303"));
  eq(-1,TelegramReplyPolicy.replyTargetFromHref("#go_to_message303#x"));
  System.out.println("PASS strict Telegram anchor rules");
 }
 static void eq(long a,long b){if(a!=b)throw new AssertionError(a+" != "+b);}
}''')
    subprocess.run(['javac','-encoding','UTF-8','-d',temp,str(ROOT/'app/src/main/java/org/openarchive/telegram/TelegramReplyPolicy.java'),str(test)],check=True)
    subprocess.run(['java','-cp',temp,'org.openarchive.telegram.Reply050Host'],check=True)
    c=sqlite3.connect(':memory:')
    c.executescript('''CREATE TABLE messages(id INTEGER PRIMARY KEY,body TEXT);CREATE TABLE staged_telegram_ids(message_id INTEGER PRIMARY KEY,telegram_id INTEGER NOT NULL UNIQUE);CREATE TABLE staged_reply_ids(message_id INTEGER PRIMARY KEY,target_telegram_id INTEGER NOT NULL);CREATE TABLE telegram_ids(message_id INTEGER PRIMARY KEY,telegram_id INTEGER NOT NULL UNIQUE);CREATE TABLE message_replies(message_id INTEGER PRIMARY KEY,target_telegram_id INTEGER NOT NULL,target_message_id INTEGER);''')
    c.executemany('INSERT INTO messages VALUES(?,?)',[(1,'FIRST'),(2,'REPLY'),(3,'LATER'),(4,'MISSING')]);
    c.executemany('INSERT INTO staged_telegram_ids VALUES(?,?)',[(1,101),(2,202),(3,303),(4,404)]);
    c.executemany('INSERT INTO staged_reply_ids VALUES(?,?)',[(2,303),(4,999)]);
    c.execute('INSERT INTO telegram_ids SELECT message_id,telegram_id FROM staged_telegram_ids');
    c.execute('''INSERT INTO message_replies(message_id,target_telegram_id,target_message_id) SELECT r.message_id,r.target_telegram_id,t.message_id FROM staged_reply_ids r LEFT JOIN telegram_ids t ON t.telegram_id=r.target_telegram_id''');
    assert c.execute('SELECT target_message_id FROM message_replies WHERE message_id=2').fetchone()==(3,)
    assert c.execute('SELECT target_message_id FROM message_replies WHERE message_id=4').fetchone()==(None,)
    before=c.execute('SELECT COUNT(*) FROM messages').fetchone()[0]
    c.commit()
    try:
        c.execute('BEGIN');c.execute("INSERT INTO messages VALUES(5,'partial')");raise IOError('simulate failure')
    except IOError:c.rollback()
    assert c.execute('SELECT COUNT(*) FROM messages').fetchone()[0]==before
    assert c.execute('PRAGMA quick_check').fetchone()[0]=='ok'
    print('PASS cross-file reply joins, unresolved target, rollback, database quick_check')
