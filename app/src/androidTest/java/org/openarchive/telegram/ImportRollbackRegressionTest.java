package org.openarchive.telegram;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.Assert.*;

/** Synthetic fault-injection: malformed second HTML part cannot publish half an import. */
@RunWith(AndroidJUnit4.class)
public class ImportRollbackRegressionTest {
    @Test public void badSecondPartPreservesPreviousPublishedMessages() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="importfail_"+UUID.randomUUID().toString().replace("-","");
        File folder=new File(ctx.getCacheDir(),"importfail-"+key);
        assertTrue(folder.mkdirs());
        MessageDb db=new MessageDb(ctx,key);
        try {
            SQLiteDatabase sql=db.getWritableDatabase();
            db.clearStage(sql);
            long existing=db.stage(sql,"Before","2024-01-01","Original persisted message",null,"");
            db.publishStaged(sql);
            db.toggleFavorite(existing);
            write(folder,"messages.html",
                "<div class='history'><div class='message default' id='message5'>"+
                "<div class='body'><div class='text'>Valid but not yet published</div></div>"+
                "</div></div>");
            write(folder,"messages2.html",
                "<html><body><div class='other'>No Telegram messages at all</div></body></html>");
            try {
                TelegramImporter.importFiles(ctx.getContentResolver(),
                    new DocumentFolder(ctx.getContentResolver(),folder),db,unused->{});
                fail("Import must fail if a numbered part contains no Telegram messages");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("HTML"));
            }
            assertEquals(1,db.totalMessages());
            assertEquals(1,db.count("",false,"",false,true));
            try(Cursor c=sql.rawQuery("SELECT body FROM messages WHERE id=?",
                    new String[]{Long.toString(existing)})) {
                assertTrue(c.moveToFirst());
                assertEquals("Original persisted message",c.getString(0));
            }
            try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM messages WHERE body LIKE '%not yet published%'",null)) {
                assertTrue(c.moveToFirst());
                assertEquals(0,c.getInt(0));
            }
        } finally {
            db.close();
            ctx.deleteDatabase("telegram_"+key+".db");
            ArchiveFileImport.deleteTree(folder);
        }
    }
    private static void write(File root,String name,String html) throws Exception {
        try(FileOutputStream out=new FileOutputStream(new File(root,name))) {
            out.write(html.getBytes(StandardCharsets.UTF_8));
        }
    }
}
