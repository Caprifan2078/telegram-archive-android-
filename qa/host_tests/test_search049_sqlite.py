#!/usr/bin/env python3
"""Test the Java fuzzy policy and real SQLite FTS4aux terms, with synthetic data."""
import sqlite3, subprocess, tempfile, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
MAIN=ROOT/'app/src/main/java/org/openarchive/telegram'
with tempfile.TemporaryDirectory(prefix='ta049_javac_') as build:
    tester=pathlib.Path(build)/'Search049Host.java'
    tester.write_text('''package org.openarchive.telegram;
import java.util.*;
public class Search049Host {
 public static void main(String[] args){
  if(!FuzzyWordPolicy.near("плахо","плохо"))throw new AssertionError("typo substitution");
  if(!FuzzyWordPolicy.near("плхо","плохо"))throw new AssertionError("typo omission");
  if(FuzzyWordPolicy.near("комп","кома"))throw new AssertionError("short false positive");
  if(FuzzyWordPolicy.near("плахо","страница"))throw new AssertionError("unrelated words");
  if(!FuzzyWordPolicy.near("похло","похол"))throw new AssertionError("transpose");
  List<String> suggestions=FuzzyWordPolicy.suggest("плахо",Arrays.asList("плохо","плохо","плоды","плата"));
  if(!suggestions.equals(Arrays.asList("плохо")))throw new AssertionError("untrusted candidates " + suggestions);
  Map<String,List<String>> hints=new LinkedHashMap<>();hints.put("плахо",suggestions);
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("плахо",true,false,hints);
  if(!p.fts.contains("body:плохо")||!p.sourceMatch("Мне плохо").contains("исправление опечатки"))
    throw new AssertionError("FTS or explanation");
  if(OfflineSynonymSearch.make("плахо",true,false).fts.contains("body:плохо"))
    throw new AssertionError("opt-in required");
  String[] samples={"стихотворение","алгоритм","контракт","автобус","нейросеть","программирование","инвестиция","прощение","телефон"};
  for(String s:samples)if(!OfflineSynonymSearch.make(s,true).fts.contains(" OR "))
    throw new AssertionError("dictionary missed "+s);
  System.out.println("PASS Java fuzzy index policy, privacy opt-in, thematic dictionary (16 assertions)");
 }
}''',encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',build,str(MAIN/'FuzzyWordPolicy.java'),str(MAIN/'OfflineSynonymSearch.java'),str(tester)],check=True)
    subprocess.run(['java','-cp',build,'org.openarchive.telegram.Search049Host'],check=True)
    with sqlite3.connect(':memory:') as c:
        c.executescript('''CREATE TABLE messages(id INTEGER PRIMARY KEY,body TEXT);
        CREATE VIRTUAL TABLE messages_fts USING fts4(body,content='messages',tokenize=unicode61);''')
        c.executemany('INSERT INTO messages VALUES(?,?)',[(1,'Мне плохо'),(2,'У нас компания'),(3,'Спасибо, Никита'),(4,'Стихотворение и алгоритм')])
        c.execute("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')")
        c.execute('CREATE VIRTUAL TABLE temp.vocab USING fts4aux(main,messages_fts)')
        found=[a for a, in c.execute("SELECT term FROM temp.vocab WHERE col='*' AND term>=? AND term<? ORDER BY term LIMIT 650",('пл','пм'))]
        assert 'плохо' in found,found
        assert [i for i, in c.execute('SELECT docid FROM messages_fts WHERE messages_fts MATCH ?',('body:плохо',))]==[1]
        assert [i for i, in c.execute('SELECT docid FROM messages_fts WHERE messages_fts MATCH ?',('body:комп',))]==[]
        print('PASS SQLite FTS4aux terms, indexed typo match, no unrelated match')
