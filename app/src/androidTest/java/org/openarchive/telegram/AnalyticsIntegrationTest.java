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
}
