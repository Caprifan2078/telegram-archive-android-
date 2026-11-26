package org.openarchive.telegram;
import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
@RunWith(AndroidJUnit4.class)
public class External7ZipRegressionTest {
    @Test public void realSevenZipArchivesPromptRejectAndOpen()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context test=InstrumentationRegistry.getInstrumentation().getContext();
        for(String name:new String[]{"aes256.zip","zipcrypto.zip","headers.7z","aes256-spaces.zip","unicode.7z"}){
            File file=File.createTempFile("external7zip-",name.endsWith(".7z")?".7z":".zip",ctx.getCacheDir());
            String format=name.endsWith(".7z")?"7z":"zip";
            String key=UUID.randomUUID().toString().replace("-","");
            File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
            try{
                try(InputStream in=test.getAssets().open("external7zip/"+name);OutputStream out=new FileOutputStream(file)){
                    byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                }
                byte[] before=java.nio.file.Files.readAllBytes(file.toPath());
                Uri uri=Uri.fromFile(file);
                assertEquals(format,ArchiveFileImport.detectFormat(ctx,uri,name));
                try{ArchiveFileImport.extract(ctx,uri,format,key,null,p->{});fail(name+" missing password");}
                catch(ArchiveFileImport.PasswordRequired expected){}
                assertFalse(root.exists());
                boolean rejected=false;
                try{ArchiveFileImport.extract(ctx,uri,format,key,"wrong-password".toCharArray(),p->{});}
                catch(Exception expected){rejected=true;}
                assertTrue(name+" wrong password accepted",rejected);assertFalse(root.exists());
                char[] secret=(name.equals("unicode.7z")?" пробел-Ё-123 ! ":name.equals("aes256-spaces.zip")?" Space-123 ! ":"qa-7zip-password").toCharArray();
                File extracted;
                try{extracted=ArchiveFileImport.extract(ctx,uri,format,key,secret,p->{});}finally{java.util.Arrays.fill(secret,'\0');}
                assertTrue(new File(extracted,"messages.html").isFile());
                assertTrue(new String(java.nio.file.Files.readAllBytes(new File(extracted,"messages.html").toPath()),StandardCharsets.UTF_8).contains("External 7zip synthetic message"));
                MessageDb db=new MessageDb(ctx,key);
                try{TelegramImporter.importFiles(ctx.getContentResolver(),new DocumentFolder(ctx.getContentResolver(),extracted),db,p->{});assertTrue(db.totalMessages()>0);}finally{db.close();}
                assertArrayEquals(before,java.nio.file.Files.readAllBytes(file.toPath()));
            }finally{ArchiveFileImport.deleteTree(root);ctx.deleteDatabase("telegram_"+key+".db");file.delete();}
        }
    }
}
