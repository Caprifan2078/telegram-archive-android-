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
    @Test public void duplicateAcrossNumberedHtmlPartsKeepsRepliesUnresolved() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="duphtml_"+UUID.randomUUID().toString().replace("-","");
        java.io.File folder=new java.io.File(ctx.getCacheDir(),"dup-html-"+key);
        assertTrue(folder.mkdirs());
        try {
            write(folder,"messages.html",
                "<div class='history'>"+
                "<div class='message default' id='message101'><div class='body'><div class='text'>First source</div></div></div>"+
                "<div class='message default' id='message202'><div class='body'><div class='reply_to'><a href='messages2.html#go_to_message101'>Reply</a></div><div class='text'>Ambiguous target</div></div></div>"+
                "</div>");
            write(folder,"messages2.html",
                "<div class='history'>"+
                "<div class='message default' id='message101'><div class='body'><div class='text'>Duplicate source</div></div></div>"+
                "<div class='message default' id='message404'><div class='body'><div class='text'>Unique source</div></div></div>"+
                "<div class='message default' id='message505'><div class='body'><div class='reply_to'><a href='#go_to_message404'>Reply</a></div><div class='text'>Unambiguous target</div></div></div>"+
                "</div>");
            MessageDb db=new MessageDb(ctx,key);
            try {
                assertEquals(5,TelegramImporter.importFiles(ctx.getContentResolver(),
                    new DocumentFolder(ctx.getContentResolver(),folder),db,ignored->{}));
                List<MessageDb.Item> page=db.page(0,10,"",false,"",false,false);
                assertEquals(5,page.size());
                Map<Long,MessageDb.ReplyInfo> found=db.replyPreviews(page);
                assertEquals(2,found.size());
                // Telegram HTML part ordering: reply message is the second row.
                long ambiguous=page.stream().filter(it->"Ambiguous target".equals(it.body))
                    .findFirst().get().id;
                long unique=page.stream().filter(it->"Unique source".equals(it.body))
                    .findFirst().get().id;
                long safeReply=page.stream().filter(it->"Unambiguous target".equals(it.body))
                    .findFirst().get().id;
                assertEquals(-1,found.get(ambiguous).targetId);
                assertEquals(101,found.get(ambiguous).telegramId);
                assertEquals(unique,found.get(safeReply).targetId);
            } finally {
                db.close();
                ctx.deleteDatabase("telegram_"+key+".db");
            }
        } finally {
            ArchiveFileImport.deleteTree(folder);
        }
    }

    private static void write(java.io.File dir,String name,String html) throws Exception {
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))) {
            out.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

}
