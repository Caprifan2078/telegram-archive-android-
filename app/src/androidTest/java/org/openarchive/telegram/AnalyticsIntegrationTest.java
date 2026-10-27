package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

/** Android/SQLite integration using synthetic, disposable records only. */
@RunWith(AndroidJUnit4.class)
public class AnalyticsIntegrationTest {
    @Test public void threePersonChatCountsDistinctAttachmentsWithoutChangingMessages()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            sql.beginTransaction();
            try{
                long a=db.stage(sql,"Аня","24.10.2026 10:00","Привет, вот фотографии",null,"");
                db.stageAttachment(sql,a,"photos/photo1.jpg","image");
                db.stageAttachment(sql,a,"photos/photo2.jpg","image");
                long b=db.stage(sql,"Борис","2026-10-24 10:01","Привет",null,"");
                db.stageAttachment(sql,b,"voice_messages/voice.ogg","voice");
                long c=db.stage(sql,"АНЯ","2026-10-24 10:02","Кружок",null,"");
                db.stageAttachment(sql,c,"round_video/circle.mp4","round");
                db.stage(sql,"Вика","2026-10-24 10:03","Спасибо!",null,"");
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            db.publishStaged(sql);
            int before=db.totalMessages();
            ChatAnalytics.Snapshot r=db.analytics();
            assertEquals(4,r.totalMessages);assertEquals(3,r.people.size());
            assertEquals(2,r.people.get(0).messages);
            assertEquals(4,r.totalMedia);
            assertEquals(2,r.type("image"));assertEquals(1,r.type("voice"));assertEquals(1,r.type("round"));
            assertEquals(4,r.days.get("2026-10-24").longValue());
            assertEquals(before,db.totalMessages()); // Analytics has no write side effects.
            assertEquals("Привет",db.page(1,1,"",false,"",false,false).get(0).body);
        }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");}
    }
    @Test public void sumsRemainConsistentWithDuplicateIdsUnknownDatesAndMultipleMedia(){
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            long a=db.stage(sql,"Alice","2024-02-29 23:30:00 UTC+03:00","alpha beta",null,"");
            db.stageAttachment(sql,a,"one.jpg","image");db.stageAttachment(sql,a,"two.jpg","image");
            long b=db.stage(sql,"Bob","2024-03-01 00:30:00 UTC-04:00","ёж",null,"");
            db.stageAttachment(sql,b,"voice.ogg","voice");
            db.stageTelegramId(sql,a,501);db.stageTelegramId(sql,b,501);
            db.stage(sql,"Системное сообщение","2024-02-30 10:00","system event",null,"");
            db.stage(sql,"Alice","2024-03-01","👩‍🔬",null,"");
            db.stage(sql,"Bob","","123",null,"");
            db.publishStaged(sql);
            ChatAnalytics.Snapshot r=db.analytics();
            assertEquals(5,r.totalMessages);assertEquals(6,r.totalWords);assertEquals(3,r.totalMedia);
            long personMessages=0,personWords=0,personMedia=0;
            for(ChatAnalytics.Person p:r.people){personMessages+=p.messages;personWords+=p.words;personMedia+=p.attachments;}
            assertEquals(r.totalMessages,personMessages);assertEquals(r.totalWords,personWords);assertEquals(r.totalMedia,personMedia);
            long categories=0;for(long count:r.types.values())categories+=count;assertEquals(r.totalMedia,categories);
            ChatTrends.Activity all=r.trends.all;
            assertEquals(3,all.validDate);assertEquals(2,all.unknownDate);
            assertEquals(2,all.validHour);assertEquals(3,all.unknownHour);
            assertEquals(1,all.hours[23]);assertEquals(1,all.hours[0]); // wall times from export, no timezone conversion
            assertEquals(1,r.days.get("2024-02-29").longValue());assertEquals(2,r.days.get("2024-03-01").longValue());
            assertEquals(100.0,ChatAnalytics.percent(personMessages,r.totalMessages),0.0001);
            assertEquals(0.0,ChatAnalytics.percent(0,0),0.0);
            try(android.database.Cursor c=sql.rawQuery("SELECT COUNT(*) FROM telegram_ids WHERE telegram_id=501",null)){
                assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0));
            }
            assertEquals(5,db.totalMessages());
        }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");}
    }
    @Test public void emptyArchiveHasNoPhantomAnalytics(){
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");MessageDb db=new MessageDb(ctx,key);
        try{
            ChatAnalytics.Snapshot r=db.analytics();
            assertEquals(0,r.totalMessages);assertEquals(0,r.totalWords);assertEquals(0,r.totalMedia);
            assertTrue(r.people.isEmpty());assertTrue(r.days.isEmpty());assertEquals(0,r.trends.all.validDate);
        }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");}
    }
}
