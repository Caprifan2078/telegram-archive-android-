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
            DeepChatContext result=DeepChatContext.read(c.getDatabasePath(DeepChatContext.dbName(key)),"ноутбук",new AtomicBoolean());
            assertTrue(result.sources.stream().anyMatch(x->x.id==target));
            assertTrue(result.sources.stream().anyMatch(x->x.id==answer));
            assertTrue(result.method.contains("ответы Telegram"));
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
}
