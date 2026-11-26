package org.openarchive.telegram;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
public class DeepChatContextIntegrationTest {
    @Test public void explicitReplyTargetIsRetrievedEvenFarAway() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="deep_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(c,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            long target=db.stage(sql,"A","2026-01-01 10:00","Да, договорились на пятницу",null,"");
            db.stageTelegramId(sql,target,1001);
            for(int i=0;i<40;i++)db.stage(sql,"A","2026-01-02 10:00","Другой разговор "+i,null,"");
            long answer=db.stage(sql,"B","2026-01-03 10:00","Заберу ноутбук",null,"");
            db.stageTelegramId(sql,answer,1002);db.stageReply(sql,answer,1001);
            db.publishStaged(sql);
            try(android.database.Cursor link=sql.rawQuery("SELECT target_message_id FROM message_replies WHERE message_id=?",new String[]{Long.toString(answer)})){
                assertTrue("Telegram reply must be persisted",link.moveToFirst());
                assertEquals("Reply must resolve to original message",target,link.getLong(0));
            }
            DeepChatContext result=DeepChatContext.read(c.getDatabasePath(DeepChatContext.dbName(key)),"ноутбук",new AtomicBoolean());
            assertTrue("Distant replied-to message missing: "+result.method,result.sources.stream().anyMatch(x->x.id==target));
            assertTrue("Topic search hit missing: "+result.method,result.sources.stream().anyMatch(x->x.id==answer));
            assertTrue("Reply provenance missing: "+result.method,result.method.contains("ответы Telegram"));
            String fast=result.compactPrompt("ноутбук",false);
            assertTrue(DeepChatContext.sentSource(fast,target));
            assertTrue(DeepChatContext.sentSource(fast,answer));
            assertEquals(42,db.totalMessages());
        }finally{db.close();c.deleteDatabase(DeepChatContext.dbName(key));}
    }
    @Test public void retrievalUsesRealFtsAndAdjacentMessagesWithoutMutation(){
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="deep_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(c,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            db.stage(sql,"A","2026-01-01 10:00","Начало",null,"");
            long target=db.stage(sql,"B","2026-01-01 10:01","Купил ноутбук",null,"");
            db.stage(sql,"A","2026-01-01 10:02","Отлично",null,"");
            db.publishStaged(sql);
            DeepChatContext answer=DeepChatContext.read(c.getDatabasePath(DeepChatContext.dbName(key)),"ноутбук",new AtomicBoolean());
            assertEquals(3,answer.total);assertEquals(3,answer.sources.size());
            assertTrue(answer.sources.stream().anyMatch(row->row.id==target));
            assertTrue(answer.prompt("ноутбук").contains("Купил ноутбук"));
            assertEquals(3,db.totalMessages());
        }finally{db.close();c.deleteDatabase(DeepChatContext.dbName(key));}
    }

    @Test public void temporalQuestionGetsBeginningMiddleAndEndWithoutLexicalMatches(){
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="deep_time_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(c,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            long[] ids=new long[101];
            for(int i=0;i<ids.length;i++)ids[i]=db.stage(sql,"QA","2026-01-01","Запись "+i,null,"");
            db.publishStaged(sql);
            DeepChatContext context=DeepChatContext.read(c.getDatabasePath(DeepChatContext.dbName(key)),"Как менялось общение со временем?",new AtomicBoolean());
            for(int i:new int[]{0,25,50,75,100})assertTrue("Timeline sample missing "+i,context.sources.stream().anyMatch(x->x.id==ids[i]));
            assertTrue(context.sources.size()<=DeepChatContext.MAX_SOURCES);
            assertTrue(context.method.contains("порядку импорта"));
            String compact=context.compactPrompt("Как менялось общение со временем?",false);
            for(int i:new int[]{0,50,100})assertTrue("Compact timeline sample missing "+i,DeepChatContext.sentSource(compact,ids[i]));
            assertEquals(101,db.totalMessages());
        }finally{db.close();c.deleteDatabase(DeepChatContext.dbName(key));}
    }
    @Test public void largeRelationshipArchiveKeepsTopicEvidenceAndReportsTimings(){
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="speed_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(c,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            sql.beginTransaction();
            long target=0;
            try{
                for(int i=0;i<10000;i++){
                    long id=db.stage(sql,i%2==0?"А":"Б","2026-02-01 12:00",
                        i==5200?"Я люблю тебя":i==8200?"Прости, я был неправ":"Обычная запись "+i,null,"");
                    if(i==5200)target=id;
                }
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            db.publishStaged(sql);
            long start=android.os.SystemClock.elapsedRealtime();
            DeepChatContext romance=DeepChatContext.read(c.getDatabasePath(DeepChatContext.dbName(key)),"Какие признания в любви встречались?",new AtomicBoolean());
            long romanceMs=android.os.SystemClock.elapsedRealtime()-start;
            assertEquals(10000,romance.total);
            final long expected=target;
            assertTrue("Rare central relationship evidence was missed",romance.sources.stream().anyMatch(x->x.id==expected));
            assertTrue("Fast mode must preserve rare evidence",DeepChatContext.sentSource(romance.compactPrompt("Какие признания в любви встречались?",false),expected));
            start=android.os.SystemClock.elapsedRealtime();
            DeepChatContext behavior=DeepChatContext.read(c.getDatabasePath(DeepChatContext.dbName(key)),"Какие конфликты были в отношениях?",new AtomicBoolean());
            long behaviorMs=android.os.SystemClock.elapsedRealtime()-start;
            assertEquals(10000,behavior.total);assertTrue(behavior.facts.contains("А = 5000"));assertTrue(behavior.facts.contains("Б = 5000"));
            assertTrue(behavior.sources.stream().anyMatch(x->x.body.contains("Прости")));
            assertTrue(romance.sources.size()<=DeepChatContext.MAX_SOURCES);
            assertEquals(10000,db.totalMessages());
            android.util.Log.i("AnalystPerf","10000 rows: romance_ms="+romanceMs+" behavior_ms="+behaviorMs);
        }finally{db.close();c.deleteDatabase(DeepChatContext.dbName(key));}
    }
    @Test public void longMatchingMessagePreservesRelevantExcerpt(){
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="deep_long_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(c,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            String body=String.join("",java.util.Collections.nCopies(300,"Обычный текст. "))+" Ноутбук стоит 54000 рублей.";
            long target=db.stage(sql,"QA","2026-02-01",body,null,"");
            db.publishStaged(sql);
            DeepChatContext context=DeepChatContext.read(c.getDatabasePath(DeepChatContext.dbName(key)),"ноутбук",new AtomicBoolean());
            String prompt=context.compactPrompt("ноутбук",false);
            assertTrue(DeepChatContext.sentSource(prompt,target));
            assertTrue(prompt.contains("54000"));
            assertEquals(1,db.totalMessages());
        }finally{db.close();c.deleteDatabase(DeepChatContext.dbName(key));}
    }
    @Test public void noTopicHitsAreNotTreatedAsEvidence(){
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="deep_none_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(c,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            db.stage(sql,"А","2026-02-01","Мы купили яблоки",null,"");
            db.stage(sql,"Б","2026-02-02","Спасибо",null,"");
            db.publishStaged(sql);
            DeepChatContext none=DeepChatContext.read(c.getDatabasePath(DeepChatContext.dbName(key)),"космодром",new AtomicBoolean());
            assertFalse(none.hasTopicEvidence);
            assertFalse(none.sources.isEmpty());
            DeepChatContext match=DeepChatContext.read(c.getDatabasePath(DeepChatContext.dbName(key)),"яблоки",new AtomicBoolean());
            assertTrue(match.hasTopicEvidence);
            assertFalse(DualAnalystContext.combine("a",match,"b",none).context.hasTopicEvidence);
            assertEquals(2,db.totalMessages());
        }finally{db.close();c.deleteDatabase(DeepChatContext.dbName(key));}
    }
    @Test public void optimizedFactsMatchReferenceScannerOnLargeMessages(){
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="fast_facts_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(c,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            String[] names={"А"," А ","Б","","Неизвестный отправитель"};
            String[] dates={"2026-02-01 12:00","bad","2026-02-03","2026-02-04 20:00","2026-02-02 12:30"};
            String body=String.join("",java.util.Collections.nCopies(300,"Длинный текст. "));
            sql.beginTransaction();
            try{
                for(int i=0;i<1000;i++)db.stage(sql,names[i%5],dates[i%5],body,null,"");
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            db.publishStaged(sql);
            for(String question:new String[]{"Сколько сообщений?","Кто больше всего писал?","Кто меньше всего писал?","Сколько участников?","Сколько дней общались?","Самый активный день?","Самый долгий перерыв?","Сколько сообщений в активный день?","Самый активный час?","Сколько сообщений по выходным?"}){
                ChatQuestionAnalyst.Query q=ChatQuestionAnalyst.parse(question);
                ChatQuestionAnalyst.Accumulator reference=new ChatQuestionAnalyst.Accumulator(q);
                long start=android.os.SystemClock.elapsedRealtime();
                try(android.database.Cursor rows=sql.rawQuery("SELECT id,author,date_text,body FROM messages ORDER BY id",null)){
                    while(rows.moveToNext())reference.add(rows.getLong(0),rows.getString(1),rows.getString(2),rows.getString(3));
                }
                long baseline=android.os.SystemClock.elapsedRealtime()-start;
                start=android.os.SystemClock.elapsedRealtime();
                ChatQuestionAnalyst.Answer fast=ChatQuestionAnalyst.readOnly(c.getDatabasePath(DeepChatContext.dbName(key)),q,new AtomicBoolean());
                long optimized=android.os.SystemClock.elapsedRealtime()-start;
                assertNotNull(fast);
                assertEquals(question,reference.finish().text,fast.text);
                assertEquals(reference.finish().sources.size(),fast.sources.size());
                android.util.Log.i("AnalystPerf","facts "+q.kind+" reference_ms="+baseline+" optimized_ms="+optimized);
            }
            assertNull(ChatQuestionAnalyst.readOnly(c.getDatabasePath(DeepChatContext.dbName(key)),ChatQuestionAnalyst.parse("Сколько сообщений?"),new AtomicBoolean(true)));
            assertEquals(1000,db.totalMessages());
        }finally{db.close();c.deleteDatabase(DeepChatContext.dbName(key));}
    }
}
