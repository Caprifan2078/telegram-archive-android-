package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Password prompts must follow real encryption, not an archive's extension/name. */
@RunWith(AndroidJUnit4.class)
public final class ArchivePasswordOnDemandRegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final String HTML="<div class='history'><div class='message default' id='message101'><div class='body'><div class='from_name'>Synthetic</div><div class='text'>Encrypted archive message</div></div></div></div>";
    private String key(){return UUID.randomUUID().toString().replace("-","");}
    private File root(String id){return new File(new File(ctx.getFilesDir(),"telegram_imports"),id);}
    private File seven(char[] password)throws Exception{
        File source=File.createTempFile("telegram-password-fixture-", ".html",ctx.getCacheDir());
        File output=File.createTempFile("telegram-password-fixture-", ".7z",ctx.getCacheDir());
        try{
            try(FileOutputStream out=new FileOutputStream(source)){
                out.write(HTML.getBytes(StandardCharsets.UTF_8));
            }
            try(SevenZOutputFile archive=password==null?
                    new SevenZOutputFile(output):new SevenZOutputFile(output,password)){
                SevenZArchiveEntry entry=archive.createArchiveEntry(source,"TelegramExport/messages.html");
                archive.putArchiveEntry(entry);
                archive.write(HTML.getBytes(StandardCharsets.UTF_8));
                archive.closeArchiveEntry();archive.finish();
            }
            return output;
        }catch(Exception error){output.delete();throw error;}finally{source.delete();}
    }
    private File zip(char[] password)throws Exception{
        File plain=File.createTempFile("telegram-password-fixture-", ".html",ctx.getCacheDir());
        File output=File.createTempFile("telegram-password-fixture-", ".zip",ctx.getCacheDir());
        try{
            try(FileOutputStream out=new FileOutputStream(plain)){
                out.write(HTML.getBytes(StandardCharsets.UTF_8));
            }
            try(ZipFile archive=new ZipFile(output,password)){
                ZipParameters params=new ZipParameters();
                params.setFileNameInZip("messages.html");
                params.setEncryptFiles(password!=null);
                if(password!=null){
                    params.setEncryptionMethod(EncryptionMethod.AES);
                    params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                }
                archive.addFile(plain,params);
            }
            return output;
        }catch(Exception error){output.delete();throw error;}finally{plain.delete();}
    }
    @Test public void detectRealSevenZEvenWhenPickerCallsItZip()throws Exception{
        File file=seven(null);
        try{assertEquals("7z",ArchiveFileImport.detectFormat(ctx,Uri.fromFile(file),"random.zip"));}
        finally{file.delete();}
    }
    @Test public void detectRealZipEvenWhenPickerCallsItSevenZ()throws Exception{
        File file=zip(null);
        try{assertEquals("zip",ArchiveFileImport.detectFormat(ctx,Uri.fromFile(file),"random.7z"));}
        finally{file.delete();}
    }
    @Test public void encryptedSevenZRequiresPasswordBeforeIndexingAndCanBeRetried()throws Exception{
        char[] password="synthetic-secret-7z".toCharArray();
        File file=seven(password);String attempted=key(),correct=key();
        try{
            try{ArchiveFileImport.extract(ctx,Uri.fromFile(file),"7z",attempted,null,p->{});fail("Must ask for encrypted 7z password");}
            catch(ArchiveFileImport.PasswordRequired expected){assertTrue(expected.getMessage().contains("парол"));}
            assertFalse("failed import must rollback",root(attempted).exists());
            File extracted=ArchiveFileImport.extract(ctx,Uri.fromFile(file),"7z",correct,password,p->{});
            assertTrue(new File(extracted,"messages.html").isFile());
        }finally{ArchiveFileImport.deleteTree(root(attempted));ArchiveFileImport.deleteTree(root(correct));file.delete();}
    }
    @Test public void encryptedZipRequiresPasswordBeforeIndexingAndCanBeRetried()throws Exception{
        char[] password="synthetic-secret-zip".toCharArray();
        File file=zip(password);String attempted=key(),correct=key();
        try{
            try{ArchiveFileImport.extract(ctx,Uri.fromFile(file),"zip",attempted,null,p->{});fail("Must ask for AES ZIP password");}
            catch(ArchiveFileImport.PasswordRequired expected){}
            assertFalse(root(attempted).exists());
            File extracted=ArchiveFileImport.extract(ctx,Uri.fromFile(file),"zip",correct,password,p->{});
            assertTrue(new File(extracted,"messages.html").isFile());
        }finally{ArchiveFileImport.deleteTree(root(attempted));ArchiveFileImport.deleteTree(root(correct));file.delete();}
    }
    @Test public void plainSevenZAndZipImportWithoutPassword()throws Exception{
        for(String format:new String[]{"zip","7z"}){
            File file=format.equals("zip")?zip(null):seven(null);String id=key();
            try{
                File extracted=ArchiveFileImport.extract(ctx,Uri.fromFile(file),format,id,null,p->{});
                assertTrue(new File(extracted,"messages.html").isFile());
            }finally{ArchiveFileImport.deleteTree(root(id));file.delete();}
        }
    }
    @Test public void bogusArchiveSignatureIsNotMistakenForPassword()throws Exception{
        File file=File.createTempFile("bogus-", ".7z",ctx.getCacheDir());
        try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[]{0x42,0x41,0x44,0x21});}
        try{
            try{ArchiveFileImport.detectFormat(ctx,Uri.fromFile(file),"bogus.7z");
                fail("Incorrect format should not be imported");}
            catch(IOException expected){assertFalse(expected.getMessage().toLowerCase(java.util.Locale.ROOT).contains("парол"));}
        }finally{file.delete();}
    }
}
