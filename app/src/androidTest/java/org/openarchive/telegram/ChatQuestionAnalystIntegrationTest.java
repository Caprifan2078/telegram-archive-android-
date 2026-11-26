package org.openarchive.telegram;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatQuestionAnalystIntegrationTest {
    @Test public void answerReadsActualIndexedMessagesAndDoesNotModifyArchive(){
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="analyst_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            db.stage(sql,"Поздний","2026-02-02 12:00","встреча завтра",null,"");
            long first=db.stage(sql,"Ранний","2026-02-01 11:00","встреча сегодня",null,"");
            db.publishStaged(sql);
            AtomicBoolean cancel=new AtomicBoolean(false);
            ChatQuestionAnalyst.Answer answer=db.askChat(ChatQuestionAnalyst.parse("Кто первый написал?"),cancel);
            assertEquals(first,answer.sources.get(0).id);assertTrue(answer.text.contains("Ранний"));
            assertEquals(2,db.askChat(ChatQuestionAnalyst.parse("Найди «встреча»"),cancel).sources.size());
            assertEquals(2,db.totalMessages());cancel.set(true);
            assertNull(db.askChat(ChatQuestionAnalyst.parse("Сколько сообщений?"),cancel));
            assertEquals(2,db.totalMessages());
        }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");}
    }

    @Test public void everySavedQuestionCardReturnsExpectedFactsFromUnsortedExport(){
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="preset_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            long last=db.stage(sql,"Борис","2026-02-06 12:00","Пока",null,"");
            long first=db.stage(sql,"Анна","2026-02-01 11:00","встреча?",null,"");
            db.stage(sql,"Анна","2026-02-01 13:00","встреча вечером",null,"");
            db.stage(sql,"Анна","2026-02-03 12:00","Хорошо",null,"");
            db.publishStaged(sql);
            AtomicBoolean cancel=new AtomicBoolean(false);
            java.util.Map<ChatQuestionAnalyst.Kind,String> expected=new java.util.EnumMap<>(ChatQuestionAnalyst.Kind.class);
            expected.put(ChatQuestionAnalyst.Kind.FIRST,"Анна");
            expected.put(ChatQuestionAnalyst.Kind.LAST,"Борис");
            expected.put(ChatQuestionAnalyst.Kind.TOTAL,"Всего сообщений: 4");
            expected.put(ChatQuestionAnalyst.Kind.MOST,"Анна");
            expected.put(ChatQuestionAnalyst.Kind.ACTIVE_DAYS,"Дней с сообщениями: 3");
            expected.put(ChatQuestionAnalyst.Kind.BUSIEST_DAY,"Максимум за день: 2");
            expected.put(ChatQuestionAnalyst.Kind.LONGEST_GAP,"2 полных календарных дней");
            expected.put(ChatQuestionAnalyst.Kind.QUESTIONS,"Сообщений со знаком вопроса: 1 из 4");
            expected.put(ChatQuestionAnalyst.Kind.TOPIC,"Сообщений с буквальным упоминанием «встреча»: 2");
            expected.put(ChatQuestionAnalyst.Kind.FIRST_TOPIC,"Анна");
            expected.put(ChatQuestionAnalyst.Kind.LAST_TOPIC,"Анна · 2026-02-01 13:00");
            expected.put(ChatQuestionAnalyst.Kind.PARTICIPANTS,"Различных имён отправителей: 2");
            expected.put(ChatQuestionAnalyst.Kind.LEAST,"Борис — по 1 из 4");
            expected.put(ChatQuestionAnalyst.Kind.TEXT_COUNT,"Сообщений с непустым текстом: 4 из 4");
            expected.put(ChatQuestionAnalyst.Kind.LONGEST_TEXT,"15 символов Unicode");
            expected.put(ChatQuestionAnalyst.Kind.AVG_LENGTH,"8.3 символов Unicode");
            expected.put(ChatQuestionAnalyst.Kind.AVG_DAY,"Среднее в активный день: 1.3");
            expected.put(ChatQuestionAnalyst.Kind.PEAK_HOUR,"Часы: 12:00");
            expected.put(ChatQuestionAnalyst.Kind.WEEKEND,"Сообщений по субботам и воскресеньям: 2");
            int count=0;
            for(String[] group:ChatQuestionAnalyst.PRESETS)for(int i=1;i<group.length;i++){
                ChatQuestionAnalyst.Query query=ChatQuestionAnalyst.parse(group[i]);
                assertTrue("Unknown saved preset: "+group[i],expected.containsKey(query.kind));
                ChatQuestionAnalyst.Answer answer=db.askChat(query,cancel);
                assertNotNull(answer);
                assertTrue(ChatQuestionAnalyst.supports(group[i]));
                ChatQuestionAnalyst.Answer exact=ChatQuestionAnalyst.readOnly(ctx.getDatabasePath("telegram_"+key+".db"),query,cancel);
                assertNotNull(exact);assertEquals(answer.text,exact.text);
                assertTrue(group[i]+": "+answer.text,answer.text.contains(expected.get(query.kind)));
                if(query.kind==ChatQuestionAnalyst.Kind.FIRST||query.kind==ChatQuestionAnalyst.Kind.FIRST_TOPIC)
                    assertEquals(first,answer.sources.get(0).id);
                if(query.kind==ChatQuestionAnalyst.Kind.LAST)assertEquals(last,answer.sources.get(0).id);
                count++;
            }
            assertEquals(19,count);
            assertFalse(ChatQuestionAnalyst.supports("Как менялось отношение участников?"));
            cancel.set(true);
            assertNull(ChatQuestionAnalyst.readOnly(ctx.getDatabasePath("telegram_"+key+".db"),ChatQuestionAnalyst.parse("Сколько сообщений?"),cancel));
            assertEquals(4,db.totalMessages());
        }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");}
    }
}
