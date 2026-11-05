package org.openarchive.telegram;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ArchiveEndToEndRegressionTest {
    private static void entry(ZipOutputStream zip,String name,byte[] data)throws Exception {
        zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry();
    }
    private static byte[] html(String id,String body,String extra) {
        return ("<html><body><div class='history'><div class='message default' id='message"+id+
            "'><div class='body'><div class='from_name'>Synthetic</div>"+
            "<div class='date details' title='2024-02-29 12:00:00'></div>"+
            "<div class='text'>"+body+"</div>"+extra+"</div></div></div></body></html>")
            .getBytes(StandardCharsets.UTF_8);
    }
    @Test public void zippedMultipartChatImportsSearchesAndResolvesAttachment()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");
        File zipFile=new File(ctx.getCacheDir(),"e2e-"+key+".zip");
        File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        MessageDb db=new MessageDb(ctx,"e2e_"+key);
        try {
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(zipFile))) {
                entry(zip,"chat/messages.html",html("1","first synthetic",""));
                entry(zip,"chat/messages2.html",html("2","uniqueneedle",
                    "<div class='photo_wrap'><a href='photos/item.jpg'>photo</a></div>"));
                entry(zip,"chat/photos/item.jpg",new byte[]{1,2,3,4});
            }
            File chat=ArchiveFileImport.extract(ctx,Uri.fromFile(zipFile),"zip",key,ignored->{});
            DocumentFolder folder=new DocumentFolder(ctx.getContentResolver(),chat);
            assertEquals(2,TelegramImporter.importFiles(ctx.getContentResolver(),folder,db,ignored->{}));
            assertEquals(2,db.totalMessages());
            assertEquals(1,db.count("uniqueneedle",false,"",false,false));
            assertNotNull(folder.resolve("photos/item.jpg"));
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT path FROM attachments",null)) {
                assertEquals(1,c.getCount());assertTrue(c.moveToFirst());assertEquals("photos/item.jpg",c.getString(0));
            }
        } finally {
            db.close();ctx.deleteDatabase("telegram_e2e_"+key+".db");
            ArchiveFileImport.deleteTree(root);zipFile.delete();
        }
    }
}
