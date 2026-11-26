package org.openarchive.telegram;
import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.*;
import static org.junit.Assert.*;
public class ArchiveImportPathRegressionTest {
    @Test public void realZipWithWindowsAndDotPathsImportsFromContentProvider()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            zip.putNextEntry(new ZipEntry("./"));zip.closeEntry();
            zip.putNextEntry(new ZipEntry(".\\Chat\\messages.html"));
            zip.write("<div class='history'><div class='message default' id='message1'><div class='body'><div class='from_name'>Synthetic</div><div class='text'>Windows path fixture</div></div></div></div>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry(".\\Chat\\photos\\picture.txt"));zip.write(new byte[]{1,2,3});zip.closeEntry();
        }
        String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING);
        Uri uri=Uri.parse("content://"+ArchivePipeTestProvider.AUTHORITY+"/fixture/"+encoded);
        String key=UUID.randomUUID().toString().replace("-","");
        File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try{
            File chat=ArchiveFileImport.extract(ctx,uri,"zip",key,null,p->{});
            assertTrue(new File(chat,"messages.html").isFile());
            assertArrayEquals(new byte[]{1,2,3},java.nio.file.Files.readAllBytes(new File(chat,"photos/picture.txt").toPath()));
            MessageDb db=new MessageDb(ctx,key);
            try{assertEquals(1,TelegramImporter.importFiles(ctx.getContentResolver(),new DocumentFolder(ctx.getContentResolver(),chat),db,p->{}));}
            finally{db.close();}
        }finally{ArchiveFileImport.deleteTree(root);ctx.deleteDatabase("telegram_"+key+".db");}
    }
}
