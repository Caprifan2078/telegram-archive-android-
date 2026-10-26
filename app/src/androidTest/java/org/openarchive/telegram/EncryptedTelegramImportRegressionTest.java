package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.Assert.*;

/** Real Android tests of the password-aware Telegram ZIP import, not merely preview. */
@RunWith(AndroidJUnit4.class)
public class EncryptedTelegramImportRegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private String nextKey(){return UUID.randomUUID().toString().replace("-","");}
    private File createArchive(File plain,String content)throws Exception{
        File zip=File.createTempFile("ta-zip-import-", ".zip",ctx.getCacheDir());
        try(FileOutputStream out=new FileOutputStream(plain)){
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        char[] password="archive-test-key-123".toCharArray();
        try(ZipFile writer=new ZipFile(zip,password)){
            ZipParameters p=new ZipParameters();
            p.setFileNameInZip("messages.html");
            p.setEncryptFiles(true);
            p.setEncryptionMethod(EncryptionMethod.AES);
            p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            writer.addFile(plain,p);
        }
        return zip;
    }
    @Test public void encryptedZipImportsTelegramHtmlWithCorrectPassword()throws Exception{
        File plain=File.createTempFile("ta-messages-", ".html",ctx.getCacheDir());
        File zip=createArchive(plain,"<html><body>Synthetic Telegram export</body></html>");
        String key=nextKey();
        File created=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try{
            File root=ArchiveFileImport.extract(ctx,Uri.fromFile(zip),"zip",key,
                "archive-test-key-123".toCharArray(),message->{});
            assertTrue(root.isDirectory());
            File html=new File(root,"messages.html");
            assertTrue("Correct password should recover messages.html",html.isFile());
            try(FileInputStream in=new FileInputStream(html)){
                byte[] bytes=new byte[(int)html.length()];
                int read=in.read(bytes);
                assertEquals(bytes.length,read);
                assertTrue(new String(bytes,StandardCharsets.UTF_8).contains("Synthetic Telegram"));
            }
        }finally{
            ArchiveFileImport.deleteTree(created);zip.delete();plain.delete();
        }
    }
    @Test public void wrongZipPasswordDoesNotLeaveIncompleteImport()throws Exception{
        File plain=File.createTempFile("ta-messages-", ".html",ctx.getCacheDir());
        File zip=createArchive(plain,"<html>Do not disclose on wrong password</html>");
        String key=nextKey();
        File created=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try{
            try{
                ArchiveFileImport.extract(ctx,Uri.fromFile(zip),"zip",key,
                    "wrong-key-999".toCharArray(),message->{});
                fail("Wrong AES password must fail the import");
            }catch(Exception expected){
                assertFalse("Failed import must roll back staged files",created.exists());
            }
        }finally{
            ArchiveFileImport.deleteTree(created);zip.delete();plain.delete();
        }
    }
}
