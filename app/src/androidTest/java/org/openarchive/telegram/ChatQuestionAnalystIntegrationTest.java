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
}
