package org.openarchive.telegram;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

/** Synthetic rows only. No personal Telegram archive or external network needed. */
@RunWith(AndroidJUnit4.class)
public class DuplicateTelegramIdSafetyTest {
    @Test public void repeatedTelegramIdsNeverResolveToAnArbitraryCopy() {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="dupids_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,key);
        try {
            SQLiteDatabase sql=db.getWritableDatabase();
            db.clearStage(sql);
            long first=db.stage(sql,"A","2026-01-01","First copy",null,"");
            db.stageTelegramId(sql,first,101);
            long second=db.stage(sql,"B","2026-01-02","Second copy",null,"");
            db.stageTelegramId(sql,second,101);
            long third=db.stage(sql,"C","2026-01-03","Third copy",null,"");
            db.stageTelegramId(sql,third,101);
            long unique=db.stage(sql,"D","2026-01-04","Unique",null,"");
            db.stageTelegramId(sql,unique,404);
            long ambiguousReply=db.stage(sql,"E","2026-01-05","Ambiguous reply",null,"");
            db.stageReply(sql,ambiguousReply,101);
            long uniqueReply=db.stage(sql,"F","2026-01-06","Safe reply",null,"");
            db.stageReply(sql,uniqueReply,404);
            db.publishStaged(sql);

            assertEquals(6,db.totalMessages());
            try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM telegram_ids WHERE telegram_id=101",null)) {
                assertTrue(c.moveToFirst()); assertEquals(0,c.getInt(0));
            }
            try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM telegram_ids WHERE telegram_id=404",null)) {
                assertTrue(c.moveToFirst()); assertEquals(1,c.getInt(0));
            }
            List<MessageDb.Item> page=db.page(0,10,"",false,"",false,false);
            Map<Long,MessageDb.ReplyInfo> replies=db.replyPreviews(page);
            assertEquals(2,replies.size());
            assertEquals(-1,replies.get(ambiguousReply).targetId);
            assertEquals(101,replies.get(ambiguousReply).telegramId);
            assertEquals(unique,replies.get(uniqueReply).targetId);
            assertEquals(404,replies.get(uniqueReply).telegramId);
        } finally {
            db.close();
            ctx.deleteDatabase("telegram_"+key+".db");
        }
    }
}
